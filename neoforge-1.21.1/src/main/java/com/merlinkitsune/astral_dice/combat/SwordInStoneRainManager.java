package com.merlinkitsune.astral_dice.combat;

import com.merlinkitsune.astral_dice.AstralDiceMod;
import com.merlinkitsune.astral_dice.damage.ModDamageTypes;
import com.merlinkitsune.astral_dice.particle.LibraryParticleOptions;
import com.merlinkitsune.astral_dice.particle.LibraryVariants;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * 石中剑-传说的粒子落击执行器。
 *
 * <p>赐福结束时扫描 24 格内的敌对目标，为每个目标排一束绿色+金色粒子：
 * 先在目标头顶上方 {@link #DROP_HEIGHT} 格蓄势 {@link #CHARGE_TICKS} tick，再用
 * {@link #FALL_TICKS} tick 落下（约 2.67 格/tick，快于活体书页飞行的 2.4 格/tick）。
 * 粒子抵达目标碰撞箱上沿时才结算 {@code astral_dice:card_spell} 伤害，而不是在赐福结束的同一 tick
 * 直接扣血。多目标按 {@link #MAX_STAGGER_TICKS} 错峰起落，避免同 tick 粒子爆炸。
 *
 * <p>命中结算包在 {@link DiceCombatEvents#beginAoe()} / {@link DiceCombatEvents#endAoe()} 的
 * 内部波及窗口内，防止延迟伤害再次触发赐福或溅射。
 */
@EventBusSubscriber(modid = AstralDiceMod.MODID)
public final class SwordInStoneRainManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(SwordInStoneRainManager.class);

    /** 粒子团起点 = 目标碰撞箱上沿再往上这么多格。 */
    private static final double DROP_HEIGHT = 8.0D;

    /** 头顶蓄势 tick 数（给玩家看清「有东西要砸下来」的余量）。 */
    private static final int CHARGE_TICKS = 4;

    /** 下落 tick 数；8 格 / 3 tick ≈ 2.67 格/tick，快于活体书页飞行的 2.4 格/tick。 */
    private static final int FALL_TICKS = 3;

    /** 拖尾采样间距（格）。 */
    private static final double TRAIL_SPACING = 0.35D;

    /** 每个拖尾采样点的粒子数。 */
    private static final int TRAIL_PARTICLES = 2;

    /** 命中时每种颜色的爆闪粒子数。 */
    private static final int IMPACT_PARTICLES = 14;

    /** 一次下落最多存活 tick；超时直接丢弃，避免世界卸载/目标异常时残留。 */
    private static final int MAX_AGE_TICKS = 100;

    /** 多目标起落的最大错峰 tick 数。 */
    private static final long MAX_STAGGER_TICKS = 10L;

    /** 绿色拖尾：自然系星屑，染成嫩绿。 */
    private static final LibraryParticleOptions GREEN_TRAIL =
            new LibraryParticleOptions(LibraryVariants.NATURE_SPARKLE, 1.05F, 0x8CFF9E);

    /** 绿色蓄势/命中光团：自然系辉光。 */
    private static final LibraryParticleOptions GREEN_GLOW =
            new LibraryParticleOptions(LibraryVariants.NATURE_GLOW, 1.25F, 0x7CFC8A);

    /** 金色拖尾：圣光星芒，染成暖金。 */
    private static final LibraryParticleOptions GOLD_TRAIL =
            new LibraryParticleOptions(LibraryVariants.HOLY_STAR, 0.95F, 0xFFE08A);

    /** 金色蓄势/命中火花：圣光火花。 */
    private static final LibraryParticleOptions GOLD_SPARK =
            new LibraryParticleOptions(LibraryVariants.HOLY_SPARK, 1.15F, 0xFFD36E);

    private static final List<Pending> PENDING = new ArrayList<>();

    private SwordInStoneRainManager() {
    }

    /**
     * 为 {@code radius} 格内所有敌对目标排下粒子打击。
     *
     * @param level  服务端世界
     * @param caster 触发赐福的玩家（伤害归属）
     * @param radius 目标筛选半径，通常传 {@code SwordInStoneLegendaryChipItem.RADIUS}
     * @param damage 每个目标命中后的法术伤害
     */
    public static void launch(ServerLevel level, Player caster, double radius, int damage) {
        if (level == null || level.isClientSide() || caster == null || damage <= 0) return;
        double safeRadius = Math.max(0.0D, radius);
        List<LivingEntity> targets = level.getEntitiesOfClass(
                LivingEntity.class,
                caster.getBoundingBox().inflate(safeRadius),
                entity -> entity != caster && entity.isAlive() && TeamTargets.isHostileTo(caster, entity));
        if (targets.isEmpty()) return;

        long now = level.getGameTime();
        for (int i = 0; i < targets.size(); i++) {
            // 超出错峰窗口后回绕（i=11 → +1），避免第 11 个起的目标全挤在同一 tick。
            long stagger = (long) i % (MAX_STAGGER_TICKS + 1L);
            PENDING.add(new Pending(level, caster, targets.get(i), damage, now + stagger));
        }
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (PENDING.isEmpty()) return;
        process(event.getServer());
    }

    private static void process(MinecraftServer server) {
        if (server == null || PENDING.isEmpty()) return;
        List<Pending> due = new ArrayList<>(PENDING);
        PENDING.clear();
        for (Pending pending : due) {
            if (pending.level.getServer() != server) continue;
            try {
                if (advance(pending)) PENDING.add(pending);
            } catch (Exception ex) {
                LOGGER.warn("[Astral Dice] 石中剑落击结算失败，已丢弃本次下落", ex);
            }
        }
    }

    /** @return {@code true} 表示本次下落仍在进行，需要放回队列。 */
    private static boolean advance(Pending pending) {
        long now = pending.level.getGameTime();
        if (now < pending.launchTick) return true;
        if (!stillValid(pending)) return false;
        if (now - pending.launchTick > MAX_AGE_TICKS) return false;

        if (pending.origin == null) {
            pending.origin = launchOrigin(pending.target);
            pending.pos = pending.origin;
        }

        if (pending.elapsed < CHARGE_TICKS) {
            emitCharge(pending);
            pending.elapsed++;
            return true;
        }

        Vec3 dest = impactPoint(pending.target);
        int fallStep = pending.elapsed - CHARGE_TICKS + 1;
        double progress = Math.min(1.0D, (double) fallStep / (double) FALL_TICKS);
        Vec3 prev = pending.pos;
        Vec3 next = pending.origin.lerp(dest, progress);
        pending.pos = next;
        emitTrail(pending, prev, next);
        pending.elapsed++;

        if (progress >= 1.0D) {
            impact(pending, dest);
            return false;
        }
        return true;
    }

    /**
     * 只校验目标与目标所在世界；施法者状态不参与 —— 否则在 6 tick 延迟窗口内施法者
     * 死亡/登出/换维度会把整次打击静默丢弃（伤害为 0）。伤害归属在 {@link #impact} 处理。
     */
    private static boolean stillValid(Pending pending) {
        if (pending.target == null || pending.target.isRemoved() || !pending.target.isAlive()) return false;
        if (pending.level == null) return false;
        return pending.target.level() == pending.level;
    }

    private static Vec3 launchOrigin(LivingEntity target) {
        AABB box = target.getBoundingBox();
        return new Vec3((box.minX + box.maxX) * 0.5D, box.maxY + DROP_HEIGHT, (box.minZ + box.maxZ) * 0.5D);
    }

    private static Vec3 impactPoint(LivingEntity target) {
        AABB box = target.getBoundingBox();
        return new Vec3((box.minX + box.maxX) * 0.5D, box.maxY, (box.minZ + box.maxZ) * 0.5D);
    }

    /** 蓄势：绿色光团绕小圈旋转，金色火花在中心明灭。 */
    private static void emitCharge(Pending pending) {
        double angle = pending.elapsed * 1.25D;
        double x = pending.origin.x + Math.cos(angle) * 0.55D;
        double y = pending.origin.y + Math.sin(angle * 1.7D) * 0.18D;
        double z = pending.origin.z + Math.sin(angle) * 0.55D;
        pending.level.sendParticles(GREEN_GLOW, x, y, z, 1, 0.02D, 0.02D, 0.02D, 0.0D);
        pending.level.sendParticles(GOLD_SPARK, pending.origin.x, pending.origin.y, pending.origin.z,
                2, 0.28D, 0.16D, 0.28D, 0.01D);
    }

    /** 下落拖尾：绿/金逐点交替，保证整条轨迹都是双色。 */
    private static void emitTrail(Pending pending, Vec3 from, Vec3 to) {
        double length = from.distanceTo(to);
        int steps = Math.min(64, Math.max(1, (int) Math.ceil(length / TRAIL_SPACING)));
        for (int i = 1; i <= steps; i++) {
            Vec3 at = from.lerp(to, (double) i / (double) steps);
            LibraryParticleOptions option = ((pending.elapsed + i) & 1) == 0 ? GREEN_TRAIL : GOLD_TRAIL;
            pending.level.sendParticles(option, at.x, at.y, at.z,
                    TRAIL_PARTICLES, 0.02D, 0.02D, 0.02D, 0.0D);
        }
    }

    /** 粒子落到头顶的**这一 tick** 才结算伤害。 */
    private static void impact(Pending pending, Vec3 center) {
        // 不用原版 FLASH：26.1.2 把它改成了 ColorParticleOption，跨线类型不一致；
        // 库粒子本身自发光，金色星芒足可做命中爆闪。
        pending.level.sendParticles(GREEN_GLOW,
                center.x, center.y, center.z, IMPACT_PARTICLES, 0.32D, 0.22D, 0.32D, 0.04D);
        pending.level.sendParticles(GOLD_SPARK,
                center.x, center.y, center.z, IMPACT_PARTICLES, 0.28D, 0.28D, 0.28D, 0.06D);
        pending.level.sendParticles(GOLD_TRAIL,
                center.x, center.y + 0.1D, center.z, 6, 0.18D, 0.18D, 0.18D, 0.02D);

        if (pending.target == null || pending.target.isRemoved() || !pending.target.isAlive()) return;
        // stillValid() 不再校验施法者：caster 为空（防御性）时退回无归属的魔法伤害，保证伤害不丢。
        DamageSource source = pending.caster == null
                ? pending.level.damageSources().magic()
                : ModDamageTypes.cardSpell(pending.level, pending.caster);
        DiceCombatEvents.beginAoe();
        try {
            pending.target.hurt(source, pending.damage);
        } finally {
            DiceCombatEvents.endAoe();
        }
    }

    private static final class Pending {
        private final ServerLevel level;
        private final Player caster;
        private final LivingEntity target;
        private final int damage;
        private final long launchTick;
        private int elapsed;
        private Vec3 origin;
        private Vec3 pos;

        private Pending(ServerLevel level, Player caster, LivingEntity target, int damage, long launchTick) {
            this.level = level;
            this.caster = caster;
            this.target = target;
            this.damage = damage;
            this.launchTick = launchTick;
        }
    }
}
