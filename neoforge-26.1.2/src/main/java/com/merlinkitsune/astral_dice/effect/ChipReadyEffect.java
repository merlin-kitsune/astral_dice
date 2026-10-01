package com.merlinkitsune.astral_dice.effect;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;

/**
 * 筹码「就绪 / 生效中」指示器（标记类，仅用于 HUD 图标）。
 *
 * <p>2026-10-01 用户裁决：**伤害增加型筹码**的状态提示一律以「可生效即常驻」为口径 ——
 * 图标只表达「加成正在生效」，不承担倒计时语义，故时长一律 {@code MobEffectInstance.INFINITE_DURATION}；
 * 存在与否由 {@code PlayerTickEvents#updateChipBonusIndicators} 每 tick 按各自条件维护。
 *
 * <p>与本模组既有的 {@code com.merlinkitsune.starenginelib.effect.CutterReadyEffect} /
 * {@code FlashlightReadyEffect} 同形（{@code MobEffect(BENEFICIAL, color)}），
 * 单独建类是为了让新增的 6 枚指示器共用一个语义清晰的类型，避免语义错配。
 */
public class ChipReadyEffect extends MobEffect {
    public ChipReadyEffect(int color) {
        super(MobEffectCategory.BENEFICIAL, color);
    }
}
