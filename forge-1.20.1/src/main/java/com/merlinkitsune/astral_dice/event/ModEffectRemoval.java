package com.merlinkitsune.astral_dice.event;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.player.Player;

/**
 * 本模组主动移除效果的统一内部通道。
 *
 * <p>效果移除拦截器({@link ModEventHandlers#onModEffectRemovalPrevented})会拦截
 * 牛奶/蜂蜜/{@code /effect clear} 等外部清除;本模组自己的移除逻辑必须经由本类移除,
 * 通过内部移除标志放行,避免"待命"提示、计数效果等被拦截后永久残留。
 * 与 {@link EffectTimerGuard} 的强制移除标志为同一模式(单一职责分离)。
 */
public final class ModEffectRemoval {
    private static boolean internal = false;

    private ModEffectRemoval() {
    }

    /** 是否为内部移除(供效果移除拦截器放行) */
    public static boolean isInternal() {
        return internal;
    }

    /** 以内部通道移除玩家身上的效果(等价于 removeEffect,但不会被外部清除拦截) */
    public static void remove(Player player, MobEffect effect) {
        if (player == null) return;
        internal = true;
        try {
            player.removeEffect(effect);
        } finally {
            internal = false;
        }
    }
}
