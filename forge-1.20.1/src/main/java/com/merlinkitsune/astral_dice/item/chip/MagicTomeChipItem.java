package com.merlinkitsune.astral_dice.item.chip;
import com.merlinkitsune.astral_dice.item.CuriosCompat;

import com.merlinkitsune.astral_dice.component.ModAttachments;
import com.merlinkitsune.astral_dice.effect.ModEffects;
import com.merlinkitsune.astral_dice.event.ModEffectRemoval;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import top.theillusivec4.curios.api.CuriosApi;
import com.merlinkitsune.astral_dice.item.card.BaseEffectCardItem;
import com.merlinkitsune.astral_dice.item.card.ExclusiveCardUtil;
import com.merlinkitsune.astral_dice.item.ModItems;

/**
 * 魔法秘典筹码:每使用 3 张效果牌,复制最后一张使用的效果牌并返回到物品栏(独立计数,与忍者立牌互不关联)。
 * 计数期间显示"魔法秘典"效果图标,等级 = 当前第几张;第 3 张复制后计数归 0,效果消失。
 * 卸下筹码时重置计数器。
 */
public class MagicTomeChipItem extends BaseChipItem {
    public MagicTomeChipItem(Properties properties) {
        super(properties);
    }

    // 被动:每使用第 3 张效果牌时,复制最后一张使用的效果牌并返回物品栏(独立计数)
    public static void onEffectCardUsed(Player player, String cardType) {
        if (player.level().isClientSide()) return;
        var curios = CuriosCompat.getCuriosInventory(player);
        if (curios.isEmpty()) return;
        if (curios.get().findFirstCurio(s -> s.is(ModItems.MAGIC_TOME_CHIP.get())).isEmpty()) return;

        int count = ModAttachments.getMagicTomeUseCount(player) + 1;
        ModAttachments.setMagicTomeUseCount(player, count);
        ModAttachments.setMagicTomeLastCard(player, cardType);
        updateCountEffect(player);
        if (count >= 3) {
            ItemStack card = BaseEffectCardItem.cardByTypeId(cardType);
            // 复制的专属效果牌绑定获得者
            if (ExclusiveCardUtil.isExclusive(card)) {
                ExclusiveCardUtil.setOwner(card, player);
            }
            if (!card.isEmpty()) {
                VitaminPillChipItem.giveCard(player, card);
            }
            ModAttachments.setMagicTomeUseCount(player, 0);
            updateCountEffect(player);
        }
    }

    // 刷新计数效果:等级 = 当前计数(第几张);计数归 0 时移除效果
    public static void updateCountEffect(Player player) {
        if (player.level().isClientSide()) return;
        int count = ModAttachments.getMagicTomeUseCount(player);
        if (count <= 0) {
            ModEffectRemoval.remove(player, ModEffects.MAGIC_TOME_COUNT.get());
            return;
        }
        player.addEffect(new MobEffectInstance(ModEffects.MAGIC_TOME_COUNT.get(), 10000, count - 1, false, true, true));
    }

    // 卸下筹码(真正卸下):重置效果牌计数并移除计数效果
    @Override
    protected void onChipUnequip(Player player, ItemStack stack) {
        ModAttachments.setMagicTomeUseCount(player, 0);
        ModEffectRemoval.remove(player, ModEffects.MAGIC_TOME_COUNT.get());
    }
}
