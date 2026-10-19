package com.merlinkitsune.astral_dice.item.chip;

import com.merlinkitsune.astral_dice.item.CurioSlotUtil;
import com.merlinkitsune.starengine.item.CuriosCompat;
import com.merlinkitsune.astral_dice.item.ModItems;
import com.merlinkitsune.astral_dice.item.card.RandomCardHandler;
import net.minecraft.world.entity.player.Player;

/**
 * 会员推荐信筹码:每次触发骰神赐福时,获得一张随机卡牌(战斗牌 + 效果牌;
 * 专属牌经 {@link RandomCardHandler} 强制排除)。由 {@link #onBlessingStart} 在赐福新周期开始时调用。
 *
 * <p><b>2026-10-08 追加能力</b>:装备期间,**装备者本人的商店**内所有卡牌价格恒 -1
 * (非随机;与经商立牌被动「每张牌 10% 降至 1 星币」叠加,最低可到 0 星币 —— 用户明确「价格降低至 0」)。
 * 结算见 {@link com.merlinkitsune.astral_dice.shop.CardShopMenu}。
 */
public class MemberRecommendationChipItem extends BaseChipItem {
    public MemberRecommendationChipItem(Properties properties) {
        super(properties);
    }

    // 玩家是否佩戴本筹码
    public static boolean isEquipped(Player player) {
        // 总闸门(骰子装备和卸除调整):未佩戴骰子 ⇒ 本件功能一律不生效(数值保留)
        if (!CurioSlotUtil.hasDiceEquipped(player)) return false;
        if (player == null) return false;
        var curios = CuriosCompat.getCuriosInventory(player);
        return curios.isPresent()
                && curios.get().findFirstCurio(s -> s.is(ModItems.MEMBER_RECOMMENDATION_CHIP.get())).isPresent();
    }

    /**
     * 商店内所有卡牌的恒减价额度(2026-10-08 追加能力):装备者本人为 {@code 1},否则 {@code 0}。
     *
     * <p>「永久」= **装备期间恒生效**(与 10% 随机降价无关),卸下即失效;仅作用于本装备者自己的商店。
     */
    public static int shopPriceReduction(Player player) {
        return isEquipped(player) ? 1 : 0;
    }

    /** 触发骰神赐福(新周期开始)时调用:获得一张随机卡牌。 */
    public static void onBlessingStart(Player player) {
        if (player == null || player.level().isClientSide()) return;
        if (!isEquipped(player)) return;
        RandomCardHandler.giveCardTo(player, RandomCardHandler.CardCategory.ALL);
    }
}
