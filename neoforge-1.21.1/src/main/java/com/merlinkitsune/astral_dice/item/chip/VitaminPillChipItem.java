package com.merlinkitsune.astral_dice.item.chip;

import net.minecraft.world.item.ItemStack;
import top.theillusivec4.curios.api.CuriosApi;
import com.merlinkitsune.astral_dice.item.HealingManager;
import com.merlinkitsune.astral_dice.item.ModItems;
import net.minecraft.world.entity.player.Player;

/**
 * 维生素药丸筹码:通过**获得**任意卡牌时,治愈 +1。
 *
 * <p>触发范围:随机/事件/立牌/筹码/效果牌特定能力等**模组发牌路径**(统一走 {@link #giveCard}),
 * 以及**购买路径** —— 卡牌商店购买与卡牌交易员成交都经 {@link #notifyCardObtained} 视为「获得」。
 * 拾取地面卡牌不会触发,避免反复丢弃/拾取刷治愈点。
 *
 * <p><b>2026-10-09 用户裁决</b>:卡牌合成配方已全部移除 ⇒ 原先的 {@code ItemCraftedEvent} 监听**已删除**
 * (商店购买视为获得)。若将来有数据包补回卡牌配方,不再有合成触发。
 */
public class VitaminPillChipItem extends BaseChipItem {
    /** 获得每张卡牌时增加的治愈点数 */
    public static final int HEALING_POINTS_PER_CARD = 1;

    public VitaminPillChipItem(Properties properties) {
        super(properties);
    }

    /**
     * 发放一张卡牌:成功放入背包时触发维生素药丸。
     * 背包满则掉落,但不会在拾取时触发(防止丢弃/拾取刷治愈点)。
     * 注意:此路径不触发看板娘立牌(mimi)被动(奖励/复制/返还等发放不再计星币,
     * mimi 仅由主动返还这一条显式路径触发)。
     *
     * <p>本重载等价于 {@code giver = null}(发牌者未知)⇒ 不触发蛟龙立牌(mamushi)被动
     * 「湖沼之王」的觉醒计数。已知发牌者的路径必须走
     * {@link #giveCard(Player, Player, ItemStack)}(见 §3.1 发牌漏斗)。
     */
    public static void giveCard(Player player, ItemStack card) {
        giveCard(null, player, card);
    }

    /**
     * 发放一张卡牌(**带发牌者**):蛟龙立牌被动「湖沼之王」的唯一漏斗 ——
     * 成功入包且 {@code giver != receiver} 时按受益人去重 +1 层觉醒(单次事件封顶 3 层)。
     *
     * @param giver    发牌者;{@code null} 表示未知(奖励/返还等路径,不计觉醒)
     * @param player   收牌者(实际入包玩家)
     * @param card     待发放卡牌
     */
    public static void giveCard(Player giver, Player player, ItemStack card) {
        if (player == null || player.level().isClientSide()) return;
        if (card == null || card.isEmpty()) return;
        int amount = card.getCount();
        // 教主立牌「狐光」:经本模组发牌漏斗**成功入包**的攻击牌 +1 层/张(掉落不计)。
        // ⚠️ 必须在 add(...) 之前判定/取数:add 会把传入栈清空;且拾取路径**刻意不挂钩**(防刷,见 TeruSignItem)。
        boolean attackCard = com.merlinkitsune.astral_dice.item.sign.TeruSignItem.isAttackCard(card);
        if (!player.getInventory().add(card)) {
            player.drop(card, false);
        } else {
            onCardGained(player, amount);
            if (attackCard) {
                com.merlinkitsune.astral_dice.item.sign.TeruSignItem.onAttackCardCount(player, amount);
            }
            // 蛟龙立牌被动「湖沼之王」:佩戴者使其他角色获得卡牌时累计觉醒层数(自己给自己不计)。
            if (giver != null) {
                com.merlinkitsune.astral_dice.item.sign.MamushiSignItem.onCardGivenToOther(giver, player);
            }
        }
    }

    /**
     * 由事件/发放逻辑调用:玩家获得任意卡牌时,若佩戴本筹码则治愈 +1(按卡牌数量)。
     * 调用前需确保传入的 stack 是卡牌且数量仍可读。
     * 注意:此路径不触发看板娘立牌(mimi)被动(见 {@link #giveCard})。
     */
    public static void onCardGained(Player player, ItemStack card) {
        if (card == null || card.isEmpty() || !ModItems.isCardItem(card)) return;
        onCardGained(player, card.getCount());
    }

    /**
     * 按卡牌数量增加治愈点(内部不校验卡牌类型)。
     */
    public static void onCardGained(Player player, int amount) {
        if (player == null || player.level().isClientSide()) return;
        if (amount <= 0) return;
        var curios = CuriosApi.getCuriosInventory(player);
        if (curios.isEmpty()) return;
        if (curios.get().findFirstCurio(s -> s.is(ModItems.VITAMIN_PILL_CHIP.get())).isEmpty()) return;
        HealingManager.add(player, HEALING_POINTS_PER_CARD * amount);
    }

    /**
     * **购买即「获得」**(2026-10-09 用户裁决):卡牌商店购买与卡牌交易员成交时,由调用方在卡牌**已写入玩家背包/槽位后**
     * 调用本方法 —— 只做「获得」语义的触发(维生素药丸治愈 + 提尔立牌狐光层数),**不再插入物品**、
     * 也**不触发看板娘立牌被动**(mimi 只按「返还卡牌」计数)。
     *
     * <p>注意与 {@link #giveCard} 的分工:后者是「发牌 + 触发」(奖励/随机/事件路径,物品由它插入);
     * 本方法是「物品已入包 + 只触发」(购买路径,物品由原版成交或商店结算插入)。
     */
    public static void notifyCardObtained(Player player, ItemStack card) {
        if (player == null || player.level().isClientSide()) return;
        if (card == null || card.isEmpty() || !ModItems.isCardItem(card)) return;
        onCardGained(player, card.getCount());
        com.merlinkitsune.astral_dice.item.sign.TeruSignItem.onAttackCardGained(player, card);
    }
}
