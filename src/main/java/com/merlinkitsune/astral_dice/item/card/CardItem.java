package com.merlinkitsune.astral_dice.item.card;

import com.merlinkitsune.astral_dice.component.AppliedStone;
import com.merlinkitsune.astral_dice.component.ModDataComponents;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

public class CardItem extends Item {
    private final String cardType;

    public CardItem(Properties properties, String cardType) {
        super(properties);
        this.cardType = cardType;
    }

    // 未消耗耐久(满耐久)的战斗牌可堆叠 64 个;已消耗耐久后单独存放(单张)
    @Override
    public int getMaxStackSize(ItemStack stack) {
        int max = AppliedStone.defaultUses(cardType);
        int uses = stack.getOrDefault(ModDataComponents.CARD_USES.get(), max);
        return uses >= max ? 64 : 1;
    }

    @Override
    public boolean isBarVisible(ItemStack stack) {
        int max = AppliedStone.defaultUses(cardType);
        int uses = stack.getOrDefault(ModDataComponents.CARD_USES.get(), max);
        return uses > 0 && uses < max;
    }

    @Override
    public int getBarWidth(ItemStack stack) {
        int max = AppliedStone.defaultUses(cardType);
        int uses = stack.getOrDefault(ModDataComponents.CARD_USES.get(), max);
        if (max <= 0) return 0;
        return Math.round(13.0f * uses / max);
    }

    @Override
    public int getBarColor(ItemStack stack) {
        float max = AppliedStone.defaultUses(cardType);
        float uses = stack.getOrDefault(ModDataComponents.CARD_USES.get(), (int) max);
        float ratio = max > 0 ? uses / max : 0;
        if (ratio > 0.5f) {
            return 0x00FF00;
        } else if (ratio > 0.25f) {
            return 0xFFFF00;
        }
        return 0xFF0000;
    }
}