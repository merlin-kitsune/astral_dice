package com.merlinkitsune.astral_dice.item.chip;

import net.minecraft.world.item.ItemStack;
import com.merlinkitsune.astral_dice.combat.DiceCombatModifiers;

/**
 * 普通瞄具筹码:攻击力 +2,并对目标施加 1 层标记(结算在 DiceCombatModifiers 攻击修饰器)。
 */
public class ScopeChipItem extends BaseChipItem {
    public ScopeChipItem(Properties properties) {
        super(properties);
    }
}
