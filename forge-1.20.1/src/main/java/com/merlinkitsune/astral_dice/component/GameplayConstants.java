package com.merlinkitsune.astral_dice.component;

import com.merlinkitsune.astral_dice.config.ModCommonConfig;

/**
 * 全局玩法常量。
 * 「仍从配置文件读取」的少量字段(赠送规则书 / 事件作用队伍 / actionbar 时长)必须保持**非 final**:
 * 它们由配置加载完成后的 {@link #refresh()} 写入,改成 final 会让编译期内联导致配置不生效。
 * 其余字段均为固定常量(不再写入配置文件),恒为 final。
 */
public final class GameplayConstants {
    // 星光点获取上限(常量:32)
    public static final int MAX_STARLIGHT = 32;
    // 标记层数上限(常量:32)
    public static final int MAX_MARKER = 32;
    // 效果牌公共冷却(单位:秒,常量:30)
    public static final int EFFECT_CARD_COOLDOWN_SECONDS = 30;
    // 功能效果牌叠加层数上限(常量:3 层,伤害效果牌不使用该叠加)
    public static final int MAX_EFFECT_STACKS = 3;
    // 是否在玩家第一次加入世界时给予《恋的规则书》(默认 true)
    public static boolean GIVE_GUIDE_BOOK_ON_FIRST_JOIN = true;

    // === 效果牌出牌数 ===
    // 效果牌单轮(单出牌周期)出牌数上限:固定常量 9,**不写入配置文件**。
    // 实际上限 = min(基础 1 + 固定来源 + 临时来源, MAX_EFFECT_CARD_PLAYS)
    public static final int MAX_EFFECT_CARD_PLAYS = 9;

    // === 充能流派 ===
    // 充能最大层数(写入常量,暂不提供配置文件)
    public static final int CHARGE_MAX_STACKS = 20;
    // 拥有至少 1 层充能时,立牌主动/效果牌冷却时间缩短比例(20%)
    public static final double CHARGE_COOLDOWN_REDUCTION = 0.2;
    // === 事件系统 ===
    // 事件是否作用于 Minecraft 同队玩家
    public static boolean EVENT_APPLY_MC_TEAM = true;
    // 事件是否作用于 FTB Teams 队友(需安装 FTB Teams,API 不符时自动跳过)
    public static boolean EVENT_APPLY_FTB_TEAM = true;
    // 事件是否作用于 OPAC 队伍(需安装 Open Parties and Claims,API 不符时自动跳过)
    public static boolean EVENT_APPLY_OPAC = true;
    // 立牌主动技能触发冷却(单位:秒,默认 180)
    public static int SIGN_ACTIVE_COOLDOWN_SECONDS = 180;
    // 立牌主动技能触发冷却 tick 数(派生值)
    public static int SIGN_ACTIVE_COOLDOWN_TICKS = SIGN_ACTIVE_COOLDOWN_SECONDS * 20;
    // 立牌主动技能等待期(固定常量 30 秒):需要选择目标/等待释放的立牌(占星师、秘密侦探、枪匠)统一使用
    public static int SKILL_WAIT_SECONDS = 30;
    // 扫地机立牌被动:生命值上限/护甲增益各自的最大上限(点)
    public static int JASMINE_MAX_BONUS = 20;
    // 史莱姆立牌主动技能作用范围(格,默认 16)
    public static int LULU_ACTIVE_RANGE = 16;
    // 上班族立牌被动攻防数值刷新间隔(秒,默认 60)
    public static int PADMAN_REFRESH_SECONDS = 60;
    // 经商立牌被动产星光间隔(秒,默认 60)
    public static int PARUNAN_PASSIVE_INTERVAL_SECONDS = 60;
    // 手持风扇-大:主动技能后对周围敌对目标施加标记的范围(格,常量:16)
    public static final int HAND_FAN_BIG_RANGE = 16;

    // actionbar 消息显示总时长上限(单位: tick,默认 3 秒;任何消息最多显示该时长)
    public static int ACTIONBAR_DURATION_TICKS = 60;
    // actionbar 消息最后淡出时长(单位: tick,默认 1 秒)
    public static int ACTIONBAR_FADE_TICKS = 20;

    // 骰神赐福持续时长(单位:秒,默认 60)
    public static int DICE_BLESSING_DURATION_SECONDS = 60;
    // 诅咒之剑:骰神赐福期间每击杀 1 个不少于 20 血的敌对目标攻击力 +1(每个赐福最多一次),最大增加上限(默认 16,最大 32)
    public static int CURSED_SWORD_BONUS_MAX = 16;
    // 骰神赐福持续时长(单位:tick,派生值)
    public static int DICE_BLESSING_DURATION_TICKS = DICE_BLESSING_DURATION_SECONDS * 20;

    // 卡牌槽位基础规则(默认按普通骰子;具体骰子槽位由 DiceTierRegistry 动态提供)
    public static final int CARD_SLOTS_PER_SIDE = 3;
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

    // 从配置文件刷新仍保留的可配置项(赠送规则书 / 事件作用队伍 / actionbar 时长);其余均为上方固定常量
    public static void refresh() {
        GIVE_GUIDE_BOOK_ON_FIRST_JOIN = ModCommonConfig.GIVE_GUIDE_BOOK_ON_FIRST_JOIN.get();

        EVENT_APPLY_MC_TEAM = ModCommonConfig.EVENT_APPLY_MC_TEAM.get();
        EVENT_APPLY_FTB_TEAM = ModCommonConfig.EVENT_APPLY_FTB_TEAM.get();
        EVENT_APPLY_OPAC = ModCommonConfig.EVENT_APPLY_OPAC.get();

        ACTIONBAR_DURATION_TICKS = ModCommonConfig.ACTIONBAR_DURATION_TICKS.get();
        ACTIONBAR_FADE_TICKS = ModCommonConfig.ACTIONBAR_FADE_TICKS.get();

        // 以下为固定常量对应的派生 tick 值
        SIGN_ACTIVE_COOLDOWN_TICKS = SIGN_ACTIVE_COOLDOWN_SECONDS * 20;
        DICE_BLESSING_DURATION_TICKS = DICE_BLESSING_DURATION_SECONDS * 20;
    }
}