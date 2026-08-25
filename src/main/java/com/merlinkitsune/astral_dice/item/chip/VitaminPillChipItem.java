package com.merlinkitsune.astral_dice.item.chip;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import top.theillusivec4.curios.api.CuriosApi;
import com.merlinkitsune.astral_dice.item.HealingManager;
import com.merlinkitsune.astral_dice.item.ModItems;

/**
 * 维生素药丸筹码:通过合成或奖励途径获得任意卡牌时,治愈 +1。
 *
 * <p>触发范围包括:直接合成卡牌、随机/事件/立牌/筹码/效果牌特定能力发放的卡牌,
 * 以及拾取掉落的卡牌。发放卡牌的代码统一走 {@link #giveCard} 以便在成功放入背包时触发;
 * 合成与拾取由 ModEventHandlers 中的事件监听补充。
 */
public class VitaminPillChipItem extends BaseChipItem {
    /** 获得每张卡牌时增加的治愈点数 */
    public static final int HEALING_POINTS_PER_CARD = 1;

    public VitaminPillChipItem(Properties properties) {
        super(properties);
    }

    /**
     * 发放一张卡牌:成功放入背包时触发维生素药丸;背包满则掉落,等待拾取时再触发。
     */
    public static void giveCard(Player player, ItemStack card) {
        if (player == null || player.level().isClientSide()) return;
        if (card == null || card.isEmpty()) return;
        int amount = card.getCount();
        if (!player.getInventory().add(card)) {
            player.drop(card, false);
        } else {
            onCardGained(player, amount);
        }
    }

    /**
     * 由事件/发放逻辑调用:玩家获得任意卡牌时,若佩戴本筹码则治愈 +1(按卡牌数量)。
     * 调用前需确保传入的 stack 是卡牌且数量仍可读。
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
}
