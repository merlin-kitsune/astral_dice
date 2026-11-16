package com.merlinkitsune.astral_dice.component;

import com.merlinkitsune.starengine.event.TimerSource;
import net.minecraft.world.entity.player.Player;

import java.util.HashMap;
import java.util.Map;

/**
 * 计时表存储 seam 的 Fabric 1.20.1 实现:把库版 {@code com.merlinkitsune.starengine.event.EffectTimerGuard}
 * 的计时表接到本模组的 {@link ModAttachments#EFFECT_TIMER_ENDS} 附件上。
 *
 * <p>存储形态与 Blueprint(forge)完全一致 —— 一个 {@code Map<String, TimerEntry>} 附件:
 * 键 = 效果注册名(如 {@code astral_dice:empower}),值 = 计时记录
 * (结束 tick + 重新施加所需参数,codec 字段 end/amp/ambient/visible/icon 未变),
 * 因此**老存档可直接读出**,不需要迁移。
 *
 * <p><b>平台差异(Fabric 1.20.1 / 逐条理由)</b>:
 * <ul>
 *   <li><b>两套 {@code EffectTimerGuard} 的 {@code TimerEntry} 不是同一个类</b>:本线
 *       {@link ModAttachments#EFFECT_TIMER_ENDS} 的泛型参数是本模组自己的
 *       {@code com.merlinkitsune.astral_dice.event.EffectTimerGuard.TimerEntry}(本地实现),
 *       而 {@link TimerSource} 接口要求的是库版
 *       {@code com.merlinkitsune.starengine.event.EffectTimerGuard.TimerEntry}。
 *       两者字段名/顺序/类型逐一相同(实测 javap:{@code long endTick, int amplifier,
 *       boolean ambient, boolean visible, boolean showIcon}),故此处做**逐字段显式转换**
 *       (不使用任何反射 / 类型强转,编译期就能发现字段漂移)。</li>
 *   <li><b>⚠️ 本线现状(需裁决)</b>:fabric 线真正在跑的是**本地** {@code event/EffectTimerGuard}
 *       (由 {@code event/ModEffectEvents} 驱动),库版守卫的 seam 在本线**从未被设置**
 *       ({@code setTimerSource} 全仓 0 调用点),而 {@code AstralDiceMod} 在本批次只允许追加
 *       {@code LoaderBus.register} 行 ⇒ 本类目前**无消费者**(纯交付物)。
 *       接法只有一行:{@code com.merlinkitsune.starengine.event.EffectTimerGuard
 *       .setTimerSource(new SignTimerSourceAdapter())},位置与 forge 的
 *       {@code AstralDiceMod} L72 相同。见 porting/fabric-1.20.1/B4-SIGNS-REPORT.md「需裁决」。</li>
 * </ul>
 *
 * <p>{@code AttachedDataKey#get} 每次调用都从 NBT 重新解码出新 Map,{@code #set} 编码后整体写回
 * —— 与本线既有计时表的读写路径逐字一致。
 */
public final class SignTimerSourceAdapter implements TimerSource {

    private static com.merlinkitsune.astral_dice.event.EffectTimerGuard.TimerEntry toLocal(
            com.merlinkitsune.starengine.event.EffectTimerGuard.TimerEntry entry) {
        if (entry == null) return null;
        return new com.merlinkitsune.astral_dice.event.EffectTimerGuard.TimerEntry(
                entry.endTick(), entry.amplifier(), entry.ambient(), entry.visible(), entry.showIcon());
    }

    private static com.merlinkitsune.starengine.event.EffectTimerGuard.TimerEntry toLib(
            com.merlinkitsune.astral_dice.event.EffectTimerGuard.TimerEntry entry) {
        if (entry == null) return null;
        return new com.merlinkitsune.starengine.event.EffectTimerGuard.TimerEntry(
                entry.endTick(), entry.amplifier(), entry.ambient(), entry.visible(), entry.showIcon());
    }

    @Override
    public Map<String, com.merlinkitsune.starengine.event.EffectTimerGuard.TimerEntry> getTimers(Player player) {
        Map<String, com.merlinkitsune.astral_dice.event.EffectTimerGuard.TimerEntry> stored =
                ModAttachments.EFFECT_TIMER_ENDS.get(player);
        Map<String, com.merlinkitsune.starengine.event.EffectTimerGuard.TimerEntry> out = new HashMap<>();
        if (stored != null) {
            for (Map.Entry<String, com.merlinkitsune.astral_dice.event.EffectTimerGuard.TimerEntry> e
                    : stored.entrySet()) {
                out.put(e.getKey(), toLib(e.getValue()));
            }
        }
        return out;
    }

    @Override
    public void setTimers(Player player,
                          Map<String, com.merlinkitsune.starengine.event.EffectTimerGuard.TimerEntry> timers) {
        Map<String, com.merlinkitsune.astral_dice.event.EffectTimerGuard.TimerEntry> stored = new HashMap<>();
        if (timers != null) {
            for (Map.Entry<String, com.merlinkitsune.starengine.event.EffectTimerGuard.TimerEntry> e
                    : timers.entrySet()) {
                stored.put(e.getKey(), toLocal(e.getValue()));
            }
        }
        ModAttachments.EFFECT_TIMER_ENDS.set(player, stored);
    }

    @Override
    public long now(Player player) {
        return player.level().getGameTime();
    }
}
