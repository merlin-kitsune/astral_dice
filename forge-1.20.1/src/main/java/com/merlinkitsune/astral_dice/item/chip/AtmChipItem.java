package com.merlinkitsune.astral_dice.item.chip;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import com.merlinkitsune.astral_dice.item.CuriosCompat;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.SlotContext;
import com.merlinkitsune.astral_dice.resource.ResourceConversion;
import com.merlinkitsune.astral_dice.item.ModItems;
import com.merlinkitsune.astral_dice.item.StarLightManager;

/**
 * ATM机筹码:装备时获得 1 点星光(一次性);
 * 使用星光兑换星币时,兑换量(星币产出)增加 40%(结算在 {@link com.merlinkitsune.astral_dice.resource.ResourceConversion})。
 */
public class AtmChipItem extends BaseChipItem {
    public AtmChipItem(Properties properties) {
        super(properties);
    }

    // 玩家是否佩戴 ATM机筹码
    public static boolean isEquipped(Player player) {
        if (player == null) return false;
        var curios = CuriosCompat.getCuriosInventory(player);
        return curios.isPresent() && curios.get().findFirstCurio(s -> s.is(ModItems.ATM.get())).isPresent();
    }

    @Override
    public void onEquip(SlotContext slotContext, ItemStack curio, ItemStack prevStack) {
        if (!(slotContext.entity() instanceof Player player)) return;
        if (player.level().isClientSide()) return;
        if (!prevStack.isEmpty()) return;
        // 装备时星光 +1(上限由 StarLightManager 统一管理)
        StarLightManager.add(player, 1);
    }
}
