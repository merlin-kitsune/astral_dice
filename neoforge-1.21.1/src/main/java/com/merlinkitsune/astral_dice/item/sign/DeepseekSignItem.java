package com.merlinkitsune.astral_dice.item.sign;

import com.merlinkitsune.astral_dice.component.ModAttachments;
import com.merlinkitsune.astral_dice.effect.ModEffects;
import com.merlinkitsune.astral_dice.item.ModItems;
import com.merlinkitsune.astral_dice.shop.CardTradeFilter;
import com.merlinkitsune.starengine.economy.StarEngineEconomy;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import top.theillusivec4.curios.api.SlotContext;

import java.util.ArrayList;
import java.util.List;
import com.merlinkitsune.astral_dice.event.PlayerEffectProtection;

/**
 * 大肥鱼立牌（deepseek，奇特，仅创造获得）。
 *
 * <p><b>被动「摸鱼中」</b>：仅按位置判定 —— 位置 10 秒不变 ⇒ 获得 {@code deepseek_fishing}
 * （免疫伤害/负面效果、不被怪物索敌、半透明、HUD 指示器）；一旦移动立即清除。
 *
 * <p><b>主动「大口干饭」</b>：立即 +100 星币，并从商店可售池随机给 1 个立牌或筹码。
 */
public class DeepseekSignItem extends BaseSignItem {
    private static final long IDLE_TICKS = 200L;
    private static final int REWARD_COINS = 100;

    public DeepseekSignItem(Properties properties) {
        super(properties);
    }

    @Override
    protected void onCurioTick(SlotContext slotContext, ItemStack stack) {
        if (!(slotContext.entity() instanceof Player player)) return;
        Level level = player.level();
        if (level.isClientSide()) return;
        long now = level.getGameTime();
        long anchor = ModAttachments.getDeepseekAnchor(player);
        BlockPos pos = player.blockPosition();
        long packed = pos.asLong();
        if (anchor == 0L) {
            ModAttachments.setDeepseekAnchor(player, packed);
            ModAttachments.setDeepseekIdleSince(player, now);
            removeFishing(player);
            return;
        }
        if (anchor != packed) {
            ModAttachments.setDeepseekAnchor(player, packed);
            ModAttachments.setDeepseekIdleSince(player, now);
            removeFishing(player);
            return;
        }
        long since = ModAttachments.getDeepseekIdleSince(player);
        if (since == 0L) {
            ModAttachments.setDeepseekIdleSince(player, now);
            return;
        }
        if (now - since >= IDLE_TICKS) {
            PlayerEffectProtection.apply(player, new MobEffectInstance(ModEffects.DEEPSEEK_FISHING, (int) IDLE_TICKS + 20, 0,
                    false, true, true));
        }
    }

    private static void removeFishing(Player player) {
        com.merlinkitsune.starengine.event.ModEffectRemoval.remove(player, ModEffects.DEEPSEEK_FISHING);
    }

    @Override
    protected InteractionResultHolder<ItemStack> handleUse(Level level, Player player, ItemStack stack) {
        if (!level.isClientSide) {
            StarEngineEconomy.deposit(player, REWARD_COINS);
            ItemStack reward = randomReward(player);
            if (reward != null) {
                if (!player.getInventory().add(reward)) player.drop(reward, false);
            }
            sendSignActionBar(player, "msg.astral_dice.deepseek_done");
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    /** 商店可售池里随机取一个立牌或筹码（立牌 / 筹码各 50%）。 */
    private static ItemStack randomReward(Player player) {
        List<Item> signs = new ArrayList<>();
        List<Item> chips = new ArrayList<>();
        for (var holder : ModItems.ITEMS.getEntries()) {
            Item item = holder.get();
            if (!CardTradeFilter.isSellable(item)) continue;
            String path = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(item).getPath();
            if (path.endsWith("_sign")) signs.add(item);
            else if (path.endsWith("_chip")) chips.add(item);
        }
        boolean pickSign = !signs.isEmpty() && (chips.isEmpty() || player.getRandom().nextBoolean());
        List<Item> pool = pickSign ? signs : chips;
        if (pool.isEmpty()) return null;
        return new ItemStack(pool.get(player.getRandom().nextInt(pool.size())));
    }

    @Override
    public void onUnequip(SlotContext slotContext, ItemStack newStack, ItemStack stack) {
        if (slotContext.entity() instanceof Player player) {
            removeFishing(player);
            ModAttachments.setDeepseekAnchor(player, 0L);
            ModAttachments.setDeepseekIdleSince(player, 0L);
        }
        super.onUnequip(slotContext, newStack, stack);
    }
}
