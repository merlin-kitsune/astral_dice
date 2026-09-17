package com.merlinkitsune.astral_dice.item.chip;

import com.merlinkitsune.astral_dice.item.ModItems;
import com.merlinkitsune.astral_dice.item.card.RandomCardHandler;
import net.minecraft.world.entity.player.Player;
import top.theillusivec4.curios.api.CuriosApi;

/**
 * 会员推荐信筹码:每次触发骰神赐福时,获得一张随机卡牌(战斗牌 + 效果牌;
 * 专属牌经 {@link RandomCardHandler} 强制排除)。由 {@link #onBlessingStart} 在赐福新周期开始时调用。
 */
public class MemberRecommendationChipItem extends BaseChipItem {
    public MemberRecommendationChipItem(Properties properties) {
        super(properties);
    }

    // 玩家是否佩戴本筹码
    public static boolean isEquipped(Player player) {
        if (player == null) return false;
        var curios = CuriosApi.getCuriosInventory(player);
        return curios.isPresent()
                && curios.get().findFirstCurio(s -> s.is(ModItems.MEMBER_RECOMMENDATION_CHIP.get())).isPresent();
    }

    /** 触发骰神赐福(新周期开始)时调用:获得一张随机卡牌。 */
    public static void onBlessingStart(Player player) {
        if (player == null || player.level().isClientSide()) return;
        if (!isEquipped(player)) return;
        RandomCardHandler.giveCardTo(player, RandomCardHandler.CardCategory.ALL);
    }
}
