package com.merlinkitsune.astral_dice.item.chip;

import net.minecraft.world.item.ItemStack;
import com.merlinkitsune.astral_dice.combat.DiceCombatModifiers;

/**
 * 美工刀-初级筹码:满血时攻击 +2,并加上当前治愈点数(结算在 DiceCombatModifiers 攻击修饰器)。
 */
public class CutterChipItem extends BaseChipItem {
    public CutterChipItem(Properties properties) {
        super(properties);
    }
}
