package com.merlinkitsune.astral_dice.item.chip;

import com.merlinkitsune.astral_dice.component.ModAttachments;
import com.merlinkitsune.astral_dice.item.ModItems;
import com.merlinkitsune.astral_dice.item.card.RandomCardHandler;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.SlotContext;

/**
 * 智能手表筹码:物品栏中卡牌数量不足 {@link #CARD_THRESHOLD} 张时,每 1:00 自动补充一张随机手牌
 * (战斗牌 + 效果牌;专属牌经 {@link RandomCardHandler} 强制排除)。
 *
 * <p>计时与探天卫星同构:每次判定后重置 {@link #GIVE_INTERVAL_TICKS} 冷却,因此达到阈值以上时
 * 不发放但冷却照常推进,回到阈值以下最多等 1:00 即可获得补充。
 */
public class SmartWatchChipItem extends BaseChipItem {
    /** 物品栏卡牌数量低于该值时补充 */
    public static final int CARD_THRESHOLD = 10;
    /** 补充间隔(tick,1 分钟) */
    public static final int GIVE_INTERVAL_TICKS = 1200;

    public SmartWatchChipItem(Properties properties) {
        super(properties);
    }

    // 玩家是否佩戴本筹码
    public static boolean isEquipped(Player player) {
        if (player == null) return false;
        var curios = CuriosApi.getCuriosInventory(player);
        return curios.isPresent() && curios.get().findFirstCurio(s -> s.is(ModItems.SMART_WATCH_CHIP.get())).isPresent();
    }

    @Override
    public void curioTick(SlotContext slotContext, ItemStack stack) {
        if (!(slotContext.entity() instanceof Player player)) return;
        if (player.level().isClientSide()) return;
        long now = player.level().getGameTime();
        if (now < ModAttachments.getSmartWatchGiveCooldownEnd(player)) return;
        if (countCards(player) < CARD_THRESHOLD) {
            RandomCardHandler.giveCardTo(player, RandomCardHandler.CardCategory.ALL);
        }
        ModAttachments.setSmartWatchGiveCooldownEnd(player, now + GIVE_INTERVAL_TICKS);
    }

    // 统计物品栏(主背包)中本模组卡牌总数
    private static int countCards(Player player) {
        int count = 0;
        for (ItemStack stack : player.getInventory().items) {
            if (!stack.isEmpty() && ModItems.isCardItem(stack)) {
                count += stack.getCount();
            }
        }
        return count;
    }
}
