package com.merlinkitsune.astral_dice.target;

import com.merlinkitsune.astral_dice.AstralDiceMod;
import com.merlinkitsune.starenginelib.component.GameplayConstants;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.TickEvent;
import com.merlinkitsune.astral_dice.network.ModNetwork;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

import com.merlinkitsune.starenginelib.target.TargetSelectionAction;
import com.merlinkitsune.starenginelib.target.TargetSelectionRegistry;
import com.merlinkitsune.starenginelib.target.TargetType;
import com.merlinkitsune.starenginelib.target.SelectorTargets;
import com.merlinkitsune.starenginelib.target.SignSelectionGate;
/**
 * 目标选择器服务端管理器（权威）。
 *
 * 每名玩家至多一个选择会话（token 随机、可被新会话替换）。流程：
 * 1. 触发方调用 {@link #start}（自身前置校验由调用方完成）→ 创建会话并下发
 *    {@code ModNetwork.TargetSelectStartMessage} 给客户端进入选择模式；
 * 2. 客户端确认 → {@code ModNetwork.TargetSelectConfirmMessage} → {@link #confirm}：
 *    token/时效/目标类型/距离全部通过后**先发通用「已确认」提示**
 *    （{@code msg.astral_dice.target_select.applied}），再调用 {@link TargetSelectionAction#apply}
 *    施加效果 —— 客户端 actionbar 只有一槽位、同 tick 内后发者覆盖先发者，故动作自带的专属提示
 *    才是玩家实际看到的那条（无专属提示的动作仍显示通用提示；顺序说明见 {@link #confirm} 内注释）；
 *    距离/类型失败保留会话允许重新瞄准，token 失效/目标消失则清除会话；
 * 3. 客户端取消 → {@code ModNetwork.TargetSelectCancelMessage} → {@link #cancel} 立即清除（便于重触发）；
 * 4. 会话收尾：**手持即选择**的动作（{@link HoldToSelect}，四张效果牌）每 tick 校验物品是否仍在主手、
 *    移出即取消且**无倒计时**；其余动作（立牌主动）按选择窗口超时取消（{@link TickEvent.PlayerTickEvent}）。
 *    玩家登出 / 死亡一律自动清除。
 *
 * 距离上限:动作可通过 {@link TargetSelectionAction#radius()} 声明自己的锁定范围,未声明者取配置
 * {@link GameplayConstants#TARGET_SELECT_RADIUS}（默认 16，配置范围 1..32）;两者一律按前置库契约的
 * **32 格上限**夹取（配置上限不可突破），且**含垂直高度差**（用 {@link ServerPlayer#distanceToSqr} 的三维距离）。
 * 客户端射线半径仅用于 UX，服务端确认时按**本会话实际授予的半径**二次校验。
 */
@Mod.EventBusSubscriber(modid = AstralDiceMod.MODID)
public final class TargetSelectionManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(TargetSelectionManager.class);

    /**
     * 选择半径的**契约上限**（格）。前置库 {@code TargetSelectionAction#radius()} 的契约写明
     * 「配置上限 32 不可突破」，故显式声明更大范围的动作用本值夹取；配置值仍是**缺省**范围。
     */
    private static final double MAX_SELECT_RADIUS = 32.0D;

    /** 选择会话（纯内存，瞬态） */
    public static final class Session {
        public final int token;
        public final String actionId;
        public final TargetType targetType;
        public final double radius;
        public final long expireTick;
        /**
         * 本次会话是否允许对自身使用（{@link SelfTargetable#allowSelf()} 的取值，启动时快照）。
         *
         * <p>随 {@code ModNetwork.TargetSelectStartMessage} 一起下发，客户端据此决定 actionbar
         * 口径与右键行为；服务端在 {@link #confirm} 里把它交给
         * {@link SelectorTargets#matches(TargetType, Player, LivingEntity, boolean)} —— 该重载只在
         * 「会话允许自身 + 目标就是选择者」时放行（前置库 {@link TargetType#matches} 始终排除自身，
         * 故放行必须发生在消费方；当前实现者为游戏大师立牌 ren 的「熊孩子特权」（`ren_privilege`）与三张可自用的效果牌动作（`express_delivery` / `luxury_feast` / `berserk`，2026-09-25 起））。
         */
        public final boolean allowSelf;

        /**
         * 本会话是否由「主手手持物品」驱动（{@link HoldToSelect}，2026-09-25 用户裁决「手持即选择」）。
         *
         * <p>为真时**没有倒计时**：{@code expireTick} 固定 0 且不参与任何判定，改为每 tick 校验物品是否
         * 仍在主手（见 {@link #tick}）；客户端据此不显示「（剩余 N 秒）」并使用「移出手持退出」的提示口径。
         * 当前为真的动作 = 四张效果牌 {@code express_delivery} / {@code luxury_feast} /
         * {@code you_have_i_have} / {@code berserk}。
         */
        public final boolean holdToSelect;

        Session(int token, String actionId, TargetType targetType, double radius, long expireTick,
                boolean allowSelf, boolean holdToSelect) {
            this.token = token;
            this.actionId = actionId;
            this.targetType = targetType;
            this.radius = radius;
            this.expireTick = expireTick;
            this.allowSelf = allowSelf;
            this.holdToSelect = holdToSelect;
        }
    }

    private static final Map<UUID, Session> SESSIONS = new ConcurrentHashMap<>();

    /**
     * 「手持即选择」的抑制闩：玩家 → 被显式取消过的动作 id。
     *
     * <p>手持语义下会话会自动重开，若不管，按 J / 下蹲+右键取消只会在下一 tick 原样弹回来（形同虚设）。
     * 故显式取消后记一条闩：**同一动作在牌被移出主手之前不再自动开局**；玩家把牌移出主手
     * （`tick` 里检测到主手不再持有该动作对应的牌）即解除，再握回来就能重新开局。
     */
    private static final Map<UUID, String> HOLD_SUPPRESSED = new ConcurrentHashMap<>();

    private TargetSelectionManager() {
    }

    /** 玩家当前是否处于目标选择会话中（供立牌触发守卫 / 测试断言使用） */
    public static boolean isSelecting(Player player) {
        return player != null && SESSIONS.containsKey(player.getUUID());
    }

    /**
     * 该「手持即选择」动作此刻是否被抑制（玩家显式取消过、且还没把牌移出主手）。
     *
     * <p>由 {@code BaseEffectCardItem#tickHeldSelector} 在自动开局前查询；解除时机见 {@link #tick}。
     */
    public static boolean isHoldSuppressed(Player player, String actionId) {
        return player != null && actionId != null
                && actionId.equals(HOLD_SUPPRESSED.get(player.getUUID()));
    }

    /** 清除玩家的会话与抑制闩（登出 / 死亡 / 测试脚手架共用） */
    private static void clearSessionState(Player player) {
        SESSIONS.remove(player.getUUID());
        HOLD_SUPPRESSED.remove(player.getUUID());
        SignSelectionGate.clear(player);
    }

    /** 测试辅助:直接清除玩家选择会话（仅 SignSkillTests 等测试使用） */
    public static void cancelSessionForTests(Player player) {
        if (player != null) {
            clearSessionState(player);
        }
    }

    /**
     * 测试辅助:当前会话 token（无会话返回 {@code -1}）。
     *
     * <p>供自动化测试/探针在服务端直接驱动确认路径 —— {@link #confirm} 必须携带与会话一致的 token，
     * 而 token 只存在于 {@code SESSIONS} 内部；本方法只把它读出来，**不做任何状态变更**。
     */
    public static int sessionTokenForTests(Player player) {
        Session session = (player == null) ? null : SESSIONS.get(player.getUUID());
        return session == null ? -1 : session.token;
    }

    /**
     * 触发目标选择模式（服务端）。
     *
     * @return 是否成功进入选择模式（动作未注册/目标不可用返回 false）
     */
    public static boolean start(ServerPlayer player, String actionId) {
        TargetSelectionAction action = TargetSelectionRegistry.get(actionId);
        if (action == null) {
            notifyActionBar(player, "msg.astral_dice.target_select.no_valid_action", ChatFormatting.RED);
            return false;
        }
        int token = ThreadLocalRandom.current().nextInt(1, Integer.MAX_VALUE);
        // 该会话的锁定范围 = 动作声明的范围(缺省 = 配置值),按契约上限 32 格夹取。
        // ⚠️ 这里**不能**再用配置值当硬上限:配置是「缺省范围」,而活体书页显式声明 32 格
        // (2026-09-19 用户要求),若仍按配置默认 16 夹取,声明的 32 会被静默截半。
        double radius = Math.max(1.0, Math.min(action.radius(), MAX_SELECT_RADIUS));
        // 对自身使用的唯一来源（消费方侧接口，不改前置库）：实现 SelfTargetable 的动作才为 true
        // （当前 allowSelf=true 的动作 = ren_privilege、三张可自用效果牌 express_delivery / luxury_feast / berserk
        //  与 lulu_healing_slime(2026-09-19 追加)；其余动作缺省 false）。
        boolean allowSelf = action instanceof SelfTargetable selfTargetable && selfTargetable.allowSelf();
        // 「手持即选择」（2026-09-25 用户裁决）：实现 HoldToSelect 的动作**没有倒计时** —— expireTick 写 0，
        // 结算一律走 Session.holdToSelect 分支（每 tick 校验物品是否仍在主手）。
        boolean holdToSelect = action instanceof HoldToSelect;
        long expireTick = holdToSelect ? 0L
                : player.level().getGameTime() + (long) GameplayConstants.SKILL_WAIT_SECONDS * 20L;
        Session session = new Session(token, actionId, action.targetType(), radius, expireTick, allowSelf, holdToSelect);
        SESSIONS.put(player.getUUID(), session);
        // 会话被替换:旧会话可能留下的「立牌门控待执行记录」必须一并清除(防跨会话误触发);
        // 新记录由调用方(立牌 performSkill / 效果牌 tickHeldSelector)在 start 成功后 arm。
        SignSelectionGate.clear(player);
        action.onStarted(player);

        LOGGER.debug("[Astral Dice][TargetSelection] start player={} action={} token={} type={} radius={} expire={} allowSelf={}",
                player.getName().getString(), actionId, token, action.targetType(), radius, expireTick, allowSelf);
        ModNetwork.sendToPlayer(player, new ModNetwork.TargetSelectStartMessage(
                token, action.targetType().ordinal(), radius,
                holdToSelect ? 0 : (int) Math.max(1, expireTick - player.level().getGameTime()), actionId, allowSelf,
                holdToSelect));
        return true;
    }

    /** 客户端确认目标（由 {@code ModNetwork.TargetSelectConfirmMessage} 调用） */
    public static void confirm(ServerPlayer player, int token, int targetId) {
        Session session = SESSIONS.get(player.getUUID());
        if (session == null || session.token != token) {
            LOGGER.warn("[Astral Dice][TargetSelection] confirm FAIL: token_mismatch player={} token={}",
                    player.getName().getString(), token);
            notifyActionBar(player, "msg.astral_dice.target_select.action_missing", ChatFormatting.RED);
            return;
        }
        if (!session.holdToSelect && player.level().getGameTime() >= session.expireTick) {
            SESSIONS.remove(player.getUUID());
            // 确认时已过期 ⇒ 该次主动等同未使用:门控记录一并清除
            SignSelectionGate.clear(player);
            LOGGER.warn("[Astral Dice][TargetSelection] confirm FAIL: expired player={} token={}",
                    player.getName().getString(), token);
            notifyActionBar(player, "msg.astral_dice.target_select.action_missing", ChatFormatting.RED);
            return;
        }
        if (!(player.level().getEntity(targetId) instanceof LivingEntity target) || !target.isAlive()) {
            LOGGER.warn("[Astral Dice][TargetSelection] confirm FAIL: target_invalid player={} token={} targetId={}",
                    player.getName().getString(), token, targetId);
            notifyActionBar(player, "msg.astral_dice.target_select.invalid_target", ChatFormatting.RED);
            return;
        }
        if (!SelectorTargets.matches(session.targetType, player, target, session.allowSelf)) {
            LOGGER.warn("[Astral Dice][TargetSelection] confirm FAIL: target_type_mismatch player={} token={} target={}({})",
                    player.getName().getString(), token, targetId, target.getName().getString());
            notifyActionBar(player, "msg.astral_dice.target_select.invalid_target", ChatFormatting.RED);
            return;
        }
        double distSq = player.distanceToSqr(target);
        // 服务端确认校验按**本会话实际授予的半径**（= 动作声明值夹取后的结果）执行；
        // 取 max(配置, 会话半径) ⇒ 对既有动作**绝不比改动前更严**（旧写法恒用配置值），
        // 同时让「活体书页 32 格」在确认这一步不被配置默认值 16 拦下（2026-09-19）。
        double maxDist = Math.max(GameplayConstants.TARGET_SELECT_RADIUS, session.radius);
        if (distSq > maxDist * maxDist) {
            LOGGER.warn("[Astral Dice][TargetSelection] confirm FAIL: target_too_far player={} token={} target={}({}) dist={} max={}",
                    player.getName().getString(), token, targetId, target.getName().getString(),
                    Math.sqrt(distSq), maxDist);
            notifyActionBar(player, "msg.astral_dice.target_select.target_too_far", ChatFormatting.RED);
            return; // 保留会话：玩家可重新瞄准
        }

        SESSIONS.remove(player.getUUID());
        TargetSelectionAction action = TargetSelectionRegistry.get(session.actionId);
        if (action == null) {
            // 动作不可用 ⇒ 该次主动无法恢复:丢弃门控记录(等同未使用)
            SignSelectionGate.clear(player);
            LOGGER.warn("[Astral Dice][TargetSelection] confirm FAIL: action_missing player={} token={} action={}",
                    player.getName().getString(), token, session.actionId);
            notifyActionBar(player, "msg.astral_dice.target_select.no_valid_action", ChatFormatting.RED);
            return;
        }

        LOGGER.debug("[Astral Dice][TargetSelection] confirm token={} target={}({}) dist={} action={} -> SUCCESS",
                token, targetId, target.getName().getString(), Math.sqrt(distSq), session.actionId);
        // 通用「已确认」提示**必须**在施效之前发出(2026-09-25 F8 修复):客户端 actionbar 只有一格
        // (starenginelib 的 ActionBarManager 为单槽位,show() 直接覆盖),同一 tick 内**后发者覆盖先发者**;
        // 而各动作的专属提示(bonnie_undercover_applied / haiqing_weak_mark_applied / ren_privilege_applied 等)
        // 都在 apply 里发出 ⇒ 只有让通用提示先发,玩家才看得到专属提示;无专属提示的动作照旧显示通用提示。
        notifyActionBar(player, "msg.astral_dice.target_select.applied", ChatFormatting.YELLOW, target.getDisplayName());
        action.apply(player, target);
        // 立牌主动技能前置门控(2026-09-17):由立牌登记的会话在**确认成功**后才恢复原流程剩余步骤
        // (风扇筹码发牌 + 立牌主动响应事件/默认提示);非立牌会话无记录 ⇒ 空操作。
        com.merlinkitsune.astral_dice.item.sign.BaseSignItem.resumeGatedActiveSkill(player, session.actionId);
    }

    /** 客户端取消（由 {@code ModNetwork.TargetSelectCancelMessage} 调用；token 不匹配时忽略） */
    public static void cancel(ServerPlayer player, int token) {
        Session session = SESSIONS.get(player.getUUID());
        if (session == null) return;
        if (session.token != token) {
            LOGGER.warn("[Astral Dice][TargetSelection] cancel ignored: token_mismatch player={} token={}",
                    player.getName().getString(), token);
            return;
        }
        SESSIONS.remove(player.getUUID());
        // 取消 ⇒ 该次主动等同未使用:门控记录一并清除
        SignSelectionGate.clear(player);
        // 手持即选择类会话被**显式取消**(J / 下蹲+右键)后加抑制闩:牌还在手里,下一 tick 会立刻自动重开,
        // 那样「取消」就形同虚设 ⇒ 先记下「本次持握不再自动开局」,等玩家把牌移出主手再解除(见 tick)。
        if (session.holdToSelect) {
            HOLD_SUPPRESSED.put(player.getUUID(), session.actionId);
            LOGGER.debug("[Astral Dice][TargetSelection] hold suppressed player={} action={} (release the card to re-arm)",
                    player.getName().getString(), session.actionId);
        }
        LOGGER.debug("[Astral Dice][TargetSelection] cancel token={} player={}", token, player.getName().getString());
    }

    /**
     * 会话维持/清理（由 {@link TickEvent.PlayerTickEvent} 的 END 相位驱动，每 tick 一次）。
     *
     * <p>两条互斥的收官口径：
     * <ul>
     *   <li><b>手持即选择</b>（{@code session.holdToSelect}，四张效果牌）—— **没有倒计时**，每 tick 只校验
     *       {@link HoldToSelect#stillHeld}：物品离开主手即取消会话并丢弃门控记录（日志 reason=released）；</li>
     *   <li>其余（立牌主动技能）—— 仍是选择窗口超时（{@code expireTick}）到期即取消。</li>
     * </ul>
     */
    static void tick(Player player) {
        if (player.level().isClientSide()) return;
        // 抑制闩:只有当主手**不再**持有那条被取消的动作对应的牌时才解除(移出手持 = 玩家明确放手)
        String suppressed = HOLD_SUPPRESSED.get(player.getUUID());
        if (suppressed != null) {
            TargetSelectionAction suppressedAction = TargetSelectionRegistry.get(suppressed);
            if (!(suppressedAction instanceof HoldToSelect hold) || !hold.stillHeld(player)) {
                HOLD_SUPPRESSED.remove(player.getUUID());
                LOGGER.debug("[Astral Dice][TargetSelection] hold suppression cleared player={} action={}",
                        player.getName().getString(), suppressed);
            }
        }
        Session session = SESSIONS.get(player.getUUID());
        if (session == null) return;
        if (session.holdToSelect) {
            TargetSelectionAction action = TargetSelectionRegistry.get(session.actionId);
            if (action instanceof HoldToSelect hold && !hold.stillHeld(player)) {
                // 移出手持 ⇒ 该次出牌等同未使用:门控记录一并清除,卡牌不消耗
                SESSIONS.remove(player.getUUID());
                SignSelectionGate.clear(player);
                LOGGER.debug("[Astral Dice][TargetSelection] cleared player={} token={} reason=released",
                        player.getName().getString(), session.token);
            }
            return;
        }
        if (player.level().getGameTime() >= session.expireTick) {
            SESSIONS.remove(player.getUUID());
            // 超时(选择窗口内未确认)⇒ 该次主动等同未使用:门控记录一并清除
            SignSelectionGate.clear(player);
            LOGGER.debug("[Astral Dice][TargetSelection] expired token={} player={}", session.token, player.getName().getString());
        }
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        tick(event.player);
    }

    @SubscribeEvent
    public static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        Player player = event.getEntity();
        if (player == null || player.level().isClientSide()) return;
        Session session = SESSIONS.get(player.getUUID());
        clearSessionState(player);
        if (session != null) {
            LOGGER.debug("[Astral Dice][TargetSelection] cleared player={} token={} reason=logout",
                    player.getName().getString(), session.token);
        }
    }

    @SubscribeEvent
    public static void onPlayerDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof Player player) || player.level().isClientSide()) return;
        Session session = SESSIONS.get(player.getUUID());
        clearSessionState(player);
        if (session != null) {
            LOGGER.debug("[Astral Dice][TargetSelection] cleared player={} token={} reason=death",
                    player.getName().getString(), session.token);
        }
    }

    // 服务端 ActionBar 反馈统一入口（黄色；错误红色）
    private static void notifyActionBar(Player player, String langKey, ChatFormatting color, Object... args) {
        if (!(player instanceof ServerPlayer serverPlayer)) return;
        Component msg = Component.translatable(langKey, args).withStyle(color);
        ModNetwork.sendToPlayer(serverPlayer,
                new ModNetwork.ActionBarMessage(msg, GameplayConstants.ACTIONBAR_DURATION_TICKS));
    }
}
