package com.merlinkitsune.astral_dice.item.chip;

import net.minecraft.world.item.ItemStack;
import com.merlinkitsune.astral_dice.combat.DiceCombatModifiers;

/**
 * 鹰眼瞄具筹码:攻击时对目标施加 1 层标记,并对**带标记目标**追加「标记层数 ×
 * {@value #DAMAGE_PER_MARK}」的**攻击伤害**(额外加伤通道,结算在 DiceCombatModifiers)。
 *
 * <p>⚠️ 2026-10-15 用户裁决:原口径是「本次战斗 ap += 标记层数 × 2」(**攻击力**通道 —— 会被暴击
 * ×1.5 放大并参与骰战「攻击点 − 防御点」的差值结算),现改为**攻击伤害**(与手电筒-爆闪同款的
 * {@code registerExtraDamageModifier} ⇒ 独立 {@code astral_dice:extra_damage} 伤害实例)。
 * 层数一律取**命中前**的层数(与改动前一致:加伤不因本次攻击新施加的那 1 层而增加)。
 */
public class EagleScopeChipItem extends BaseChipItem {
    /** 每 1 层目标标记提供的**攻击伤害**加成(额外加伤通道) */
    public static final int DAMAGE_PER_MARK = 2;

    public EagleScopeChipItem(Properties properties) {
        super(properties);
    }
}
