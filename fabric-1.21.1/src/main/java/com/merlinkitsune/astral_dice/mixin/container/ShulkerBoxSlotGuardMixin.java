package com.merlinkitsune.astral_dice.mixin.container;

import com.merlinkitsune.astral_dice.item.card.TemporaryCardUtil;
import net.minecraft.world.inventory.ShulkerBoxSlot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 临时牌:**潜影盒 GUI 槽守卫**(目标 {@code ShulkerBoxSlot#mayPlace})。
 *
 * <h2>为什么 {@link SlotPlaceGuardMixin} 覆盖不到这里</h2>
 * 1.21.1 实测字节码({@code javap -p -c net.minecraft.world.inventory.ShulkerBoxSlot}):
 * <pre>
 * public boolean mayPlace(ItemStack stack) {
 *     return stack.getItem().canFitInsideContainerItems();
 * }
 * </pre>
 * 它是 {@code Slot#mayPlace} 的**覆写**,且**不调用** {@code super.mayPlace(...)}
 * (字节码里没有 {@code invokespecial Slot.mayPlace})⇒ 注入在 {@code Slot#mayPlace} 上的
 * {@link SlotPlaceGuardMixin} 对它**完全无效**;而 1.21.1 的
 * {@code Item#canFitInsideContainerItems()} 仍是**类型级**(实测 descriptor {@code ()Z},无
 * {@code ItemStack} 入参;{@code CardItem} 继承的缺省实现恒 {@code true})⇒ 潜影盒槽不会挡临时牌。
 *
 * <p>NeoForge 1.21.1 之所以不需要这道守卫,是因为它把 {@code mayPlace} 里的调用点改成了**栈级**
 * ({@code ItemStack#canFitInsideContainerItems()},由 NeoForge 打在 {@code Item} 上的补丁方法提供)。
 * Fabric 侧没有这个补丁 ⇒ 必须由本 mixin 按**栈**补一道:入参栈带临时标记 ⇒ 返回 {@code false}
 * (潜影盒槽永远不是「玩家自己的物品栏槽」⇒ 传 {@code targetIsPlayerOwnedSlot=false})。
 *
 * <p>判据仍走唯一入口 {@link TemporaryCardUtil#isPlacementBlocked(ItemStack, boolean)}。
 */
@Mixin(ShulkerBoxSlot.class)
public abstract class ShulkerBoxSlotGuardMixin {

    @Inject(method = "mayPlace", at = @At("HEAD"), cancellable = true)
    private void astralDice$blockTemporaryCardInShulkerSlot(ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
        if (stack == null || stack.isEmpty()) return;
        if (TemporaryCardUtil.isPlacementBlocked(stack, false)) {
            cir.setReturnValue(false);
        }
    }
}
