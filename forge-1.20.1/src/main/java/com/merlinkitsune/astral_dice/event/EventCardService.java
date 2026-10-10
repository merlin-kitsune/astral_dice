package com.merlinkitsune.astral_dice.event;

import com.merlinkitsune.astral_dice.audio.ModSounds;
import com.merlinkitsune.astral_dice.audio.SoundPlayback;
import com.merlinkitsune.astral_dice.event.api.AstralEvent;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.network.PacketDistributor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * 事件效果卡的服务端编排（用户裁决 2026-10-10）。
 *
 * <p>顺序：**先给受影响玩家下发弹卡包（并播放 `event_fade_in`）**，卡动画 1.4s + 0.5s = **1.9s
 * （40t）后**才执行事件本身（效果 + 计时器）**并同刻播放该事件音效**。
 */
public final class EventCardService {
    private static final Logger LOGGER = LoggerFactory.getLogger(EventCardService.class);

    /** 弹卡到结算的延迟（tick）：动画 1.5s 播完后再等 0.5s。 */
    public static final int DELAY_TICKS = 38;   // = 动画 1.4s（0.2+1.0+0.2）+ 0.5s

    private static final List<Pending> QUEUE = new ArrayList<>();

    private EventCardService() {
    }

    private record Pending(ServerLevel level, long executeAt, Consumer<ServerLevel> action) {
    }

    /** 通用延迟调度（本 tick 起 delayTicks 后执行；用于「先治疗后伤害」这类分步结算）。 */
    public static void schedule(ServerLevel level, int delayTicks, Consumer<ServerLevel> action) {
        QUEUE.add(new Pending(level, level.getGameTime() + Math.max(1, delayTicks), action));
    }

    /** 弹卡 + 登记 40t 后的结算。 */
    public static void showThenRun(ServerLevel level, List<Player> targets, AstralEvent event,
                                   int tier, Consumer<ServerLevel> action) {
        for (var p : targets) {
            if (p instanceof ServerPlayer sp) {
                com.merlinkitsune.astral_dice.network.ModNetwork.CHANNEL.send(
                        PacketDistributor.PLAYER.with(() -> sp),
                        new com.merlinkitsune.astral_dice.network.EventCardMessage(event.id(), event.defaultDurationTicks(), tier));
                SoundPlayback.playTo(sp, ModSounds.EVENT_FADE_IN.get());
            }
        }
        QUEUE.add(new Pending(level, level.getGameTime() + DELAY_TICKS, action));
        LOGGER.info("AP_EVENT_CARD: shown:{}:exec_at={}", event.id(), level.getGameTime() + DELAY_TICKS);
    }

    /** 到点执行（由 {@link EventEffects#onPlayerTick} 每 10 tick 调一次）。 */
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
