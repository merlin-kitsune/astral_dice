package com.merlinkitsune.astral_dice.combat;

import com.merlinkitsune.astral_dice.AstralDiceMod;
import com.merlinkitsune.astral_dice.damage.ModDamageTypes;
import com.merlinkitsune.astral_dice.effect.PrecisionStrikeEffect;
import com.merlinkitsune.astral_dice.particle.GlowingDustOptions;
import com.merlinkitsune.starenginelib.combat.HostileTargets;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.joml.Vector3f;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 「轨道轰炸」(机械师立牌 megas 主动)的**唯一执行器**：目标选择、粒子下落追踪、落地结算、爆炸视觉。
 *
 * <h2>技能口径（2026-09-26 用户原文）</h2>
 * <ul>
 *   <li><b>目标</b>：指定 1 个敌对目标，以其为中心 {@value #RADIUS} 格范围；</li>
 *   <li><b>次数</b>：每消耗 2 张卡牌 +1 次轰炸，上限 {@value #MAX_STRIKES} 次（激活需 ≥2 张 ⇒ 至少 1 次）；</li>
 *   <li><b>伤害</b>：单次命中随机 1 个怪物，基础 {@value #BASE_DAMAGE} 点；每消耗 1 张战斗牌，
 *       该次轰炸 + 该牌费用 ×2（战斗牌费用总和一次性算好，加到每次轰炸上）；</li>
 *   <li><b>真伤</b>：{@code skill_damage}（无视护甲、不进法伤链、击杀归属施法者）；</li>
 *   <li><b>命中顺序</b>：首次轰炸必命中指定目标，之后随机；</li>
 *   <li><b>间隔</b>：相邻两次轰炸的**发射**间隔 {@value #STRIKE_INTERVAL_TICKS} tick（0.5 秒）；</li>
 *   <li><b>偏好</b>：随机优先高威胁目标（精英/Boss + 血量降序加权），但保留低概率命中低威胁；</li>
 *   <li><b>无目标</b>：范围内无存活目标 ⇒ 丢弃剩余轰炸次数；</li>
 *   <li><b>精准打击</b>：消耗 ≥6 张卡牌时触发，每次命中给目标 +1 层（层数 = 被轰炸次数）；</li>
 *   <li><b>FX</b>：**火流星**（头白热 / 尾暗橙红 + 原版火焰拖尾）从目标上方
 *       {@value #FALL_HEIGHT} 格下落，追踪目标实时位置，下落 {@value #FALL_TICKS} tick（0.4 秒），
 *       落点为**目标脚下的地面**（非头顶），落地 TNT 级爆炸特效 + 熔岩爆燃（纯视觉，不破坏方块）。</li>
 * </ul>
 */
@EventBusSubscriber(modid = AstralDiceMod.MODID)
public final class OrbitalBombardmentManager {

    /** 轰炸范围半径（格，以指定目标为中心）。 */
    public static final double RADIUS = 12.0D;

    /** 单次轰炸基础伤害。 */
    public static final float BASE_DAMAGE = 2.0F;

    /** 轰炸次数上限。 */
    public static final int MAX_STRIKES = 10;

    /**
     * 相邻两次轰炸的**发射**间隔（tick）＝ 0.5 秒。
     *
     * <p>2026-09-27 用户裁决：原 1 秒「间隔太长」⇒ 缩短到 0.5 秒。计时口径为
     * **本轮开始下落 → 下一轮开始下落**（{@link Job#strikeStartAt} + 本值），
     * 而不是「落地 → 下一轮开始」，否则一轮周期会被下落耗时额外拉长。
     */
    public static final int STRIKE_INTERVAL_TICKS = 10;

    /** 粒子团下落耗时（tick）＝ 0.4 秒（2026-09-27 用户裁决：0.6 秒 → 0.4 秒）。 */
    public static final int FALL_TICKS = 8;

    /** 触发「精准打击」所需的最小消耗卡牌数。 */
    public static final int PRECISION_THRESHOLD = 6;

    /** 触发「精准打击」时，每消耗 1 张战斗牌额外加的伤害倍数（费用 × 该倍数）。 */
    public static final int COST_DAMAGE_MULTIPLIER = 2;

    /** 火流星配色：头部白热黄（核心）。 */
    private static final Vector3f FIRE_HEAD = new Vector3f(1.00F, 0.94F, 0.66F);

    /** 火流星配色：尾部暗橙红（余烬拖尾）。 */
    private static final Vector3f FIRE_TAIL = new Vector3f(0.86F, 0.24F, 0.05F);

    // scale 见 DUST_SCALE_HEAD / DUST_SCALE_TAIL（火流星：头大尾小）。

    /** 拖尾间距 / 每步粒数（取自飞星同款观感）。 */
    private static final double TRAIL_SPACING = 0.45D;
    private static final int TRAIL_PARTICLES_PER_STEP = 2;

    /** 落地爆闪的发光粒子数。 */
    private static final int IMPACT_BURST_PARTICLES = 16;

    /** 每 tick 贴在弹体上的原版火焰粒数（火流星「燃烧」观感）。 */
    private static final int FLAME_PER_TICK = 4;

    /** 落地熔岩爆燃粒数（火流星砸地的灼烧感）。 */
    private static final int IMPACT_LAVA_PARTICLES = 14;

    /** 落地火焰余烬粒数。 */
    private static final int IMPACT_FLAME_PARTICLES = 40;

    /** 弹体 scale：头部（白热核）到尾部（余烬）由大到小。 */
    private static final float DUST_SCALE_HEAD = 1.9F;
    private static final float DUST_SCALE_TAIL = 0.9F;

    /**
     * 粒子团起始高度（目标正上方，格）。
     *
     * <p>2026-09-27 用户裁决：原 6 格「发射高度太低」⇒ 至少翻倍，取 12.0（= 原 2 倍）。
     */
    private static final double FALL_HEIGHT = 12.0D;

    /**
     * 落点地面扫描深度（格）：自目标脚底向下最多扫这么多格找可站立面。
     *
     * <p>2026-09-27 用户裁决：轰炸「并没有落到地面，而是跟飞星一样只砸到头顶就爆了」
     * ⇒ 落点改为**目标脚下的地面**（见 {@link #impactPoint}）。
     */
    private static final int GROUND_SCAN_DEPTH = 16;

    /** 取目标脚底格时的向上容差（目标恰好停在整数高度时不至于把脚底格算成空气）。 */
    private static final double GROUND_EPSILON = 0.001D;

    /** 安全上界：单次下落最长存活（tick），超时丢弃（防目标瞬移导致永久滞留）。 */
    private static final long MAX_AGE_TICKS = 200L;

    private static final Logger LOGGER = LoggerFactory.getLogger(OrbitalBombardmentManager.class);

    /** 进行中的轰炸编排队列。 */
    private static final List<Job> JOBS = new ArrayList<>();

    private OrbitalBombardmentManager() {
    }

    /**
     * 编排一场完整的轨道轰炸。
     *
     * @param caster        施法玩家
     * @param primaryTarget 指定目标（首次轰炸必命中它）
     * @param totalStrikes  轰炸总次数（已按「每 2 张卡 +1 次」算好，上限 10）
     * @param perStrikeDamage 每次轰炸的伤害（= 基础 2 + 战斗牌费用总和 ×2）
     * @param precision      是否触发「精准打击」（消耗 ≥6 张卡）
     */
    public static void schedule(ServerPlayer caster, LivingEntity primaryTarget,
                                int totalStrikes, float perStrikeDamage, boolean precision) {
        if (caster == null || primaryTarget == null || caster.level().isClientSide()) return;
        if (primaryTarget.isRemoved() || !primaryTarget.isAlive()) return;
        if (totalStrikes <= 0) return;
        JOBS.add(new Job(caster.serverLevel(), caster, primaryTarget,
                totalStrikes, perStrikeDamage, precision));
    }

    // ==================================================================================
    // 每 tick 调度
    // ==================================================================================

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (JOBS.isEmpty()) return;
        process(event.getServer());
    }

    private static void process(MinecraftServer server) {
        if (server == null || JOBS.isEmpty()) return;
        List<Job> due = new ArrayList<>();
        Iterator<Job> it = JOBS.iterator();
        while (it.hasNext()) {
            due.add(it.next());
            it.remove();
        }
        for (Job job : due) {
            try {
                advance(job);
            } catch (Exception ex) {
                LOGGER.warn("[Astral Dice] 轨道轰炸结算失败，已丢弃本场轰炸", ex);
            }
        }
    }

    private static void advance(Job job) {
        long now = job.level.getGameTime();
        // 施法者失效 ⇒ 丢弃整场轰炸
        if (job.caster == null || job.caster.isRemoved() || !job.caster.isAlive()) return;
        if (job.caster.level() != job.level) return;

        // 已完成全部轰炸 ⇒ 结束
        if (job.completed >= job.totalStrikes) return;

        if (job.falling == null) {
            // 尚未到下一轮的开始时刻（间隔 1 秒）
            if (now < job.nextStrikeAt) {
                JOBS.add(job);
                return;
            }
            // 选择本轮目标（首轮必命中指定目标，之后随机）
            LivingEntity target = job.completed == 0 ? job.primary : pickTarget(job);
            if (target == null || !target.isAlive() || target.isRemoved()) {
                // 范围内无存活目标 ⇒ 丢弃剩余轰炸次数
                return;
            }
            job.strikeStartAt = now;
            job.falling = new FallState(target, launchOrigin(target));
        }

        FallState fall = job.falling;
        // 下落目标失效 ⇒ 丢弃本轮（不再结算），进入下一轮
        if (!stillValid(fall.target, job.level)) {
            job.falling = null;
            job.completed++;
            job.nextStrikeAt = job.strikeStartAt + STRIKE_INTERVAL_TICKS;
            JOBS.add(job);
            return;
        }

        // 追踪目标实时位置，下落
        long age = now - job.lastTick;
        fall.elapsed++;
        if (fall.elapsed > MAX_AGE_TICKS) {
            job.falling = null;
            job.completed++;
            job.nextStrikeAt = job.strikeStartAt + STRIKE_INTERVAL_TICKS;
            JOBS.add(job);
            return;
        }

        Vec3 dest = impactPoint(job.level, fall.target);
        double progress = Math.min(1.0D, (double) fall.elapsed / (double) FALL_TICKS);
        Vec3 prev = fall.pos;
        Vec3 next = fall.origin.lerp(dest, progress);
        fall.pos = next;
        job.lastTick = now;

        emitTrail(job, prev, next);

        if (progress >= 1.0D) {
            impact(job, fall.target, dest);
            job.falling = null;
            job.completed++;
            job.nextStrikeAt = job.strikeStartAt + STRIKE_INTERVAL_TICKS;
        }
        JOBS.add(job);
    }

    // ==================================================================================
    // 目标选择（高威胁偏好加权随机）
    // ==================================================================================

    /**
     * 从指定目标周围 {@value #RADIUS} 格内选 1 个存活敌对目标，高威胁偏好加权（精英/Boss + 血量降序），
     * 但保留低概率命中低威胁。
     */
    private static LivingEntity pickTarget(Job job) {
        AABB box = job.primary.getBoundingBox().inflate(RADIUS);
        List<LivingEntity> candidates = new ArrayList<>();
        for (LivingEntity e : job.level.getEntitiesOfClass(LivingEntity.class, box)) {
            if (e == job.caster || !e.isAlive() || e.isRemoved()) continue;
            if (!HostileTargets.isHostile(job.caster, e)) continue;
            candidates.add(e);
        }
        if (candidates.isEmpty()) return null;

        // 高威胁偏好：精英/Boss 与高血量获得更高权重，但低威胁仍保留 1 的基准权重（低概率被选中）
        candidates.sort(Comparator.comparingDouble((LivingEntity e) -> e.getMaxHealth()).reversed());
        long totalWeight = 0L;
        long[] weights = new long[candidates.size()];
        for (int i = 0; i < candidates.size(); i++) {
            long w = 1L; // 基准权重，保证低威胁也有概率被选中
            LivingEntity e = candidates.get(i);
            if (EliteTargets.isEliteOrBoss(e)) w += 8L;
            w += (long) Math.max(0, e.getMaxHealth() / 10.0D); // 每 10 点血量 +1 权重
            weights[i] = w;
            totalWeight += w;
        }
        long roll = ThreadLocalRandom.current().nextLong(totalWeight);
        long acc = 0L;
        for (int i = 0; i < candidates.size(); i++) {
            acc += weights[i];
            if (roll < acc) return candidates.get(i);
        }
        return candidates.get(candidates.size() - 1);
    }

    // ==================================================================================
    // 落体几何 + 粒子 + 落地结算
    // ==================================================================================

    /** 落体起点 = 目标头顶正上方 {@value #FALL_HEIGHT} 格。 */
    private static Vec3 launchOrigin(LivingEntity target) {
        Vec3 feet = target.position();
        return new Vec3(feet.x, feet.y + target.getBbHeight() + FALL_HEIGHT, feet.z);
    }

    /**
     * 落体终点 = **目标脚下的地面**（不再是目标头顶）。
     *
     * <p>2026-09-27 用户裁决：「轰炸并没有落到地面，而是跟飞星一样只砸到头顶就爆了」
     * ⇒ 落点取目标所在列**最近的可站立面顶面**；目标悬空（飞行怪 / 半空）时继续下落到
     * 真正的地面。伤害目标仍是 {@code target} 本身，与落点位置解耦。
     */
    private static Vec3 impactPoint(net.minecraft.server.level.ServerLevel level, LivingEntity target) {
        return new Vec3(target.getX(), groundSurfaceY(level, target), target.getZ());
    }

    /** 目标脚下最近的可站立面顶面 Y；整列无可站立面（虚空/悬空）时退回目标脚底 Y。 */
    private static double groundSurfaceY(net.minecraft.server.level.ServerLevel level, LivingEntity target) {
        int bx = net.minecraft.util.Mth.floor(target.getX());
        int bz = net.minecraft.util.Mth.floor(target.getZ());
        int startY = net.minecraft.util.Mth.floor(target.getY() + GROUND_EPSILON);
        net.minecraft.core.BlockPos.MutableBlockPos pos = new net.minecraft.core.BlockPos.MutableBlockPos();
        for (int y = startY; y >= startY - GROUND_SCAN_DEPTH; y--) {
            pos.set(bx, y, bz);
            net.minecraft.world.level.block.state.BlockState state = level.getBlockState(pos);
            if (!state.isAir() && state.blocksMotion()) {
                return (double) y + 1.0D;
            }
        }
        return target.getY();
    }

    private static boolean stillValid(LivingEntity target, ServerLevel level) {
        return target != null && !target.isRemoved() && target.isAlive() && target.level() == level;
    }

    /** 火流星粒子（颜色随「尾 → 头」在 {@link #FIRE_TAIL} 与 {@link #FIRE_HEAD} 间插值）。 */
    private static GlowingDustOptions dust(Vector3f color, float scale) {
        return new GlowingDustOptions(color, scale);
    }

    private static void emitTrail(Job job, Vec3 from, Vec3 to) {
        double length = from.distanceTo(to);
        int steps = Math.max(1, (int) Math.ceil(length / TRAIL_SPACING));
        for (int i = 1; i <= steps; i++) {
            double f = (double) i / (double) steps;                 // 0 = 尾, 1 = 头
            Vec3 at = from.lerp(to, f);
            Vector3f col = new Vector3f(FIRE_TAIL).lerp(FIRE_HEAD, (float) f);
            float scale = DUST_SCALE_TAIL + (DUST_SCALE_HEAD - DUST_SCALE_TAIL) * (float) f;
            job.level.sendParticles(dust(col, scale), at.x, at.y, at.z,
                    TRAIL_PARTICLES_PER_STEP, 0.04D, 0.04D, 0.04D, 0.0D);
        }
        // 燃烧尾焰：原版火焰粒子贴在弹体当前位置，制造「火球在烧」的观感
        job.level.sendParticles(ParticleTypes.FLAME, to.x, to.y, to.z,
                FLAME_PER_TICK, 0.09D, 0.09D, 0.09D, 0.015D);
    }

    /**
     * 落地：TNT 级爆炸视觉（纯视觉，不破坏方块）+ 单体真伤结算 + 精准打击层数。
     */
    private static void impact(Job job, LivingEntity target, Vec3 center) {
        // 爆炸视觉（不破坏方块）:TNT 级烟团 + 闪光 + 熔岩爆燃 + 火焰余烬（火流星砸地）
        job.level.sendParticles(ParticleTypes.EXPLOSION_EMITTER,
                center.x, center.y, center.z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
        job.level.sendParticles(ParticleTypes.FLASH,
                center.x, center.y, center.z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
        job.level.sendParticles(ParticleTypes.LAVA,
                center.x, center.y, center.z, IMPACT_LAVA_PARTICLES, 0.8D, 0.4D, 0.8D, 0.0D);
        job.level.sendParticles(ParticleTypes.FLAME,
                center.x, center.y, center.z, IMPACT_FLAME_PARTICLES, 1.1D, 0.5D, 1.1D, 0.06D);
        job.level.sendParticles(dust(new Vector3f(1.00F, 0.45F, 0.12F), DUST_SCALE_HEAD),
                center.x, center.y, center.z, IMPACT_BURST_PARTICLES, 0.35D, 0.30D, 0.35D, 0.03D);
        job.level.playSound(null, center.x, center.y, center.z,
                SoundEvents.GENERIC_EXPLODE, SoundSource.BLOCKS, 4.0F,
                (1.0F + (job.level.random.nextFloat() - job.level.random.nextFloat()) * 0.2F) * 0.7F);

        // 精准打击：命中 +1 层（先加层，结算时读到新层数）
        if (job.precision) {
            PrecisionStrikeEffect.addStacks(target, 1);
        }
        // 伤害 = 基础 + 战斗牌费用×2 + 精准打击层数
        float damage = job.perStrikeDamage + PrecisionStrikeEffect.getStacks(target);
        if (damage > 0.0F) {
            DiceCombatEvents.aoeProcessing = true;
            try {
                target.hurt(ModDamageTypes.skillDamage(job.level, job.caster), damage);
            } finally {
                DiceCombatEvents.aoeProcessing = false;
            }
            // 全局伤害显示规定（AGENTS 第 386 条③）：技能伤害**必须**弹跳字，
            // 与「怪力侦探投掷」「活体书页」同口径（绿字 0x7CFC00；一实体一数字取最新值）。
            // ⚠️ 不能指望骰战路径代发：本伤害以 aoeProcessing 包裹 ⇒ 骰战结算被早退，
            //    数字必须在此显式补发（2026-09-27 用户报障「该技能伤害完全不显示伤害数字」）。
            com.merlinkitsune.astral_dice.network.DamageNumberPayload.send(target, Math.round(damage), LivingPageImpact.SPELL_DAMAGE_COLOR);
        }
    }

    // ==================================================================================
    // 内部类型
    // ==================================================================================

    private static final class Job {
        final ServerLevel level;
        final ServerPlayer caster;
        final LivingEntity primary;
        final int totalStrikes;
        final float perStrikeDamage;
        final boolean precision;

        int completed = 0;
        long nextStrikeAt = 0L;
        /** 本轮开始下落的 tick（间隔按「发射 → 发射」计：{@code strikeStartAt + STRIKE_INTERVAL_TICKS}）。 */
        long strikeStartAt = 0L;
        long lastTick = 0L;
        FallState falling = null;

        Job(ServerLevel level, ServerPlayer caster, LivingEntity primary,
            int totalStrikes, float perStrikeDamage, boolean precision) {
            this.level = level;
            this.caster = caster;
            this.primary = primary;
            this.totalStrikes = totalStrikes;
            this.perStrikeDamage = perStrikeDamage;
            this.precision = precision;
            this.nextStrikeAt = level.getGameTime();
            this.lastTick = level.getGameTime();
        }
    }

    private static final class FallState {
        final LivingEntity target;
        final Vec3 origin;
        Vec3 pos;
        int elapsed = 0;

        FallState(LivingEntity target, Vec3 origin) {
            this.target = target;
            this.origin = origin;
            this.pos = origin;
        }
    }
}
