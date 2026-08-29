package com.merlinkitsune.astral_dice.effect;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import com.merlinkitsune.astral_dice.event.ModEventHandlers;

/**
 * 美工刀状态效果(标记类):佩戴美工刀-初级/锋利且生命值已满时显示效果图标,提示加成生效中。
 * 生效条件与时长维护在 ModEventHandlers 的玩家 tick 中。
 */
public class CutterReadyEffect extends MobEffect {
    public CutterReadyEffect(int color) {
        super(MobEffectCategory.BENEFICIAL, color);
    }
}
