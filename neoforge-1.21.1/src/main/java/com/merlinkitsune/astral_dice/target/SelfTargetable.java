package com.merlinkitsune.astral_dice.target;
import com.merlinkitsune.starenginelib.target.SelectorTargets;

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
 * <p><b>实现者与放行口径（2026-09-25 起；2026-09-19 追加史莱姆立牌）</b>：返回 {@code true} 的实现者有三类 ——
 * ① 游戏大师立牌 ren 的主动「熊孩子特权」（{@code ren_privilege}）；② 三张可自用的效果牌动作
 * {@code express_delivery} / {@code luxury_feast} / {@code berserk}（消费方实现类
 * {@code BaseEffectCardItem.SelectorAction}，取值来自各牌
 * {@code registerSelectorAction(actionId, allowSelf)} 的 {@code allowSelf}）；③ 史莱姆立牌 lulu 的主动
 * 「治愈粘液」（{@code lulu_healing_slime}，2026-09-19 重写为「对自身或被指向的玩家施加」时实现）。其余动作恒为
 * {@code false}（未实现本接口，或实现但返回 false —— 如效果牌 {@code you_have_i_have} 与立牌动作
 * {@code bonnie_undercover} / {@code haiqing_weak_mark} / {@code moses_apply_broken}、演示动作
 * {@code test_echo_*}），玩家可见行为与改动前逐字一致。放行做在**消费方**而不动前置库 ——
 * {@link SelectorTargets#matches(TargetType, Player, LivingEntity, boolean)} 这一重载只在
 * 「会话允许自身 + 目标就是选择者」时放行，{@link TargetSelectionManager#confirm} 会把
 * {@code Session.allowSelf} 交给它。故无需放宽 {@code TargetType#matches}（该枚举仍在库里排除自身）。
 */
public interface SelfTargetable {
    /** 本次选择会话是否允许把技能用在选择者自己身上（缺省 false = 不允许）。 */
    default boolean allowSelf() {
        return false;
    }
}
