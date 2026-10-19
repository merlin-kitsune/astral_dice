package com.merlinkitsune.astral_dice.component;

import com.merlinkitsune.starengine.event.EffectTimerGuard;
import com.merlinkitsune.starengine.event.TimerSource;
import net.minecraft.world.entity.player.Player;

import java.util.HashMap;
import java.util.Map;

/**
 * 计时表存储 seam 的 Forge 1.20.1 实现:把库版 {@link EffectTimerGuard} 的计时表
 * 接到本模组的 {@link ModAttachments#EFFECT_TIMER_ENDS} 附件上。
 *
 * <p>存储形态与下沉前完全一致 —— 一个 {@code Map<String, TimerEntry>} 附件:
 * 键 = 效果注册名(如 {@code astral_dice:empower}),值 = 库的 {@code TimerEntry}
 * (结束 tick + 重新施加所需参数,codec 字段 end/amp/ambient/visible/icon 未变),
 * 因此**老存档可直接读出**,不需要迁移。
 *
 * <p>{@link AttachedDataKey#get} 每次调用都从 NBT 重新解码出新 Map,{@link AttachedDataKey#set}
 * 编码后整体写回 —— 与本线原 {@code EffectTimerGuard} 的读写路径逐字一致。
 */
public final class SignTimerSourceAdapter implements TimerSource {
    @Override
    public Map<String, EffectTimerGuard.TimerEntry> getTimers(Player player) {
        Map<String, EffectTimerGuard.TimerEntry> stored = ModAttachments.EFFECT_TIMER_ENDS.get(player);
        return stored == null ? new HashMap<>() : new HashMap<>(stored);
    }

    @Override
    public void setTimers(Player player, Map<String, EffectTimerGuard.TimerEntry> timers) {
        ModAttachments.EFFECT_TIMER_ENDS.set(player,
                timers == null ? new HashMap<>() : new HashMap<>(timers));
    }

    @Override
    public long now(Player player) {
        return player.level().getGameTime();
    }
}
