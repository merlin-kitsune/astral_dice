package com.merlinkitsune.astral_dice.item.chip;

import com.merlinkitsune.starenginelib.combat.HostileTargets;
import com.merlinkitsune.astral_dice.component.ModAttachments;
import com.merlinkitsune.astral_dice.event.RailgunStrikeScheduler;
import com.merlinkitsune.astral_dice.item.ChargeManager;
import com.merlinkitsune.astral_dice.item.ModItems;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import top.theillusivec4.curios.api.CuriosApi;

import java.util.List;

/**
 * 电磁炮筹码:
 * <ul>
 *   <li>充能层数不少于 6 时,攻击力 +5(被动加成,不受冷却影响);</li>
 *   <li>对敌对目标发起攻击时,在 **1 秒后**对目标 3 格范围内所有敌对目标降下雷击
 *       (**雷击伤害 = 5 + 本次攻击(骰战最终)伤害 × 50%,下限 5 点**,经原版
 *       {@code LightningBolt#setDamage} 覆写默认的 5.0F;**伤害类型为真伤**——
 *       无视护甲值与盔甲韧性;仍会点火并生成闪电苦力怕),
 *       随后进入 **1:00** 冷却;</li>
 *   <li>**充能与冷却都在雷击真正落下的那一刻才结算**:1 秒后范围内没有任何可命中的敌对目标
 *       (例如被攻击的目标已死、周围 3 格也没有其它敌对目标)时,整次触发作废——不消耗充能、
 *       不进入冷却、也不生成雷击(旧实现先扣资源后落雷,会白扣 6 层充能)。</li>
 * </ul>
 * <p>延迟经 {@link RailgunStrikeScheduler} 由服务端 tick 边界驱动(原版
 * {@code TickTask} 传入未来 tick 会立即执行,无法表达 1 秒延迟)。
 *
 * <p>雷击的伤害类型为**真伤**({@code astral_dice:true_damage},登记于 {@code minecraft:bypasses_armor}
 * → 无视护甲值与盔甲韧性):伤害结算在原版 {@code Entity#thunderHit} 内,故由
 * {@code mixin.EntityThunderHitMixin} **只对本模组电磁炮降下的闪电**({@link com.merlinkitsune.astral_dice.damage.RailgunBolts})
 * 替换伤害源,原版闪电的其余行为(目标筛选、点火、闪电苦力怕、僵尸猪灵/女巫转化)保持不变。
 * 伤害实体为空,既不算近战攻击、也不进入骰战结算——只有**伤害数值**取自本次攻击,
 * 因此不会触发骰神赐福。</p>
 */
public class RailgunChipItem extends BaseChipItem {
    /** 攻击力加成所需的充能层数 */
    public static final int CHARGE_REQUIRED = 6;
    /** 满足条件时提供的攻击力 */
    public static final int ATTACK_BONUS = 5;
    /** 雷击覆盖半径(格) */
    public static final int AOE_RADIUS = 3;
    /** 雷击延迟(1 秒) */
    public static final int STRIKE_DELAY_TICKS = 20;
    /** 雷击触发冷却(1:00) */
    public static final int COOLDOWN_TICKS = 20 * 60;
    /** 雷击基础伤害(点):与"攻击伤害 × 比例"部分相加 */
    public static final float STRIKE_DAMAGE_BASE = 5.0F;
    /** 雷击伤害占本次攻击(骰战最终)伤害的比例 */
    public static final float STRIKE_DAMAGE_RATIO = 0.5F;
    /** 雷击伤害下限(点):{@link #STRIKE_DAMAGE_BASE} 本身即 5,此处仅作显式兜底 */
    public static final float STRIKE_DAMAGE_MIN = 5.0F;

    public RailgunChipItem(Properties properties) {
        super(properties);
    }

    /** 玩家是否佩戴电磁炮 */
    public static boolean isEquipped(Player player) {
        if (player == null) return false;
        var curios = CuriosApi.getCuriosInventory(player);
        return curios.isPresent()
                && curios.get().findFirstCurio(s -> s.is(ModItems.RAILGUN_CHIP.get())).isPresent();
    }

    /** 当前充能是否满足攻击力加成条件;满足则返回 +5 */
    public static int getAttackBonus(Player player) {
        if (!isEquipped(player)) return 0;
        return ChargeManager.getStacks(player) >= CHARGE_REQUIRED ? ATTACK_BONUS : 0;
    }

    /** 是否处于雷击冷却中(1:00) */
    public static boolean isOnCooldown(Player player) {
        if (player == null) return false;
        return player.level().getGameTime() < ModAttachments.getRailgunCooldownEnd(player);
    }

    /**
     * 对敌对目标发起攻击时调用:登记 1 秒后的范围雷击。
     *
     * <p>**此处不消耗充能、也不进入冷却**——两者都推迟到 {@link #executeStrike} 真正落下雷击时
     * 才结算,因此"1 秒后范围内没有任何可命中的敌对目标"时整次触发作废,不会白扣资源。
     * 冷却中或充能不足时不登记,返回 {@code null}。
     *
     * <p>雷击伤害 = {@link #STRIKE_DAMAGE_BASE} + 本次攻击伤害 × {@link #STRIKE_DAMAGE_RATIO}。
     * 调用点位于伤害结算的**前段**(骰神赐福/骰战尚未计算),故先以 {@code immediateDamage}
     * (攻击事件的即时伤害)兜底登记,骰战结算完成、最终伤害确定后由 {@link #applyFinalDamage} 回填。
     *
     * @param immediateDamage 本次攻击的即时伤害(骰战结算前的兜底值)
     * @return 本次登记的雷击句柄(供回填最终伤害);未触发时为 {@code null}
     */
    public static RailgunStrikeScheduler.Pending onAttack(Player player, LivingEntity target, float immediateDamage) {
        if (player == null || target == null) return null;
        if (player.level().isClientSide()) return null;
        if (!isEquipped(player)) return null;
        if (!HostileTargets.isHostile(player, target)) return null;
        if (!(player.level() instanceof ServerLevel level)) return null;
        if (isOnCooldown(player)) return null;
        if (ChargeManager.getStacks(player) < CHARGE_REQUIRED) return null;

        return RailgunStrikeScheduler.schedule(level, target, target.position(),
                player instanceof ServerPlayer serverPlayer ? serverPlayer : null,
                STRIKE_DELAY_TICKS, strikeDamage(immediateDamage));
    }

    /**
     * 骰战结算完成后回填本次攻击的最终伤害
     * (雷击伤害随之为 {@link #STRIKE_DAMAGE_BASE} + 最终伤害 × {@link #STRIKE_DAMAGE_RATIO})。
     */
    public static void applyFinalDamage(RailgunStrikeScheduler.Pending pending, float finalDamage) {
        if (pending == null) return;
        pending.setDamage(strikeDamage(finalDamage));
    }

    /**
     * 雷击伤害 = {@link #STRIKE_DAMAGE_BASE} + 本次攻击伤害 × {@link #STRIKE_DAMAGE_RATIO},
     * 最低 {@link #STRIKE_DAMAGE_MIN}(攻击伤害为负时按 0 计)。
     */
    private static float strikeDamage(float attackDamage) {
        return Math.max(STRIKE_DAMAGE_MIN,
                STRIKE_DAMAGE_BASE + Math.max(0.0F, attackDamage) * STRIKE_DAMAGE_RATIO);
    }

    /**
     * 延迟到期时由 {@link RailgunStrikeScheduler} 调用:先取中心 {@value #AOE_RADIUS} 格内的
     * 可命中敌对目标,再结算充能与冷却并逐一降下雷击。
     *
     * <p><b>没有任何可命中目标时整次触发作废</b>:不消耗充能、不进入冷却、也不生成雷击——
     * 例如被攻击的目标在 1 秒延迟内已死亡,且周围 {@value #AOE_RADIUS} 格内也没有其它敌对目标。
     * 充能若已被其它来源(电流核心/电击手套/安全气囊等)消耗掉,同样只作废本次触发。
     *
     * @return 是否真的落下了雷击(即本次触发是否消耗了充能与冷却)
     */
    public static boolean executeStrike(ServerLevel level, Vec3 center, ServerPlayer cause, float damage) {
        if (level == null || center == null) return false;
        AABB aabb = new AABB(center, center).inflate(AOE_RADIUS);
        List<LivingEntity> victims = level.getEntitiesOfClass(LivingEntity.class, aabb,
                // cause 可能为 null(无归属):此时两参重载退化为"仅生物敌对"的既有语义
                e -> HostileTargets.isHostile(cause, e) && e.isAlive());
        if (victims.isEmpty()) return false;
        if (cause != null) {
            // 充能在 1 秒延迟内可能已被其它筹码花掉:不足则整次触发作废(不进入冷却)
            if (ChargeManager.getStacks(cause) < CHARGE_REQUIRED) return false;
            ChargeManager.consume(cause, CHARGE_REQUIRED);
            ModAttachments.setRailgunCooldownEnd(cause, cause.level().getGameTime() + COOLDOWN_TICKS);
        }
        for (LivingEntity victim : victims) {
            strike(level, victim.position(), cause, damage);
        }
        return true;
    }

    // 原生雷击:setVisualOnly(false) 才会造成伤害、点火并把苦力怕转化为闪电苦力怕。
    // 伤害值经 LightningBolt#setDamage 覆写(原版字段 damage 默认 5.0F);点火与闪电苦力怕
    // 由 LightningBolt#tick 内 !visualOnly 分支独立驱动,与伤害值无关。
    // 标记为"电磁炮雷击"后,EntityThunderHitMixin 会把这份伤害换成真伤(无视护甲值/盔甲韧性)。
    private static void strike(ServerLevel level, Vec3 pos, ServerPlayer cause, float damage) {
        LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(level, net.minecraft.world.entity.EntitySpawnReason.MOB_SUMMONED);
        if (bolt == null) return;
        bolt.snapTo(pos);
        bolt.setVisualOnly(false);
        bolt.setDamage(damage);
        bolt.setCause(cause);
        com.merlinkitsune.astral_dice.damage.RailgunBolts.mark(bolt);
        level.addFreshEntity(bolt);
    }
}
