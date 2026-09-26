package com.merlinkitsune.astral_dice.item.chip;

/**
 * 美工刀-锋利筹码:与美工刀-初级一致 —— 生命值不低于 60%(或处于「汲取」)时 **基础攻击力 +4**
 * (计入攻击力),并按当前治愈层数提供**伤害加成**。
 *
 * <p>与 {@link CutterChipItem} 为**不同物品,可同时装备**,加成叠加:攻击力 +2+4;
 * 治愈层数的伤害加成**每枚各计 1 份**(2026-09-26 用户裁决)⇒ 两枚同装时治愈部分 ×2。
 * 门槛判定与常量统一放在 {@link CutterChipItem},本类不复制。
 */
public class CutterBladeChipItem extends BaseChipItem {

    /** 美工刀-锋利的基础攻击力加成。与 tooltip / 手册「攻击力 +4」同源。 */
    public static final int BASE_ATTACK = 4;

    public CutterBladeChipItem(Properties properties) {
        super(properties);
    }
}
