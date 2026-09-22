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

    /** 效果牌公共冷却 tick:拥有充能时把**基础值**封顶为 20 秒(2026-09-25 改口径,同上) */
    public static long effectCardCooldownTicks(Player player, long baseTicks) {
        return capByCharge(player, baseTicks, GameplayConstants.CHARGE_EFFECT_CARD_COOLDOWN_CAP_SECONDS);
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
