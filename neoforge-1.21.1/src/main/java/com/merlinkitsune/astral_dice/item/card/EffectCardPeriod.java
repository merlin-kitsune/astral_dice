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
 * - 临时出牌数加成(仅当前出牌周期有效,周期归零时清除):活体书页每次使用累计 +1(可叠加,
 *   非"效果存在即 +1"的开关式)、命运的指引效果存在即 +1(覆盖式,不累计)、可口糖果满血触发 +1
 *   (每周期一次)、探天卫星轨道炮触发 +1(每 1:00 一次)、
 *   立牌主动技能一次性 +1({@link #grantBonusPlay},同样只作用于当前出牌轮)。
 * - 出牌数上限:min(1 + 固定 + 临时, {@link GameplayConstants#MAX_EFFECT_CARD_PLAYS})
 *   实时计算,加成来源可叠加,但单轮总出牌数固定封顶 9 张(固定常量,非配置文件项)。
 * - 出牌数打满上限后立即开始冷却倒计时(30 秒);未打满的一轮在**本轮所有计时器(效果待定/被锁时长)
 *   都结束后**同样进入一轮 30 秒冷却(2026-09-15 用户裁决,选项 2),冷却归零时出牌数归零。
 *   效果牌本身的效果单独计算;单个轮询内所有已出效果牌的效果全部结束后才可重新出牌
 *   (冷却已归零但效果仍在生效时,出牌被锁定)。
 * - 效果牌轮次(出牌周期)定义:指当前出牌周期——不论出牌数是否已达上限——只要仍有
 *   效果牌的"能力/效果"或"出牌冷却"未结束,周期即未结束。当所有效果牌进度走完
 *   **且** 出牌冷却时间走完后,这一周期才算结束;周期边界用于"每轮一次"类计数清理
 *   与新一轮开牌锁判定。
 * - 冷却期间允许继续出牌累积出牌数(上限内),冷却不会被后续出牌重置。
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
            return effect.unwrapKey().map(key -> key.location().getPath()).orElse("source_" + index);
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
        // 活体书页效果本身仍作为效果待定来源注册(registerEffectPendingSource),与出牌数无关。
        registerTemporarySource(p -> p.hasEffect(ModEffects.FATE_GUIDANCE));     // 命运的指引效果(存在即 +1,覆盖式,不累计)
        registerTemporarySource(p -> ModAttachments.isCandyChipPlayBonusActive(p)); // 可口糖果:满血使用效果牌触发(每轮一次)
        registerTemporarySource(p -> ModAttachments.isSatellitePlayBonusActive(p)); // 探天卫星:使用轨道炮后触发(每 1:00 一次)
        // 立牌主动技能的一次性 +1 不再注册为"来源"(它是一次性授予、不是可由谓词反复判定的状态),
        // 直接由 EffectCardPeriod 的出牌轮自有字段承载,见 getMaxAllowed 的 EFFECT_CARD_BONUS_PLAYS。

        // 效果待定来源(全部效果牌统一注册;新增效果牌在此追加或调用 registerEffectPendingSource)
        registerEffectPendingSource(ModEffects.LIVING_PAGE);
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
        // 活体书页:每次使用在本周期内累计 +1(仅当前周期,周期归零时清除;可叠加,非"效果存在即 +1"的开关式)
        extra += ModAttachments.getLivingPageCycleBonus(player);
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
        long now = player.level().getGameTime();
        long maxEnd = ModAttachments.getEffectCardCooldownEnd(player);
        for (EffectPendingSource source : EFFECT_PENDING_SOURCES) {
            Holder<MobEffect> effect = source.effect();
            if (effect != null) {
                maxEnd = Math.max(maxEnd, now + remainingEffectTicks(player, effect));
            }
        }
        return Math.max(0, maxEnd - now);
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
     * 出牌锁判定:
     * 1. 本轮出牌数已达上限 → 阻止;
     * 2. 冷却进行中(仅在打满上限后才开始) → 允许继续出牌累积(上限内);
     * 3. 冷却已归零(或未开始)但效果牌效果仍在生效 → 阻止开始新一轮(效果结束后才可重新出牌)。
     */
    public static boolean isBlocked(Player player) {
        if (isBurstFull(player)) return true;
        if (isCooldownActive(player)) return false;
        return isEffectPending(player);
    }

    /**
     * 出牌登记:出牌数 +1(调用前需通过 {@link #isBlocked} 校验)。
     *
     * <p><b>冷却严格按照「出牌数打满后才进入冷却」</b>:未打满时**不启动**冷却倒计时,
     * 只在本次出牌使出牌数达到上限({@link #getMaxAllowed})时才开始 30 秒冷却;
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
        if (count >= getMaxAllowed(player)) {
            long cooldownTicks = ChargeManager.cooldownTicks(player,
                    GameplayConstants.EFFECT_CARD_COOLDOWN_SECONDS * 20L);
            if (cooldown <= now) {
                ModAttachments.setEffectCardCooldownEnd(player, now + cooldownTicks);
            }
        }
    }

    /**
     * 每 tick 调用(实际每 20 tick 一次,见 {@code PlayerTickEvents#onPlayerTick})。
     *
     * <p>三种情形:
     * <ol>
     *   <li><b>冷却已到期</b>:出牌数与全部"每轮一次"标记归零,周期结束(原有行为);</li>
     *   <li><b>未打满的一轮在计时器全部结束后收尾(2026-09-15 用户裁决)</b>:出牌数未达上限、
     *       但本轮的"剩余被锁时长"({@link #getRemainingBlockTicks},由 {@code EFFECT_PENDING_SOURCES}
     *       的效果自动推导,不硬编码效果列表)已归 0 时,同样启动一轮 30 秒冷却(时长与"打满上限"复用
     *       同一 {@code ChargeManager.cooldownTicks} 口径)。判据:{@code played > 0 &&
     *       played < getMaxAllowed(player)} 且 {@code getRemainingBlockTicks(player) <= 0};
     *       仍有计时器在跑时继续等待(即正常累积中)。
     *       <b>最终语义(2026-09-15 用户裁决)</b>:未打满的一轮在所有计时器结束后也会进入一轮冷却,
     *       但<b>不</b>作废剩余出牌数;冷却期间仍可继续出牌,冷却到期后计数归零。
     *       为此本分支<b>不</b>把出牌数补齐到当轮上限——{@link #isBurstFull} 因而保持为假,
     *       玩家不会在冷却期间被"已打满"提前拦住({@link #isBlocked} 对"冷却进行中"本身并不拦截),
     *       剩余出牌数得以在冷却期间继续使用,冷却不会被后续出牌重置。</li>
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
            int maxAllowed = getMaxAllowed(player);
            if (played < maxAllowed) {
                // 未打满:仍有计时器在跑(剩余被锁时长 > 0)时继续等待,即正常累积中;
                // 本轮所有计时器结束后按 2026-09-15 用户裁决同样进入一轮冷却(下方统一启动),
                // 但**不**作废剩余出牌数(不再把计数补齐到当轮上限):冷却期间仍可继续出牌,
                // 冷却到期后计数归零。
                if (getRemainingBlockTicks(player) > 0) return;
            }
            long recoverTicks = ChargeManager.cooldownTicks(player,
                    GameplayConstants.EFFECT_CARD_COOLDOWN_SECONDS * 20L);
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
