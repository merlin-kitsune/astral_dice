package com.merlinkitsune.astral_dice.config;

import net.minecraftforge.common.ForgeConfigSpec;

/**
 * 公共配置:仅保留少量仍允许玩家调整的选项。
 * 其余玩法数值已固定为 {@link com.merlinkitsune.astral_dice.component.GameplayConstants} 常量,
 * 不再写入配置文件;配置版本固定为 1。
 */
public final class ModCommonConfig {
    public static final int CONFIG_VERSION = 1;

    public static final ForgeConfigSpec SPEC;

    public static final ForgeConfigSpec.IntValue MAX_STARLIGHT;
    public static final ForgeConfigSpec.IntValue MAX_MARKER;
    public static final ForgeConfigSpec.IntValue EFFECT_CARD_COOLDOWN_SECONDS;
    public static final ForgeConfigSpec.IntValue MAX_EFFECT_STACKS;
    public static final ForgeConfigSpec.IntValue EVENT_RANGE;
    public static final ForgeConfigSpec.BooleanValue EVENT_APPLY_MC_TEAM;
    public static final ForgeConfigSpec.BooleanValue EVENT_APPLY_FTB_TEAM;
    public static final ForgeConfigSpec.BooleanValue EVENT_APPLY_OPAC;
    public static final ForgeConfigSpec.BooleanValue EVENT_APPLY_MAID;
    public static final ForgeConfigSpec.IntValue HAND_FAN_BIG_RANGE;
    public static final ForgeConfigSpec.IntValue ACTIONBAR_DURATION_TICKS;
    public static final ForgeConfigSpec.IntValue ACTIONBAR_FADE_TICKS;

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();
        builder.comment("配置文件版本号:固定为 1")
                .define("config_version", CONFIG_VERSION);

        MAX_STARLIGHT = builder.comment("星光获取上限(默认：最大 32 点)")
                .defineInRange("max_starlight", 32, 8, 48);
        MAX_MARKER = builder.comment("标记层数上限(默认：最大 16 层)")
                .defineInRange("max_marker", 16, 8, 48);
        EFFECT_CARD_COOLDOWN_SECONDS = builder.comment("效果牌公共冷却(单位：秒,默认：30)")
                .defineInRange("effect_card_cooldown_seconds", 30, 5, 120);
        MAX_EFFECT_STACKS = builder.comment("功能效果牌叠加层数上限(默认：3 层,伤害效果牌不使用该叠加)")
                .defineInRange("max_effect_stacks", 3, 1, 9);

        builder.push("event_system").comment("=== 事件系统 ===");
        EVENT_RANGE = builder.comment("事件作用范围(格,默认 16)")
                .defineInRange("event_range", 16, 1, 32);
        EVENT_APPLY_MC_TEAM = builder.comment("事件是否作用于 Minecraft 同队玩家")
                .define("event_apply_mc_team", true);
        EVENT_APPLY_FTB_TEAM = builder.comment("事件是否作用于 FTB Teams 队友(需安装 FTB Teams,API 不符时自动跳过)")
                .define("event_apply_ftb_team", true);
        EVENT_APPLY_OPAC = builder.comment("事件是否作用于 OPAC 队伍(需安装 Open Parties and Claims,API 不符时自动跳过)")
                .define("event_apply_opac", true);
        EVENT_APPLY_MAID = builder.comment("事件是否作用于玩家拥有的已放出女仆(需安装车万女仆模组)")
                .define("event_apply_maid", true);
        builder.pop();

        builder.push("chips").comment("=== 筹码 ===");
        HAND_FAN_BIG_RANGE = builder.comment("手持风扇-大:主动技能后对周围敌对目标施加标记的范围(单位：格,默认：16)")
                .defineInRange("hand_fan_big_range", 16, 1, 64);
        builder.pop();

        builder.push("actionbar").comment("=== actionbar ===");
        ACTIONBAR_DURATION_TICKS = builder.comment("actionbar 消息显示总时长上限(单位: tick,默认：3 秒; 任何消息最多显示该时长)")
                .defineInRange("actionbar_duration_ticks", 60, 20, 200);
        ACTIONBAR_FADE_TICKS = builder.comment("actionbar 消息最后淡出时长(单位: tick,默认：1 秒)")
                .defineInRange("actionbar_fade_ticks", 20, 1, 60);
        builder.pop();

        SPEC = builder.build();
    }

    private ModCommonConfig() {
    }
}
