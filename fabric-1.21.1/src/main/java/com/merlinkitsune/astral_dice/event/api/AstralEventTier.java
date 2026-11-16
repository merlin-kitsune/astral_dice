package com.merlinkitsune.astral_dice.event.api;

/**
 * 事件阶级。当前仅「秘密侦探专属」的 {@code investigation_stage} 事件使用。
 *
 * <p>2026-10-10 用户裁决:击杀「隐匿调查」目标 ⇒ 触发该事件并<b>提升一个阶级</b>;其它任何触发方式
 * 只能触发<b>当前阶级</b>。阶级值沿用 {@code ModAttachments.getInvestigationStage}(0..4),卸下秘密侦探立牌清零。
 * 一条事件关联 <b>4 张图标</b>(调查阶段1/2/3 + 真相揭露),按阶级切换。
 */
public enum AstralEventTier {
    NONE(0, ""),
    STAGE_I(1, "1"),
    STAGE_II(2, "2"),
    STAGE_III(3, "3"),
    REVELATION(4, "revelation");

    private final int level;
    private final String iconSuffix;

    AstralEventTier(int level, String iconSuffix) {
        this.level = level;
        this.iconSuffix = iconSuffix;
    }

    public int level() {
        return this.level;
    }

    /** 图标文件名后缀:investigation_stage_1 / _2 / _3 / _revelation。 */
    public String iconSuffix() {
        return this.iconSuffix;
    }

    /** 把 0..4 的存档阶级值映射为枚举(越界一律钳制)。 */
    public static AstralEventTier of(int stage) {
        return switch (Math.max(0, Math.min(4, stage))) {
            case 1 -> STAGE_I;
            case 2 -> STAGE_II;
            case 3 -> STAGE_III;
            case 4 -> REVELATION;
            default -> NONE;
        };
    }
}
