package com.merlinkitsune.astral_dice.item.card;

import com.merlinkitsune.starenginelib.component.GameplayConstants;
import com.merlinkitsune.astral_dice.component.ModAttachments;
import com.merlinkitsune.astral_dice.effect.ModEffects;
import com.merlinkitsune.astral_dice.item.ChargeManager;
import com.merlinkitsune.astral_dice.item.chip.ElectricGloveChipItem;
import net.minecraft.world.entity.player.Player;
import net.minecraft.core.Holder;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;


import java.util.ArrayList;
import java.util.List;
import com.merlinkitsune.astral_dice.item.ModItems;

/**
 * 效果牌出牌冷却(窗口)核心逻辑。
 *
 * 规则(冷却与效果判定分离):
 * - 基础出牌数固定为 1(游戏设计决定,不可配置)。
 * - 固定出牌数加成(佩戴即提供,不卸载一直有效):大背包 +1、忍术飞镖 +1。
 * - 临时出牌数加成(仅当前出牌周期有效,周期归零时清除):活体书页**命中前已有 ≥3 层标记**的目标时
 *   累计 +1(可叠加,非"效果存在即 +1"的开关式;补记入口 {@link #grantLivingPageCycleBonus})、
 *   命运的指引效果存在即 +1(覆盖式,不累计)、可口糖果满血触发 +1
 *   (每周期一次)、探天卫星轨道炮触发 +1(每 1:00 一次)、
 *   立牌主动技能一次性 +1({@link #grantBonusPlay},同样只作用于当前出牌轮)。
 * - 出牌数上限:min(1 + 固定 + 临时, {@link GameplayConstants#MAX_EFFECT_CARD_PLAYS})
 *   实时计算,加成来源可叠加,但单轮总出牌数固定封顶 9 张(固定常量,非配置文件项)。
 * - **冷却与效果时长彻底分离(2026-09-30 用户裁决)**:只要出牌即启动冷却,且**同一轮内每次出牌都把它
 *   重置**为 {@link #COOLDOWN_SECONDS} 秒(30 → 45);出牌数打满不再单独判定,同样由该次出牌重置。
 *   冷却归零时出牌数归零(该周期结束)。
 *   效果牌自身的「效果时长」不再锁住出牌 —— 冷却归零后即便效果仍在生效,也可以立即开新一轮;
 *   出牌锁只看出牌数(见 {@link #isBlocked})。
 * - 效果牌轮次(出牌周期)定义:指当前出牌周期——不论出牌数是否已达上限——只要出牌冷却未结束,
 *   周期即未结束;冷却走完(出牌数归零)即周期结束。周期边界用于"每轮一次"类计数清理
 *   与新一轮开牌锁判定。
 *   ⚠️ 2026-09-30 起周期**不再**等待"效果牌自身的效果结束"。
 * - 冷却期间允许继续出牌累积出牌数(上限内);但**每次出牌都会把冷却重置**
 *   (2026-09-30 用户裁决)。
 * - 出牌数来源与上限全部实时计算(不缓存),卸载大背包/忍术飞镖立即生效,
 *   更换立牌/骰子无法刷新出牌锁。
 * - 出牌数/冷却/忍者临时出牌附件均已 .sync() 到客户端,客户端可执行与
 *   服务端一致的 isBlocked 预检(BaseEffectCardItem 在 use/interactLivingEntity
 *   中先做客户端预检,被阻止时不消耗卡片)。
 * - 冷却倒计时归 0 但 tick 尚未清零(每 20 tick 清理一次)的窗口内,旧计数
 *   视为新窗口(见 {@link #isBurstFull}),不会误锁新一轮。
 * - 效果待定判定通过 {@link #registerEffectPendingSource} 注册表统一提供(替代硬编码效果列表),
 *   新增效果牌时注册其"效果是否在生效"的判定即可。
 */
public final class EffectCardPeriod {
    private EffectCardPeriod() {
    }

    // === 冷却时长(模组侧口径,2026-09-30 用户裁决) ===
    // ⚠️ 刻意**不**复用库 GameplayConstants 的 30/20 秒:库的工作树(next, 2.0.0-SNAPSHOT)与
    //    消费方引用的稳定线(1.0.5)存在版本落差,改库要跨分支 bump + 重发 + 三线 refresh;
    //    故本模块自行持值,并由 EffectCardPeriod / ChargeManager / ModTooltipHandler 三处共源。
    /** 效果牌冷却基础时长(秒):30 → 45(2026-09-30 用户裁决) */
    public static final int COOLDOWN_SECONDS = 45;
    /** 拥有「充能」时效果牌冷却的封顶秒数:20 → 30(2026-09-30 用户裁决) */
    public static final int CHARGE_COOLDOWN_CAP_SECONDS = 30;
    // === 出牌数来源(可扩展) ===
    @FunctionalInterface
    public interface ExtraPlaySource {
        boolean isActive(Player player);

        // 出牌数加成(默认 +1;需要其他数值时匿名类覆写)
        default int amount() {
            return 1;
        }
    }

    // === 效果待定来源(可扩展):效果牌使用后是否仍在生效 ===
    @FunctionalInterface
    public interface EffectPendingSource {
        boolean isActive(Player player);

        // 该待定来源对应的效果(用于计算剩余被锁时长;纯逻辑判定可返回 null 不参与时长计算)
        default Holder<MobEffect> effect() {
            return null;
        }
    }

    private static final List<ExtraPlaySource> FIXED_SOURCES = new ArrayList<>();
    private static final List<ExtraPlaySource> TEMPORARY_SOURCES = new ArrayList<>();
    private static final List<EffectPendingSource> EFFECT_PENDING_SOURCES = new ArrayList<>();

    // 注册固定出牌数来源(佩戴即提供)
    public static void registerFixedSource(ExtraPlaySource source) {
        FIXED_SOURCES.add(source);
    }

    // 注册临时出牌数来源(效果/技能状态驱动)
    public static void registerTemporarySource(ExtraPlaySource source) {
        TEMPORARY_SOURCES.add(source);
    }

    // 注册效果待定来源(效果牌使用后判定其效果是否仍在生效)
    public static void registerEffectPendingSource(EffectPendingSource source) {
        EFFECT_PENDING_SOURCES.add(source);
    }

    // 注册"某效果生效期间出牌被锁"的来源(效果驱动,自动提供剩余被锁时长)
    public static void registerEffectPendingSource(Holder<MobEffect> effect) {
        registerEffectPendingSource(new EffectPendingSource() {
            @Override
            public boolean isActive(Player player) {
                return player.hasEffect(effect);
            }

            @Override
            public Holder<MobEffect> effect() {
                return effect;
            }
        });
    }

    /**
     * 「效果牌施加的效果」的权威清单(只读、去重、已剔除 {@code null})——供调试命令
     * {@code /astralparty clearcardeffect} 使用。
     *
     * <p>与 {@link #EFFECT_PENDING_SOURCES} **同源**(逐条取 {@code effect()} 去重),因此
     * 新增效果牌注册后自动纳入,不存在清单漂移;返回 {@link List#copyOf} 的不可变副本,
     * 调用方无法改动注册表。注意本清单的语义是「效果牌留下的、会锁住出牌的效果」
     * (= 出牌锁三条判据里的第 ③ 条),**不含**立牌主动效果与不参与出牌锁的展示类效果。
     */
    public static List<Holder<MobEffect>> effectPendingEffects() {
        List<Holder<MobEffect>> effects = new ArrayList<>();
        for (EffectPendingSource source : EFFECT_PENDING_SOURCES) {
            Holder<MobEffect> effect = source.effect();
            if (effect != null && !effects.contains(effect)) {
                effects.add(effect);
            }
        }
        return List.copyOf(effects);
    }

    /**
     * 效果待定来源的**只读**视图(与 {@link #effectPendingSourceIds()} 一一对应、同序)——供调试命令
     * {@code /astralparty dump} 逐条输出「每个来源自己的 {@code isActive} 结果」。
     *
     * <p>返回 {@link List#copyOf} 的不可变副本,调用方无法改动注册表;顺序 = 注册顺序(静态块里的
     * 注册次序,长期稳定),因此输出行序稳定。本方法**只读**、不改变注册语义。
     *
     * <p>{@link #effectPendingEffects()} 是「去重后的效果清单」(可清对象),本方法是「逐条来源」
     * (可判定对象)——两者同源但用途不同,不要互相替代。
     */
    public static List<EffectPendingSource> effectPendingSources() {
        return List.copyOf(EFFECT_PENDING_SOURCES);
    }

    /**
     * 与 {@link #effectPendingSources()} **一一对应、同序**的稳定来源标识(调试输出用)。
     *
     * <p>效果驱动的来源取其效果注册 id 的**路径段**(如 {@code living_page}),这样标识既稳定
     * (不依赖匿名类的 {@code toString} / 对象身份哈希)又和效果注册 id 对得上;纯逻辑来源
     * (当前不存在)退化为按注册顺序的 {@code source_N}。同一效果被注册多次时,第 2 条起追加
     * {@code #2}、{@code #3} 保证唯一(当前 9 条来源各自对应不同效果,不会走到该分支)。
     */
    public static List<String> effectPendingSourceIds() {
        List<String> ids = new ArrayList<>();
        java.util.Set<String> seen = new java.util.HashSet<>();
        for (int i = 0; i < EFFECT_PENDING_SOURCES.size(); i++) {
            String base = pendingSourceId(EFFECT_PENDING_SOURCES.get(i), i);
            String unique = base;
            int suffix = 2;
            while (!seen.add(unique)) {
                unique = base + "#" + suffix++;
            }
            ids.add(unique);
        }
        return List.copyOf(ids);
    }

    private static String pendingSourceId(EffectPendingSource source, int index) {
        Holder<MobEffect> effect = source.effect();
        if (effect != null) {
            return effect.unwrapKey().map(key -> key.identifier().getPath()).orElse("source_" + index);
        }
        return "source_" + index;
    }

    static {
        // 固定来源:大背包 +1、忍术飞镖 +1(不卸载持续提供)
        registerFixedSource(p -> hasCurio(p, ModItems.BIG_BACKPACK_CHIP.get()));
        registerFixedSource(p -> hasCurio(p, ModItems.NINJA_STAR_CHIP.get()));
        // 临时来源(仅当前出牌周期有效,周期归零时清除):
        // 活体书页已改为"每次使用累计 +1"的本周期计数(见 getMaxAllowed 的 LIVING_PAGE_CYCLE_BONUS),
        // 不再注册为"效果存在即 +1"的开关式临时来源(否则会与累计计数重复计算);
        // 2026-09-19 起该牌为纯即时伤害、不给玩家任何效果 ⇒ 也不再作为"效果待定"来源(见下方注释)。
        registerTemporarySource(p -> p.hasEffect(ModEffects.FATE_GUIDANCE));     // 命运的指引效果(存在即 +1,覆盖式,不累计)
        registerTemporarySource(p -> ModAttachments.isCandyChipPlayBonusActive(p)); // 可口糖果:满血使用效果牌触发(每轮一次)
        registerTemporarySource(p -> ModAttachments.isSatellitePlayBonusActive(p)); // 探天卫星:使用轨道炮后触发(每 1:00 一次)
        // 立牌主动技能的一次性 +1 不再注册为"来源"(它是一次性授予、不是可由谓词反复判定的状态),
        // 直接由 EffectCardPeriod 的出牌轮自有字段承载,见 getMaxAllowed 的 EFFECT_CARD_BONUS_PLAYS。

        // 效果待定来源(全部效果牌统一注册;新增效果牌在此追加或调用 registerEffectPendingSource)
        // ⚠️ 活体书页**不在此列**(2026-09-19 用户裁决「移除所有原本效果器」):它已改为纯即时伤害、
        //    不给玩家任何效果,出牌轮只由出牌数/冷却推进,不存在"等它的效果结束"这一步。
        registerEffectPendingSource(ModEffects.MONSTER_LASER);
        registerEffectPendingSource(ModEffects.MONSTER_BRICK);
        registerEffectPendingSource(ModEffects.ORBITAL_STRIKE);
        registerEffectPendingSource(ModEffects.DIRECTIONAL_BLAST);
        registerEffectPendingSource(ModEffects.FATE_GUIDANCE);
        registerEffectPendingSource(ModEffects.KING_POWER);
        registerEffectPendingSource(ModEffects.BERSERK);
        registerEffectPendingSource(ModEffects.UNWAVERING);
    }

    // 当前出牌数上限 = min(基础 1 + 固定 + 临时 + 本周期一次性追加, MAX_EFFECT_CARD_PLAYS)(实时计算)
    public static int getMaxAllowed(Player player) {
        int extra = 0;
        for (ExtraPlaySource source : FIXED_SOURCES) {
            if (source.isActive(player)) extra += source.amount();
        }
        for (ExtraPlaySource source : TEMPORARY_SOURCES) {
            if (source.isActive(player)) extra += source.amount();
        }
        // 活体书页:命中(前)已有 ≥3 层标记的目标时在本周期内累计 +1(仅当前周期,周期归零时清除;
        // 可叠加,非"效果存在即 +1"的开关式;补记入口 = grantLivingPageCycleBonus,且该入口自身封顶 9)
        extra += ModAttachments.getLivingPageCycleBonus(player);
        // 符卡-福(风水师立牌专属牌):本周期内**每次打出都 +1**(裁决 B),与立牌主动的一次性槽位解耦;
        // 入口自带 MAX_EFFECT_CARD_PLAYS 封顶,叠加结果同样受下方 min(9, 1+extra) 约束
        extra += ModAttachments.getFuCardCycleBonus(player);
        // 立牌主动技能一次性追加(仅当前出牌轮有效,周期结束由 clearRoundBonuses 清除)
        extra += getBonusPlays(player);
        // 防御性下界:附件被写成负值(异常/溢出)时不得让上限退化为 0 或负数——
        // 否则 count >= max 恒成立,出牌会被永久判定为"已打满"
        if (extra < 0) extra = 0;
        // 单轮出牌数固定封顶(常量 9,不写入配置文件)
        return Math.min(GameplayConstants.MAX_EFFECT_CARD_PLAYS, 1 + extra);
    }

    /**
     * 当前出牌轮由立牌主动技能一次性追加的出牌数(0/1)。
     * 与"固定/临时来源"不同,它是一次性**授予**的结果,不是可由谓词反复判定的状态。
     */
    public static int getBonusPlays(Player player) {
        return ModAttachments.getEffectCardBonusPlays(player);
    }

    /**
     * 授予「当前出牌轮 +1 张出牌数」的一次性效果(立牌主动技能入口)。
     *
     * <p><b>一次性</b>:同一出牌轮内只授予一次,不累积、不跨轮保留(周期结束时由
     * {@link #clearRoundBonuses} 清除,不需要也不允许调用方自行清理);出牌轮上限仍受
     * {@link GameplayConstants#MAX_EFFECT_CARD_PLAYS} 封顶约束。
     *
     * @return true = 本次授予成功;false = 本轮已授予过(调用方据此拒绝技能释放,且不得消耗主动技能冷却)
     */
    public static boolean grantBonusPlay(Player player) {
        if (getBonusPlays(player) > 0) return false;
        ModAttachments.setEffectCardBonusPlays(player, 1);
        return true;
    }

    /**
     * 「符卡-福」(风水师立牌专属牌)的出牌数 +1 —— **单一入口**:全仓调用点只有
     * {@code item/card/FuCardItem#applyEffect} 一处,故「+1 的具体口径」被收进这一个方法体,
     * 改口径只需改这里(调用点与用例结构不必动)。
     *
     * <p><b>当前口径(2026-09-26 用户裁决 B:每张各 +1)</b>:新增本周期专属计数器
     * {@code fu_card_cycle_bonus}(语义 = 本周期内打出符卡-福的次数),**每次打出都 +1**、
     * 不做 {@code >0 即拒绝} 的判定;该计数器进入 {@link #getMaxAllowed} 的 {@code extra}
     * (与 {@link #getBonusPlays} / 活体书页累加项同级)并受既有
     * {@code min(9, 1+extra)} 全局封顶 ⇒ **天然不可能刷爆**,无需额外限制;
     * 周期结束时由 {@link #clearRoundBonuses} 与活体书页计数器同址归零。
     *
     * <p><b>与忍者立牌主动彻底解耦</b>:不再调用 {@link #grantBonusPlay}
     * ({@code effect_card_bonus_plays} 那个"每轮单个二进制槽位"),故「忍者先给过 +1」不再吞掉本卡的 +1
     * —— 两者可叠加(可判别 B 与旧口径的交叉验证点)。
     *
     * <p>⚠️ <b>调用时序不可颠倒</b>:必须发生在 {@link BaseEffectCardItem#applyEffect} 之内、
     * **早于** {@link #registerPlay} —— 否则 {@code registerPlay} 里「{@code count >= getMaxAllowed}」
     * 会在本轮上限仍是旧值时立即成立并起 30 秒冷却,「消耗 1 / 返回 1」的净 0 不成立
     * (见 {@code FuCardItem#applyEffect} 的调用点注释)。
     *
     * @return true = 本次成功授予(该次出牌净 0);false = 已达 {@link GameplayConstants#MAX_EFFECT_CARD_PLAYS} 封顶
     */
    public static boolean grantFuCardBonusPlay(Player player) {
        if (player == null) return false;
        int current = ModAttachments.getFuCardCycleBonus(player);
        if (current >= GameplayConstants.MAX_EFFECT_CARD_PLAYS) return false;
        ModAttachments.setFuCardCycleBonus(player, current + 1);
        return true;
    }

    /**
     * 活体书页「连续出牌」补记:命中**前**已有 ≥3 层标记的目标时,本出牌周期出牌数 +1。
     *
     * <p><b>用户裁决(2026-09-25)</b>:仅当命中标记层数 ≥ 3 才应用出牌数 +1,且严格遵守
     * 「单轮出牌数封顶 {@link GameplayConstants#MAX_EFFECT_CARD_PLAYS}」的全局规则。
     * 判定用的层数由调用方({@code combat/LivingPageImpact#resolve})在**施加本次 1 层标记之前**读取。
     *
     * <p><b>跨轮保护</b>:本方法在**命中时**才被调用(飞行结束),与出牌不在同一 tick;
     * 若该出牌轮已归零(忍者宽限强重置等),不得把这次补记漏记到新一轮里 ⇒ 以
     * {@link #getPlayCount} > 0(本轮仍存活)为前提。正常路径下飞行期间活体书页效果仍在生效
     * ({@link #registerEffectPendingSource}),出牌轮不可能归零。
     *
     * <p>与 {@link #grantBonusPlay} 的区别:后者是立牌主动技能的**一次性**授予(同一轮只成功一次),
     * 本方法每次满足条件的命中都可累加,但累加值本身也封顶
     * {@link GameplayConstants#MAX_EFFECT_CARD_PLAYS}(避免周期长期不结算时无界增长)。
     *
     * @return true = 本次补记成功(+1);false = 未补记(本轮已归零或已达封顶)
     */
    public static boolean grantLivingPageCycleBonus(Player player) {
        if (player == null) return false;
        if (getPlayCount(player) <= 0) return false;
        int current = ModAttachments.getLivingPageCycleBonus(player);
        if (current >= GameplayConstants.MAX_EFFECT_CARD_PLAYS) return false;
        ModAttachments.setLivingPageCycleBonus(player, current + 1);
        return true;
    }

    /**
     * 出牌轮归零:清除全部"仅当前出牌轮有效"的出牌数加成与标记,
     * 以及**本周期已武装的电击手套法伤扩散**({@link ElectricGloveChipItem#disarmAoe}——下个周期可重新武装)。
     * <b>唯一入口</b> —— 仅由 {@link #registerPlay} 的周期边界、{@link #tick} 的周期结束(情形 1)
     * 与 {@link #forceResetRound}(忍者宽限期满的强重置)调用
     * (玩家死亡清理自 2026-09-15「S4-C6 清理无效项」裁决后不再调用:这些键不是 copyOnDeath,
     * 新实体上本就是默认值),禁止在别处各自列一遍
     * (历史上分散清理曾导致状态残留与"上限中途下降"的永久锁死 BUG)。
     *
     * <p><b>为什么 disarmAoe 必须放在这里(2026-09-15,本批 C1)</b>:此前它只写在 {@link #tick} 情形 1,
     * 于是"周期正常到期"会解除武装、而忍者宽限期满走的 {@link #forceResetRound} 不会 ⇒
     * <b>两条"轮次完全重置"路径的清理口径不等价</b>,宽限强重置后电击手套本周期仍处于武装态。
     * 上移到本方法后两条路径共用同一套清理口径(它同时也是 {@code registerPlay} 周期边界块的口径,
     * 那里的调用是<b>期望</b>的:轮次完全重置即解除武装)。
     */
    public static void clearRoundBonuses(Player player) {
        ModAttachments.setEffectCardBonusPlays(player, 0);
        ModAttachments.setCandyChipPlayBonusActive(player, false);
        ModAttachments.setSatellitePlayBonusActive(player, false);
        ModAttachments.setLivingPageCycleBonus(player, 0);
        // 符卡-福本周期计数(裁决 B):与活体书页计数器同址清零
        ModAttachments.setFuCardCycleBonus(player, 0);
        // 周期归零:解除电击手套本周期已武装的法伤扩散(下个周期可重新武装)
        ElectricGloveChipItem.disarmAoe(player);
    }

    /**
     * 出牌轮"完全重置"回调(第二批「立牌主动技能三态化」第 4 条):**只**在本方法的两个调用点触发——
     * {@link #tick} 的情形 1(冷却到期后计数归零)与 {@link #registerPlay} 的周期边界块;
     * 情形 2/3(未打满的收尾冷却、不变量违例修复)**不构成"完全重置"**,不得调用。
     *
     * <p>用途:忍者立牌主动"忍术连击"的锁定跟随出牌周期,其主动冷却从"该轮出牌状态完全重置那一刻"开始
     * (见 {@code BaseSignItem#onEffectCardRoundReset})。
     */
    public static void onRoundFullyReset(Player player) {
        com.merlinkitsune.astral_dice.item.sign.BaseSignItem.onEffectCardRoundReset(player);
    }

    /**
     * 强制重置当前出牌轮(忍者主动"宽限 1:00 内未出任何效果牌"的保险,见
     * {@code BaseSignItem#tickSignActiveLock}):出牌数、出牌冷却与全部"每轮一次"标记一并归零
     * (与 {@link #tick} 情形 1 / {@link #registerPlay} 周期边界同一套写法,不另列清理项)。
     *
     * <p>**不**回调 {@link #onRoundFullyReset}:调用方自行决定后续迁移
     * (忍者在此之后立即让自己的主动技能进入冷却)。
     */
    public static void forceResetRound(Player player) {
        ModAttachments.setEffectCardCooldownEnd(player, 0);
        ModAttachments.setEffectCardPlayCount(player, 0);
        clearRoundBonuses(player);
    }

    // 本轮已出牌数
    public static int getPlayCount(Player player) {
        return ModAttachments.getEffectCardPlayCount(player);
    }

    // 周期是否已满(出牌数达到上限)
    // 注意:冷却倒计时已归 0 但 tick 尚未清零(每 20 tick 才清理一次)时,旧计数不再视为"满"——
    // 视为新窗口,避免冷却结束瞬间误锁新一轮(registerPlay 内部有同样的 stale 处理)。
    // 本方法保持只读,客户端(附件已同步)可安全调用做预检。
    public static boolean isBurstFull(Player player) {
        long cdEnd = ModAttachments.getEffectCardCooldownEnd(player);
        if (cdEnd > 0 && player.level().getGameTime() >= cdEnd) return false;
        int count = getPlayCount(player);
        if (count <= 0) return false;
        return count >= getMaxAllowed(player);
    }

    // 冷却是否进行中
    public static boolean isCooldownActive(Player player) {
        long cdEnd = ModAttachments.getEffectCardCooldownEnd(player);
        return cdEnd > 0 && player.level().getGameTime() < cdEnd;
    }

    // 是否仍有效果牌效果在生效(单个轮询内所有已出效果牌的效果结束后才可重新出牌)
    // 通过 EffectPendingSource 注册表统一判定,新增效果牌注册后自动生效
    public static boolean isEffectPending(Player player) {
        for (EffectPendingSource source : EFFECT_PENDING_SOURCES) {
            if (source.isActive(player)) {
                return true;
            }
        }
        return false;
    }

    // 剩余被锁 tick:取“全局冷却结束时间”与“所有效果牌效果中最长的结束时间”的较大值
    // (遍历 EFFECT_PENDING_SOURCES,由各来源的 effect() 推导剩余时长,与出牌锁判定保持单一注册源)
    public static long getRemainingBlockTicks(Player player) {
        // 2026-09-30:只反映**出牌冷却**剩余(效果牌自身的效果时长不再计入出牌锁)
        long now = player.level().getGameTime();
        return Math.max(0, ModAttachments.getEffectCardCooldownEnd(player) - now);
    }

    // 剩余被锁秒数(向上取整)
    public static int getRemainingBlockSeconds(Player player) {
        return (int) Math.ceil(getRemainingBlockTicks(player) / 20.0);
    }

    private static int remainingEffectTicks(Player player, Holder<MobEffect> effect) {
        MobEffectInstance instance = player.getEffect(effect);
        return instance != null ? instance.getDuration() : 0;
    }


    /**
     * 出牌锁判定:**只看本轮出牌数是否已达上限**。
     *
     * <p>2026-09-30 用户裁决后:冷却进行中<b>不</b>拦(玩家可继续用掉本轮剩余出牌数);
     * 「效果待定」(效果牌留下的效果仍在生效)<b>不再</b>锁住出牌 —— 出牌锁与效果时长彻底分离。
     */
    public static boolean isBlocked(Player player) {
        // 2026-09-30 用户裁决:出牌锁只看出牌数 —— 「效果待定」不再锁牌(冷却与效果时长彻底分离);
        // 冷却进行中同样不拦(玩家仍可用掉本轮剩余的出牌数)。
        return isBurstFull(player);
    }

    /**
     * 出牌登记:出牌数 +1(调用前需通过 {@link #isBlocked} 校验)。
     *
     * <p><b>冷却在每次出牌时启动/重置(2026-09-30 用户裁决)</b>:不再要求"打满上限"才进冷却 ——
     * 出任何一张牌都会把冷却到期时刻写成 {@code now + EffectCardPeriod.COOLDOWN_SECONDS}(45 秒),
     * 同一轮内连续出牌会不断把到期时刻推后(最后一次出牌之后 45 秒冷却结束);
     * 冷却归零后由 {@link #tick} 清空出牌数占用。任何增加出牌数的手段(固定/临时来源、立牌主动的一次性 +1)
     * 都只能提高上限,不能绕过 {@link GameplayConstants#MAX_EFFECT_CARD_PLAYS} 这一最高优先级封顶。
     */
    public static void registerPlay(Player player) {
        // 忍者主动"锁定(生效中)"期的出牌佐证(第二批「三态化」第 4 条):期内**出过任何效果牌**即置真。
        // 唯一置真入口(registerPlay 全仓唯一调用点 = BaseEffectCardItem 的服务端出牌路径);
        // 该标记跨周期边界保持(不能读 play_count / bonus_plays,两者都会被周期边界归零)。
        if (!player.level().isClientSide() && com.merlinkitsune.astral_dice.item.sign.BaseSignItem
                .isSignActiveLocked(player)) {
            ModAttachments.setSignActiveLockPlayed(player, true);
        }
        long now = player.level().getGameTime();
        long cooldown = ModAttachments.getEffectCardCooldownEnd(player);
        int played = ModAttachments.getEffectCardPlayCount(player);
        // 周期边界(任一成立即开新周期:先归零再登记,避免跨窗口残留计数):
        //   ① 冷却倒计时已归 0 但 tick 尚未清理;
        //   ② 不变量违例 —— 出牌数已达当轮上限却**没有**冷却在跑(只可能来自"上限在周期中途下降",
        //      见 tick 的 2026-09-14 严重 BUG 说明)。此处把它当新周期处理,保证无论调用方如何,
        //      registerPlay 自身不会留下"count >= max 且无冷却"的死状态。
        if ((cooldown > 0 && now >= cooldown)
                || (cooldown <= 0 && played > 0 && played >= getMaxAllowed(player))) {
            ModAttachments.setEffectCardCooldownEnd(player, 0);
            ModAttachments.setEffectCardPlayCount(player, 0);
            // 周期归零:一次性出牌数加成 / 可口糖果 / 探天卫星 / 活体书页累计 统一清除
            clearRoundBonuses(player);
            // 出牌轮完全重置:通知立牌主动(忍者在此刻起主动技能冷却)
            onRoundFullyReset(player);
            cooldown = 0;
        }
        int count = ModAttachments.getEffectCardPlayCount(player) + 1;
        ModAttachments.setEffectCardPlayCount(player, count);
        // 仅当本次出牌打满当前上限时才进入冷却(未打满不开始倒计时)。
        // 2026-09-15(D-1 配套):一轮冷却**已在跑**时不得被"冷却期间的补牌"重启或延长——
        // 未打满的一轮在计时器结束后已开始冷却(见 tick),此时 count 尚未达上限,玩家仍可补完
        // 剩余出牌数(上限内);若补牌打满时重开 30 秒,会把该轮冷却从"计时器结束时刻"拖到
        // "最后一次补牌时刻"(补得越晚冷却越长,持续出牌时周期永不结束)。
        // 判据用"是否仍在进行中"(cooldown > 0 && now < cooldown):仍在进行中 ⇒ 保持原到期时刻
        // 不变(什么都不写);仅当没有冷却在跑(为 0 或已到期)时才写入 now + cooldownTicks。
        // 正常打满路径(无冷却在跑)与改动前逐字等价。
        // 2026-09-30 用户裁决:冷却与「效果牌自身的效果时长」彻底分离 —— **每次出牌都启动/重置**冷却。
        // 同一轮内连续出牌会不断把到期时刻推后(最后一张牌之后 COOLDOWN_SECONDS 秒冷却结束);
        // 打满上限时同样走这一行(不再单独判「已打满」)。
        long cooldownTicks = ChargeManager.effectCardCooldownTicks(player, COOLDOWN_SECONDS * 20L);
        ModAttachments.setEffectCardCooldownEnd(player, now + cooldownTicks);
    }

    /**
     * 每 tick 调用(实际每 20 tick 一次,见 {@code PlayerTickEvents#onPlayerTick})。
     *
     * <p>三种情形:
     * <ol>
     *   <li><b>冷却已到期</b>:出牌数与全部"每轮一次"标记归零,周期结束(原有行为);</li>
     *   <li><b>不变量违例的修复(2026-09-14 严重 BUG)</b>:出牌数已达当轮上限、却<b>没有</b>冷却在跑。
     *       该状态只可能来自「上限在周期中途下降」——卸下大背包/忍术飞镖(固定 +1)、
     *       卸下可口糖果/探天卫星筹码、命运的指引效果到期等,
     *       都会让 {@link #getMaxAllowed} 实时变小,而 {@link #registerPlay} 当初是按<b>当时的</b>上限
     *       判定"未打满、不进入冷却"的,于是计数留存下来。旧实现此处 {@code if (cooldown <= 0) return;}
     *       直接返回 ⇒ 计数永远清不掉、{@link #isBurstFull} 永远为真 ⇒ <b>效果牌永久不可用</b>,
     *       界面停在「本轮出牌数已用完!剩余冷却 0 秒」,且摘掉任何筹码/立牌都无法恢复
     *       (计数是玩家附件,与物品无关)。这里按既定口径「打满上限即进入冷却」补上这一轮冷却,
     *       使其在一轮冷却后走情形 1 正常清除。</li>
     * </ol>
     */
    public static void tick(Player player) {
        long now = player.level().getGameTime();
        long cooldown = ModAttachments.getEffectCardCooldownEnd(player);
        int played = ModAttachments.getEffectCardPlayCount(player);
        if (cooldown > 0 && now < cooldown) return;          // 冷却进行中:不动
        if (cooldown <= 0) {
            if (played <= 0) return;                          // 无残留(不凭空开冷却)
            // 不变量违例:出牌数已达当轮上限、却**没有**冷却在跑(只可能来自"上限在周期中途下降",
            // 见 2026-09-14 的说明)⇒ 按「打满即进入冷却」补上这一轮,使其在一轮冷却后正常清除。
            if (played < getMaxAllowed(player)) return;
            long recoverTicks = ChargeManager.effectCardCooldownTicks(player, COOLDOWN_SECONDS * 20L);
            ModAttachments.setEffectCardCooldownEnd(player, now + recoverTicks);
            return;
        }
        ModAttachments.setEffectCardCooldownEnd(player, 0);
        ModAttachments.setEffectCardPlayCount(player, 0);
        // 周期归零:一次性出牌数加成(立牌主动) / 可口糖果(每轮一次) / 探天卫星(每 1:00 一次) /
        // 活体书页本周期累计 / 电击手套本周期武装,统一清除(唯一入口,避免各处各列一遍导致残留)
        clearRoundBonuses(player);
        // 出牌轮完全重置:通知立牌主动(忍者在此刻起主动技能冷却)
        onRoundFullyReset(player);
    }

    private static boolean hasCurio(Player player, net.minecraft.world.item.Item item) {
        var curios = top.theillusivec4.curios.api.CuriosApi.getCuriosInventory(player);
        return curios.isPresent() && curios.get().findFirstCurio(s -> s.is(item)).isPresent();
    }
}
