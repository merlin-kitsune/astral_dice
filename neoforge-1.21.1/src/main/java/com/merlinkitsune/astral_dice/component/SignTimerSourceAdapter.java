package com.merlinkitsune.astral_dice.component;

import com.merlinkitsune.starengine.event.EffectTimerGuard;
import com.merlinkitsune.starengine.event.TimerSource;
import net.minecraft.world.entity.player.Player;

import java.util.HashMap;
import java.util.Map;

/**
 * 计时表存储 seam 的 NeoForge 1.21.1 实现:把库版 {@link EffectTimerGuard} 的计时表
 * 接到本模组的 {@link ModAttachments#EFFECT_TIMER_ENDS} 附件上。
 *
 * <p>存储形态与下沉前完全一致 —— 一个 {@code Map<String, TimerEntry>} 附件:
 * 键 = 效果注册名(如 {@code astral_dice:empower}),值 = 库的 {@code TimerEntry}
 * (结束 tick + 重新施加所需参数,codec 字段 end/amp/ambient/visible/icon 未变),
 * 因此**老存档可直接读出**,不需要迁移。
 *
 * <p>读写都做一次浅拷贝:NeoForge 的 {@code getData} 返回的是附件内的同一个 Map 实例,
 * 直接把引用交给守卫会让守卫的「新建 Map 再整体写回」语义失效(原地修改绕过脏标记)。
 */
public final class SignTimerSourceAdapter implements TimerSource {
    @Override
    public Map<String, EffectTimerGuard.TimerEntry> getTimers(Player player) {
        Map<String, EffectTimerGuard.TimerEntry> stored =
                player.getData(ModAttachments.EFFECT_TIMER_ENDS.get());
        return stored == null ? new HashMap<>() : new HashMap<>(stored);
    }

    @Override
    public void setTimers(Player player, Map<String, EffectTimerGuard.TimerEntry> timers) {
        player.setData(ModAttachments.EFFECT_TIMER_ENDS.get(),
                timers == null ? new HashMap<>() : new HashMap<>(timers));
    }

    @Override
    public long now(Player player) {
        return player.level().getGameTime();
    }
}
