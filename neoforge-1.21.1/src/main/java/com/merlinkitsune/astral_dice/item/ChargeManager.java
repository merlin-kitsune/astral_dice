package com.merlinkitsune.astral_dice.item;

import com.merlinkitsune.astral_dice.component.GameplayConstants;
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
 * 立牌主动/效果牌冷却时间 -20%(无防御力/护甲加成)。
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
        ChargeEffect.consumeOne(player);
    }

    /** 清空充能 */
    public static void removeAll(Player player) {
        ChargeEffect.removeAll(player);
    }

    /** 返回经过充能减冷却后的 tick 数(拥有充能时 -20%,最少 1 tick) */
    public static long cooldownTicks(Player player, long baseTicks) {
        if (baseTicks <= 1 || !hasCharge(player)) return baseTicks;
        double reduced = baseTicks * (1.0 - GameplayConstants.CHARGE_COOLDOWN_REDUCTION);
        return Math.max(1, (long) Math.ceil(reduced));
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
