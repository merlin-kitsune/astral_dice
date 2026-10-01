package com.merlinkitsune.astral_dice.platform.item;

import net.minecraft.world.item.ItemStack;

/**
 * 「按物品栈决定堆叠上限」契约 —— 替代 Forge/NeoForge 给 {@code Item} 打的
 * {@code getMaxStackSize(ItemStack)} 补丁方法。
 *
 * <h2>为什么必须自建</h2>
 * 原版 1.20.1 的 {@code Item#getMaxStackSize()} **无参**（类型级，javap 实证），
 * 而本模组的战斗牌要「未消耗耐久时可堆 64、消耗过耐久后只能单张」—— 这是**栈级**语义，
 * 原版给不了。Forge 的做法是加一个 stack-aware 重载；Fabric API 的 {@code FabricItem}
 * 在 1.20.1 **也没有**这个重载（实测只有 allowNbtUpdateAnimation / allowContinuingBlockBreaking /
 * getAttributeModifiers / isSuitableFor / getRecipeRemainder）。
 *
 * ⇒ 本模组自建契约 + 一个极小 mixin({@code mixin/ItemStackMaxCountMixin})注入
 * {@code ItemStack#getMaxStackSize()} 的 HEAD。
 */
public interface StackCountOverrideItem {

    /** 返回本栈的堆叠上限（须 ≥1）。 */
    int maxStackSize(ItemStack stack);
}
