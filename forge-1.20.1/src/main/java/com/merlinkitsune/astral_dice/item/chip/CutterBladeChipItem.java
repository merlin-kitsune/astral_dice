package com.merlinkitsune.astral_dice.item.chip;

import net.minecraft.world.item.ItemStack;
import com.merlinkitsune.astral_dice.combat.DiceCombatModifiers;

/**
 * 美工刀-锋利筹码:与美工刀-初级一致,满血时攻击 +4 基础攻击并加上当前治愈点数(结算在 DiceCombatModifiers 攻击修饰器);
 * 与美工刀-初级为不同物品,可同时装备(两者加成叠加)。
 */
public class CutterBladeChipItem extends BaseChipItem {
    public CutterBladeChipItem(Properties properties) {
        super(properties);
    }
}
