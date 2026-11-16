package com.merlinkitsune.astral_dice.effect;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;

/**
 * 「雷暴将至」事件的 HUD 指示器（敌对生物攻/防 +2，2:00）。
 *
 * <p>图标 = 同名事件图标（`assets/astral_dice/textures/mob_effect/storm_approaching.png`，由
 * `tools/gen_event_indicator_textures.py` 从 `textures/gui/event/storm_approaching.png` 生成，32×32）。
 * 施加时 `showIcon=true` ⇒ 由原版效果 HUD 双端同步显示（2026-10-10 用户裁决 1/2）。
 */
public class StormApproachingEffect extends MobEffect {
    /** 与事件同步的时长。 */
    public static final int DURATION_TICKS = 2400;

    public StormApproachingEffect() {
        super(MobEffectCategory.NEUTRAL, 0x4A6FA5);
    }
}
