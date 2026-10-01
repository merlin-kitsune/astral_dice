package com.merlinkitsune.astral_dice.item.chip;

import net.minecraft.world.item.ItemStack;
import com.merlinkitsune.astral_dice.combat.DiceCombatModifiers;

/**
 * 鹰眼瞄具筹码:攻击时按目标标记层数*2 获得攻击力加成,并施加 1 层标记(结算在 DiceCombatModifiers 攻击修饰器)。
 */
public class EagleScopeChipItem extends BaseChipItem {
    public EagleScopeChipItem(Properties properties) {
        super(properties);
    }
}
