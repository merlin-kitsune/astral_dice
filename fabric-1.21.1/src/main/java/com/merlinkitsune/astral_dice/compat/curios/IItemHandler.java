package com.merlinkitsune.astral_dice.compat.curios;

import net.minecraft.world.item.ItemStack;

/** 槽位内容读写(Curios 侧为 NeoForge 的 {@code IItemHandler} 子集)。 */
public interface IItemHandler {
    int getSlots();

    ItemStack getStackInSlot(int slot);

    void setStackInSlot(int slot, ItemStack stack);
}
