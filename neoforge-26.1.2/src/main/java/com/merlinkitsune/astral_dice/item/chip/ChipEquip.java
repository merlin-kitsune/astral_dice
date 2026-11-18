package com.merlinkitsune.astral_dice.item.chip;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import top.theillusivec4.curios.api.CuriosApi;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * 筹码装备判定的通用入口（与 {@code isEquipped(Player)} 家族等价，但宿主放宽到 {@link LivingEntity}）。
 *
 * <p>存量的 {@code isEquipped(Player)} 保持原样（避免一次性大改），
 * 新增的类玩家生物路径统一走本类；两者判据同源（Curios 的 {@code chip} 槽）。
 */
public final class ChipEquip {

    private static final String CHIP_SLOT = "chip";

    private ChipEquip() {
    }

    /** 该实体是否装备了指定筹码（按物品比较）。 */
    public static boolean isEquipped(LivingEntity entity, Supplier<? extends Item> item) {
        if (entity == null || item == null) return false;
        Item target = item.get();
        if (target == null) return false;
        for (ItemStack stack : equippedChips(entity)) {
            if (stack.is(target)) return true;
        }
        return false;
    }

    /** 该实体当前装备的全部筹码栈（只读快照，用于加成遍历）。 */
    public static List<ItemStack> equippedChips(LivingEntity entity) {
        if (entity == null) return List.of();
        List<ItemStack> result = new ArrayList<>();
        CuriosApi.getCuriosInventory(entity).ifPresent(handler -> handler.getStacksHandler(CHIP_SLOT).ifPresent(chip -> {
            for (int i = 0; i < chip.getStacks().getSlots(); i++) {
                ItemStack stack = chip.getStacks().getStackInSlot(i);
                if (stack.getItem() instanceof BaseChipItem) {
                    result.add(stack);
                }
            }
        }));
        return result;
    }
}
