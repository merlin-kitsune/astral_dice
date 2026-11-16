package com.merlinkitsune.astral_dice.effect;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;

/**
 * 「金鲤化龙」：金鲤立牌 souri 在【金鳞】达到 {@code 4} 层时获得的锁存态标记效果
 * （用户口径：化龙用立牌图标）。
 *
 * <p>攻击加成不挂在本效果上：{@code 每层金鳞攻击力 +1} 由
 * {@code combat/DiceCombatModifiers} 的实时谓词按附件层数求和。
 *
 * <p>图标 = {@code images/金鲤立牌.png}（实装路径 {@code textures/mob_effect/souri_dragon.png}）。
 */
public class SouriDragonEffect extends MobEffect {

    public SouriDragonEffect() {
        super(MobEffectCategory.BENEFICIAL, 0xD4AF37);
    }
}
