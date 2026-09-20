package com.merlinkitsune.astral_dice.item.card;

/**
 * 「临时牌可以放入」的槽位标记接口(绿洲女王 nardis 批)。
 *
 * <p>用途:{@code mixin/container/SlotPlaceGuardMixin} 与
 * {@code mixin/container/ContainerMoveGuardMixin} 在判定「目标槽是否允许放置临时牌」时,
 * 除了「玩家自己的物品栏槽」之外,还允许**本模组自己的卡牌栏槽** —— 因为把临时牌放进卡牌栏
 * 就是「装备」,是需求明确允许的两条去路之一(另一条是拿在手里)。
 *
 * <p>实现方:{@code screen/CardInventoryMenu} 的 {@code AttackCardSlot} / {@code DefenseCardSlot}
 * (它们的 {@code container} 是 {@code SimpleContainer},**不能**靠容器类型判定 ——
 * 那会误放行其它用 {@code SimpleContainer} 的容器)。
 *
 * <p>本接口是 **public** 的:审计/探针可以直接读取「槽是否为本模组卡牌栏槽」这一事实,
 * 无需实现它。
 */
public interface TemporaryCardPermissiveSlot {
}
