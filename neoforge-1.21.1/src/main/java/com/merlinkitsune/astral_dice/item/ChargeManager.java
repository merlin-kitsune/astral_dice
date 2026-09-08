package com.merlinkitsune.astral_dice.item;

import com.merlinkitsune.astral_dice.combat.DiceCombatModifiers;
import com.merlinkitsune.astral_dice.component.GameplayConstants;
import com.merlinkitsune.astral_dice.effect.ChargeEffect;
import net.minecraft.world.entity.player.Player;

/**
 * 充能流派管理器(玩家级资源)。
 *
 * 层数存储于 {@link ChargeEffect}(amplifier = 层数-1,上限见
 * {@link GameplayConstants#CHARGE_MAX_STACKS})。
 * 流派固定效果(与层数多少无关):拥有至少 1 层充能时,
 * 防御力 +1(折算为真实护甲)、立牌主动/效果牌冷却时间 -20%。
 */
public final class ChargeManager {
    private static final String DEFENSE_ARMOR_KEY = "charge_def_armor";

    private ChargeManager() {
    }

    public static boolean hasCharge(Player player) {
        return ChargeEffect.getStacks(player) > 0;
    }

    public static int getStacks(Player player) {
        return ChargeEffect.getStacks(player);
    }

    /** 增加充能层数(自动刷新防御加成),返回增加后的层数 */
    public static int addStacks(Player player, int stacks) {
        int total = ChargeEffect.addStacks(player, stacks);
        refresh(player);
        return total;
    }

    /** 减少 1 层(自动刷新防御加成) */
    public static void consumeOne(Player player) {
        ChargeEffect.consumeOne(player);
        refresh(player);
    }

    /** 清空充能(自动刷新防御加成) */
    public static void removeAll(Player player) {
        ChargeEffect.removeAll(player);
        refresh(player);
    }

    /** 按当前充能状态刷新防御护甲加成(每 tick 或增删层数时调用) */
    public static void refresh(Player player) {
        if (player == null || player.level().isClientSide()) return;
        DiceCombatModifiers.setDefenseArmorBonus(player, DEFENSE_ARMOR_KEY,
                hasCharge(player) ? GameplayConstants.CHARGE_DEFENSE_BONUS : 0);
    }

    /** 返回经过充能减冷却后的 tick 数(拥有充能时 -20%,最少 1 tick) */
    public static long cooldownTicks(Player player, long baseTicks) {
        if (baseTicks <= 1 || !hasCharge(player)) return baseTicks;
        double reduced = baseTicks * (1.0 - GameplayConstants.CHARGE_COOLDOWN_REDUCTION);
        return Math.max(1, (long) Math.ceil(reduced));
    }

    /** 玩家 tick 驱动:刷新防御加成(未来也可在此刷新 HUD/显示) */
    public static void tick(Player player) {
        refresh(player);
    }
}
