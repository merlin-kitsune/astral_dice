package com.merlinkitsune.astral_dice.api.combat;

/**
 * 骰战结算完成后的回调（B3）。
 *
 * <p>在每次骰战结算尾部被调用一次；回调里**不得**再次制造伤害并期待再次收到通知 ——
 * 分发器带重入闸门，回调内部触发的伤害结算不会递归通知。抛出的异常会被分发器捕获并记录，
 * 不影响其它监听器。
 */
@FunctionalInterface
public interface CombatResolutionListener {

    void onResolution(CombatResolutionView view);
}
