package com.merlinkitsune.astral_dice.config;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * 公共配置:承载全部玩法常量。
 * 配置版本号:每次新增配置项时推进(备份旧文件并继承旧值)。
 */
public final class ModCommonConfig {
        // 当前配置版本:新增配置选项时必须 +1
        public static final int CONFIG_VERSION = 9;

        public static final ModConfigSpec SPEC;

        public static final ModConfigSpec.IntValue MAX_STARLIGHT;
        public static final ModConfigSpec.IntValue MAX_MARKER;
        public static final ModConfigSpec.IntValue EFFECT_CARD_COOLDOWN_SECONDS;
        public static final ModConfigSpec.IntValue MAX_EFFECT_STACKS;
        public static final ModConfigSpec.IntValue MAX_EFFECT_CARD_PLAYS;
        public static final ModConfigSpec.IntValue MAX_DAMAGE_EFFECT_BONUS;
        public static final ModConfigSpec.IntValue TARGET_CHIP_RANGE;
        public static final ModConfigSpec.IntValue EVENT_RANGE;
        public static final ModConfigSpec.BooleanValue EVENT_APPLY_MC_TEAM;
        public static final ModConfigSpec.BooleanValue EVENT_APPLY_FTB_TEAM;
        public static final ModConfigSpec.BooleanValue EVENT_APPLY_OPAC;
        public static final ModConfigSpec.BooleanValue EVENT_APPLY_MAID;
        public static final ModConfigSpec.IntValue LIVING_BOOK_PAGE_BONUS_CAP;
        public static final ModConfigSpec.IntValue SIGN_ACTIVE_COOLDOWN_SECONDS;
        public static final ModConfigSpec.IntValue SKILL_WAIT_SECONDS;
        public static final ModConfigSpec.IntValue JASMINE_MAX_BONUS;
        public static final ModConfigSpec.IntValue LULU_ACTIVE_RANGE;
        public static final ModConfigSpec.IntValue PADMAN_REFRESH_SECONDS;
        public static final ModConfigSpec.IntValue PARUNAN_PASSIVE_INTERVAL_SECONDS;
        public static final ModConfigSpec.IntValue HAND_FAN_BIG_RANGE;
        public static final ModConfigSpec.IntValue KOMACHI_DAMAGE_BONUS_MAX;
        public static final ModConfigSpec.IntValue ACTIONBAR_DURATION_TICKS;
        public static final ModConfigSpec.IntValue ACTIONBAR_FADE_TICKS;
        public static final ModConfigSpec.IntValue HEALING_CYCLE_SECONDS;
        public static final ModConfigSpec.IntValue DICE_BLESSING_DURATION_SECONDS;

        static {
                ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
                builder.comment("配置文件版本号:新增配置项时推进(自动备份旧文件并继承旧值)")
                                .define("config_version", CONFIG_VERSION);

                MAX_STARLIGHT = builder.comment("星光获取上限(默认：最大 32 点)")
                                .defineInRange("max_starlight", 32, 8, 48);
                MAX_MARKER = builder.comment("标记层数上限(默认：最大 16 层)")
                                .defineInRange("max_marker", 16, 8, 48);
                EFFECT_CARD_COOLDOWN_SECONDS = builder.comment("效果牌公共冷却(单位：秒,默认：30)")
                                .defineInRange("effect_card_cooldown_seconds", 30, 5, 120);
                MAX_EFFECT_STACKS = builder.comment("功能效果牌叠加层数上限(默认：3 层,伤害效果牌不使用该叠加)")
                                .defineInRange("max_effect_stacks", 3, 1, 9);
                MAX_EFFECT_CARD_PLAYS = builder.comment("效果牌周期内连续出牌上限(默认：9 张)")
                                .defineInRange("max_effect_card_plays", 9, 1, 18);
                MAX_DAMAGE_EFFECT_BONUS = builder.comment("伤害效果牌伤害加成上限(默认：最大 16 点)")
                                .defineInRange("max_damage_effect_bonus", 16, 1, 64);
                TARGET_CHIP_RANGE = builder.comment("标靶筹码:骰神赐福后标记作用范围(单位：格,默认：16)")
                                .defineInRange("target_chip_range", 16, 1, 32);

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
                LIVING_BOOK_PAGE_BONUS_CAP = builder.comment("活体书页出牌伤害数增加上限(默认：最大 20 点)")
                                .defineInRange("living_book_page_bonus_cap", 20, 1, 64);
                builder.pop();

                builder.push("signs").comment("=== 立牌 ===");
                SIGN_ACTIVE_COOLDOWN_SECONDS = builder.comment("立牌主动技能冷却(单位：秒,默认：180)")
                                .defineInRange("sign_active_cooldown_seconds", 180, 1, 360);
                SKILL_WAIT_SECONDS = builder.comment("立牌主动技能等待期，需要选择目标的技能(占星师/秘密侦探)激活后,等待期内未对目标释放则自动取消(单位：秒,默认：30)")
                                .defineInRange("skill_wait_seconds", 30, 5, 120);
                JASMINE_MAX_BONUS = builder.comment("扫地机立牌被动:生命值上限/护甲增益各自的最大上限")
                                .defineInRange("jasmine_max_bonus", 20, 1, 64);
                LULU_ACTIVE_RANGE = builder.comment("史莱姆立牌主动技能作用范围(单位：格,默认：16)")
                                .defineInRange("lulu_active_range", 16, 1, 32);
                PADMAN_REFRESH_SECONDS = builder.comment("上班族立牌被动攻防数值刷新间隔(单位：秒,默认：60)")
                                .defineInRange("padman_refresh_seconds", 60, 1, 180);
                PARUNAN_PASSIVE_INTERVAL_SECONDS = builder.comment("经商立牌被动产星光间隔(单位：秒,默认：60)")
                                .defineInRange("parunan_passive_interval_seconds", 60, 1, 180);
                HAND_FAN_BIG_RANGE = builder.comment("手持风扇-大:主动技能后对周围敌对目标施加标记的范围(单位：格,默认：16)")
                                .defineInRange("hand_fan_big_range", 16, 1, 64);
                KOMACHI_DAMAGE_BONUS_MAX = builder.comment("忍者立牌:效果牌伤害增益上限(每使用3张效果牌+1,默认：10,最大：16)")
                                .defineInRange("komachi_damage_bonus_max", 10, 0, 16);
                builder.pop();

                builder.push("actionbar").comment("=== actionbar ===");
                ACTIONBAR_DURATION_TICKS = builder.comment("actionbar 消息显示总时长上限(单位: tick,默认：3 秒; 任何消息最多显示该时长)")
                                .defineInRange("actionbar_duration_ticks", 60, 20, 200);
                ACTIONBAR_FADE_TICKS = builder.comment("actionbar 消息最后淡出时长(单位: tick,默认：1 秒)")
                                .defineInRange("actionbar_fade_ticks", 20, 1, 60);
                builder.pop();

                builder.push("healing").comment("=== 治愈 ===");
                HEALING_CYCLE_SECONDS = builder.comment("治愈点数结算周期(单位：秒,默认：30)")
                                .defineInRange("healing_cycle_seconds", 30, 5, 120);
                builder.pop();

                builder.push("dice").comment("=== 骰子 ===");
                DICE_BLESSING_DURATION_SECONDS = builder.comment("骰神赐福持续时长(单位：秒,默认：60)")
                                .defineInRange("dice_blessing_duration_seconds", 60, 10, 300);
                builder.pop();

                SPEC = builder.build();
        }

        private ModCommonConfig() {
        }
}
