package com.merlinkitsune.astral_dice.item.card;

import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * 效果牌工具(兼容层):随机效果牌发放委托 {@link RandomCardHandler}。
 * 新代码请直接使用 {@link RandomCardHandler}(支持卡牌类别筛选与统一发放逻辑)。
 */
public final class EffectCardUtil {
    private EffectCardUtil() {
    }

    // 效果牌是否专属(随机发放强制排除)
    public static boolean isRandomBlacklisted(ItemStack stack) {
        return RandomCardHandler.isExclusive(stack);
    }

    // 随机发放效果牌的候选池(功能效果牌与伤害效果牌均属效果牌,均入池;专属牌强制排除)
    public static List<ItemStack> getRandomEffectCardPool() {
        return RandomCardHandler.getCardPool(RandomCardHandler.CardCategory.EFFECT);
    }
}
