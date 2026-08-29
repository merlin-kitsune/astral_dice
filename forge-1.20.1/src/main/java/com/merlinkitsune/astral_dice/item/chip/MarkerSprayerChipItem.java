package com.merlinkitsune.astral_dice.item.chip;

import net.minecraft.world.item.ItemStack;
import com.merlinkitsune.astral_dice.combat.SpellDamageRegistry;

/**
 * 标记喷灌筹码:对目标造成远程或魔法伤害后,使目标获得一层"标记"(结算在 SpellDamageRegistry 法伤修饰器)。
 */
public class MarkerSprayerChipItem extends BaseChipItem {
    public MarkerSprayerChipItem(Properties properties) {
        super(properties);
    }
}
