package com.merlinkitsune.astral_dice.item.sign;

import com.merlinkitsune.astral_dice.component.GameplayConstants;
import com.merlinkitsune.astral_dice.component.ModAttachments;
import com.merlinkitsune.astral_dice.component.ModDataComponents;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import top.theillusivec4.curios.api.SlotContext;
import com.merlinkitsune.astral_dice.item.card.ExclusiveCardUtil;
import com.merlinkitsune.astral_dice.item.ModItems;
import com.merlinkitsune.astral_dice.item.chip.VitaminPillChipItem;

public class RinSignItem extends BaseSignItem {

    public RinSignItem(Properties properties) {
        super(properties);
    }

    @Override
    protected void clearSignData(Player player, ItemStack stack) {
        super.clearSignData(player, stack);
        // 移除立牌时重置调查员(rin)已使用的活体书页数量
        ModAttachments.setRinPages(player, 0);
    }

    @Override
    protected InteractionResultHolder<ItemStack> handleUse(Level level, Player player, ItemStack stack) {
        if (level.isClientSide) {
            return InteractionResultHolder.success(stack);
        }
        // 主动:获得一张"活体书页"(专属牌,绑定获得者);若使用前物品栏中无活体书页则共获得两张
        int giveCount = countLivingPages(player) == 0 ? 2 : 1;
        for (int i = 0; i < giveCount; i++) {
            ItemStack page = new ItemStack(ModItems.LIVING_PAGE.get());
            ExclusiveCardUtil.setOwner(page, player);
            VitaminPillChipItem.giveCard(player, page);
        }
        return InteractionResultHolder.success(stack);
    }

    // 统计物品栏中的活体书页数量
    private static int countLivingPages(Player player) {
        int count = 0;
        for (ItemStack s : player.getInventory().items) {
            if (!s.isEmpty() && s.is(ModItems.LIVING_PAGE.get())) {
                count += s.getCount();
            }
        }
        return count;
    }
}
