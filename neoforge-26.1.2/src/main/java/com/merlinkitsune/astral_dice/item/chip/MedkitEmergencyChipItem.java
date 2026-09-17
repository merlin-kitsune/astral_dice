package com.merlinkitsune.astral_dice.item.chip;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import top.theillusivec4.curios.api.SlotContext;
import com.merlinkitsune.astral_dice.item.HealingManager;

/**
 * 医疗箱-紧急治疗筹码:触发骰神赐福时增加 1 点治愈(由 {@link HealingManager#onBlessingTriggered} 统一结算)。
 * 装备不再立即回血;卸下无副作用(治愈点已是玩家资源,不随装备移除)。
 */
public class MedkitEmergencyChipItem extends BaseChipItem {
    public MedkitEmergencyChipItem(Properties properties) {
        super(properties);
    }

    @Override
    protected void onChipUnequip(Player player, ItemStack stack) {
        // 卸下无副作用:不扣治愈点
    }
}
