package com.merlinkitsune.astral_dice.mixin.container;

import com.merlinkitsune.astral_dice.item.card.TemporaryCardUtil;
import net.minecraft.world.inventory.ShulkerBoxSlot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 临时牌:**潜影盒 GUI 槽守卫** —— 1.20.1 专有(1.21.1 无此 mixin)。
 *
 * <h2>为什么 {@link SlotPlaceGuardMixin} 覆盖不到这里</h2>
 * 1.20.1 实测 {@code ShulkerBoxSlot.java}:
 * <pre>
 * public boolean mayPlace(ItemStack stack) {
 *    return stack.getItem().canFitInsideContainerItems();
 * }
 * </pre>
 * 它是 {@code Slot#mayPlace} 的**覆写**,且**不调用** {@code super.mayPlace(...)}
 * ⇒ 注入在 {@code Slot#mayPlace} 上的 {@code SlotPlaceGuardMixin} 对它**完全无效**;
 * 同时 1.20.1 的 {@code Item#canFitInsideContainerItems()} 是**类型级**(实测 {@code Item.java:452},
 * 无 {@code ItemStack} 入参)⇒ 无法像 1.21.1 那样用 {@code CardItem#canFitInsideContainerItems(ItemStack)}
 * 按栈拒绝(类型级覆写会把同 id 的**永久牌**一起挡掉,违反功能对等)。
 *
 * <p>故在这里按**栈**补一道:入参栈带临时标记 ⇒ 直接返回 {@code false}
 * (潜影盒槽永远不是「玩家自己的物品栏槽」⇒ 传 {@code targetIsPlayerOwnedSlot=false})。
 * 对应 1.21.1 侧的同类保护点 = {@code ShulkerBoxSlot#mayPlace}
 * → {@code IItemStackExtension#canFitInsideContainerItems()}(1.21.1 实测
 * {@code ShulkerBoxSlot.java:17})。
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
