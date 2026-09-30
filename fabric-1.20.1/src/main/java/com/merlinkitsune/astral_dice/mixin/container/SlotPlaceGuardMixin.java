package com.merlinkitsune.astral_dice.mixin.container;

import com.merlinkitsune.astral_dice.item.card.TemporaryCardPermissiveSlot;
import com.merlinkitsune.astral_dice.item.card.TemporaryCardUtil;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 临时牌:**槽位放置守卫**(目标 {@link Slot#mayPlace})。
 *
 * <p>需求「需严格保持临时牌只能在物品栏和手中,block 所有移至其他容器的请求」。
 * 本 mixin 在 {@code mayPlace} 的 HEAD 处判定:入参栈是临时牌、且该槽**不是**
 * 「玩家自己的物品栏槽」或「本模组卡牌栏槽」⇒ 直接返回 {@code false}。
 *
 * <h2>1.20.1 调用点证据</h2>
 * <ul>
 *   <li>{@code Slot#safeInsert}(实测 {@code Slot.java:179-184})先判 {@code mayPlace};</li>
 *   <li>{@code AbstractContainerMenu#doClick} 的放置/交换分支判 {@code mayPlace}
 *       (实测 {@code :436,469,478});</li>
 *   <li>{@code AbstractContainerMenu#moveItemStackTo} 的**空槽**分支判 {@code mayPlace}
 *       (实测 {@code :690})。</li>
 * </ul>
 * ⚠️ 但 {@code moveItemStackTo} 的**合并**分支(1.20.1 实测 {@code :631-687})完全不查
 * {@code mayPlace} ⇒ 光有本 mixin 挡不住「Shift 点击把临时牌并进箱子里的同类栈」,还必须搭配
 * {@link ContainerMoveGuardMixin}(两者都必须调用
 * {@link TemporaryCardUtil#isPlacementBlocked(ItemStack, boolean)} 这**唯一**判据)。
 *
 * <p>⚠️ 1.20.1 特有限制:本 mixin 只覆盖**未覆写** {@code mayPlace} 的 {@code Slot} 实现
 * (箱子/木桶/漏斗/发射器/末影箱/熔炉等原版容器菜单,以及 {@code ContainerSlot} 不存在于 1.20.1
 * ⇒ 原版容器槽一律走基类)。**覆写** {@code mayPlace} 的槽不会经过本注入点
 * —— 1.20.1 逐个处置见 {@code ShulkerBoxSlotGuardMixin}(潜影盒 GUI 槽)、
 * {@code ShulkerBoxBlockEntityGuardMixin}(潜影盒漏斗面)、{@code BundleInsertGuardMixin}(收纳袋);
 * 其余原版覆写者({@code ResultSlot} / {@code FurnaceFuelSlot} / {@code MerchantResultSlot} /
 * {@code BeaconMenu} / {@code BrewingStandMenu} 的匿名槽)**本来就对任意卡牌返回 false**,
 * 无需处理。1.20.1 与 1.21.1 的覆盖面对等性说明见交付报告。
 */
@Mixin(Slot.class)
public abstract class SlotPlaceGuardMixin {

    @Inject(method = "mayPlace", at = @At("HEAD"), cancellable = true)
    private void astralDice$blockTemporaryCardPlacement(ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
        if (stack == null || stack.isEmpty()) return;
        if (!TemporaryCardUtil.isTemporary(stack)) return;
        // 该槽是否"允许临时牌":玩家自己的物品栏槽,或本模组卡牌栏槽(标记接口,不用容器类型猜)
        boolean playerOwnedOrPermissive =
                (((Slot) (Object) this).container instanceof Inventory)
                        || ((Object) this instanceof TemporaryCardPermissiveSlot);
        if (TemporaryCardUtil.isPlacementBlocked(stack, playerOwnedOrPermissive)) {
            cir.setReturnValue(false);
        }
    }
}
