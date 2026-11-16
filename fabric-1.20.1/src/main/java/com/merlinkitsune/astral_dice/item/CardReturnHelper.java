package com.merlinkitsune.astral_dice.item;

import com.merlinkitsune.astral_dice.combat.CardRegistry;
import com.merlinkitsune.astral_dice.component.AppliedStone;
import com.merlinkitsune.astral_dice.component.ModDataComponents;
import com.merlinkitsune.astral_dice.item.card.TemporaryCardUtil;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * 把骰子上已装配的卡牌（{@link AppliedStone}）还原成卡牌物品栈并归还玩家（2026-11-15 用户裁决，实验性升星专用）。
 *
 * <p>只在**服务端**生效（客户端直接返回 0）——装配会销毁卡牌物品栈，返还必须由服务端权威执行一次。
 *
 * <p>入包语义与既有归还逻辑（{@code item.sign.JillSignItem} 等）一致：先 {@code Inventory#add}
 * （会自动合并到同类栈）；失败时生存模式 {@code player.drop(stack, false)}，创造模式直接丢弃（不产生掉落物）。
 */
public final class CardReturnHelper {
    private CardReturnHelper() {
    }

    /**
     * @param stones 骰子上记录的已装配卡牌（通常来自 {@code WeaponEnhancement#appliedStones()}）
     * @return 实际归还的卡牌张数（日志取证用）
     */
    public static int returnCards(Player player, List<AppliedStone> stones) {
        if (player == null || stones == null || stones.isEmpty()) return 0;
        if (player.level().isClientSide()) return 0;
        int returned = 0;
        for (AppliedStone stone : stones) {
            if (stone == null) continue;
            ItemStack stack = buildCard(player, stone);
            if (stack.isEmpty()) continue;
            if (!player.getInventory().add(stack)) {
                if (!player.isCreative()) {
                    player.drop(stack, false);
                }
            }
            returned++;
        }
        return returned;
    }

    /**
     * 按 (type, uses, temporary, owner) 重建卡牌栈 —— 与 {@code screen.CardInventoryMenu#loadFromDice} 同一范式：
     * {@code CARD_USES} 写回剩余耐久，临时牌补 {@link TemporaryCardUtil#mark(Player, ItemStack, java.util.UUID)}。
     */
    private static ItemStack buildCard(Player player, AppliedStone stone) {
        ItemStack stack = CardRegistry.typeToItem(stone.type());
        if (stack.isEmpty()) return ItemStack.EMPTY;
        ModDataComponents.CARD_USES.set(stack, stone.uses());
        if (stone.temporary()) {
            TemporaryCardUtil.mark(player, stack, stone.ownerId());
        }
        return stack;
    }
}
