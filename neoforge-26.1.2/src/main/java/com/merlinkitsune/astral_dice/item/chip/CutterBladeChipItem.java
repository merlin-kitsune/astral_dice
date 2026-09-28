package com.merlinkitsune.astral_dice.item.chip;

import net.minecraft.world.item.ItemStack;
import com.merlinkitsune.astral_dice.combat.DiceCombatModifiers;

/**
 * 美工刀-锋利筹码:与美工刀-初级同构,生命值不低于 60%(或处于「汲取」)时 **攻击力 +4**、
 * 并使**攻击伤害**额外增加「当前治愈点数」(攻击力走 {@code ATTACK_MODIFIERS}、治愈点数走额外加伤);
 * 与美工刀-初级为不同物品,可同时装备(两者的攻击力与治愈点数加成各自叠加)。
 */
public class CutterBladeChipItem extends BaseChipItem {
    public CutterBladeChipItem(Properties properties) {
        super(properties);
    }
}
