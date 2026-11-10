package com.merlinkitsune.astral_dice.effect;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;

/**
 * 「金鳞」：金鲤立牌 souri 的层数镜像效果（层数真值 = 玩家附件
 * {@code ModAttachments#SOURI_GOLD_SCALES}，本效果只是 HUD 可见载体 +
 * {@code amplifier == 层数 - 1}）。
 *
 * <p>收益**不**挂在本效果上：每层防御力 +1 由 {@code SouriSignManager} 用
 * {@code DiceCombatModifiers#setDefenseArmorBonus} 折算成真实护甲（固定 id ⇒ 幂等），
 * 金鲤化龙后每层攻击力 +1 由 {@code DiceCombatModifiers} 的实时谓词提供。
 *
 * <p>图标暂用 {@code images/金鲤立牌.png}（用户 2026-11-09 答复：金鳞图待补，先用立牌图占位）。
 */
public class SouriGoldScaleEffect extends MobEffect {

    /** 层数上限（用户需求冻结值） */
    public static final int MAX_STACKS = 4;

    public SouriGoldScaleEffect() {
        super(MobEffectCategory.BENEFICIAL, 0xF2C14E);
    }
}
