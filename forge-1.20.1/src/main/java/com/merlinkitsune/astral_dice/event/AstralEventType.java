package com.merlinkitsune.astral_dice.event;

import net.minecraft.resources.ResourceLocation;

/**
 * 事件类型:由唯一 ID 与效果逻辑组成,可被立牌注册与触发。
 */
public record AstralEventType(ResourceLocation id, EventEffect effect) {
    public void trigger(EventContext context) {
        effect.apply(context);
    }
}
