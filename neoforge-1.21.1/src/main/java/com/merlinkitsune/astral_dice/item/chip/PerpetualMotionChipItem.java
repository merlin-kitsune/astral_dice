package com.merlinkitsune.astral_dice.item.chip;

import com.merlinkitsune.astral_dice.item.ChargeManager;
import com.merlinkitsune.astral_dice.item.ModItems;
import net.minecraft.world.entity.player.Player;
import top.theillusivec4.curios.api.CuriosApi;

/**
 * 永动机筹码:触发骰神赐福时,充能 +6。
 * 触发点位于 {@code DiceCombatEvents} 骰神赐福新周期开始处。
 */
public class PerpetualMotionChipItem extends BaseChipItem {
    /** 每次触发骰神赐福获得的充能层数 */
    public static final int CHARGE_GAIN = 6;

    public PerpetualMotionChipItem(Properties properties) {
        super(properties);
    }

    public static boolean isEquipped(Player player) {
        if (player == null) return false;
        var curios = CuriosApi.getCuriosInventory(player);
        return curios.isPresent() && curios.get().findFirstCurio(s -> s.is(ModItems.PERPETUAL_MOTION.get())).isPresent();
    }

    /** 触发骰神赐福时调用:佩戴永动机则增加 6 点充能 */
    public static void onBlessingStart(Player player) {
        if (player == null || player.level().isClientSide()) return;
        if (!isEquipped(player)) return;
        ChargeManager.addStacks(player, CHARGE_GAIN);
    }
}
