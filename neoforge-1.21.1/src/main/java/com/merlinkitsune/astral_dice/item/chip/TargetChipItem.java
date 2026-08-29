package com.merlinkitsune.astral_dice.item.chip;

import net.minecraft.world.item.ItemStack;
import com.merlinkitsune.astral_dice.combat.DiceCombatModifiers;

/**
 * 标靶筹码:攻击力 +1;触发"骰神赐福"后对附近(TARGET_CHIP_RANGE 格)范围内随机一个敌对目标施加一层"标记"
 * (结算在 DiceCombatModifiers 攻击修饰器与赐福触发点)。
 */
public class TargetChipItem extends BaseChipItem {
    public TargetChipItem(Properties properties) {
        super(properties);
    }
}
