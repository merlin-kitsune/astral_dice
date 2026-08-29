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
        long lastRefresh = ModDataComponents.PADMAN_LAST_REFRESH.getOrDefault(stack,  0L);
        // 被动:每 60 秒刷新基础攻防数值(首次佩戴时立即刷新)
        if (lastRefresh == 0L || gameTime - lastRefresh >= GameplayConstants.PADMAN_REFRESH_SECONDS * 20L) {
            refreshBonus(stack);
            ModDataComponents.PADMAN_LAST_REFRESH.set(stack,  gameTime);
        }
    }

    @Override
    protected InteractionResultHolder<ItemStack> handleUse(Level level, Player player, ItemStack stack) {
        if (level.isClientSide) {
            return InteractionResultHolder.success(stack);
        }
        // 主动:重置被动计数器,本次攻击/防御点数取最大值(4/4)
        ModDataComponents.PADMAN_ATK_BONUS.set(stack,  4);
        ModDataComponents.PADMAN_DEF_BONUS.set(stack,  4);
        ModDataComponents.PADMAN_LAST_REFRESH.set(stack,  player.level().getGameTime());
        return InteractionResultHolder.success(stack);
    }

    @Override
    protected void clearSignData(Player player, ItemStack stack) {
        super.clearSignData(player, stack);
        // 清除攻防点数与被动刷新计时(重戴后立即刷新)
        ModDataComponents.PADMAN_ATK_BONUS.set(stack,  0);
        ModDataComponents.PADMAN_DEF_BONUS.set(stack,  0);
        ModDataComponents.PADMAN_LAST_REFRESH.set(stack,  0L);
    }

    private static void refreshBonus(ItemStack stack) {
        // 攻防点数变动范围统一为 -2 至 4(不随星级变化)
        int min = -2;
        int max = 4;
        int atk = min + ThreadLocalRandom.current().nextInt(max - min + 1);
        int def = min + ThreadLocalRandom.current().nextInt(max - min + 1);
        ModDataComponents.PADMAN_ATK_BONUS.set(stack,  atk);
        ModDataComponents.PADMAN_DEF_BONUS.set(stack,  def);
    }

    public static int getAttackBonus(ItemStack stack) {
        return ModDataComponents.PADMAN_ATK_BONUS.getOrDefault(stack,  0);
    }

    public static int getDefenseBonus(ItemStack stack) {
        return ModDataComponents.PADMAN_DEF_BONUS.getOrDefault(stack,  0);
    }
}
