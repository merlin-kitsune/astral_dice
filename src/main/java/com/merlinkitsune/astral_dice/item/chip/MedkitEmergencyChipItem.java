package com.merlinkitsune.astral_dice.item.chip;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import top.theillusivec4.curios.api.SlotContext;
import com.merlinkitsune.astral_dice.item.HealingManager;

/**
 * 医疗箱-紧急治疗筹码:提供 1 点治愈基础点(下限)。基础点由 {@link HealingManager#getBasePoints}
 * 实时计算,装备期间生效、卸下立即移除;装备时若玩家从"无治愈"变为"有治愈",由
 * {@link HealingManager#onBasePointsChanged} 触发立即回血并启动倒计时。
 */
public class MedkitEmergencyChipItem extends BaseChipItem {
    public MedkitEmergencyChipItem(Properties properties) {
        super(properties);
    }

    @Override
    public void onEquip(SlotContext slotContext, ItemStack curio, ItemStack prevStack) {
        if (!(slotContext.entity() instanceof Player player)) return;
        if (player.level().isClientSide()) return;
        if (!prevStack.isEmpty()) return;
        HealingManager.onBasePointsChanged(player);
    }

    @Override
    protected void onChipUnequip(Player player, ItemStack stack) {
        HealingManager.onBasePointsChanged(player);
    }
}
