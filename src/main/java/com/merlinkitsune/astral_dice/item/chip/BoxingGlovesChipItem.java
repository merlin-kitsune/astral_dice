package com.merlinkitsune.astral_dice.item.chip;
import com.merlinkitsune.astral_dice.combat.DiceCombatModifiers;

/**
 * 拳击手套筹码(初级/中级/高级):骰神赐福攻击力 +1/+3/+5。
 * 加成结算在 {@link com.merlinkitsune.astral_dice.combat.DiceCombatModifiers} 攻击修饰器。
 */
public class BoxingGlovesChipItem extends BaseChipItem {
    /** 拳击手套-初级攻击加成 */
    public static final int BONUS_LOW = 1;
    /** 拳击手套-中级攻击加成 */
    public static final int BONUS_MEDIUM = 3;
    /** 拳击手套-高级攻击加成 */
    public static final int BONUS_HIGH = 5;

    public BoxingGlovesChipItem(Properties properties) {
        super(properties);
    }
}
