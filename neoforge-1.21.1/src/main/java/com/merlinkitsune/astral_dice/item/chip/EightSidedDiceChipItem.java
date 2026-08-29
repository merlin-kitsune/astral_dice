package com.merlinkitsune.astral_dice.item.chip;

import com.merlinkitsune.astral_dice.component.ModAttachments;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * 八面骰筹码:触发骰神赐福后掷 1d10,累计点数每满 8 点 +1 星光;骰点恰为 8 时立即获得 8 个星币。
 * 移除筹码时清除累计骰点,防止反复佩戴累计。
 */
public class EightSidedDiceChipItem extends BaseChipItem {
    public EightSidedDiceChipItem(Properties properties) {
        super(properties);
    }

    // 移除八面骰筹码(真正卸下)时清除累计骰点
    @Override
    protected void onChipUnequip(Player player, ItemStack stack) {
        ModAttachments.setEightSidedAccum(player, 0);
    }
}
