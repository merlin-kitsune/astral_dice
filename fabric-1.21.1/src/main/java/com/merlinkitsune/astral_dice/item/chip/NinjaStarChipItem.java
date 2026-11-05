package com.merlinkitsune.astral_dice.item.chip;

import net.minecraft.world.item.ItemStack;
import com.merlinkitsune.astral_dice.combat.SpellDamageRegistry;
import com.merlinkitsune.astral_dice.item.card.EffectCardPeriod;

/**
 * 忍术飞镖筹码:
 * - 效果牌出牌数 +1(永久加成,注册于 EffectCardPeriod 固定出牌数来源);
 * - 已使用伤害效果牌且对目标造成远程或魔法伤害时,获得目标标记层数的伤害加成(结算在 SpellDamageRegistry 法伤修饰器)。
 */
public class NinjaStarChipItem extends BaseChipItem {
    public NinjaStarChipItem(Properties properties) {
        super(properties);
    }
}
