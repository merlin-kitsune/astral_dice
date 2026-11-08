package com.merlinkitsune.astral_dice.item.chip;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.type.inventory.ICurioStacksHandler;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * 筹码装备判定的通用入口（与 {@code isEquipped(Player)} 家族等价，但宿主放宽到 {@link LivingEntity}）。
 *
 * <p>forge 1.20.1 的 Curios 5.x 返回 {@code LazyOptional}，因此这里用
 * {@code getStacksHandler("chip")} + 逐槽比较，而不是 1.21.1 的 {@code findCurios}。
 */
public final class ChipEquip {

    private static final String CHIP_SLOT = "chip";

    private ChipEquip() {
    }

    public static boolean isEquipped(LivingEntity entity, Supplier<? extends Item> item) {
        if (entity == null || item == null) return false;
        Item target = item.get();
        if (target == null) return false;
        for (ItemStack stack : equippedChips(entity)) {
            if (stack.is(target)) return true;
        }
        return false;
    }

    public static List<ItemStack> equippedChips(LivingEntity entity) {
        if (entity == null) return List.of();
        List<ItemStack> result = new ArrayList<>();
        CuriosApi.getCuriosInventory(entity).ifPresent(handler -> {
            ICurioStacksHandler stacks = handler.getStacksHandler(CHIP_SLOT).orElse(null);
            if (stacks == null) return;
            var inv = stacks.getStacks();
            for (int i = 0; i < inv.getSlots(); i++) {
                ItemStack stack = inv.getStackInSlot(i);
                if (!stack.isEmpty() && stack.getItem() instanceof BaseChipItem) {
                    result.add(stack);
                }
            }
        });
        return result;
    }
}
