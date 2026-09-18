package com.merlinkitsune.astral_dice.target;

/**
 * 消费方侧（本模组）目标选择「允许对自身使用」扩展点。
 *
 * <p>由实现了 {@link com.merlinkitsune.starenginelib.target.TargetSelectionAction} 的动作类
 * **按需**同时实现本接口（如 {@code new TargetSelectionAction() implements SelfTargetable}），
 * 并覆写 {@link #allowSelf()} 返回 {@code true}；{@link TargetSelectionManager#start} 会把该标志
 * 随会话（{@code Session.allowSelf}）与 {@code TargetSelectStartPayload} 一起下发，
 * 客户端据此在 actionbar 上给出「或按下 鼠标右键 对自身使用」的那一条口径，
 * 并在右键时改为提交对自身的确认。
 *
 * <p><b>本次仅铺设管线</b>：本模组**没有任何**动作实现本接口（三个立牌动作
 * {@code bonnie_undercover} / {@code haiqing_weak_mark} / {@code moses_apply_broken}
 * 与演示动作 {@code test_echo_*} 都不实现）⇒ {@link #allowSelf()} 在所有现有会话中恒为
 * {@code false}，玩家可见行为与改动前逐字一致。
 *
 * <p><b>将来真正启用自身目标时还需同时放宽目标类型校验</b>：目标类型的权威判定在
 * {@link com.merlinkitsune.starenginelib.target.TargetType#matches}
 * （{@code PLAYER} / {@code LIVING} 显式排除选择者自身，{@code ENEMY} / {@code ENEMY_OR_RIVAL}
 * 本就不会命中自身），而该枚举位于前置库 {@code starengine_lib} —— 只实现本接口并**不会**让
 * 服务端接受自身目标：{@link TargetSelectionManager#confirm} 仍会以
 * {@code SelectorTargets.matches(...)} 为准拒绝。故本接口单独存在时只影响客户端提示与
 * 提交路径，不构成「已支持自身目标」的完整实现。
 *
 * <p><b>26.1.2 移植说明</b>：纯接口、无任何平台 API 触点，与 1.21.1 基准逐字同形。
 */
public interface SelfTargetable {
    /** 本次选择会话是否允许把技能用在选择者自己身上（缺省 false = 不允许）。 */
    default boolean allowSelf() {
        return false;
    }
}
