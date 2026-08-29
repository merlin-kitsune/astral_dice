package com.merlinkitsune.astral_dice.item.sign;

import com.merlinkitsune.astral_dice.component.GameplayConstants;
import com.merlinkitsune.astral_dice.component.ModDataComponents;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import top.theillusivec4.curios.api.SlotContext;

import java.util.concurrent.ThreadLocalRandom;

public class PadmanSignItem extends BaseSignItem {

    public PadmanSignItem(Properties properties) {
        super(properties);
    }

    @Override
    protected void onCurioTick(SlotContext slotContext, ItemStack stack) {
        if (!(slotContext.entity() instanceof Player player)) return;
        long gameTime = player.level().getGameTime();
        long lastRefresh = stack.getOrDefault(ModDataComponents.PADMAN_LAST_REFRESH.get(), 0L);
        // 被动:每 60 秒刷新基础攻防数值(首次佩戴时立即刷新)
        if (lastRefresh == 0L || gameTime - lastRefresh >= GameplayConstants.PADMAN_REFRESH_SECONDS * 20L) {
            refreshBonus(stack);
            stack.set(ModDataComponents.PADMAN_LAST_REFRESH.get(), gameTime);
        }
    }

    @Override
    protected InteractionResultHolder<ItemStack> handleUse(Level level, Player player, ItemStack stack) {
        if (level.isClientSide) {
            return InteractionResultHolder.success(stack);
        }
        // 主动:重置被动计数器,本次攻击/防御点数取最大值(4/4)
        stack.set(ModDataComponents.PADMAN_ATK_BONUS.get(), 4);
        stack.set(ModDataComponents.PADMAN_DEF_BONUS.get(), 4);
        stack.set(ModDataComponents.PADMAN_LAST_REFRESH.get(), player.level().getGameTime());
        return InteractionResultHolder.success(stack);
    }

    @Override
    protected void clearSignData(Player player, ItemStack stack) {
        super.clearSignData(player, stack);
        // 清除攻防点数与被动刷新计时(重戴后立即刷新)
        stack.set(ModDataComponents.PADMAN_ATK_BONUS.get(), 0);
        stack.set(ModDataComponents.PADMAN_DEF_BONUS.get(), 0);
        stack.set(ModDataComponents.PADMAN_LAST_REFRESH.get(), 0L);
    }

    private static void refreshBonus(ItemStack stack) {
        // 攻防点数变动范围统一为 -2 至 4(不随星级变化)
        int min = -2;
        int max = 4;
        int atk = min + ThreadLocalRandom.current().nextInt(max - min + 1);
        int def = min + ThreadLocalRandom.current().nextInt(max - min + 1);
        stack.set(ModDataComponents.PADMAN_ATK_BONUS.get(), atk);
        stack.set(ModDataComponents.PADMAN_DEF_BONUS.get(), def);
    }

    public static int getAttackBonus(ItemStack stack) {
        return stack.getOrDefault(ModDataComponents.PADMAN_ATK_BONUS.get(), 0);
    }

    public static int getDefenseBonus(ItemStack stack) {
        return stack.getOrDefault(ModDataComponents.PADMAN_DEF_BONUS.get(), 0);
    }
}
