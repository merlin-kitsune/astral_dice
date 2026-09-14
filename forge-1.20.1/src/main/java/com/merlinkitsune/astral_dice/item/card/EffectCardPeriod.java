package com.merlinkitsune.astral_dice.item.card;
import com.merlinkitsune.astral_dice.item.CuriosCompat;

import com.merlinkitsune.astral_dice.component.GameplayConstants;
import com.merlinkitsune.astral_dice.component.ModAttachments;
import com.merlinkitsune.astral_dice.effect.ModEffects;
import com.merlinkitsune.astral_dice.item.ChargeManager;
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
 * - 出牌数打满上限后才开始冷却倒计时(30 秒);未打满不开始倒计时,冷却归零时出牌数归零。
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
        default MobEffect effect() {
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
    public static void registerEffectPendingSource(MobEffect effect) {
        registerEffectPendingSource(new EffectPendingSource() {
            @Override
            public boolean isActive(Player player) {
                return player.hasEffect(effect);
            }

            @Override
            public MobEffect effect() {
                return effect;
            }
        });
    }

    static {
        // 固定来源:大背包 +1、忍术飞镖 +1(不卸载持续提供)
        registerFixedSource(p -> hasCurio(p, ModItems.BIG_BACKPACK_CHIP.get()));
        registerFixedSource(p -> hasCurio(p, ModItems.NINJA_STAR_CHIP.get()));
        // 临时来源(仅当前出牌周期有效,周期归零时清除):
        // 活体书页已改为"每次使用累计 +1"的本周期计数(见 getMaxAllowed 的 LIVING_PAGE_CYCLE_BONUS),
        // 不再注册为"效果存在即 +1"的开关式临时来源(否则会与累计计数重复计算);
        // 活体书页效果本身仍作为效果待定来源注册(registerEffectPendingSource),与出牌数无关。
        registerTemporarySource(p -> p.hasEffect(ModEffects.FATE_GUIDANCE.get()));     // 命运的指引效果(存在即 +1,覆盖式,不累计)
        registerTemporarySource(p -> ModAttachments.isCandyChipPlayBonusActive(p)); // 可口糖果:满血使用效果牌触发(每轮一次)
        registerTemporarySource(p -> ModAttachments.isSatellitePlayBonusActive(p)); // 探天卫星:使用轨道炮后触发(每 1:00 一次)
        // 立牌主动技能的一次性 +1 不再注册为"来源"(它是一次性授予、不是可由谓词反复判定的状态),
        // 直接由 EffectCardPeriod 的出牌轮自有字段承载,见 getMaxAllowed 的 EFFECT_CARD_BONUS_PLAYS。

        // 效果待定来源(全部效果牌统一注册;新增效果牌在此追加或调用 registerEffectPendingSource)
        registerEffectPendingSource(ModEffects.LIVING_PAGE.get());
        registerEffectPendingSource(ModEffects.MONSTER_LASER.get());
        registerEffectPendingSource(ModEffects.MONSTER_BRICK.get());
        registerEffectPendingSource(ModEffects.ORBITAL_STRIKE.get());
        registerEffectPendingSource(ModEffects.DIRECTIONAL_BLAST.get());
        registerEffectPendingSource(ModEffects.FATE_GUIDANCE.get());
        registerEffectPendingSource(ModEffects.KING_POWER.get());
        registerEffectPendingSource(ModEffects.BERSERK.get());
        registerEffectPendingSource(ModEffects.UNWAVERING.get());
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
     * 出牌轮归零:清除全部"仅当前出牌轮有效"的出牌数加成与标记。
     * <b>唯一入口</b> —— {@link #registerPlay} 的周期边界与 {@link #tick} 的周期结束共用,
     * 禁止在别处各自列一遍(历史上分散清理曾导致状态残留与"上限中途下降"的永久锁死 BUG)。
     */
    private static void clearRoundBonuses(Player player) {
        ModAttachments.setEffectCardBonusPlays(player, 0);
        ModAttachments.setCandyChipPlayBonusActive(player, false);
        ModAttachments.setSatellitePlayBonusActive(player, false);
        ModAttachments.setLivingPageCycleBonus(player, 0);
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
            MobEffect effect = source.effect();
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

    private static int remainingEffectTicks(Player player, MobEffect effect) {
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
            cooldown = 0;
        }
        int count = ModAttachments.getEffectCardPlayCount(player) + 1;
        ModAttachments.setEffectCardPlayCount(player, count);
        // 仅当本次出牌打满当前上限时才进入冷却(未打满不开始倒计时)
        if (count >= getMaxAllowed(player)) {
            long cooldownTicks = ChargeManager.cooldownTicks(player,
                    GameplayConstants.EFFECT_CARD_COOLDOWN_SECONDS * 20L);
            ModAttachments.setEffectCardCooldownEnd(player, now + cooldownTicks);
        }
    }

    /**
     * 每 tick 调用(实际每 20 tick 一次,见 {@code PlayerTickEvents#onPlayerTick})。
     *
     * <p>两种情形:
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
            if (played <= 0) return;                          // 无残留
            if (played < getMaxAllowed(player)) return;       // 正常累积中(未打满、无冷却)
            long recoverTicks = ChargeManager.cooldownTicks(player,
                    GameplayConstants.EFFECT_CARD_COOLDOWN_SECONDS * 20L);
            ModAttachments.setEffectCardCooldownEnd(player, now + recoverTicks);
            return;
        }
        ModAttachments.setEffectCardCooldownEnd(player, 0);
        ModAttachments.setEffectCardPlayCount(player, 0);
        // 周期归零:一次性出牌数加成(立牌主动) / 可口糖果(每轮一次) / 探天卫星(每 1:00 一次) /
        // 活体书页本周期累计,统一清除(唯一入口,避免各处各列一遍导致残留)
        clearRoundBonuses(player);
        // 周期归零:解除电击手套本周期已武装的法伤扩散(下个周期可重新武装)
        com.merlinkitsune.astral_dice.item.chip.ElectricGloveChipItem.disarmAoe(player);
    }

    private static boolean hasCurio(Player player, net.minecraft.world.item.Item item) {
        var curios = com.merlinkitsune.astral_dice.item.CuriosCompat.getCuriosInventory(player);
        return curios.isPresent() && curios.get().findFirstCurio(s -> s.is(item)).isPresent();
    }
}
