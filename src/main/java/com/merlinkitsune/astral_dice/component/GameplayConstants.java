package com.merlinkitsune.astral_dice.component;

import com.merlinkitsune.astral_dice.config.ModCommonConfig;

/**
 * 全局玩法常量(运行时从配置文件读取)。
 * 配置加载完成后(FMLCommonSetupEvent)调用 {@link #refresh()} 将配置值写入字段。
 * 注意:字段为非 final,引用处为运行时读取,切勿改回 final(否则编译期内联导致配置不生效)。
 */
public final class GameplayConstants {
    // 星光点获取上限(默认最大 32 点)
    public static int MAX_STARLIGHT = 32;
    // 标记层数上限(默认最大 16 层)
    public static int MAX_MARKER = 16;
    // 效果牌公共冷却(单位:秒,默认 30)
    public static int EFFECT_CARD_COOLDOWN_SECONDS = 30;
    // 功能效果牌叠加层数上限(默认 3 层,伤害效果牌不使用该叠加)
    public static int MAX_EFFECT_STACKS = 3;
    // 效果牌周期内连续出牌上限(默认 9 张)
    public static int MAX_EFFECT_CARD_PLAYS = 9;
    // 伤害效果牌伤害加成上限(默认最大 16 点)
    public static int MAX_DAMAGE_EFFECT_BONUS = 16;
    // 标靶筹码:骰神赐福后标记作用范围(格,默认 16)
    public static int TARGET_CHIP_RANGE = 16;

    // === 事件系统 ===
    // 事件作用范围(格,默认 16)
    public static int EVENT_RANGE = 16;
    // 事件是否作用于 Minecraft 同队玩家
    public static boolean EVENT_APPLY_MC_TEAM = true;
    // 事件是否作用于 FTB Teams 队友(需安装 FTB Teams,API 不符时自动跳过)
    public static boolean EVENT_APPLY_FTB_TEAM = true;
    // 事件是否作用于 OPAC 队伍(需安装 Open Parties and Claims,API 不符时自动跳过)
    public static boolean EVENT_APPLY_OPAC = true;
    // 事件是否作用于玩家拥有的已放出女仆(需安装车万女仆模组)
    public static boolean EVENT_APPLY_MAID = true;
    // 活体书页出牌伤害数增加上限(默认最大 20 点)
    public static int LIVING_BOOK_PAGE_BONUS_CAP = 20;
    // 立牌主动技能触发冷却(单位:秒,默认 180)
    public static int SIGN_ACTIVE_COOLDOWN_SECONDS = 180;
    // 立牌主动技能触发冷却 tick 数(派生值)
    public static int SIGN_ACTIVE_COOLDOWN_TICKS = SIGN_ACTIVE_COOLDOWN_SECONDS * 20;
    // 立牌主动技能等待期(秒,默认 30):需要选择目标的技能(占星师/秘密侦探)激活后,等待期内未释放则自动取消
    public static int SKILL_WAIT_SECONDS = 30;
    // 扫地机立牌被动:生命值上限/护甲增益各自的最大上限(点)
    public static int JASMINE_MAX_BONUS = 20;
    // 史莱姆立牌主动技能作用范围(格,默认 16)
    public static int LULU_ACTIVE_RANGE = 16;
    // 上班族立牌被动攻防数值刷新间隔(秒,默认 60)
    public static int PADMAN_REFRESH_SECONDS = 60;
    // 经商立牌被动产星光间隔(秒,默认 60)
    public static int PARUNAN_PASSIVE_INTERVAL_SECONDS = 60;
    // 手持风扇-大:主动技能后对周围敌对目标施加标记的范围(格,默认 16)
    public static int HAND_FAN_BIG_RANGE = 16;
    // 忍者立牌:效果牌伤害增益上限(每使用 3 张效果牌 +1,默认 10,最大 16)
    public static int KOMACHI_DAMAGE_BONUS_MAX = 10;

    // actionbar 消息显示总时长上限(单位: tick,默认 3 秒;任何消息最多显示该时长)
    public static int ACTIONBAR_DURATION_TICKS = 60;
    // actionbar 消息最后淡出时长(单位: tick,默认 1 秒)
    public static int ACTIONBAR_FADE_TICKS = 20;

    // 骰神赐福持续时长(单位:秒,默认 60)
    public static int DICE_BLESSING_DURATION_SECONDS = 60;
    // 骰神赐福持续时长(单位:tick,派生值)
    public static int DICE_BLESSING_DURATION_TICKS = DICE_BLESSING_DURATION_SECONDS * 20;

    // 卡牌槽位/费用点数固定规则(所有骰子统一)
    public static final int CARD_SLOTS_PER_SIDE = 6;
    public static final int CARD_SLOTS_TOTAL = CARD_SLOTS_PER_SIDE * 2;
    public static final int MAX_CARD_COST = 6;

    // 战斗伤害/法伤计算间隔(单位:tick,默认 20t,不写入配置文件)
    public static final int COMBAT_DAMAGE_CALC_INTERVAL_TICKS = 20;
    public static final int SPELL_DAMAGE_CALC_INTERVAL_TICKS = 20;

    // 治愈效果计时器(单位:tick,默认 30 秒,不写入配置文件)
    public static final int HEALING_TIMER_TICKS = 30 * 20;



    // 骰子星级对应的最大费用点数:0星3、1星4、2星5、3星6
    public static int cardCostForStar(int starLevel) {
        return Math.min(MAX_CARD_COST, 3 + Math.max(0, starLevel));
    }
    private GameplayConstants() {
    }

    // 从配置文件刷新全部常量(配置加载完成后调用)
    public static void refresh() {
        MAX_STARLIGHT = ModCommonConfig.MAX_STARLIGHT.get();
        MAX_MARKER = ModCommonConfig.MAX_MARKER.get();
        EFFECT_CARD_COOLDOWN_SECONDS = ModCommonConfig.EFFECT_CARD_COOLDOWN_SECONDS.get();
        MAX_EFFECT_STACKS = ModCommonConfig.MAX_EFFECT_STACKS.get();
        MAX_EFFECT_CARD_PLAYS = ModCommonConfig.MAX_EFFECT_CARD_PLAYS.get();
        MAX_DAMAGE_EFFECT_BONUS = ModCommonConfig.MAX_DAMAGE_EFFECT_BONUS.get();
        TARGET_CHIP_RANGE = ModCommonConfig.TARGET_CHIP_RANGE.get();

        EVENT_RANGE = ModCommonConfig.EVENT_RANGE.get();
        EVENT_APPLY_MC_TEAM = ModCommonConfig.EVENT_APPLY_MC_TEAM.get();
        EVENT_APPLY_FTB_TEAM = ModCommonConfig.EVENT_APPLY_FTB_TEAM.get();
        EVENT_APPLY_OPAC = ModCommonConfig.EVENT_APPLY_OPAC.get();
        EVENT_APPLY_MAID = ModCommonConfig.EVENT_APPLY_MAID.get();
        LIVING_BOOK_PAGE_BONUS_CAP = ModCommonConfig.LIVING_BOOK_PAGE_BONUS_CAP.get();
        SIGN_ACTIVE_COOLDOWN_SECONDS = ModCommonConfig.SIGN_ACTIVE_COOLDOWN_SECONDS.get();
        SIGN_ACTIVE_COOLDOWN_TICKS = SIGN_ACTIVE_COOLDOWN_SECONDS * 20;
        SKILL_WAIT_SECONDS = ModCommonConfig.SKILL_WAIT_SECONDS.get();
        JASMINE_MAX_BONUS = ModCommonConfig.JASMINE_MAX_BONUS.get();
        LULU_ACTIVE_RANGE = ModCommonConfig.LULU_ACTIVE_RANGE.get();
        PADMAN_REFRESH_SECONDS = ModCommonConfig.PADMAN_REFRESH_SECONDS.get();
        PARUNAN_PASSIVE_INTERVAL_SECONDS = ModCommonConfig.PARUNAN_PASSIVE_INTERVAL_SECONDS.get();
        HAND_FAN_BIG_RANGE = ModCommonConfig.HAND_FAN_BIG_RANGE.get();
        KOMACHI_DAMAGE_BONUS_MAX = ModCommonConfig.KOMACHI_DAMAGE_BONUS_MAX.get();

        ACTIONBAR_DURATION_TICKS = ModCommonConfig.ACTIONBAR_DURATION_TICKS.get();
        ACTIONBAR_FADE_TICKS = ModCommonConfig.ACTIONBAR_FADE_TICKS.get();

        DICE_BLESSING_DURATION_SECONDS = ModCommonConfig.DICE_BLESSING_DURATION_SECONDS.get();
        DICE_BLESSING_DURATION_TICKS = DICE_BLESSING_DURATION_SECONDS * 20;
    }
}
