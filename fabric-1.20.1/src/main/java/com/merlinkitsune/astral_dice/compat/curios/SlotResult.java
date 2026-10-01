package com.merlinkitsune.astral_dice.compat.curios;

import net.minecraft.world.item.ItemStack;

/** 命中结果(Curios {@code SlotResult} 的等价物)。 */
public record SlotResult(String slotId, int index, ItemStack stack) {
}
