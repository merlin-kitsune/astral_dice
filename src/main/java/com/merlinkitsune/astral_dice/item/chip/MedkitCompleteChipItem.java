package com.merlinkitsune.astral_dice.item.chip;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import top.theillusivec4.curios.api.SlotContext;
import com.merlinkitsune.astral_dice.item.HealingManager;

/**
 * 医疗箱-完备治疗筹码:装备时立即恢复 6 点生命(1 治愈单位 = 2 点血量);
 * 触发骰神赐福时增加 3 点治愈(由 {@link HealingManager#onBlessingTriggered} 统一结算)。
 * 卸下无副作用(治愈点已是玩家资源,不随装备移除)。
 */
public class MedkitCompleteChipItem extends BaseChipItem {
    public MedkitCompleteChipItem(Properties properties) {
        super(properties);
    }

    @Override
    public void onEquip(SlotContext slotContext, ItemStack curio, ItemStack prevStack) {
        if (!(slotContext.entity() instanceof Player player)) return;
        if (player.level().isClientSide()) return;
        if (!prevStack.isEmpty()) return;
        HealingManager.onMedkitEquipped(player, HealingManager.MEDKIT_COMPLETE_HEAL);
    }

    @Override
    protected void onChipUnequip(Player player, ItemStack stack) {
        // 卸下无副作用:不扣治愈点
    }
}
