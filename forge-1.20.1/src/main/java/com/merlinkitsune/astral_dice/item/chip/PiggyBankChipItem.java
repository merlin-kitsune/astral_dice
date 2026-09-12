package com.merlinkitsune.astral_dice.item.chip;

import com.merlinkitsune.astral_dice.component.ModAttachments;
import com.merlinkitsune.starengine.item.CuriosCompat;
import com.merlinkitsune.astral_dice.item.ModItems;
import com.merlinkitsune.astral_dice.resource.ResourceConversion;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * 小猪存钱罐筹码:每使用 {@link #CARDS_REQUIRED} 张效果牌后,获得 {@link #COIN_REWARD} 星币
 * (独立计数,与魔法秘典/忍者立牌互不关联;由 {@link #onEffectCardUsed} 在每次使用效果牌时调用)。
 * 卸下筹码时重置计数。
 */
public class PiggyBankChipItem extends BaseChipItem {
    /** 触发一次星币奖励所需使用的效果牌数量 */
    public static final int CARDS_REQUIRED = 2;
    /** 每次触发获得的星币数量 */
    public static final int COIN_REWARD = 3;

    public PiggyBankChipItem(Properties properties) {
        super(properties);
    }

    // 玩家是否佩戴本筹码
    public static boolean isEquipped(Player player) {
        if (player == null) return false;
        var curios = CuriosCompat.getCuriosInventory(player);
        return curios.isPresent() && curios.get().findFirstCurio(s -> s.is(ModItems.PIGGY_BANK_CHIP.get())).isPresent();
    }

    /** 每使用一张效果牌时调用:计数 +1,满 {@link #CARDS_REQUIRED} 张时发放星币并归零。 */
    public static void onEffectCardUsed(Player player) {
        if (player == null || player.level().isClientSide()) return;
        if (!isEquipped(player)) return;
        int count = ModAttachments.getPiggyBankUseCount(player) + 1;
        if (count >= CARDS_REQUIRED) {
            ResourceConversion.giveItem(player, new ItemStack(ModItems.STAR_COIN.get(), COIN_REWARD));
            count = 0;
        }
        ModAttachments.setPiggyBankUseCount(player, count);
    }

    // 卸下筹码(真正卸下):重置效果牌计数
    @Override
    protected void onChipUnequip(Player player, ItemStack stack) {
        ModAttachments.setPiggyBankUseCount(player, 0);
    }
}
