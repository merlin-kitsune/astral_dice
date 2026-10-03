package com.merlinkitsune.astral_dice.item;

import com.merlinkitsune.starenginelib.component.GameplayConstants;
import com.merlinkitsune.astral_dice.effect.ChargeEffect;
import net.minecraft.world.entity.player.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 充能流派管理器(玩家级资源)。
 *
 * <p>层数存储于 {@link ChargeEffect}(amplifier = 层数-1,上限见
 * {@link GameplayConstants#CHARGE_MAX_STACKS})。
 * 流派固定效果(与层数多少无关):拥有至少 1 层充能时,
 * 立牌主动冷却至多 160 秒、效果牌冷却至多 20 秒 —— 把**基础值**封顶
 * (2026-09-25 改口径,取代旧的「-20% 比例减免」);封顶之后再由调用方叠加
 * 其它减免(诡异骰子 -50% 等)。已在进行的倒计时不受影响(只在开始冷却时取值)。
 * 无防御力/护甲加成。
 */
public final class ChargeManager {
    /** 死亡时暂存的充能层数:等待重生后恢复(仅内存态,用于跨死亡实体转移) */
    private static final Map<UUID, Integer> DEATH_PRESERVED_STACKS = new HashMap<>();

    private ChargeManager() {
    }

    public static boolean hasCharge(Player player) {
        return ChargeEffect.getStacks(player) > 0;
    }

    /**
     * 判断某物品是否是「**充能类筹码**」。
     *
     * <p>判定用**类**而不是 {@code ModItems} 的静态实例 —— 后者在静态字段里 {@code .get()}
     * 会有「注册未完成就被类加载」的顺序风险(DeferredItem/RegistryObject 未绑定)。
     *
     * <p>⚠️ 名单与筹码 tooltip 调用 {@code addChargeCounter} 的那批**同源**(共 10 枚):
     * 飞行引擎 / 能量回收器 / 电流剑 / 高级外设 / 永动机 / 电流核心 / 电击手套 / 安全气囊 /
     * 电磁炮 / 原初核心。**新增充能类筹码时必须在此登记**,否则「未装备筹码 ⇒ 隐藏充能图标」
     * 的判定会漏(该筹码的持有者会看不到图标)。
     */
    private static boolean isChargeChipItem(net.minecraft.world.item.Item item) {
        return item instanceof com.merlinkitsune.astral_dice.item.chip.WarpEngineChipItem
                || item instanceof com.merlinkitsune.astral_dice.item.chip.EnergyRecyclerChipItem
                || item instanceof com.merlinkitsune.astral_dice.item.chip.ElectricSwordChipItem
                || item instanceof com.merlinkitsune.astral_dice.item.chip.AdvancedPeripheralsChipItem
                || item instanceof com.merlinkitsune.astral_dice.item.chip.PerpetualMotionChipItem
                || item instanceof com.merlinkitsune.astral_dice.item.chip.CurrentCoreChipItem
                || item instanceof com.merlinkitsune.astral_dice.item.chip.ElectricGloveChipItem
                || item instanceof com.merlinkitsune.astral_dice.item.chip.AirbagChipItem
                || item instanceof com.merlinkitsune.astral_dice.item.chip.RailgunChipItem
                || item instanceof com.merlinkitsune.astral_dice.item.chip.PrimordialCoreChipItem;
    }

    /**
     * 玩家是否装备了**任一充能类筹码**(饰品栏)。
     *
     * <p>用途:充能状态图标的可见性 —— 2026-10-03 用户裁决「玩家未装备任何充能筹码,但是有
     * 充能状态时,需要隐藏充能状态显示图标,只有装备回充能筹码才应该恢复显示」。
     */
    public static boolean isChargeChipEquipped(Player player) {
        if (player == null) return false;
        // 饰品遍历收敛到 CurioSlotUtil(各线 Curios 入口不同,统一在那里处理)
        return CurioSlotUtil.hasAnyCurioMatching(player, s -> isChargeChipItem(s.getItem()));
    }

    public static int getStacks(Player player) {
        return ChargeEffect.getStacks(player);
    }

    /** 增加充能层数,返回增加后的层数 */
    public static int addStacks(Player player, int stacks) {
        return ChargeEffect.addStacks(player, stacks);
    }

    /** 减少 1 层 */
    public static void consumeOne(Player player) {
        consume(player, 1);
    }

    /**
     * 一次性减少指定层数,返回实际消耗层数。
     *
     * <p>充能被消耗时统一在此挂钩 {@link EmpowerManager#onChargeConsumed}:
     * 佩戴「原初核心」筹码的玩家按实际消耗层数获得等量「赋能」。
     */
    public static int consume(Player player, int amount) {
        int consumed = ChargeEffect.consume(player, amount);
        if (consumed > 0) {
            EmpowerManager.onChargeConsumed(player, consumed);
        }
        return consumed;
    }

    /** 清空充能 */
    public static void removeAll(Player player) {
        ChargeEffect.removeAll(player);
    }

    /**
     * 立牌主动技能冷却 tick:拥有充能时把**基础值**封顶为 160 秒
     * ({@link GameplayConstants#CHARGE_SIGN_COOLDOWN_CAP_SECONDS})。
     *
     * <p>2026-09-25 改口径:取代旧的「有充能即 -20%」比例减免。封顶只作用于**基础值**,
     * 之后由调用方继续叠加其它减免(诡异骰子 -50%、枪匠「精密技巧」的 120 秒基础值等);
     * 基础值本就低于上限时保持原值。**已在进行的倒计时不受影响** —— 本方法只在开始冷却时取值。
     */
    public static long signCooldownTicks(Player player, long baseTicks) {
        return capByCharge(player, baseTicks, GameplayConstants.CHARGE_SIGN_COOLDOWN_CAP_SECONDS);
    }

    /** 效果牌公共冷却 tick:拥有充能时把**基础值**封顶为 30 秒(2026-09-30 用户裁决,20 → 30;
     *  取值口径统一在 {@link EffectCardPeriod#CHARGE_COOLDOWN_CAP_SECONDS},不再走库常量) */
    public static long effectCardCooldownTicks(Player player, long baseTicks) {
        return capByCharge(player, baseTicks,
                com.merlinkitsune.astral_dice.item.card.EffectCardPeriod.CHARGE_COOLDOWN_CAP_SECONDS);
    }

    /** 有充能时按上限封顶;无充能、基础值 ≤1 tick 或本就更低时原样返回 */
    private static long capByCharge(Player player, long baseTicks, int capSeconds) {
        if (baseTicks <= 1 || !hasCharge(player)) return baseTicks;
        return Math.min(baseTicks, capSeconds * 20L);
    }

    /** 玩家死亡前调用:暂存当前充能层数,供重生后恢复(死亡不丢失充能) */
    public static void preserveOnDeath(Player player) {
        if (player == null || player.level().isClientSide()) return;
        int stacks = ChargeEffect.getStacks(player);
        if (stacks > 0) {
            DEATH_PRESERVED_STACKS.put(player.getUUID(), stacks);
        } else {
            DEATH_PRESERVED_STACKS.remove(player.getUUID());
        }
    }

    /** 玩家重生后调用:恢复死亡前暂存的充能层数 */
    public static void restoreAfterDeath(Player player) {
        if (player == null || player.level().isClientSide()) return;
        UUID uuid = player.getUUID();
        Integer stacks = DEATH_PRESERVED_STACKS.remove(uuid);
        if (stacks != null && stacks > 0) {
            ChargeEffect.addStacks(player, stacks);
        }
    }

    /** 清理指定玩家的死亡暂存(登出/异常路径兜底) */
    public static void clearDeathPreserved(Player player) {
        if (player != null) {
            DEATH_PRESERVED_STACKS.remove(player.getUUID());
        }
    }
}
