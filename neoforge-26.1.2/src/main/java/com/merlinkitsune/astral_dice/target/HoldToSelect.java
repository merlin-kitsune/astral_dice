package com.merlinkitsune.astral_dice.target;

import net.minecraft.world.entity.player.Player;

/**
 * 消费方侧（本模组）目标选择「手持即选择」扩展点。
 *
 * <p>实现了 {@link com.merlinkitsune.starenginelib.target.TargetSelectionAction} 的动作类
 * **按需**同时实现本接口，表示该动作由**主手手持对应物品**触发，而不是按键触发：
 * <ul>
 *   <li>开局由消费方每 tick 驱动（{@code BaseEffectCardItem#tickHeldSelector}，见
 *       {@code event/PlayerTickEvents#onPlayerTick}）：主手持有该物品且当前无会话 ⇒ 自动开局；</li>
 *   <li>收官由 {@link TargetSelectionManager#tick} 每 tick 校验 {@link #stillHeld(Player)}：
 *       物品一旦离开主手（切槽 / 丢弃 / 被消耗 / 换成别的牌）即**立即取消会话**并丢弃门控记录；</li>
 *   <li>此类会话**没有倒计时**：{@code Session.expireTick} 不参与判定（{@code Session.holdToSelect}），
 *       客户端提示也不追加「（剩余 N 秒）」（见 {@code client/TargetSelectionClient#steadyPrompt}）。</li>
 * </ul>
 *
 * <p><b>当前实现者（2026-09-25 起）</b>：四张选择器类效果牌的动作 —— 消费方实现类
 * {@code BaseEffectCardItem.SelectorAction}，动作 id 为 {@code express_delivery} / {@code luxury_feast} /
 * {@code you_have_i_have} / {@code berserk}。立牌主动技能仍是**按键触发 + 30 秒倒计时**，不实现本接口 ——
 * 故「无倒计时」只作用于效果牌，立牌的选择窗口口径逐字未变。
 *
 * <p>与 {@link SelfTargetable} 同样做在**消费方**：前置库的
 * {@code TargetSelectionAction} 不需要新增方法，库里也没有「手持」这一概念。
 *
 * <p><b>26.1.2 移植说明</b>：纯接口、无任何平台 API 触点（只用 {@link Player} 做参数类型），
 * 与 1.21.1 基准 {@code neoforge-1.21.1/.../target/HoldToSelect.java} **逐字同形**。
 */
public interface HoldToSelect {
    /** 该动作对应的物品此刻是否仍被玩家**主手**持有（每 tick 由服务端会话校验调用）。 */
    boolean stillHeld(Player player);
}
