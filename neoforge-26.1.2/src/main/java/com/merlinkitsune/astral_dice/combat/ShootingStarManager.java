package com.merlinkitsune.astral_dice.combat;

import com.merlinkitsune.astral_dice.AstralDiceMod;
import com.merlinkitsune.astral_dice.component.ModAttachments;
import com.merlinkitsune.astral_dice.damage.ModDamageTypes;
import com.merlinkitsune.astral_dice.item.ModItems;
import com.merlinkitsune.astral_dice.item.StarLightManager;
import com.merlinkitsune.astral_dice.particle.GlowingDustOptions;
import net.minecraft.core.particles.ColorParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.NeutralMob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.item.Item;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.theillusivec4.curios.api.CuriosApi;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import com.merlinkitsune.starenginelib.combat.HostileTargets;

/**
 * 「飞星」筹码（紫色飞星 / 金色飞星）的**唯一执行器**：触发检测、粒子下落编排、命中结算。
 *
 * <h2>技能口径（2026-09-21 用户原文）</h2>
 * <ul>
 *   <li><b>紫色飞星（史诗）</b>：路过敌对目标 ⇒ 使其受到 1 点伤害并自身 +1 层「星光」；</li>
 *   <li><b>金色飞星（传奇）</b>：同上，但基础伤害 2 点；若目标为**精英怪物或 Boss**，
 *       额外造成**自身当前「星光」层数**的伤害；</li>
 *   <li><b>共享计时器</b>：两枚筹码共用**同一个 10 秒冷却**（{@link #COOLDOWN_TICKS}），
 *       该计时器**不创建任何效果**，只在 tooltip 里显示剩余秒数；</li>
 *   <li><b>粒子时序</b>：粒子自目标**头顶上方**下落，**落到目标头顶（碰撞箱上沿）即视为命中**并结算筹码伤害；
 *       高度 ×3（用户裁决 2026-09-24）⇒ 行程 ×3.6（历史 1.2 × 3），时长相伴减半（{@link #FALL_TICKS} = 10 tick）
 *       ⇒ **每 tick 下落速度 = 原口径的 6 倍**、起点比旧口径高得多。
 *       两枚同时装备时**依次下落**（先紫色飞星），第一束命中后再隔 0.5 秒（{@link #VOLLEY_GAP_TICKS}）落第二束。</li>
 * </ul>
 *
 * <h2>触发判定</h2>
 * <ul>
 *   <li><b>水平圆柱</b>：水平距离 ≤ {@link #RADIUS} 格（用户裁决 3 格），取最近的合格目标；</li>
 *   <li><b>同高度窗口</b>：脚底高差 ≤ {@link #PASS_VERTICAL_WINDOW} 格（用户 2026-09-22 裁决）
 *       —— 只允许同一层（含跳跃与上下台阶），**禁用** AABB 粗筛的竖直容差（它达「脚底 −3 … +4.8」，
 *       会让站上 4 格高台的玩家命中地面的怪）；</li>
 *   <li>「敌对目标」= 全局唯一入口 {@link HostileTargets#isHostile(net.minecraft.world.entity.Entity, net.minecraft.world.entity.Entity)}
 *       （带「视谁为敌」上下文）**再减去「未被激怒的中立生物」**（用户 2026-09-24 裁决
 *       「飞星应当只对敌对目标生效」）—— 判据见 {@link #isStarTarget}。</li>
 * </ul>
 *
 * <h2>伤害与粒子</h2>
 * <ul>
 *   <li>伤害类型 {@code astral_dice:true_damage}（无视护甲值 / 盔甲韧性），
 *       **击杀归属玩家**（{@code causing} = 玩家）；不构成玩家直接攻击 ⇒ 不触发骰战 / 骰神赐福；</li>
 *   <li>粒子复用「活体书页」的**观感与轨迹参数**（拖尾间距 / 每步粒数 / 命中爆闪），
 *       但把不可染色的 {@code END_ROD} 换成**可染色且自发光**的 {@link GlowingDustOptions}
 *       （本模组粒子 {@code astral_dice:glowing_dust}：全亮光照 + 半透明混合，见 {@code client/GlowingDustParticle}）
 *       —— 紫色 / 金色各一色（{@code END_ROD} 是固定白色，无法满足「金色飞星粒子 = 同效果但改金色」的要求；
 *       而原版 {@code DustParticleOptions} 没有覆写光照 ⇒ 暗处不发光，正是本次要补的缺口）。</li>
 * </ul>
 */
@EventBusSubscriber(modid = AstralDiceMod.MODID)
public final class ShootingStarManager {

    /** 共享触发冷却：10 秒（两枚筹码共用，用户裁决）。 */
    public static final int COOLDOWN_TICKS = 200;

    /** 路过判定半径（格，用户裁决）。 */
    public static final double RADIUS = 3.0D;

    /**
     * 路过判定的**竖直窗口**（格，以双方**脚底**之差的绝对值计）—— 用户 2026-09-22 裁决。
     *
     * <p>为什么需要它：候选集用的 {@code inflate(RADIUS)} 是**三轴同时膨胀的 AABB**，竖直容差实为
     * 「脚底 −3 … 脚底 +4.8」——比水平还宽，于是站在 4 格高的台子上也会命中地面的怪
     * （实机取证：靶子 +4 格被接受、+6 格被拒绝）。技能语义是「**路过**」，只允许同一层
     * （含跳跃与上下台阶），故在粗筛之后再做这层精筛。
     */
    public static final double PASS_VERTICAL_WINDOW = 2.0D;

    /**
     * 粒子下落耗时（tick）—— **0.5 秒**（用户裁决 2026-09-24）。
     *
     * <p>推导：高度 ×3（{@link #FALL_DISTANCE_MULTIPLIER} 由 1.2 → 3.6）+ **速度 ×6**
     * ⇒ 时长 = 行程 ×3 ÷ 速度 ×6 = 原时长的 **1/2**（20 → 10 tick）。
     */
    public static final int FALL_TICKS = 10;

    /** 两枚同时装备时，第一束命中后到第二束开始下落的间隔 —— 0.5 秒（用户 2026-09-24 裁决「间隔加快到 2 倍」）。 */
    public static final int VOLLEY_GAP_TICKS = 10;

    /** 旧口径的落体参考高（格）：起点 = 目标脚底上方 3.0 格、终点 = 碰撞箱中心。 */
    private static final double LEGACY_FALL_REFERENCE = 3.0D;

    /**
     * 下落**行程**相对旧口径（{@link #LEGACY_FALL_REFERENCE} − 碰撞箱高 / 2）的倍数
     * = 历史提速 1.2 倍（2026-09-21 用户裁决）× 本次抬高 3 倍（2026-09-24 用户裁决「高度提升 3 倍」）= **3.6**。
     */
    private static final double FALL_DISTANCE_MULTIPLIER = 3.6D;

    /** 落体行程下界（格）：极端体型（碰撞箱高 ≥ 6 格）下旧行程 ≤ 0，兜底保证仍有可见下落。 */
    private static final double MIN_FALL_DISTANCE = 0.6D;

    // ---- 粒子参数（取自 LivingPageFlightScheduler，保持同款观感）----
    private static final double TRAIL_SPACING = 0.45D;
    private static final int TRAIL_PARTICLES_PER_STEP = 2;
    private static final int IMPACT_BURST_PARTICLES = 16;
    private static final float DUST_SCALE = 1.2F;

    /** 安全上界：单束粒子最长存活（tick），超过即丢弃（防目标瞬移导致永久滞留）。 */
    private static final long MAX_AGE_TICKS = 200L;

    private static final Logger LOGGER = LoggerFactory.getLogger(ShootingStarManager.class);

    private static final List<Pending> PENDING = new ArrayList<>();

    /** 单束粒子的类型（决定伤害与颜色）。 */
    public enum Kind {
        /** 紫色飞星：1 点伤害，紫色粒子。 */
        PURPLE(1.0F, 0xB36BFF),
        /** 金色飞星：2 点伤害 + 精英/Boss 额外星光层数伤害，金色粒子。 */
        GOLDEN(2.0F, 0xFFD14A);

        private final float baseDamage;
        private final int color;

        Kind(float baseDamage, int color) {
            this.baseDamage = baseDamage;
            this.color = color;
        }

        public float baseDamage() {
            return baseDamage;
        }
    }

    private static final class Pending {
        private final ServerLevel level;
        private final ServerPlayer caster;
        private final LivingEntity target;
        private final Kind kind;
        private final long launchTick;

        private Vec3 origin;
        private Vec3 pos;
        private int elapsed;

        private Pending(ServerLevel level, ServerPlayer caster, LivingEntity target, Kind kind, long launchTick) {
            this.level = level;
            this.caster = caster;
            this.target = target;
            this.kind = kind;
            this.launchTick = launchTick;
        }
    }

    private ShootingStarManager() {
    }

    // ==================================================================================
    // 落体几何（起点 / 终点 / 行程）—— 公开给冒烟探针核对「1.2 倍速 + 头顶命中」口径
    // ==================================================================================

    /**
     * 本口径的落体行程（格）= **旧行程 × {@link #FALL_DISTANCE_MULTIPLIER}**（= 1.2 × 3.0 = 3.6）。
     *
     * <p>旧行程 = {@link #LEGACY_FALL_REFERENCE} − 碰撞箱高 / 2（脚底上方 3.0 格 → 碰撞箱中心）；
     * 本次（2026-09-24 用户裁决「高度提升 3 倍、下落速度加快到 6 倍」）行程再 ×3、时长减半
     * （{@link #FALL_TICKS} 20 → 10）⇒ **每 tick 下落速度 = 原口径的 6 倍**、起点相应抬高 3 倍。
     */
    public static double fallDistance(LivingEntity target) {
        double legacy = LEGACY_FALL_REFERENCE - target.getBbHeight() * 0.5D;
        return Math.max(MIN_FALL_DISTANCE, legacy * FALL_DISTANCE_MULTIPLIER);
    }

    /** 落体起点 = 目标**头顶**（碰撞箱上沿）正上方 {@link #fallDistance} 格，水平取目标所在列。 */
    public static Vec3 launchOrigin(LivingEntity target) {
        Vec3 feet = target.position();
        return new Vec3(feet.x, feet.y + target.getBbHeight() + fallDistance(target), feet.z);
    }

    /** 落体终点 = 目标**头顶**（碰撞箱上沿）正中 —— 「落到目标头顶即视为命中」（用户裁决 2026-09-21）。 */
    public static Vec3 impactPoint(LivingEntity target) {
        AABB bb = target.getBoundingBox();
        return new Vec3((bb.minX + bb.maxX) * 0.5D, bb.maxY, (bb.minZ + bb.maxZ) * 0.5D);
    }

    // ==================================================================================
    // 触发检测（由两枚筹码的 curioTick 调用；共享冷却 + 幂等，重复调用安全）
    // ==================================================================================

    /**
     * 每 tick 的触发检测。两枚筹码同时装备时会各调用一次 —— 第一次触发即写入共享冷却，
     * 第二次因此在冷却早退，故**天然幂等**；无目标时最多重复一次 3 格 AABB 查询，开销可忽略。
     */
    public static void tick(ServerPlayer player) {
        if (player == null || player.level().isClientSide()) return;
        long now = player.level().getGameTime();
        if (now < ModAttachments.getShootingStarCooldownEnd(player)) return;

        boolean purple = isEquipped(player, ModItems.PURPLE_SHOOTING_STAR_CHIP.get());
        boolean golden = isEquipped(player, ModItems.GOLDEN_SHOOTING_STAR_CHIP.get());
        if (!purple && !golden) return;

        LivingEntity target = findPassingTarget(player);
        if (target == null) return;

        ModAttachments.setShootingStarCooldownEnd(player, now + COOLDOWN_TICKS);
        launchVolley(player, target, purple, golden, now);
    }

    private static boolean isEquipped(ServerPlayer player, Item item) {
        var curios = CuriosApi.getCuriosInventory(player);
        if (curios.isEmpty()) return false;
        return curios.get().findFirstCurio(s -> s.is(item)).isPresent();
    }

    /**
     * 取「路过窗口」内最近的合格敌对目标（不合格 = 出窗口 / 非敌对 / 未被激怒的中立生物）。
     *
     * <p><b>两级判据</b>（用户 2026-09-22 裁决「水平圆柱 + 同高度窗口」）：{@code inflate(RADIUS)} 的
     * AABB 只当**粗筛** —— 它三轴同时膨胀、竖直容差达「脚底 −3 … +4.8」，**不能**直接当命中判据；
     * 随后由 {@link #isWithinPassWindow} 做「**水平距离 ≤ {@link #RADIUS}** + **脚底高差 ≤
     * {@link #PASS_VERTICAL_WINDOW}**」的精筛。「最近的」也按**水平**距离取，与门限同口径。
     */
    private static LivingEntity findPassingTarget(ServerPlayer player) {
        AABB box = player.getBoundingBox().inflate(RADIUS);
        LivingEntity best = null;
        double bestDistSqr = Double.MAX_VALUE;
        for (LivingEntity candidate : player.level().getEntitiesOfClass(LivingEntity.class, box)) {
            if (candidate == player || !candidate.isAlive()) continue;
            if (!isStarTarget(player, candidate)) continue;
            if (!isWithinPassWindow(player, candidate)) continue;
            double distSqr = horizontalDistanceSqr(player, candidate);
            if (distSqr < bestDistSqr) {
                bestDistSqr = distSqr;
                best = candidate;
            }
        }
        return best;
    }

    /**
     * 飞星的「敌对目标」档位 = 全局唯一入口 {@link HostileTargets#isHostile(net.minecraft.world.entity.Entity, net.minecraft.world.entity.Entity)}
     * **再减去「未被激怒的中立生物」**（用户 2026-09-24 裁决「飞星应当只对敌对目标生效」）。
     *
     * <p>⚠️ 这是对全局口径的**有意收窄**，不是漏用入口：全局口径自 2026-09-24 重写起把「中立生物」
     * **一律**计入（不再要求 {@code isAngry()}），那套宽口径是为**主动技能的释放目标判定**
     * （秘密侦探 / 枪匠）定的；而飞星是**被动自动触发**（路过即落星）—— 不该凭空砸到未激怒的
     * 狼 / 铁傀儡 / 北极熊 / 蜜蜂。故此处回到重写**之前**的判据（中立生物须已被激怒）。
     *
     * <p>三步顺序与库的旧实现保持一致：① 敌对生物一律计入（末影人 / 僵尸猪灵**同时**实现
     * {@link Enemy} 与 {@link NeutralMob}，若先判中立会把「平静的末影人」错杀）；
     * ② 中立生物看 {@link NeutralMob#isAngry()}；③ 其余（含消费方 seam 声明的试验假人、
     * 以及「曾攻击过观察者的非同队玩家」）**完全走入口**，不在玩法代码里自建判定。
     */
    private static boolean isStarTarget(ServerPlayer player, LivingEntity candidate) {
        if (candidate instanceof Enemy) return true;
        if (candidate instanceof NeutralMob neutral) return neutral.isAngry();
        return HostileTargets.isHostile(player, candidate);
    }

    /**
     * 「路过窗口」精筛：**水平距离 ≤ {@link #RADIUS}** 且 **脚底高差 ≤ {@link #PASS_VERTICAL_WINDOW}**。
     *
     * <p>水平用两实体中心点的水平距离（不再是 AABB 的「半径 + 双方半宽」）⇒ 与文案「半径 N 格」字面一致；
     * 竖直按**脚底**差：玩家跳跃高度约 1.25 格、台阶 1 格都落在窗口内，2 格以上的高台/坑洞不再算「路过」。
     */
    private static boolean isWithinPassWindow(LivingEntity self, LivingEntity other) {
        if (horizontalDistanceSqr(self, other) > RADIUS * RADIUS) return false;
        return Math.abs(self.getY() - other.getY()) <= PASS_VERTICAL_WINDOW;
    }

    /** 两点之间的**水平**平方距离（忽略 Y）—— 与「路过窗口」同口径，「取最近」也用它。 */
    private static double horizontalDistanceSqr(LivingEntity a, LivingEntity b) {
        double dx = a.getX() - b.getX();
        double dz = a.getZ() - b.getZ();
        return dx * dx + dz * dz;
    }

    /**
     * 编排一次「齐射」：先紫色飞星，命中后间隔 0.5 秒再落金色；只装备一枚时直接落该枚。
     */
    private static void launchVolley(ServerPlayer player, LivingEntity target, boolean purple, boolean golden, long now) {
        // 26.1.2 平台差异:ServerPlayer#serverLevel() 已删;ServerPlayer#level() 的返回类型即 ServerLevel
        ServerLevel level = player.level();
        if (purple) {
            PENDING.add(new Pending(level, player, target, Kind.PURPLE, now));
            if (golden) {
                // 紫：t=0 开始 → t=10 命中；金：t=10+10=20 开始 → t=30 命中（各 0.5 秒落地、间隔 0.5 秒）
                PENDING.add(new Pending(level, player, target, Kind.GOLDEN, now + FALL_TICKS + VOLLEY_GAP_TICKS));
            }
        } else if (golden) {
            PENDING.add(new Pending(level, player, target, Kind.GOLDEN, now));
        }
    }

    // ==================================================================================
    // 粒子下落
    // ==================================================================================

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (PENDING.isEmpty()) return;
        process(event.getServer());
    }

    private static void process(MinecraftServer server) {
        if (server == null || PENDING.isEmpty()) return;
        List<Pending> due = new ArrayList<>();
        Iterator<Pending> it = PENDING.iterator();
        while (it.hasNext()) {
            due.add(it.next());
            it.remove();
        }
        for (Pending pending : due) {
            try {
                advance(pending);
            } catch (Exception ex) {
                LOGGER.warn("[Astral Dice] 飞星粒子结算失败，已丢弃本次下落", ex);
            }
        }
    }

    private static void advance(Pending pending) {
        long now = pending.level.getGameTime();
        // 尚未到该束的开始时刻（齐射的第二发在等待）：保留在队列里
        if (now < pending.launchTick) {
            PENDING.add(pending);
            return;
        }
        if (!stillValid(pending)) return;   // 目标/施法者已失效 ⇒ 直接丢弃（不结算）

        if (pending.origin == null) {
            // 起点 = 目标头顶正上方 fallDistance 格（用户裁决：提高发射高度），一旦起落即固定
            pending.origin = launchOrigin(pending.target);
            pending.pos = pending.origin;
        }

        Vec3 dest = impactPoint(pending.target);
        pending.elapsed++;
        if (now - pending.launchTick > MAX_AGE_TICKS) return;

        double progress = Math.min(1.0D, (double) pending.elapsed / (double) FALL_TICKS);
        Vec3 prev = pending.pos;
        Vec3 next = pending.origin.lerp(dest, progress);
        pending.pos = next;

        emitTrail(pending, prev, next);

        if (progress >= 1.0D) {
            impact(pending, dest);
            return;
        }
        PENDING.add(pending);
    }

    private static boolean stillValid(Pending pending) {
        if (pending.target == null || pending.target.isRemoved() || !pending.target.isAlive()) return false;
        if (pending.target.level() != pending.level) return false;
        if (pending.caster == null || pending.caster.isRemoved() || !pending.caster.isAlive()) return false;
        return pending.caster.level() == pending.level;
    }

    private static GlowingDustOptions dust(Kind kind) {
        return new GlowingDustOptions(kind.color, DUST_SCALE);
    }

    private static void emitTrail(Pending pending, Vec3 from, Vec3 to) {
        double length = from.distanceTo(to);
        int steps = Math.max(1, (int) Math.ceil(length / TRAIL_SPACING));
        for (int i = 1; i <= steps; i++) {
            Vec3 at = from.lerp(to, (double) i / (double) steps);
            pending.level.sendParticles(dust(pending.kind),
                    at.x, at.y, at.z, TRAIL_PARTICLES_PER_STEP, 0.03D, 0.03D, 0.03D, 0.0D);
        }
    }

    /** 粒子落到目标**头顶**（{@link #impactPoint}）—— **此刻才应用筹码伤害**（用户要求：先落粒子，后结算）。 */
    private static void impact(Pending pending, Vec3 center) {
        pending.level.sendParticles(ColorParticleOption.create(ParticleTypes.FLASH, 0xFFFFFF),
                center.x, center.y, center.z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
        pending.level.sendParticles(dust(pending.kind),
                center.x, center.y, center.z, IMPACT_BURST_PARTICLES, 0.25D, 0.25D, 0.25D, 0.02D);

        float damage = pending.kind.baseDamage();
        if (pending.kind == Kind.GOLDEN && EliteTargets.isEliteOrBoss(pending.target)) {
            // 额外伤害 = 玩家**当前**星光层数（在本层星光入账之前读取）
            damage += StarLightManager.get(pending.caster);
        }
        if (damage > 0.0F) {
            pending.target.hurt(ModDamageTypes.trueDamage(pending.level, pending.caster), damage);
        }
        // 命中即 +1 层「星光」（两枚筹码一致）
        StarLightManager.add(pending.caster, 1);
    }
}
