package com.merlinkitsune.astral_dice.combat;

import com.merlinkitsune.astral_dice.AstralDiceMod;
import com.merlinkitsune.astral_dice.component.ModAttachments;
import com.merlinkitsune.astral_dice.damage.ModDamageTypes;
import com.merlinkitsune.astral_dice.item.ModItems;
import com.merlinkitsune.astral_dice.item.StarLightManager;
import com.merlinkitsune.starenginelib.item.CuriosCompat;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Vector3f;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import com.merlinkitsune.starenginelib.combat.HostileTargets;

/**
 * 「飞星」筹码（紫色飞星 / 金色飞星）的**唯一执行器**（1.20.1 Forge 移植版）：
 * 触发检测、粒子下落编排、命中结算。语义与 1.21.1 侧**逐字等价**。
 *
 * <h2>技能口径（2026-09-21 用户原文）</h2>
 * <ul>
 *   <li><b>紫色飞星（史诗）</b>：路过敌对目标且不对其发动攻击 ⇒ 使其受到 1 点伤害并自身 +1 层「星光」；</li>
 *   <li><b>金色飞星（传奇）</b>：同上，但基础伤害 2 点；若目标为**精英怪物或 Boss**，
 *       额外造成**自身当前「星光」层数**的伤害；</li>
 *   <li><b>共享计时器</b>：两枚筹码共用**同一个 10 秒冷却**，该计时器**不创建任何效果**，
 *       只在 tooltip 里显示剩余秒数；</li>
 *   <li><b>粒子时序</b>：粒子自目标**头顶上方**下落，**落到目标头顶（碰撞箱上沿）即视为命中**并结算筹码伤害；
 *       下落速度 = 旧口径的 {@link #FALL_SPEED_MULTIPLIER} 倍（用户裁决 2026-09-21），
 *       行程随之 ×1.2、而**总下落时间仍为 1 秒**（{@link #FALL_TICKS}）⇒ 起点比旧口径更高。
 *       两枚同时装备时依次下落（先紫色飞星），第一束命中后再隔 1 秒（{@link #VOLLEY_GAP_TICKS}）落第二束。</li>
 * </ul>
 *
 * <p><b>平台差异（与 1.21.1 逐字等价）</b>：本线用 {@code TickEvent.ServerTickEvent} +
 * {@code phase == END} 早退（1.21.1 为 {@code ServerTickEvent.Post}）；{@code ServerLevel}
 * 由 {@code (ServerLevel) player.level()} 取得（1.21.1 用 {@code player.serverLevel()}）；
 * Curios 经库 shim {@code CuriosCompat}（1.21.1 直用 {@code CuriosApi}）。
 * 调度器写法照 {@code event/LivingPageFlightScheduler}。
 *
 * <p>粒子复用「活体书页」的观感与轨迹参数，但把不可染色的 {@code END_ROD} 换成可染色的
 * {@link DustParticleOptions}（紫 / 金各一色）。
 */
@Mod.EventBusSubscriber(modid = AstralDiceMod.MODID)
public final class ShootingStarManager {

    /** 共享触发冷却：10 秒（两枚筹码共用，用户裁决）。 */
    public static final int COOLDOWN_TICKS = 200;

    /** 路过判定半径（格，用户裁决）。 */
    public static final double RADIUS = 3.0D;

    /** 粒子下落耗时：1 秒（用户裁决；2026-09-21 改速后**时长保持不变**）。 */
    public static final int FALL_TICKS = 20;

    /** 两枚同时装备时，第一束命中后到第二束开始下落的间隔：1 秒（用户裁决）。 */
    public static final int VOLLEY_GAP_TICKS = 20;

    /** 「不对其发动攻击」的判定窗口（tick）：与共享冷却同周期。 */
    public static final int ATTACK_GRACE_TICKS = COOLDOWN_TICKS;

    /** 旧口径的落体参考高（格）：起点 = 目标脚底上方 3.0 格、终点 = 碰撞箱中心。 */
    private static final double LEGACY_FALL_REFERENCE = 3.0D;

    /** 下落速度相对旧口径的倍数（用户裁决 2026-09-21：加快至 1.2 倍）。 */
    private static final double FALL_SPEED_MULTIPLIER = 1.2D;

    /** 落体行程下界（格）：极端体型（碰撞箱高 ≥ 6 格）下旧行程 ≤ 0，兜底保证仍有可见下落。 */
    private static final double MIN_FALL_DISTANCE = 0.6D;

    // ---- 粒子参数（取自 LivingPageFlightScheduler，保持同款观感）----
    private static final double TRAIL_SPACING = 0.45D;
    private static final int TRAIL_PARTICLES_PER_STEP = 2;
    private static final int IMPACT_BURST_PARTICLES = 16;
    private static final float DUST_SCALE = 1.2F;

    /** 安全上界：单束粒子最长存活（tick），超过即丢弃。 */
    private static final long MAX_AGE_TICKS = 200L;

    private static final Logger LOGGER = LoggerFactory.getLogger(ShootingStarManager.class);

    private static final List<Pending> PENDING = new ArrayList<>();

    /** 单束粒子的类型（决定伤害与颜色）。 */
    public enum Kind {
        /** 紫色飞星：1 点伤害，紫色粒子。 */
        PURPLE(1.0F, new Vector3f(0.70F, 0.42F, 1.00F)),
        /** 金色飞星：2 点伤害 + 精英/Boss 额外星光层数伤害，金色粒子。 */
        GOLDEN(2.0F, new Vector3f(1.00F, 0.82F, 0.29F));

        private final float baseDamage;
        private final Vector3f color;

        Kind(float baseDamage, Vector3f color) {
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
     * 新口径的落体行程（格）= **旧行程 × {@link #FALL_SPEED_MULTIPLIER}**。
     *
     * <p>旧行程 = {@link #LEGACY_FALL_REFERENCE} − 碰撞箱高 / 2（脚底上方 3.0 格 → 碰撞箱中心）；
     * 时长固定为 {@link #FALL_TICKS}（1 秒，用户裁决「保持总下落时间不变」）
     * ⇒ 行程 ×1.2 即**每 tick 下落速度 ×1.2**，起点相应抬高（用户裁决「提高发射高度」）。
     */
    public static double fallDistance(LivingEntity target) {
        double legacy = LEGACY_FALL_REFERENCE - target.getBbHeight() * 0.5D;
        return Math.max(MIN_FALL_DISTANCE, legacy * FALL_SPEED_MULTIPLIER);
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
     * 第二次因此在冷却早退，故**天然幂等**。
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
        // ⚠️ 平台差异：1.20.1 的 CuriosApi.getCuriosInventory 返回 LazyOptional，
        //    本仓统一经库 shim CuriosCompat（返回 Optional）访问，写法与 1.21.1 侧一致。
        var curios = CuriosCompat.getCuriosInventory(player);
        if (curios.isEmpty()) return false;
        return curios.get().findFirstCurio(s -> s.is(item)).isPresent();
    }

    /** 取半径内最近的合格敌对目标（不合格 = 非敌对 / 最近被本玩家攻击过）。 */
    private static LivingEntity findPassingTarget(ServerPlayer player) {
        AABB box = player.getBoundingBox().inflate(RADIUS);
        LivingEntity best = null;
        double bestDistSqr = Double.MAX_VALUE;
        for (LivingEntity candidate : player.level().getEntitiesOfClass(LivingEntity.class, box)) {
            if (candidate == player || !candidate.isAlive()) continue;
            if (!HostileTargets.isHostile(player, candidate)) continue;
            if (attackedByPlayerRecently(player, candidate)) continue;
            double distSqr = player.distanceToSqr(candidate);
            if (distSqr < bestDistSqr) {
                bestDistSqr = distSqr;
                best = candidate;
            }
        }
        return best;
    }

    /** 该目标最近一次受伤是否来自本玩家（= 「对其发动过攻击」）。 */
    private static boolean attackedByPlayerRecently(ServerPlayer player, LivingEntity target) {
        if (target.getLastHurtByMob() != player) return false;
        int since = target.tickCount - target.getLastHurtByMobTimestamp();
        return since >= 0 && since < ATTACK_GRACE_TICKS;
    }

    /** 编排一次「齐射」：先紫色飞星，命中后间隔 1 秒再落金色；只装备一枚时直接落该枚。 */
    private static void launchVolley(ServerPlayer player, LivingEntity target, boolean purple, boolean golden, long now) {
        ServerLevel level = (ServerLevel) player.level();
        if (purple) {
            PENDING.add(new Pending(level, player, target, Kind.PURPLE, now));
            if (golden) {
                // 紫：t=0 开始 → t=20 命中；金：t=20+20=40 开始 → t=60 命中
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
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
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

    private static DustParticleOptions dust(Kind kind) {
        return new DustParticleOptions(kind.color, DUST_SCALE);
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
        pending.level.sendParticles(ParticleTypes.FLASH,
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
