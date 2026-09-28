package com.merlinkitsune.astral_dice.item.chip;

import net.minecraft.world.item.ItemStack;
import com.merlinkitsune.astral_dice.combat.DiceCombatModifiers;

/**
 * 美工刀-初级筹码:生命值不低于 60%(或处于「汲取」)时 **攻击力 +2**、并使**攻击伤害**额外增加
 * 「当前治愈点数」—— 两段口径<b>不同</b>,必须区分(均结算在 {@code DiceCombatModifiers}):
 * 攻击力走 {@code ATTACK_MODIFIERS}(会被按「攻击力快照」结算的技能读到),
 * 治愈点数走额外加伤 {@code EXTRA_DAMAGE_MODIFIERS}(独立伤害类型,不进攻击力)。
 */
public class CutterChipItem extends BaseChipItem {
    public CutterChipItem(Properties properties) {
        super(properties);
    }
}
