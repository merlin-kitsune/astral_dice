package com.merlinkitsune.astral_dice.compat.curios;

import net.minecraft.world.item.ItemStack;

/**
 * 饰品物品契约(Fabric 侧适配) —— 形状对齐 Curios 5.x 的
 * {@code top.theillusivec4.curios.api.type.capability.ICurioItem}。
 *
 * <h2>与 Trinkets 的回调映射(逐条给出,含易错点)</h2>
 * <table border="1">
 *   <caption>Curios → Trinkets</caption>
 *   <tr><th>本接口</th><th>Trinkets {@code Trinket}</th><th>映射说明</th></tr>
 *   <tr><td>{@code curioTick(ctx, stack)}</td><td>{@code tick(stack, ref, entity)}</td><td>1:1</td></tr>
 *   <tr><td>{@code onEquip(ctx, prevStack, stack)}</td><td>{@code onEquip(stack, ref, entity)}</td>
 *       <td>⚠️ Curios 第 2 参 = **槽位原内容**(普通装备时为空栈)、第 3 参 = 刚装上的物品。
 *           Trinkets 只给「刚装上的物品」⇒ 映射时 **prevStack 传 {@link ItemStack#EMPTY}**。
 *           本模组的方法体正是按此语义写的(见 {@code DiceCurioItem.onEquip} 的注释)。</td></tr>
 *   <tr><td>{@code onUnequip(ctx, newStack, stack)}</td><td>{@code onUnequip(stack, ref, entity)}</td>
 *       <td>⚠️ Curios 第 2 参 = 将要占用槽位的栈(真实卸下时为 EMPTY、换装时为新物品)、
 *           第 3 参 = **被卸下的那件**。Trinkets 只给被卸下的那件 ⇒ 映射时
 *           **newStack 传 {@link ItemStack#EMPTY}**、stack 传 Trinkets 给的栈。
 *           ⇒ {@code CurioSlotUtil.isIntentionalUnequip} 的判据退化为「newStack 为空 ⇒ 真实移除」,
 *           **与 Curios 侧语义等价**(换上别的物品在 Trinkets 下也表现为一次空栈卸载 + 一次新装备)。</td></tr>
 *   <tr><td>{@code canEquip(ctx, stack)}</td><td>{@code canEquip(stack, ref, entity)}</td><td>1:1</td></tr>
 *   <tr><td>{@code canUnequip(ctx, stack)}</td><td>{@code canUnequip(stack, ref, entity)}</td><td>1:1</td></tr>
 * </table>
 */
public interface ICurioItem {

    default void curioTick(SlotContext slotContext, ItemStack stack) {
    }

    default void onEquip(SlotContext slotContext, ItemStack prevStack, ItemStack stack) {
    }

    default void onUnequip(SlotContext slotContext, ItemStack newStack, ItemStack stack) {
    }

    default boolean canEquip(SlotContext slotContext, ItemStack stack) {
        return true;
    }

    default boolean canUnequip(SlotContext slotContext, ItemStack stack) {
        return true;
    }
}
