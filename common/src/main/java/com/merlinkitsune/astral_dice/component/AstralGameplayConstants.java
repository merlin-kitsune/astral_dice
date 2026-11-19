package com.merlinkitsune.astral_dice.component;

/**
 * Astral Dice 内容玩法常量。
 *
 * <p>这些数值原先位于 StarEngine 的
 * {@code com.merlinkitsune.starengine.component.GameplayConstants}。按 API 归属约定
 * （库只保留跨消费方通用的 seam），于 26.4-SNAPSHOT.97 搬回主模组；库侧只保留
 * actionbar、事件作用队伍、目标选择半径与枪械伤害开关等通用常量。
 *
 * <p>全部为固定常量，不写入配置文件；原库侧的派生 tick 值在这里用常量表达式直接给出。
 */
public final class AstralGameplayConstants {
    // 星光点获取上限(常量:32)
    public static final int MAX_STARLIGHT = 32;
    // 标记层数上限(常量:32)
    public static final int MAX_MARKER = 32;
    // 效果牌公共冷却(单位:秒,常量:30)
    public static final int EFFECT_CARD_COOLDOWN_SECONDS = 30;
    // 功能效果牌叠加层数上限(常量:3 层,伤害效果牌不使用该叠加)
    public static final int MAX_EFFECT_STACKS = 3;

    // === 效果牌出牌数 ===
    // 效果牌单轮(单出牌周期)出牌数上限:固定常量 9,**不写入配置文件**。
    // 实际上限 = min(基础 1 + 固定来源 + 临时来源, MAX_EFFECT_CARD_PLAYS)
    public static final int MAX_EFFECT_CARD_PLAYS = 9;

    // === 充能流派 ===
    // 充能最大层数(写入常量,暂不提供配置文件)
    public static final int CHARGE_MAX_STACKS = 20;
    // 拥有至少 1 层充能时,立牌主动技能冷却的**上限**(单位:秒,2026-09-25 改口径):
    // 基础值超过该上限时封顶为 160 秒;基础值本就更低(如枪匠「精密技巧」的 120 秒)则不受影响。
    // 该上限作用于**基础值**,之后再叠加其它减免(诡异骰子 -50% 等)。
    public static final int CHARGE_SIGN_COOLDOWN_CAP_SECONDS = 160;
    // 拥有至少 1 层充能时,效果牌公共冷却的**上限**(单位:秒,2026-09-25 改口径):
    // 基础值 30 秒 ⇒ 有充能时 20 秒;同上,作用于基础值。
    public static final int CHARGE_EFFECT_CARD_COOLDOWN_CAP_SECONDS = 20;

    // 立牌主动技能触发冷却(单位:秒,默认 180)
    public static final int SIGN_ACTIVE_COOLDOWN_SECONDS = 180;
    // 立牌主动技能触发冷却 tick 数(派生值)
    public static final int SIGN_ACTIVE_COOLDOWN_TICKS = SIGN_ACTIVE_COOLDOWN_SECONDS * 20;
    // 立牌主动技能等待期(固定常量 30 秒):需要选择目标/等待释放的立牌(占星师、秘密侦探、枪匠)统一使用
    public static final int SKILL_WAIT_SECONDS = 30;
    // 扫地机立牌被动:生命值上限/护甲增益各自的最大上限(点)
    public static final int JASMINE_MAX_BONUS = 20;
    // 史莱姆立牌主动技能作用范围(格,默认 16)
    public static final int LULU_ACTIVE_RANGE = 16;
    // 上班族立牌被动攻防数值刷新间隔(秒,默认 60)
    public static final int PADMAN_REFRESH_SECONDS = 60;
    // 经商立牌被动产星光间隔(秒,默认 60)
    public static final int PARUNAN_PASSIVE_INTERVAL_SECONDS = 60;
    // 手持风扇-大:主动技能后对周围敌对目标施加标记的范围(格,常量:16)
    public static final int HAND_FAN_BIG_RANGE = 16;

    // 骰神赐福持续时长(单位:秒,默认 60)
    public static final int DICE_BLESSING_DURATION_SECONDS = 60;
    // 骰神赐福持续时长(单位:tick,派生值)
    public static final int DICE_BLESSING_DURATION_TICKS = DICE_BLESSING_DURATION_SECONDS * 20;
    // 诅咒之剑:骰神赐福期间每击杀 1 个不少于 20 血的敌对目标攻击力 +1(每个赐福最多一次),最大增加上限。
    public static final int CURSED_SWORD_BONUS_MAX = 32;

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

    private AstralGameplayConstants() {
    }
}
