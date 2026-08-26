package com.merlinkitsune.astral_dice.effect;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;

/**
 * 骇客立牌主动"远程骇入"的攻击力加成效果。
 * 实际加成数值由玩家附件 hacker_active_bonus 提供,DiceCombatModifiers 读取。
 */
public class HackerHackEffect extends MobEffect {
    public HackerHackEffect() {
        super(MobEffectCategory.BENEFICIAL, 0x00E676);
    }
}
