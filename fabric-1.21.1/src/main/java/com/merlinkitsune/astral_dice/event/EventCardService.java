package com.merlinkitsune.astral_dice.event;

import com.merlinkitsune.astral_dice.audio.ModSounds;
import com.merlinkitsune.astral_dice.audio.SoundPlayback;
import com.merlinkitsune.astral_dice.event.api.AstralEvent;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * 事件效果卡的服务端编排（用户裁决 2026-10-10）。
 *
 * <p>顺序：**先给受影响玩家下发弹卡包（并播放 `event_fade_in`）**，卡动画 {@link #CARD_ANIM_TICKS}t
 * （1.4s）+ {@link #SETTLE_BUFFER_TICKS}t（0.5s）缓冲 = {@link #DELAY_TICKS}t（1.9s）后，才执行事件本身
 * （效果 + 计时器）**并同刻播放该事件音效**。
 *
 * <p><b>不变式（用户 2026-10-11 裁决）</b>：一切事件效果只在 {@link #DELAY_TICKS} 这一个唯一结算点
 * 一次性施加；弹卡前 / 动画中（{@link #CARD_ANIM_TICKS}t 内）**零副作用**（无伤害、无状态、无事件音效）。
 * 下面三个常量是这条不变式的**唯一来源** —— 客户端动画时长与结算刻都从它们推导，禁止在任何一侧写死数字。
 *
 * <p><b>⚠️ fabric-1.20.1 平台差异</b>：
 * <ul>
 *   <li><b>发包</b>：forge 用 {@code ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> sp), msg)}；
 *       本线 {@code SimpleChannel} 只有按玩家发送的便捷入口 ⇒ 改成
 *       {@code ModNetwork.sendToPlayer(sp, msg)}（{@code network/ModNetwork.java:101}）。协议顺序不变 ——
 *       {@code EventCardMessage} 必须登记在本线 id 段里（见本批报告「待接线：网络注册行」）。</li>
 *   <li><b>目标选择器强制取消</b>：forge 有 {@code TargetSelectionManager.cancel(ServerPlayer, String reason)}
 *       这个「服务端强制取消、不校验 token、不写抑制闩、发关闭包」的重载；本线自 2026-11-01 起**已补齐**
 *       同一重载（缺陷 C-2），调用形式与 forge 逐字一致，只差协议 id（本线 30 / forge 28）。
 *       旧实现曾借「测试辅助清场入口」（= {@code clearSessionState}）顶替 —— 语义上满足
 *       D5「不写抑制闩」，但**不发 TargetSelectClose 关闭包** ⇒ 客户端可能仍停在选择模式
 *       （吞鼠标 / 左键被当确认）。该缺陷已消除。</li>
 * </ul>
 */
public final class EventCardService {
    private static final Logger LOGGER = LoggerFactory.getLogger(EventCardService.class);

    /** 弹卡 → 结算的延迟（tick）：38t = 1.9s = {@link #CARD_ANIM_TICKS} + {@link #SETTLE_BUFFER_TICKS}。 */
    public static final int DELAY_TICKS = 38;
    /** 卡片动画时长（tick）：28t = 1.4s = 进场 0.2s + 停留 1.0s + 收起 0.2s（客户端 EventCardOverlay 按此推导）。 */
    public static final int CARD_ANIM_TICKS = 28;
    /** 动画播完 → 结算之间的**静默缓冲**（tick）：10t = 0.5s。这段时间屏幕上已无卡、效果也还没施加。 */
    public static final int SETTLE_BUFFER_TICKS = DELAY_TICKS - CARD_ANIM_TICKS;
    /**
     * 「奇怪的问号」使用后的冷却（tick）= 动画窗口 {@link #DELAY_TICKS} + 10 秒（用户裁决 2026-10-29 D3）。
     *
     * <p>为什么不是另写一个拍脑袋的数：窗口本身就是「卡在屏幕上、事件尚未结算」的那段；冷却从窗口结束起再
     * 等 10 秒，玩家才可能在**结算之后**再抽一次。改动动画时长时冷却自动跟随，不会出现「卡还没结算就能再抽」。
     */
    public static final int QUESTION_COOLDOWN_TICKS = DELAY_TICKS + 20 * 10;

    private static final List<Pending> QUEUE = new ArrayList<>();

    private EventCardService() {
    }

    private record Pending(ServerLevel level, long executeAt, Consumer<ServerLevel> action) {
    }

    /** 通用延迟调度（本 tick 起 delayTicks 后执行；用于「先治疗后伤害」这类分步结算）。 */
    public static void schedule(ServerLevel level, int delayTicks, Consumer<ServerLevel> action) {
        QUEUE.add(new Pending(level, level.getGameTime() + Math.max(1, delayTicks), action));
    }

    /** 弹卡 + 登记 {@link #DELAY_TICKS}t 后的结算（唯一结算点）。 */
    public static void showThenRun(ServerLevel level, List<Player> targets, AstralEvent event,
                                   int tier, Consumer<ServerLevel> action) {
        for (var p : targets) {
            if (p instanceof ServerPlayer sp) {
                // 平台差异：forge = ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> sp), msg)
                com.merlinkitsune.astral_dice.network.ModNetwork.sendToPlayer(sp,
                        new com.merlinkitsune.astral_dice.network.EventCardMessage(event.id(), event.defaultDurationTicks(), tier));
                SoundPlayback.playTo(sp, ModSounds.EVENT_FADE_IN.get());
                // 2026-10-29 用户裁决 D4/D5：弹卡这一刻起锁 —— 窗口期（= 本方法的 DELAY_TICKS）内任一入口
                // 都不得再打开目标选择器 / 事件选择界面，也不得再抽事件卡；并强制取消**此刻已打开**的会话。
                // 为什么必须在弹卡这一 tick 做：玩家确认选择 / 右键出牌与弹卡往往在同一 tick 内先后发生，
                // 晚一个 tick 就可能「旧界面还开着、新事件已经在演」，确认动作会在动画尚未结算时抢先触发另一事件。
                EventAnimationLock.begin(sp, DELAY_TICKS);
                EventChoiceManager.cancelFor(sp, "animation");
                // 目标选择器侧：用服务端强制取消重载（D5，与 neo 线同口径）—— 清会话但**不写**抑制闩，
                // 动画窗结束后玩家仍手持效果牌时自然自动重开；若走旧 token 版 cancel 会写 HOLD_SUPPRESSED，
                // 反而把「动画结束即可选」变成「必须先把牌移出主手」。
                // 已补齐（2026-11-01，缺陷 C-2）：本线现有 cancel(ServerPlayer, String) 重载，与 neo/forge 同口径 ——
                //   清会话 + 清门控 + **发 TargetSelectCloseMessage**（客户端 token 匹配才退出选择模式）；
                //   不写 HOLD_SUPPRESSED 抑制闩 ⇒ 动画窗结束后玩家仍手持效果牌时会自然自动重开选择界面。
                //   旧实现借测试辅助清场入口顶替，缺关闭包 ⇒ 客户端可能仍停在选择模式、吞鼠标/左键当确认。
                //   调用形式与 forge-1.20.1 EventCardService.java:78 逐字一致。
                com.merlinkitsune.astral_dice.target.TargetSelectionManager.cancel(sp, "animation");
            }
        }
        QUEUE.add(new Pending(level, level.getGameTime() + DELAY_TICKS, action));
        LOGGER.info("AP_EVENT_CARD: shown:{}:exec_at={}", event.id(), level.getGameTime() + DELAY_TICKS);
    }

    /** 到点执行（由 {@link EventEffects#onPlayerTick} **每 tick** 调一次，见该类注释「消除结算时机漂移」）。 */
    public static void drain(ServerLevel level) {
        if (QUEUE.isEmpty()) return;
        long now = level.getGameTime();
        // ⚠️ 必须**快照迭代 + 先移除后执行**：结算动作内部可能再次 schedule（如「取长补短」先治疗后伤害），
        //    直接在 removeIf 里执行会 ConcurrentModificationException（实测：治疗已生效、1s 后的伤害永远不执行）。
        for (Pending pending : new ArrayList<>(QUEUE)) {
            if (pending.executeAt() > now) continue;
            QUEUE.remove(pending);
            try {
                pending.action().accept(pending.level());
            } catch (Exception ex) {
                LOGGER.warn("AP_EVENT_CARD: exec_failed:{}", ex.toString());
            }
        }
    }

    /** 单个玩家播放一个音效。 */
    public static void playTo(Player target, SoundEvent sound) {
        if (target instanceof ServerPlayer sp) SoundPlayback.playTo(sp, sound);
    }

    /**
     * 事件触发音效（**与结算同刻**，用户裁决 2026-10-10 的清单）：
     * 取长补短(治疗方 event_heal / 受伤方 event_damage) · 天崩地裂 event_damage · 问题外卖 event_poisoning ·
     * 拥挤通道 event_transport · 天使降临 event_heal · 天降之物 event_bonus。
     *
     * <p>⚠️ 本线 {@code audio/ModSounds.java} 目前只有 15 条非事件音效（EVENT_* 八字全缺）——
     * 这 6 个常量的落地属 B2-7（ModSounds + sounds.json + 8 个 .ogg），本类**照 forge 调用点原样写**，
     * 常量补齐后即生效；在此之前本文件编不过（硬错，而非静默哑剧）。
     */
    public static void playTriggerSounds(List<Player> targets, AstralEvent event) {
        String path = event.id().getPath();
        switch (path) {
            case "learn_from_others" -> {
                Player highest = null;
                for (Player p : targets) {
                    if (highest == null || p.getHealth() > highest.getHealth()) highest = p;
                }
                for (Player p : targets) playTo(p, ModSounds.EVENT_HEAL.get());
                if (highest != null) playTo(highest, ModSounds.EVENT_DAMAGE.get());
            }
            case "world_collapse" -> targets.forEach(p -> playTo(p, ModSounds.EVENT_DAMAGE.get()));
            case "problem_delivery" -> targets.forEach(p -> playTo(p, ModSounds.EVENT_POISONING.get()));
            case "crowded_passage" -> targets.forEach(p -> playTo(p, ModSounds.EVENT_TRANSPORT.get()));
            case "angel_descent" -> targets.forEach(p -> playTo(p, ModSounds.EVENT_HEAL.get()));
            case "gift_from_sky" -> targets.forEach(p -> playTo(p, ModSounds.EVENT_BONUS.get()));
            default -> { }
        }
    }
}
