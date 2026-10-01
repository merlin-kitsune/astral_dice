package com.merlinkitsune.astral_dice.effect;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import com.merlinkitsune.astral_dice.combat.DiceCombatModifiers;

/**
 * 战斗爽(大当家立牌 fen 主动):攻击力 +3,持续 1:00。
 * 数值结算在 DiceCombatModifiers 攻击修饰器。
 */
public class FenFrenzyEffect extends MobEffect {
    public FenFrenzyEffect(int color) {
        super(MobEffectCategory.BENEFICIAL, color);
    }
}
