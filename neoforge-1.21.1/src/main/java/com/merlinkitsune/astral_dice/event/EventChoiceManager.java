package com.merlinkitsune.astral_dice.event;

import com.merlinkitsune.astral_dice.AstralDiceMod;
import com.merlinkitsune.astral_dice.event.api.AstralEvent;
import com.merlinkitsune.astral_dice.event.api.AstralEventTrigger;
import com.merlinkitsune.astral_dice.item.sign.BaseSignItem;
import com.merlinkitsune.astral_dice.network.EventChoiceClosePayload;
import com.merlinkitsune.astral_dice.network.EventChoiceStartPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 大侦探立牌主动技能：<b>事件选择会话</b>的服务端权威（用户裁决 2026-10-10）。
 *
 * <p>流程：立牌主动技能（{@code FannySignItem#handleUse}）→ {@link #start(ServerPlayer)} 从现有事件池
 * {@link AstralEventRegistry#publicPool(Player)} <b>独立抽 2 个选项</b>（允许相同、不做正/负/中性平衡）
 * → 下发 {@link EventChoiceStartPayload} 打开选择界面 → 玩家 15 秒内选一个并点确认
 * → {@link #confirm(ServerPlayer, int, int)}：**立即播主动技能音效** → 等待 1 秒 → 关界面
 * → 随后进入**既有事件触发流程**（{@link AstralEventDispatcher#trigger} →
 * {@code EventCardService#showThenRun}：弹卡 → 1.4s 动画 + 0.5s → 结算/音效）。
 *
 * <p><b>强制触发第一个选项</b>（不改走确认路径、不播音效）：15 秒超时未选 / 玩家死亡 / 玩家登出。
 *
 * <p>⚠️ 主动技能音效**只在玩家点确认时**播：{@code FannySignItem#playsSkillUseSoundOnUse()} 返回 false，
 * 立牌「点击使用」那一步不播（见 {@link BaseSignItem#playSkillUseSound}）。
 */
@EventBusSubscriber(modid = AstralDiceMod.MODID)
public final class EventChoiceManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(EventChoiceManager.class);

    /** 选项个数（固定 2 个）。 */
    public static final int OPTION_COUNT = 2;

    /** 等待期 = 15 秒（用户裁决；**不复用**库里 {@code GameplayConstants.SKILL_WAIT_SECONDS}=30，那是目标选择器的）。 */
    public static final int CHOICE_TIMEOUT_TICKS = 15 * 20;

    /** 确认后「立即播音效 → 等待 1 秒 → 关闭界面 → 再进入事件触发流程」里的那 1 秒（20 ticks）。 */
    public static final int CONFIRM_DELAY_TICKS = 20;

    private static final Map<UUID, Session> SESSIONS = new ConcurrentHashMap<>();

    private EventChoiceManager() {
    }

    /** 一次未决的选择（每个玩家最多一个）。 */
    private static final class Session {
        final int token;
        final ResourceLocation firstEvent;
        final ResourceLocation secondEvent;
        /** 超时时刻（{@code level.getGameTime()} 口径）。 */
        final long expireTick;
        /** 已定选项索引：-1 = 未定；>=0 = 已确认/已被强制（此后不再接受新的确认与超时结算）。 */
        int resolving = -1;

        Session(int token, ResourceLocation firstEvent, ResourceLocation secondEvent, long expireTick) {
            this.token = token;
            this.firstEvent = firstEvent;
            this.secondEvent = secondEvent;
            this.expireTick = expireTick;
        }

        ResourceLocation chosen() {
            return resolving == 0 ? firstEvent : secondEvent;
        }
    }

    /**
     * 立牌主动技能入口：抽 2 个事件选项并打开选择界面。
     *
     * @return true = 已开会话并下发界面；false = 事件池为空（无可用事件，本次主动技能无事可做）
     */
    public static boolean start(ServerPlayer player) {
        List<AstralEvent> pool = AstralEventRegistry.publicPool(player);
        if (pool.isEmpty()) {
            LOGGER.warn("AP_EVENT_CHOICE: pool_empty:player={}", player.getGameProfile().getName());
            return false;
        }
        // ⚠️ 两次**独立**抽取：用户裁决明确「两个选项允许相同、允许任意正/负/中性组合」⇒ 不去重、不洗牌平衡。
        AstralEvent first = pool.get(player.getRandom().nextInt(pool.size()));
        AstralEvent second = pool.get(player.getRandom().nextInt(pool.size()));

        int token = ThreadLocalRandom.current().nextInt(1, Integer.MAX_VALUE);
        Session session = new Session(token, first.id(), second.id(),
                player.level().getGameTime() + CHOICE_TIMEOUT_TICKS);
        SESSIONS.put(player.getUUID(), session);

        PacketDistributor.sendToPlayer(player, new EventChoiceStartPayload(token, first.id(), second.id(),
                first.defaultDurationTicks(), second.defaultDurationTicks()));
        LOGGER.info("AP_EVENT_CHOICE: open:{}:opt0={}:opt1={}:expire={}",
                token, first.id(), second.id(), session.expireTick);
        return true;
    }

    /** C2S 确认入口（服务端权威；token 与「尚未结算」双重校验，重复包被丢弃）。 */
    public static void confirm(ServerPlayer player, int token, int optionIndex) {
        Session session = SESSIONS.get(player.getUUID());
        if (session == null || session.token != token || session.resolving >= 0) return;
        if (optionIndex < 0 || optionIndex >= OPTION_COUNT) return;
        session.resolving = optionIndex;
        // ① 立即播主动技能音效（**只有**玩家点了确认才播；点击使用立牌那一步不播）
        BaseSignItem.playSkillUseSound(player);
        LOGGER.info("AP_EVENT_CHOICE: confirm:{}:pick={}:event={}", token, optionIndex, session.chosen());

        ServerLevel level = player.serverLevel();
        // ② 等待 1 秒 → 关闭界面 → **随后**再进入事件触发流程（用户需求的严格顺序：
        //    立即播音效 → 等 1 秒 → 关界面 → 触发）。关闭包先发（客户端先关掉界面），
        //    同一 tick 内紧接着走既有事件触发流程（弹卡 → 1.4s 动画 + 0.5s → 结算/音效）。
        EventCardService.schedule(level, CONFIRM_DELAY_TICKS, lvl -> {
            SESSIONS.remove(player.getUUID(), session);
            PacketDistributor.sendToPlayer(player, new EventChoiceClosePayload(token));
            AstralEventDispatcher.trigger(player, session.chosen(), AstralEventTrigger.ACTIVE_SKILL);
        });
    }

    /** 强制触发第一个选项（超时 / 死亡 / 登出）：不播主动技能音效，直接关界面并走事件触发流程。 */
    private static void forceFirst(Player player, Session session, String reason) {
        if (session.resolving >= 0) return;   // 玩家已确认 ⇒ 尊重其选择（会话由确认路径收尾）
        if (!SESSIONS.remove(player.getUUID(), session)) return;
        session.resolving = 0;
        if (!(player instanceof ServerPlayer serverPlayer)) return;
        PacketDistributor.sendToPlayer(serverPlayer, new EventChoiceClosePayload(session.token));
        LOGGER.info("AP_EVENT_CHOICE: force:{}:pick=0:reason={}:event={}",
                session.token, reason, session.firstEvent);
        AstralEventDispatcher.trigger(serverPlayer, session.firstEvent, AstralEventTrigger.ACTIVE_SKILL);
    }

    /** 15 秒等待期到期 ⇒ 强制第一个选项。 */
    static void tick(Player player) {
        if (player.level().isClientSide()) return;
        Session session = SESSIONS.get(player.getUUID());
        if (session == null || session.resolving >= 0) return;
        if (player.level().getGameTime() < session.expireTick) return;
        forceFirst(player, session, "timeout");
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        tick(event.getEntity());
    }

    /** 玩家死亡 ⇒ 强制第一个选项。 */
    @SubscribeEvent
    public static void onPlayerDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        Session session = SESSIONS.get(player.getUUID());
        if (session == null) return;
        forceFirst(player, session, "death");
    }

    /** 玩家登出（退出游戏/服务器）⇒ 强制第一个选项。 */
    @SubscribeEvent
    public static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        Session session = SESSIONS.get(event.getEntity().getUUID());
        if (session == null) return;
        forceFirst(event.getEntity(), session, "logout");
    }
}
