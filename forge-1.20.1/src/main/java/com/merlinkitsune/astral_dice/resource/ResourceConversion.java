package com.merlinkitsune.astral_dice.resource;

import com.merlinkitsune.astral_dice.item.chip.AtmChipItem;
import com.merlinkitsune.astral_dice.item.ModItems;
import com.merlinkitsune.astral_dice.item.StarLightManager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * 点数流派转化工具:集中管理各流派点数与物品/伤害之间的转换比例。
 * 筹码/立牌需要点数转化时统一调用本类,禁止散落硬编码比例。
 * 新增转化(如未来"反击"流派的转化)在此扩展。
 */
public final class ResourceConversion {
    // 星光→星币比例:2 点星光 = 1 个星币
    public static final int STARLIGHT_PER_COIN = 2;

    private ResourceConversion() {
    }

    /**
     * 星光 → 星币(按 2:1 比例)。
     *
     * @param player 玩家
     * @param amount 用于兑换的星光数;-1 = 消耗全部星光(余数保留);其他 = 最多消耗 amount 点
     * @return 实际获得的星币数
     */
    public static int starlightToStarCoins(Player player, int amount) {
        if (player.level().isClientSide()) return 0;
        int starlight = StarLightManager.get(player);
        int use = amount < 0 ? starlight : Math.min(amount, starlight);
        int coins = use / STARLIGHT_PER_COIN;
        if (coins <= 0) return 0;
        int spent = StarLightManager.spend(player, coins * STARLIGHT_PER_COIN);
        int gained = spent / STARLIGHT_PER_COIN;
        // ATM机筹码:使用星光兑换星币时,兑换量(星币产出)增加 40%
        if (AtmChipItem.isEquipped(player)) {
            gained += (int) (gained * 0.4);
        }
        if (gained > 0) {
            giveItem(player, new ItemStack(ModItems.STAR_COIN.get(), gained));
        }
        return gained;
    }

    // 发放物品(背包满则掉落)
    public static void giveItem(Player player, ItemStack stack) {
        if (!player.getInventory().add(stack)) {
            player.drop(stack, false);
        }
    }
}
