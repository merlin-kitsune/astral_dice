package com.merlinkitsune.astral_dice.item.chip;

import com.merlinkitsune.astral_dice.item.ChargeManager;
import com.merlinkitsune.astral_dice.item.CuriosCompat;
import com.merlinkitsune.astral_dice.item.ModItems;
import net.minecraft.world.entity.player.Player;

/**
 * 永动机筹码:触发骰神赐福时,若当前充能**不足 6 点**则补充至 6 点(已达标不再增加,不溢出)。
 * 触发点位于 {@code DiceCombatEvents} 骰神赐福新周期开始处。
 */
public class PerpetualMotionChipItem extends BaseChipItem {
    /** 触发骰神赐福时补足的充能目标层数(仅补足,不叠加) */
    public static final int CHARGE_TARGET = 6;

    public PerpetualMotionChipItem(Properties properties) {
        super(properties);
    }

    public static boolean isEquipped(Player player) {
        if (player == null) return false;
        var curios = CuriosCompat.getCuriosInventory(player);
        return curios.isPresent() && curios.get().findFirstCurio(s -> s.is(ModItems.PERPETUAL_MOTION.get())).isPresent();
    }

    /** 触发骰神赐福时调用:佩戴永动机且当前充能不足 {@link #CHARGE_TARGET} 点时,补充至该值 */
    public static void onBlessingStart(Player player) {
        if (player == null || player.level().isClientSide()) return;
        if (!isEquipped(player)) return;
        int current = ChargeManager.getStacks(player);
        if (current >= CHARGE_TARGET) return;
        ChargeManager.addStacks(player, CHARGE_TARGET - current);
    }
}
