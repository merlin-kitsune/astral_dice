package com.merlinkitsune.astral_dice.event.api;

import net.minecraft.ChatFormatting;

/**
 * 事件类型(正面 / 中性 / 负面)。
 *
 * <p>2026-10-10 用户裁决:**纯标签** —— 只用于界面文本染色,<b>不得</b>参与任何结算判定
 * (不做「负面事件不可被某立牌挡下」之类)。染色口径:正面=绿 §a、中性=黄 §e、负面=红 §c。
 */
public enum AstralEventPolarity {
    POSITIVE(ChatFormatting.GREEN),
    NEUTRAL(ChatFormatting.YELLOW),
    NEGATIVE(ChatFormatting.RED);

    private final ChatFormatting color;

    AstralEventPolarity(ChatFormatting color) {
        this.color = color;
    }

    /** 该类型对应的文本颜色(仅界面染色用)。 */
    public ChatFormatting color() {
        return this.color;
    }
}
