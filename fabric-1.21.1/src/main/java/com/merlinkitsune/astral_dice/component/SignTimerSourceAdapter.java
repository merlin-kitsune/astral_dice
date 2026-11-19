package com.merlinkitsune.astral_dice.component;

import com.merlinkitsune.starengine.event.EffectTimerGuard;
import com.merlinkitsune.starengine.event.TimerSource;
import net.minecraft.world.entity.player.Player;

import java.util.HashMap;
import java.util.Map;

/**
 * 计时表存储 seam 的 fabric-1.21.1 实现：把库版 {@link EffectTimerGuard}
 * 的计时表接到本模组的 {@link ModAttachments#EFFECT_TIMER_ENDS} 附件上。
 *
 * <p>附件值的泛型已是库版 {@link EffectTimerGuard.TimerEntry}（本地副本已删除），
 * 因此这里只做防御性拷贝：{@code AttachedDataKey#get} 返回内部 Map，直接交出会被守卫原地改；
 * 写入也拷贝一份，避免附件持有调用方的临时 Map。
 *
 * <p>读取/写入路径与本地守卫逐字一致：{@code get} 每次从 NBT 解码，{@code set} 编码后整体写回；
 * 老存档字段（end/amp/ambient/visible/icon）未变，可直接读出。
 */
public final class SignTimerSourceAdapter implements TimerSource {

    @Override
    public Map<String, EffectTimerGuard.TimerEntry> getTimers(Player player) {
        Map<String, EffectTimerGuard.TimerEntry> stored = ModAttachments.EFFECT_TIMER_ENDS.get(player);
        return stored == null ? new HashMap<>() : new HashMap<>(stored);
    }

    @Override
    public void setTimers(Player player, Map<String, EffectTimerGuard.TimerEntry> timers) {
        ModAttachments.EFFECT_TIMER_ENDS.set(player, timers == null ? new HashMap<>() : new HashMap<>(timers));
    }

    @Override
    public long now(Player player) {
        return player.level().getGameTime();
    }
}
