package com.merlinkitsune.astral_dice.item.chip;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import top.theillusivec4.curios.api.CuriosApi;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * 筹码装备判定的通用入口（与 {@code isEquipped(Player)} 家族等价，但宿主放宽到 {@link LivingEntity}）。
 *
 * <p>存量的 33 个 {@code isEquipped(Player)} 保持原样（避免一次性大改），
 * 新增的类玩家生物路径统一走本类；两者判据同源（Curios 的 {@code chip} 槽）。
 */
public final class ChipEquip {

    private ChipEquip() {
    }

    /** 该实体是否装备了指定筹码（按物品比较）。 */
    public static boolean isEquipped(LivingEntity entity, Supplier<? extends Item> item) {
        if (entity == null || item == null) return false;
        Item target = item.get();
        if (target == null) return false;
        return CuriosApi.getCuriosInventory(entity)
                .map(handler -> handler.findCurios(stack -> stack.is(target)).size() > 0)
                .orElse(false);
    }

    /** 该实体当前装备的全部筹码栈（只读快照，用于加成遍历）。 */
    public static List<net.minecraft.world.item.ItemStack> equippedChips(LivingEntity entity) {
        if (entity == null) return List.of();
        List<net.minecraft.world.item.ItemStack> result = new ArrayList<>();
        CuriosApi.getCuriosInventory(entity).ifPresent(handler -> {
            for (var slot : handler.findCurios(stack -> stack.getItem() instanceof BaseChipItem)) {
                result.add(slot.stack());
            }
        });
        return result;
    }
}
