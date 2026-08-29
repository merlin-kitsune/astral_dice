package com.merlinkitsune.astral_dice.item.chip;

import net.minecraft.world.item.ItemStack;
import com.merlinkitsune.astral_dice.combat.DiceCombatModifiers;

/**
 * 手电筒-强光筹码:骰神赐福期间每 4 点星光 +1 攻击力(结算在 DiceCombatModifiers 攻击修饰器)。
 */
public class FlashlightChipItem extends BaseChipItem {
    public FlashlightChipItem(Properties properties) {
        super(properties);
    }
}
