package com.merlinkitsune.astral_dice.effect;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;

/**
 * 「红温警告」事件的 HUD 指示器（攻击力 +5，3:00）。
 *
 * <p>图标 = 同名事件图标（`assets/astral_dice/textures/mob_effect/red_heat_warning.png`，由
 * `tools/gen_event_indicator_textures.py` 从 `textures/gui/event/red_heat_warning.png` 生成，32×32）。
 * 施加时 `showIcon=true` ⇒ 由原版效果 HUD 双端同步显示（2026-10-10 用户裁决 1/2）。
 */
public class RedHeatWarningEffect extends MobEffect {
    /** 与事件同步的时长。 */
    public static final int DURATION_TICKS = 3600;

    public RedHeatWarningEffect() {
        super(MobEffectCategory.BENEFICIAL, 0xE04A2F);
    }
}
