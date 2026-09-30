package com.merlinkitsune.astral_dice.item.chip;

import net.minecraft.world.item.ItemStack;
import com.merlinkitsune.astral_dice.item.card.EffectCardPeriod;

/**
 * 大背包筹码:效果牌出牌数 +1(装备后生效,注册于 EffectCardPeriod 固定出牌数来源)。
 */
public class BigBackpackChipItem extends BaseChipItem {
    public BigBackpackChipItem(Properties properties) {
        super(properties);
    }
}
