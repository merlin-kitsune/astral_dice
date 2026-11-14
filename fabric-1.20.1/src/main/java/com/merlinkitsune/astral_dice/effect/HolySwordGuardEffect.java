package com.merlinkitsune.astral_dice.effect;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;

/**
 * 圣剑守护：石中剑-精良在骰神赐福触发时给自身与队伍角色施加的纯标记效果。
 * 攻击力 +2 / 受到伤害 -2 分别由 {@code DiceCombatModifiers} 与 {@code ChipDamageHandler} 读取本效果实现。
 */
public class HolySwordGuardEffect extends MobEffect {
    public HolySwordGuardEffect() {
        super(MobEffectCategory.BENEFICIAL, 0xF2C14E);
    }
}
