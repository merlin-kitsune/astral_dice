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
 *   <li><b>间隔</b>：相邻两次轰炸间隔 {@value #STRIKE_INTERVAL_TICKS} tick（1 秒）；</li>
 *   <li><b>偏好</b>：随机优先高威胁目标（精英/Boss + 血量降序加权），但保留低概率命中低威胁；</li>
 *   <li><b>无目标</b>：范围内无存活目标 ⇒ 丢弃剩余轰炸次数；</li>
 *   <li><b>精准打击</b>：消耗 ≥6 张卡牌时触发，每次命中给目标 +1 层（层数 = 被轰炸次数）；</li>
 *   <li><b>FX</b>：白色发光粒子团从目标头顶高处下落（追踪目标实时位置，下落 {@value #FALL_TICKS} tick
 *       = 0.6 秒），落地 TNT 级爆炸特效（纯视觉，不破坏方块）。</li>
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

    /** 相邻两次轰炸的间隔（tick）＝ 1 秒。 */
    public static final int STRIKE_INTERVAL_TICKS = 20;

    /** 粒子团下落耗时（tick）＝ 0.6 秒。 */
    public static final int FALL_TICKS = 12;

    /** 触发「精准打击」所需的最小消耗卡牌数。 */
    public static final int PRECISION_THRESHOLD = 6;

    /** 触发「精准打击」时，每消耗 1 张战斗牌额外加的伤害倍数（费用 × 该倍数）。 */
    public static final int COST_DAMAGE_MULTIPLIER = 2;

    /** 白色发光粒子团颜色（纯白）。 */
    private static final Vector3f WHITE = new Vector3f(1.0F, 1.0F, 1.0F);

    /** 发光粒子团 scale（头部大小的观感由多颗聚集 + 大 scale 模拟）。 */
    private static final float DUST_SCALE = 1.6F;

    /** 拖尾间距 / 每步粒数（取自飞星同款观感）。 */
    private static final double TRAIL_SPACING = 0.45D;
    private static final int TRAIL_PARTICLES_PER_STEP = 2;

    /** 落地爆闪的发光粒子数。 */
    private static final int IMPACT_BURST_PARTICLES = 16;

    /** 粒子团起始高度（目标头顶正上方，格）。 */
    private static final double FALL_HEIGHT = 6.0D;

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
            job.falling = new FallState(target, launchOrigin(target));
        }

        FallState fall = job.falling;
        // 下落目标失效 ⇒ 丢弃本轮（不再结算），进入下一轮
        if (!stillValid(fall.target, job.level)) {
            job.falling = null;
            job.completed++;
            job.nextStrikeAt = job.level.getGameTime() + STRIKE_INTERVAL_TICKS;
            JOBS.add(job);
            return;
        }

        // 追踪目标实时位置，下落
        long age = now - job.lastTick;
        fall.elapsed++;
        if (fall.elapsed > MAX_AGE_TICKS) {
            job.falling = null;
            job.completed++;
            job.nextStrikeAt = job.level.getGameTime() + STRIKE_INTERVAL_TICKS;
            JOBS.add(job);
            return;
        }

        Vec3 dest = impactPoint(fall.target);
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
            job.nextStrikeAt = job.level.getGameTime() + STRIKE_INTERVAL_TICKS;
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

    /** 落体终点 = 目标碰撞箱上沿正中（落地 = 命中）。 */
    private static Vec3 impactPoint(LivingEntity target) {
        AABB bb = target.getBoundingBox();
        return new Vec3((bb.minX + bb.maxX) * 0.5D, bb.maxY, (bb.minZ + bb.maxZ) * 0.5D);
    }

    private static boolean stillValid(LivingEntity target, ServerLevel level) {
        return target != null && !target.isRemoved() && target.isAlive() && target.level() == level;
    }

    private static GlowingDustOptions dust() {
        return new GlowingDustOptions(WHITE, DUST_SCALE);
    }

    private static void emitTrail(Job job, Vec3 from, Vec3 to) {
        double length = from.distanceTo(to);
        int steps = Math.max(1, (int) Math.ceil(length / TRAIL_SPACING));
        for (int i = 1; i <= steps; i++) {
            Vec3 at = from.lerp(to, (double) i / (double) steps);
            job.level.sendParticles(dust(), at.x, at.y, at.z,
                    TRAIL_PARTICLES_PER_STEP, 0.03D, 0.03D, 0.03D, 0.0D);
        }
    }

    /**
     * 落地：TNT 级爆炸视觉（纯视觉，不破坏方块）+ 单体真伤结算 + 精准打击层数。
     */
    private static void impact(Job job, LivingEntity target, Vec3 center) {
        // 爆炸视觉（不破坏方块）：中心大烟团 + 白色闪光 + 爆炸音效
        job.level.sendParticles(ParticleTypes.EXPLOSION_EMITTER,
                center.x, center.y, center.z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
        job.level.sendParticles(ParticleTypes.FLASH,
                center.x, center.y, center.z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
        job.level.sendParticles(dust(), center.x, center.y, center.z,
                IMPACT_BURST_PARTICLES, 0.25D, 0.25D, 0.25D, 0.02D);
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
