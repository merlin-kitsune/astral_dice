package com.merlinkitsune.astral_dice.effect;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;

/**
 * 「问题外卖」事件的**专用指示器**（中毒/饥饿 0:15 + 15 秒治疗失效）。
 *
 * <p>图标 = 同名事件图标（`assets/astral_dice/textures/mob_effect/problem_delivery.png`，由
 * `tools/gen_event_indicator_textures.py` 从 `textures/gui/event/problem_delivery.png` 生成，32×32）。
 * 施加时 `showIcon=true` ⇒ 由原版效果 HUD 双端同步显示（2026-10-10 用户裁决 1/2）。
 */
public class ProblemDeliveryEffect extends MobEffect {
    /** 与事件同步的时长。 */
    public static final int DURATION_TICKS = 300;

    public ProblemDeliveryEffect() {
        super(MobEffectCategory.HARMFUL, 0x6B8E23);
    }
}
