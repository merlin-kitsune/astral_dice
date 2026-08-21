package com.merlinkitsune.astral_dice.item.card;

import com.merlinkitsune.astral_dice.component.AppliedStone;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

public class CardItem extends Item {
    private final String cardType;

    public CardItem(Properties properties, String cardType) {
        super(properties);
        this.cardType = cardType;
    }

    // 战斗牌使用 MC 原生耐久机制:maxDamage = 默认次数,damage = 已消耗次数
    @Override
    public int getMaxDamage(ItemStack stack) {
        return AppliedStone.defaultUses(cardType);
    }

    // 未消耗耐久(满耐久)的战斗牌可堆叠 64 个;已消耗耐久后单独存放(单张)
    @Override
    public int getMaxStackSize(ItemStack stack) {
        return stack.isDamaged() ? 1 : 64;
    }

    @Override
    public boolean isBarVisible(ItemStack stack) {
        return stack.isDamaged();
    }

    @Override
    public int getBarWidth(ItemStack stack) {
        int max = stack.getMaxDamage();
        if (max <= 0) return 0;
        int damage = stack.getDamageValue();
        return Math.round(13.0f * (max - damage) / max);
    }

    @Override
    public int getBarColor(ItemStack stack) {
        int max = stack.getMaxDamage();
        if (max <= 0) return 0x00FF00;
        int damage = stack.getDamageValue();
        float ratio = (float) (max - damage) / max;
        if (ratio > 0.5f) {
            return 0x00FF00;
        } else if (ratio > 0.25f) {
            return 0xFFFF00;
        }
        return 0xFF0000;
    }
}