package com.merlinkitsune.astral_dice.command;

import com.merlinkitsune.astral_dice.AstralDiceMod;
import com.merlinkitsune.astral_dice.component.ModAttachments;
import com.merlinkitsune.astral_dice.effect.ModEffects;
import com.merlinkitsune.starenginelib.event.ModEffectRemoval;
import com.merlinkitsune.astral_dice.item.card.EffectCardPeriod;
import com.merlinkitsune.astral_dice.item.sign.BaseSignItem;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.RegistryObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * {@code /astralparty} 调试命令(仅 OP,权限级 2)。
 *
 * <p>四个子命令:三个写入类按「出牌锁的三条判据」分工——出牌锁判定见
 * {@link EffectCardPeriod#isBlocked(net.minecraft.world.entity.player.Player)}:
 * <ol>
 *   <li>{@code isBurstFull} —— 出牌数达当轮上限;</li>
 *   <li>{@code isCooldownActive} —— 出牌冷却进行中;</li>
 *   <li>{@code isEffectPending} —— 效果牌留下的效果仍在生效(「效果待定」)。</li>
 * </ol>
 * <ul>
 *   <li>{@code /astralparty resetcardlock [目标]} —— 只解 <b>①②</b>:调
 *       {@link EffectCardPeriod#forceResetRound} 把出牌数(含全部「每轮一次」标记)与出牌冷却归零。
 *       <b>不</b>解 ③、<b>不</b>碰立牌锁定态({@code sign_active_lock_*})与待命等待器({@code sign_ready_*})。</li>
 *   <li>{@code /astralparty clearcardeffect [目标]} —— 只解 <b>③</b>:移除
 *       {@link EffectCardPeriod#effectPendingEffects()}(效果牌施加且会锁住出牌的那批效果实例)。</li>
 *   <li>{@code /astralparty cleareffect [目标]} —— <b>全部</b>本模组效果({@link ModEffects#ALL},33 个),
 *       是上面两者的并集口径再加上立牌主动效果等其余效果;用于排障兜底,不是解锁出牌的最小手段。</li>
 *   <li>{@code /astralparty dump [目标]} —— <b>只读</b>:把本模组自身的出牌锁原始值 / 派生判定、
 *       立牌三态原始值、玩家实际携带的本模组效果、效果待定来源明细倾倒成
 *       {@code APDUMP|<组>|<键>=<值>} 行(见 {@link #DUMP_PREFIX});**不改变任何状态**。</li>
 * </ul>
 *
 * <p><b>dump 的输出约定(核心是「格式稳定」,不是给人看)</b>:① 每行固定前缀 {@code APDUMP|},
 * 组分 {@code HEAD}/{@code LOCKRAW}/{@code LOCKDERIVED}/{@code SIGN}/{@code EFFECTS}/{@code PENDING};
 * ② 同时走两条通道 —— {@code source.sendSuccess(...)}(玩家 chat)与 {@link #LOGGER}.info(保证进
 * {@code latest.log},不依赖 chat 渲染);③ 行里**不含任何颜色码**(§ 会破坏正则),机器行正文也
 * **不**走 lang key(它是机器格式,不是给人读的文案);④ 数值一律十进制、tick 用 long、布尔用
 * {@code true/false};⑤ 多玩家按选择器顺序逐段输出,段内行序固定(不依赖任何 {@code HashMap}
 * 迭代序);⑥ {@code LOCKDERIVED} 组与 {@code SIGN} 组的 {@code is_sign_active_locked} 是
 * **判定入口字段**,行尾带 {@code |assert=forbidden} 标记 —— 它们只作人工参考,**禁止作为断言落点**,
 * 断言一律锚定 {@code LOCKRAW}/{@code SIGN} 的原始值。
 *
 * <p>别名:{@code resetcardcolddown}(用户原话拼写)与 {@code resetcardcooldown}(拼写正确版)都是
 * {@code resetcardlock} 的别名,与主字面量**共用同一实现**,不存在第二份逻辑。
 *
 * <p>权限:全部子命令 {@code requires(src -> src.hasPermission(2))}。目标参数可选
 * ({@link EntityArgument#players()},支持 {@code @a}/玩家名),省略时作用于执行者自己;
 * 执行者不是玩家且未给参数、或参数解析不出任何玩家时,返回可读的失败文案而**不抛异常**。
 *
 * <p>反馈文案一律走 lang key({@code command.astral_dice.astralparty.*},经
 * {@link Component#translatable}),不在代码里内联任何可显示文本。
 *
 * <p><b>定位与边界(管理员功能)</b>:本命令是本模组的**正式管理员功能**,随 jar 发布、不设任何
 * dev/测试专用开关;入口唯一——根字面量与每个子命令都 {@code requires(hasPermission(2))},没有任何
 * 降低门槛的旁路(配置开关、非 OP 别名、客户端旁路)。命令只在**服务端**注册与执行
 * ({@code RegisterCommandsEvent} 的服务端命令派发器 + {@link CommandSourceStack} 的
 * {@link ServerPlayer} 路径),不存在仅客户端生效的分支。
 *
 * <p><b>能力边界</b>:只做「重置 / 清除 / 只读转储本模组自身状态」这一件事——不给予任何资源(物品/卡牌/
 * 筹码/星币/星光/治愈/最大生命等),不提供任何「把任意数值设成任意值」的通用写入(没有 seteffect /
 * setcooldown / setplaycount 之类的入口),默认只作用于执行者、给了选择器才作用于该玩家(绝不隐式
 * 作用于全体),也不触碰原版或其它模组的玩家状态(如光谱箭施加的发光、经验、背包)。{@code dump} 是
 * **纯只读**子命令:全部取值只经 getter 与 {@code is*} 判定(外加 {@code player.level().getGameTime()}
 * 这一时间基准,用于解释本模组自己的绝对 tick 值),不写任何附件/组件/效果,也不越界输出原版或其它
 * 模组的状态(效果清单只遍历 {@link ModEffects#ALL},非本模组效果一律不输出)。本类不被任何
 * 游戏内正常玩法路径调用,也不为玩法逻辑开调试分支。
 */
@Mod.EventBusSubscriber(modid = AstralDiceMod.MODID)
public final class AstralPartyCommand {
    /** 仅 OP:权限级 2 */
    private static final int REQUIRED_PERMISSION_LEVEL = 2;
    /** 可选的目标参数名 */
    private static final String ARG_TARGETS = "targets";

    private static final String KEY_CLEAR_EFFECT_SELF = "command.astral_dice.astralparty.cleareffect.success";
    private static final String KEY_CLEAR_EFFECT_OTHER = "command.astral_dice.astralparty.cleareffect.success.other";
    private static final String KEY_CLEAR_CARD_EFFECT_SELF = "command.astral_dice.astralparty.clearcardeffect.success";
    private static final String KEY_CLEAR_CARD_EFFECT_OTHER = "command.astral_dice.astralparty.clearcardeffect.success.other";
    private static final String KEY_RESET_LOCK_SELF = "command.astral_dice.astralparty.resetcardlock.success";
    private static final String KEY_RESET_LOCK_OTHER = "command.astral_dice.astralparty.resetcardlock.success.other";
    private static final String KEY_NO_PLAYER = "command.astral_dice.astralparty.error.no_player";
    /** dump 的**人类可读摘要**(唯一走 lang key 的 dump 反馈;APDUMP 机器行正文刻意不走 lang) */
    private static final String KEY_DUMP_SUMMARY = "command.astral_dice.astralparty.dump.summary";

    /** 根字面量 */
    private static final String ROOT_LITERAL = "astralparty";
    /** {@code resetcardlock} 的主字面量 */
    private static final String RESET_LOCK_LITERAL = "resetcardlock";
    /** {@code resetcardlock} 的别名(用户原话拼写 + 拼写正确版),与主字面量同一实现 */
    private static final String[] RESET_LOCK_ALIASES = { "resetcardcolddown", "resetcardcooldown" };

    // === dump(只读转储)的机器格式约定 =====================================================
    // 机器格式的核心价值是「稳定」:固定前缀 + 固定分隔符 + 固定键序,供 PowerShell 侧对
    // run/<版本>/logs/latest.log 做正则断言。任何形状改动都等于破坏既有断言,不要随手改。
    /** 每行固定前缀;分隔符为 {@code |} */
    private static final String DUMP_PREFIX = "APDUMP";
    /** 分隔符:前缀 / 组名 / 键值之间 */
    private static final String DUMP_SEP = "|";
    /** 组:每个目标玩家的段头(形如 {@code APDUMP|HEAD|<玩家名>=<UUID>},不带键名) */
    private static final String DUMP_GROUP_HEAD = "HEAD";
    /** 组:出牌锁**原始值**(断言应锚定这里) */
    private static final String DUMP_GROUP_LOCK_RAW = "LOCKRAW";
    /** 组:出牌锁**派生判定**(仅供人工参考) */
    private static final String DUMP_GROUP_LOCK_DERIVED = "LOCKDERIVED";
    /** 组:立牌三态原始值 + 派生判定 */
    private static final String DUMP_GROUP_SIGN = "SIGN";
    /** 组:玩家实际携带的本模组效果 */
    private static final String DUMP_GROUP_EFFECTS = "EFFECTS";
    /** 组:效果待定来源明细 */
    private static final String DUMP_GROUP_PENDING = "PENDING";
    /** 追加在「判定入口字段」行尾的标记:该字段只作人工参考,禁止作为断言落点 */
    private static final String DUMP_ASSERT_FORBIDDEN = DUMP_SEP + "assert=forbidden";

    /** dump 的日志通道:保证机器行一定进 {@code latest.log},不依赖 chat 渲染 */
    private static final Logger LOGGER = LoggerFactory.getLogger(AstralPartyCommand.class);

    private AstralPartyCommand() {
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal(ROOT_LITERAL)
                .requires(AstralPartyCommand::hasPermission)
                .then(Commands.literal("cleareffect")
                        .requires(AstralPartyCommand::hasPermission)
                        .executes(ctx -> onSelf(ctx, AstralPartyCommand::clearAllEffects))
                        .then(Commands.argument(ARG_TARGETS, EntityArgument.players())
                                .executes(ctx -> onTargets(ctx, AstralPartyCommand::clearAllEffects))))
                .then(Commands.literal("clearcardeffect")
                        .requires(AstralPartyCommand::hasPermission)
                        .executes(ctx -> onSelf(ctx, AstralPartyCommand::clearCardEffects))
                        .then(Commands.argument(ARG_TARGETS, EntityArgument.players())
                                .executes(ctx -> onTargets(ctx, AstralPartyCommand::clearCardEffects))))
                .then(Commands.literal("dump")
                        .requires(AstralPartyCommand::hasPermission)
                        .executes(ctx -> onSelf(ctx, AstralPartyCommand::dumpState))
                        .then(Commands.argument(ARG_TARGETS, EntityArgument.players())
                                .executes(ctx -> onTargets(ctx, AstralPartyCommand::dumpState))))
                .then(resetCardLockNode(RESET_LOCK_LITERAL));
        // 别名:同一构建器工厂 + 同一执行方法,命令只注册一次主名 + 别名
        for (String alias : RESET_LOCK_ALIASES) {
            root.then(resetCardLockNode(alias));
        }
        event.getDispatcher().register(root);
    }

    private static LiteralArgumentBuilder<CommandSourceStack> resetCardLockNode(String literal) {
        return Commands.literal(literal)
                .requires(AstralPartyCommand::hasPermission)
                .executes(ctx -> onSelf(ctx, AstralPartyCommand::resetCardLock))
                .then(Commands.argument(ARG_TARGETS, EntityArgument.players())
                        .executes(ctx -> onTargets(ctx, AstralPartyCommand::resetCardLock)));
    }

    private static boolean hasPermission(CommandSourceStack source) {
        return source.hasPermission(REQUIRED_PERMISSION_LEVEL);
    }

    /** 作用于一批玩家目标的子命令实现 */
    @FunctionalInterface
    private interface PlayerTargetAction {
        int run(CommandSourceStack source, List<ServerPlayer> targets);
    }

    /** 无玩家参数:作用于执行者自己;执行者不是玩家时给出可读错误(不抛异常) */
    private static int onSelf(CommandContext<CommandSourceStack> ctx, PlayerTargetAction action) {
        ServerPlayer self = ctx.getSource().getPlayer();
        if (self == null) {
            ctx.getSource().sendFailure(Component.translatable(KEY_NO_PLAYER));
            return 0;
        }
        return action.run(ctx.getSource(), List.of(self));
    }

    /**
     * 带玩家参数:作用于解析出的全部目标。
     *
     * <p>选择器匹配不到玩家时 {@link EntityArgument#getPlayers} 自身会抛
     * {@link CommandSyntaxException},由 Brigadier 转成可读的命令错误(不是崩溃);
     * 这里再兜一层空集合,保证任何情况下都不会以异常收场。
     */
    private static int onTargets(CommandContext<CommandSourceStack> ctx, PlayerTargetAction action)
            throws CommandSyntaxException {
        Collection<ServerPlayer> resolved = EntityArgument.getPlayers(ctx, ARG_TARGETS);
        if (resolved == null || resolved.isEmpty()) {
            ctx.getSource().sendFailure(Component.translatable(KEY_NO_PLAYER));
            return 0;
        }
        return action.run(ctx.getSource(), List.copyOf(resolved));
    }

    /**
     * {@code cleareffect}:清除目标身上的**全部**本模组效果({@link ModEffects#ALL}),并清理
     * 与所清效果「同生共死」的耦合状态。
     */
    private static int clearAllEffects(CommandSourceStack source, List<ServerPlayer> targets) {
        int cleared = 0;
        for (ServerPlayer target : targets) {
            for (RegistryObject<MobEffect> entry : ModEffects.ALL) {
                MobEffect effect = entry.get();
                if (removeModEffect(target, effect)) {
                    clearCompanionState(target, effect);
                    cleared++;
                }
            }
        }
        sendClearResult(source, targets, cleared, KEY_CLEAR_EFFECT_SELF, KEY_CLEAR_EFFECT_OTHER);
        return Command.SINGLE_SUCCESS;
    }

    /**
     * {@code clearcardeffect}:只清除**效果牌施加的效果**——权威清单取
     * {@link EffectCardPeriod#effectPendingEffects()}(= 出牌锁第 ③ 条的全部效果来源)。
     *
     * <p>不清立牌主动效果({@code misaki_burst}/{@code papara_bite}/{@code nancy_lu_hack}/
     * {@code weak_mark} 等),也不清效果牌顺带施加的原版 rider(迅捷/中毒/生命恢复/抗性提升)——
     * 它们不参与出牌锁,且来源众多(药水/信标等),清掉会波及与效果牌无关的增益。
     */
    private static int clearCardEffects(CommandSourceStack source, List<ServerPlayer> targets) {
        List<MobEffect> cardEffects = EffectCardPeriod.effectPendingEffects();
        int cleared = 0;
        for (ServerPlayer target : targets) {
            for (MobEffect effect : cardEffects) {
                if (removeModEffect(target, effect)) {
                    clearCompanionState(target, effect);
                    cleared++;
                }
            }
        }
        sendClearResult(source, targets, cleared, KEY_CLEAR_CARD_EFFECT_SELF, KEY_CLEAR_CARD_EFFECT_OTHER);
        return Command.SINGLE_SUCCESS;
    }

    /**
     * {@code resetcardlock}:重置出牌锁的 ①②——出牌数(含全部「每轮一次」标记)与出牌冷却。
     *
     * <p><b>明确不做 ③</b>:不清 {@code EFFECT_PENDING_SOURCES} 对应的效果(那是
     * {@link #clearCardEffects} 的职责)、不清立牌锁定态 {@code sign_active_lock_*}、不清待命等待器
     * {@code sign_ready_*}、也不补 {@code EffectCardPeriod#onRoundFullyReset} 回调。
     *
     * <p>幂等安全性:{@link EffectCardPeriod#forceResetRound} 只做 4 个轮次附件的**无条件**赋值
     * (出牌冷却 0 / 出牌数 0 / 一次性出牌数加成与「每轮一次」标记归零)+ 解除电击手套武装
     * ({@code ElectricGloveChipItem#disarmAoe} 同样是无条件写 false),不读任何旧值、不依赖前置状态,
     * 因此对「未处于任何轮次状态」的玩家重复调用没有任何副作用。
     */
    private static int resetCardLock(CommandSourceStack source, List<ServerPlayer> targets) {
        for (ServerPlayer target : targets) {
            EffectCardPeriod.forceResetRound(target);
        }
        if (isSoleSelfTarget(source, targets)) {
            source.sendSuccess(() -> Component.translatable(KEY_RESET_LOCK_SELF), false);
        } else {
            source.sendSuccess(() -> Component.translatable(KEY_RESET_LOCK_OTHER, targets.size()), false);
        }
        return Command.SINGLE_SUCCESS;
    }

    /**
     * {@code dump}:**只读**转储本模组自身的状态,输出 {@code APDUMP|<组>|<键>=<值>} 行。
     *
     * <p>设计取向(决定了实现取舍,勿随手改):测试流程的断言 100% 是对
     * {@code run/<版本>/logs/latest.log} 的正则匹配,PowerShell 侧没有读游戏内状态的能力,
     * 因此本子命令的**核心价值是输出格式的稳定性**,不是"好看的输出"。所以:
     * <ul>
     *   <li>原始值优先于派生值 —— 断言锚定 {@code LOCKRAW}/{@code SIGN}/{@code EFFECTS}/{@code PENDING},
     *       {@code LOCKDERIVED} 组与 {@code is_sign_active_locked} 只是**判定入口**,行尾带
     *       {@code |assert=forbidden} 标记(它们的布尔值由多条原始值实时推导,直接断言会把派生口径
     *       也一起冻结进用例);</li>
     *   <li>行内**不含颜色码**(§ 会破坏正则),机器行正文也不走 lang key;</li>
     *   <li>键序与顺序固定(逐组写死;效果组按注册 id 序数排序;来源组按注册顺序),
     *       不依赖任何 {@code HashMap} 迭代序。</li>
     * </ul>
     *
     * <p><b>只读</b>:全部取值只经 getter 与 {@code is*} 判定,包括 {@code isBurstFull} 等判定入口;
     * 不写任何附件 / 物品组件 / 效果。唯一的"非本模组字段"是 {@code game_time}
     * ({@code player.level().getGameTime()}) —— 它是解释本模组那些**绝对 tick 值**
     * ({@code effect_card_cooldown_end}、{@code sign_active_lock_end} 等)所必需的时间基准,
     * 不涉及任何玩家/世界状态(不输出血量、经验、背包、原版或其它模组的效果)。
     */
    private static int dumpState(CommandSourceStack source, List<ServerPlayer> targets) {
        for (ServerPlayer target : targets) {
            for (String line : buildDumpLines(target)) {
                // 双通道:①玩家 chat(可被聊天栏渲染,但不加任何颜色码);②服务端日志
                // (保证进 latest.log,不依赖 chat 渲染——这是自动化断言真正读的那一条)
                source.sendSuccess(() -> Component.literal(line), false);
                LOGGER.info("{}", line);
            }
        }
        // 人类可读摘要(唯一带 lang key 的 dump 反馈;刻意**不**用 APDUMP| 前缀,免得污染机器行正则)
        source.sendSuccess(() -> Component.translatable(KEY_DUMP_SUMMARY, targets.size()), false);
        return Command.SINGLE_SUCCESS;
    }

    /** 单个玩家的完整转储段(段头 + 五个组),行序固定 */
    private static List<String> buildDumpLines(ServerPlayer player) {
        List<String> lines = new ArrayList<>();
        lines.add(DUMP_PREFIX + DUMP_SEP + DUMP_GROUP_HEAD + DUMP_SEP
                + player.getGameProfile().getName() + "=" + player.getUUID());
        appendLockRaw(lines, player);
        appendLockDerived(lines, player);
        appendSign(lines, player);
        appendEffects(lines, player);
        appendPending(lines, player);
        return lines;
    }

    /** {@code APDUMP|<组>|<键>=<值>} */
    private static String row(String group, String key, Object value) {
        return DUMP_PREFIX + DUMP_SEP + group + DUMP_SEP + key + "=" + value;
    }

    /** 判定入口行:与 {@link #row} 同形,行尾追加「禁止作为断言落点」标记 */
    private static String derivedRow(String group, String key, Object value) {
        return row(group, key, value) + DUMP_ASSERT_FORBIDDEN;
    }

    /** A 组:出牌锁原始值(断言应锚定这里) */
    private static void appendLockRaw(List<String> lines, ServerPlayer player) {
        lines.add(row(DUMP_GROUP_LOCK_RAW, "game_time", player.level().getGameTime()));
        lines.add(row(DUMP_GROUP_LOCK_RAW, "effect_card_cooldown_end",
                ModAttachments.getEffectCardCooldownEnd(player)));
        lines.add(row(DUMP_GROUP_LOCK_RAW, "effect_card_play_count",
                ModAttachments.getEffectCardPlayCount(player)));
        lines.add(row(DUMP_GROUP_LOCK_RAW, "effect_card_bonus_plays",
                EffectCardPeriod.getBonusPlays(player)));
        lines.add(row(DUMP_GROUP_LOCK_RAW, "living_page_cycle_bonus",
                ModAttachments.getLivingPageCycleBonus(player)));
        lines.add(row(DUMP_GROUP_LOCK_RAW, "max_allowed", EffectCardPeriod.getMaxAllowed(player)));
        lines.add(row(DUMP_GROUP_LOCK_RAW, "remaining_block_ticks",
                EffectCardPeriod.getRemainingBlockTicks(player)));
    }

    /** B 组:出牌锁派生判定(仅供人工参考,**禁止作为断言落点**) */
    private static void appendLockDerived(List<String> lines, ServerPlayer player) {
        lines.add(derivedRow(DUMP_GROUP_LOCK_DERIVED, "is_burst_full",
                EffectCardPeriod.isBurstFull(player)));
        lines.add(derivedRow(DUMP_GROUP_LOCK_DERIVED, "is_cooldown_active",
                EffectCardPeriod.isCooldownActive(player)));
        lines.add(derivedRow(DUMP_GROUP_LOCK_DERIVED, "is_effect_pending",
                EffectCardPeriod.isEffectPending(player)));
        lines.add(derivedRow(DUMP_GROUP_LOCK_DERIVED, "is_blocked",
                EffectCardPeriod.isBlocked(player)));
    }

    /** C 组:立牌三态原始值 + 派生判定 */
    private static void appendSign(List<String> lines, ServerPlayer player) {
        // sign_active_lock_sign 为空串时输出 `sign_active_lock_sign=`(原始值优先,不替换成占位符)
        lines.add(row(DUMP_GROUP_SIGN, "sign_active_lock_sign",
                ModAttachments.getSignActiveLockSign(player)));
        lines.add(row(DUMP_GROUP_SIGN, "sign_active_lock_end",
                ModAttachments.getSignActiveLockEnd(player)));
        lines.add(row(DUMP_GROUP_SIGN, "sign_active_reduction_pool",
                ModAttachments.getSignActiveReductionPool(player)));
        lines.add(row(DUMP_GROUP_SIGN, "sign_active_lock_grace_end",
                ModAttachments.getSignActiveLockGraceEnd(player)));
        lines.add(row(DUMP_GROUP_SIGN, "sign_active_lock_played",
                ModAttachments.getSignActiveLockPlayed(player)));
        // 锁定态的"离线补偿基准"(最后一次见到该玩家的 gameTime;0 = 无锁定/宽限计时)。只读:
        // 用于实测取证该附件跨重登持久化(dump → saveall → 重登 → 再 dump,值应等于重登前那一拍)。
        lines.add(row(DUMP_GROUP_SIGN, "sign_active_lock_last_seen",
                ModAttachments.getSignActiveLockLastSeen(player)));
        lines.add(row(DUMP_GROUP_SIGN, "sign_active_cooldown_end",
                ModAttachments.getSignActiveCooldownEnd(player)));
        lines.add(row(DUMP_GROUP_SIGN, "sign_active_max_cooldown",
                ModAttachments.getSignActiveMaxCooldown(player)));
        lines.add(row(DUMP_GROUP_SIGN, "sign_ready_type", ModAttachments.getSignReadyType(player)));
        lines.add(row(DUMP_GROUP_SIGN, "sign_ready_expire", ModAttachments.getSignReadyExpire(player)));
        lines.add(derivedRow(DUMP_GROUP_SIGN, "is_sign_active_locked",
                BaseSignItem.isSignActiveLocked(player)));
    }

    /**
     * D 组:玩家**实际携带**的本模组效果(效果注册 id / amplifier / 剩余 duration)。
     *
     * <p>只遍历 {@link ModEffects#ALL}(本模组自己的注册集合),**绝不**遍历玩家的全部效果——
     * 因此原版效果(迅捷/发光等)与其它模组的效果一行都不会出现。排序固定为「注册 id 序数升序」,
     * 不依赖注册集合的迭代序。{@code amplifier} 为原版原始值(0 基),不做 +1 换算。
     */
    private static void appendEffects(List<String> lines, ServerPlayer player) {
        List<RegistryObject<MobEffect>> effects = new ArrayList<>(ModEffects.ALL);
        effects.sort(Comparator.comparing(entry -> effectId(entry)));
        for (RegistryObject<MobEffect> entry : effects) {
            MobEffectInstance instance = player.getEffect(entry.get());
            if (instance == null) continue;   // 只输出「实际携带」的
            lines.add(DUMP_PREFIX + DUMP_SEP + DUMP_GROUP_EFFECTS + DUMP_SEP
                    + "effect=" + effectId(entry)
                    + DUMP_SEP + "amplifier=" + instance.getAmplifier()
                    + DUMP_SEP + "duration=" + instance.getDuration());
        }
    }

    /**
     * E 组:效果待定来源明细(逐条输出,顺序 = 注册顺序,与
     * {@link EffectCardPeriod#effectPendingSourceIds()} 一一对应、同序)。
     *
     * <p>字段:{@code source}=稳定来源标识、{@code effect}=其 {@code effect()} 的注册 id
     * ({@code none} = 无对应效果的纯逻辑来源)、{@code is_active}=该来源自己的 {@code isActive}
     * 判定结果、{@code remaining}=该效果实例的剩余 tick。
     *
     * <p><b>关于「isActive 依赖非效果状态」的来源</b>:当前 9 条来源全部由
     * {@code registerEffectPendingSource(MobEffect)} 注册,判定就是 {@code player.hasEffect(effect)},
     * 不存在依赖非效果状态的来源,故没有额外的原始值需要输出。若将来新增这类来源,必须在此处把
     * 它所依赖的原始值一并输出(否则 E 组无法解释 {@code is_active} 的成因)。
     */
    private static void appendPending(List<String> lines, ServerPlayer player) {
        List<EffectCardPeriod.EffectPendingSource> sources = EffectCardPeriod.effectPendingSources();
        List<String> ids = EffectCardPeriod.effectPendingSourceIds();
        for (int i = 0; i < sources.size(); i++) {
            EffectCardPeriod.EffectPendingSource source = sources.get(i);
            MobEffect effect = source.effect();
            MobEffectInstance instance = effect == null ? null : player.getEffect(effect);
            String sourceId = i < ids.size() ? ids.get(i) : "source_" + i;
            lines.add(DUMP_PREFIX + DUMP_SEP + DUMP_GROUP_PENDING + DUMP_SEP
                    + "source=" + sourceId
                    + DUMP_SEP + "effect=" + (effect == null ? "none" : effectId(effect))
                    + DUMP_SEP + "is_active=" + source.isActive(player)
                    + DUMP_SEP + "remaining=" + (instance == null ? 0 : instance.getDuration()));
        }
    }

    /** 效果注册 id(全限定,如 {@code astral_dice:berserk});取不到时退化为固定占位符 */
    private static String effectId(RegistryObject<MobEffect> entry) {
        ResourceLocation id = entry.getId();
        return id == null ? "astral_dice:unknown" : id.toString();
    }

    /** 效果注册 id(全限定);效果未绑定到注册表时退化为固定占位符(纯逻辑来源走 {@code none} 分支) */
    private static String effectId(MobEffect effect) {
        ResourceLocation id = BuiltInRegistries.MOB_EFFECT.getKey(effect);
        return id == null ? "astral_dice:unknown" : id.toString();
    }

    /**
     * 移除目标身上的单个本模组效果;返回是否真的移除了(用于计数)。
     *
     * <p><b>必须</b>经 {@link ModEffectRemoval} 的内部通道:Forge 侧
     * {@code event/ModEffectEvents.java:107-127} 的 {@code onModEffectRemovalPrevented} 会拦截并取消
     * **全部 {@code astral_dice:} 命名空间效果**的外部移除(牛奶 / {@code /effect clear}),
     * 直接调 {@code removeEffect} 会让本命令完全无效。走内部通道另有两个附带收益(无需在此重写):
     * <ul>
     *   <li>{@code ModEffectEvents.onEffectTimerForget} 遗忘 {@code effect_timer_ends} 记录——
     *       否则 {@code EffectTimerGuard.tick} 的 {@code inst == null} 分支会把效果原样施加回来;</li>
     *   <li>{@code InvestigationEventUtil.onUndercoverRemoved} 清掉 {@code undercover_source}。</li>
     * </ul>
     */
    private static boolean removeModEffect(ServerPlayer player, MobEffect effect) {
        if (!player.hasEffect(effect)) return false;
        ModEffectRemoval.remove(player, effect);
        return true;
    }

    /**
     * 清理与「刚被移除的那个效果」**同生共死**的耦合状态。
     *
     * <p>判据(逐条裁决与文件:行号证据见 {@code docs/batch3/CMD-progress.md} §5):只清**该效果自身**的
     * 功能半身 / 来源归属,且模组自己在效果结束路径上也会清它。凡属**玩家资源与进度计数器**
     * ({@code healing_points}/{@code healing_timer_end}、{@code investigation_stage}、
     * {@code cursed_sword_*}、{@code empower_decay_at})一律不动;立牌锁定态
     * {@code sign_active_lock_*} 由玩家级 tick 自动迁移为冷却,也无需在此处理。
     */
    private static void clearCompanionState(ServerPlayer player, MobEffect effect) {
        if (effect == ModEffects.FATE_GUIDANCE.get()) {
            // 「命运的指引」的效果实例只是显示,功能由附件截止时刻驱动
            // (FateGuidanceCardItem.java:53 写入、:86-89 isFateGuidanceActive 判定);
            // 只清效果会让「七咒减伤减半」等隐藏生效到原到期时刻。
            ModAttachments.setFateActiveUntil(player, 0L);
            return;
        }
        if (effect == ModEffects.WEAK_MARK.get()) {
            // 虚弱印记的来源归属:施加(DiceCombatEvents.java:248)与到期清理
            // (HaiqingSignItem.java:143-150)都与效果同生共死;唯一读取点
            // (HaiqingSignItem.java:131)被 hasEffect(WEAK_MARK) 守卫,故清理行为中性。
            ModAttachments.setWeakMarkSource(player, Optional.empty());
            return;
        }
        // 三个「待命」提示效果(haiqing_ready / bonnie_ready / moses_ready)与旧「待命等待器」
        // 已随目标选择器并入而整体移除(2026-09-17 主线 → dev-next 合并裁决):
        // 这三个效果不再注册、sign_ready_type / sign_ready_expire 也不再有写入方,
        // 故此处不再需要「清效果时同步清等待窗口」的伴生清理。
        // **刻意不动** MARKED 的伴生原版发光(minecraft:glowing):本命令的能力边界是
        // 「只重置/清除本模组自身状态」,不得去动原版或其它模组的状态(光谱箭等也会施加发光)。
        // 后果(已知取舍,勿在此处补代码):清掉 marked 后发光按它自己的计时自然结束,
        // 期间可能出现「发光但无标记」(MarkManager.java:63 注释描述的形态)。
        //
        // undercover_source 无需在此处理:走内部通道时事件未被取消,
        // InvestigationEventUtil.onUndercoverRemoved(InvestigationEventUtil.java:120-128)会自动清。
    }

    private static void sendClearResult(CommandSourceStack source, List<ServerPlayer> targets, int cleared,
                                        String selfKey, String otherKey) {
        if (isSoleSelfTarget(source, targets)) {
            source.sendSuccess(() -> Component.translatable(selfKey, cleared), false);
        } else {
            // 参数顺序(两语言一致):先「清除数量」,后「受影响玩家数」
            source.sendSuccess(() -> Component.translatable(otherKey, cleared, targets.size()), false);
        }
    }

    private static boolean isSoleSelfTarget(CommandSourceStack source, List<ServerPlayer> targets) {
        return targets.size() == 1 && targets.get(0) == source.getPlayer();
    }
}
