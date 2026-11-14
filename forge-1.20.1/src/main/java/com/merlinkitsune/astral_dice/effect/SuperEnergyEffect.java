package com.merlinkitsune.astral_dice.effect;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;

/**
 * 超强精力：完美树枝的层数指示器，amplifier + 1 = 当前层数（最多 10 层）。
 * 攻击力 +2/层、防御力 +1/层分别由 {@code DiceCombatModifiers} 与 {@code PerfectBranchChipItem} 的护甲刷新实现。
 */
public class SuperEnergyEffect extends MobEffect {
    public SuperEnergyEffect() {
        super(MobEffectCategory.BENEFICIAL, 0xFF7F24);
    }
}
