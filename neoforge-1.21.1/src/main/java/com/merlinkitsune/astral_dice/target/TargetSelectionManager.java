package com.merlinkitsune.astral_dice.target;

import com.merlinkitsune.astral_dice.AstralDiceMod;
import com.merlinkitsune.astral_dice.component.GameplayConstants;
import com.merlinkitsune.astral_dice.network.ActionBarPayload;
import com.merlinkitsune.astral_dice.network.TargetSelectCancelPayload;
import com.merlinkitsune.astral_dice.network.TargetSelectConfirmPayload;
import com.merlinkitsune.astral_dice.network.TargetSelectStartPayload;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 目标选择器服务端管理器（权威）。
 *
 * 每名玩家至多一个选择会话（token 随机、可被新会话替换）。流程：
 * 1. 触发方调用 {@link #start}（自身前置校验由调用方完成）→ 创建会话并下发
 *    {@link TargetSelectStartPayload} 给客户端进入选择模式；
 * 2. 客户端确认 → {@link TargetSelectConfirmPayload} → {@link #confirm}：
 *    token/时效/目标类型/距离全部通过后调用 {@link TargetSelectionAction#apply} 施加效果；
 *    距离/类型失败保留会话允许重新瞄准，token 失效/目标消失则清除会话；
 * 3. 客户端取消 → {@link TargetSelectCancelPayload} → {@link #cancel} 立即清除（便于重触发）；
 * 4. 会话过期（{@link PlayerTickEvent.Post}）、玩家登出/死亡自动清除。
 *
 * 距离上限一律取配置 {@link GameplayConstants#TARGET_SELECT_RADIUS}（默认 16，配置范围 1..32），
 * 客户端射线半径仅用于 UX，服务端确认时按配置值二次校验。
 */
@EventBusSubscriber(modid = AstralDiceMod.MODID)
public final class TargetSelectionManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(TargetSelectionManager.class);

    /** 选择会话（纯内存，瞬态） */
    public static final class Session {
        public final int token;
        public final String actionId;
        public final TargetType targetType;
        public final double radius;
        public final long expireTick;

        Session(int token, String actionId, TargetType targetType, double radius, long expireTick) {
            this.token = token;
            this.actionId = actionId;
            this.targetType = targetType;
            this.radius = radius;
            this.expireTick = expireTick;
        }
    }

    private static final Map<UUID, Session> SESSIONS = new ConcurrentHashMap<>();

    private TargetSelectionManager() {
    }

    /** 玩家当前是否处于目标选择会话中（供立牌触发守卫 / 测试断言使用） */
    public static boolean isSelecting(Player player) {
        return player != null && SESSIONS.containsKey(player.getUUID());
    }

    /** 测试辅助:直接清除玩家选择会话（仅 SignSkillTests 等测试使用） */
    public static void cancelSessionForTests(Player player) {
        if (player != null) {
            SESSIONS.remove(player.getUUID());
        }
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
        double radius = Math.max(1.0, Math.min(action.radius(), GameplayConstants.TARGET_SELECT_RADIUS));
        long expireTick = player.level().getGameTime() + (long) GameplayConstants.SKILL_WAIT_SECONDS * 20L;
        Session session = new Session(token, actionId, action.targetType(), radius, expireTick);
        SESSIONS.put(player.getUUID(), session);
        action.onStarted(player);

        LOGGER.debug("[Astral Dice][TargetSelection] start player={} action={} token={} type={} radius={} expire={}",
                player.getName().getString(), actionId, token, action.targetType(), radius, expireTick);
        PacketDistributor.sendToPlayer(player, new TargetSelectStartPayload(
                token, action.targetType().ordinal(), radius,
                (int) Math.max(1, expireTick - player.level().getGameTime()), actionId));
        return true;
    }

    /** 客户端确认目标（由 {@link TargetSelectConfirmPayload} 调用） */
    public static void confirm(ServerPlayer player, int token, int targetId) {
        Session session = SESSIONS.get(player.getUUID());
        if (session == null || session.token != token) {
            LOGGER.warn("[Astral Dice][TargetSelection] confirm FAIL: token_mismatch player={} token={}",
                    player.getName().getString(), token);
            notifyActionBar(player, "msg.astral_dice.target_select.action_missing", ChatFormatting.RED);
            return;
        }
        if (player.level().getGameTime() >= session.expireTick) {
            SESSIONS.remove(player.getUUID());
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
        if (!session.targetType.matches(player, target)) {
            LOGGER.warn("[Astral Dice][TargetSelection] confirm FAIL: target_type_mismatch player={} token={} target={}({})",
                    player.getName().getString(), token, targetId, target.getName().getString());
            notifyActionBar(player, "msg.astral_dice.target_select.invalid_target", ChatFormatting.RED);
            return;
        }
        double distSq = player.distanceToSqr(target);
        double maxDist = GameplayConstants.TARGET_SELECT_RADIUS;
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
            LOGGER.warn("[Astral Dice][TargetSelection] confirm FAIL: action_missing player={} token={} action={}",
                    player.getName().getString(), token, session.actionId);
            notifyActionBar(player, "msg.astral_dice.target_select.no_valid_action", ChatFormatting.RED);
            return;
        }

        LOGGER.debug("[Astral Dice][TargetSelection] confirm token={} target={}({}) dist={} action={} -> SUCCESS",
                token, targetId, target.getName().getString(), Math.sqrt(distSq), session.actionId);
        action.apply(player, target);
        notifyActionBar(player, "msg.astral_dice.target_select.applied", ChatFormatting.YELLOW, target.getDisplayName());
    }

    /** 客户端取消（由 {@link TargetSelectCancelPayload} 调用；token 不匹配时忽略） */
    public static void cancel(ServerPlayer player, int token) {
        Session session = SESSIONS.get(player.getUUID());
        if (session == null) return;
        if (session.token != token) {
            LOGGER.warn("[Astral Dice][TargetSelection] cancel ignored: token_mismatch player={} token={}",
                    player.getName().getString(), token);
            return;
        }
        SESSIONS.remove(player.getUUID());
        LOGGER.debug("[Astral Dice][TargetSelection] cancel token={} player={}", token, player.getName().getString());
    }

    /** 会话过期清理（由 {@link PlayerTickEvent.Post} 驱动） */
    static void tick(Player player) {
        if (player.level().isClientSide()) return;
        Session session = SESSIONS.get(player.getUUID());
        if (session == null) return;
        if (player.level().getGameTime() >= session.expireTick) {
            SESSIONS.remove(player.getUUID());
            LOGGER.debug("[Astral Dice][TargetSelection] expired token={} player={}", session.token, player.getName().getString());
        }
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        tick(event.getEntity());
    }

    @SubscribeEvent
    public static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        Player player = event.getEntity();
        if (player == null || player.level().isClientSide()) return;
        Session session = SESSIONS.remove(player.getUUID());
        if (session != null) {
            LOGGER.debug("[Astral Dice][TargetSelection] cleared player={} token={} reason=logout",
                    player.getName().getString(), session.token);
        }
    }

    @SubscribeEvent
    public static void onPlayerDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof Player player) || player.level().isClientSide()) return;
        Session session = SESSIONS.remove(player.getUUID());
        if (session != null) {
            LOGGER.debug("[Astral Dice][TargetSelection] cleared player={} token={} reason=death",
                    player.getName().getString(), session.token);
        }
    }

    // 服务端 ActionBar 反馈统一入口（黄色；错误红色）
    private static void notifyActionBar(Player player, String langKey, ChatFormatting color, Object... args) {
        if (!(player instanceof ServerPlayer serverPlayer)) return;
        Component msg = Component.translatable(langKey, args).withStyle(color);
        PacketDistributor.sendToPlayer(serverPlayer,
                new ActionBarPayload(msg, GameplayConstants.ACTIONBAR_DURATION_TICKS));
    }
}
