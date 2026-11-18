package com.merlinkitsune.astral_dice.api.combat;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 骰战结算回调的注册表（B3）。
 *
 * <p>内部表是 {@link CopyOnWriteArrayList}，注册可发生在任何阶段，但按约定仍只应在初始化阶段
 * 调用。分发带 {@link ThreadLocal} 重入闸门：监听器自己制造的伤害（例如额外一次 {@code hurt}）
 * 不会再触发一轮通知，避免无限递归。
 */
public final class CombatResolutionListeners {

    private static final Logger LOGGER = LoggerFactory.getLogger("Astral Dice");

    private static final List<CombatResolutionListener> LISTENERS = new CopyOnWriteArrayList<>();

    private static final ThreadLocal<Boolean> FIRING = ThreadLocal.withInitial(() -> Boolean.FALSE);

    private CombatResolutionListeners() {
    }

    /** 注册一个监听器；{@code null} 被忽略，重复注册会重复收到通知。 */
    public static void register(CombatResolutionListener listener) {
        if (listener == null) {
            return;
        }
        LISTENERS.add(listener);
    }

    /** 分发一次结算快照；重入期间或没有监听器时直接返回。 */
    public static void fire(CombatResolutionView view) {
        if (view == null || LISTENERS.isEmpty() || FIRING.get()) {
            return;
        }
        FIRING.set(Boolean.TRUE);
        try {
            for (CombatResolutionListener listener : LISTENERS) {
                try {
                    listener.onResolution(view);
                } catch (RuntimeException e) {
                    LOGGER.error("AP_COMBAT_API: combat_resolution_listener failed: {}", e.toString());
                }
            }
        } finally {
            FIRING.set(Boolean.FALSE);
        }
    }

    /** 当前已注册的监听器数量（诊断用）。 */
    public static int size() {
        return LISTENERS.size();
    }
}
