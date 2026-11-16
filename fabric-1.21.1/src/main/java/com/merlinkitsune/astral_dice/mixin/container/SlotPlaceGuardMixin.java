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
 * <h2>调用点证据(2026-11-04 实测 1.21.1 Mojmap 合并 jar 字节码)</h2>
 * <ul>
 *   <li>{@code Slot#safeInsert} 先判 {@code mayPlace}(收纳袋空槽分支即走这里);</li>
 *   <li>{@code AbstractContainerMenu#clicked} 的放置/交换分支判 {@code mayPlace};</li>
 *   <li>{@code AbstractContainerMenu#moveItemStackTo} 的**空槽**分支判 {@code mayPlace}
 *       (字节码偏移 268)。</li>
 * </ul>
 * ⚠️ 但 {@code moveItemStackTo} 的**合并**分支(字节码偏移 66-170:只调 {@code getItem} /
 * {@code isSameItemSameComponents} / {@code getMaxStackSize} / {@code setChanged})完全不查 {@code mayPlace}
 * ⇒ 光有本 mixin 挡不住「Shift 点击把临时牌并进箱子里的同类栈」,还必须搭配
 * {@link ContainerMoveGuardMixin}(两者都必须调用
 * {@link TemporaryCardUtil#isPlacementBlocked(ItemStack, boolean)} 这**唯一**判据)。
 *
 * <p>覆盖限制:1.21.1 实测共 30 个类声明 {@code mayPlace};其中**不调 super** 的只有
 * {@code ShulkerBoxSlot}(见 {@link ShulkerBoxSlotGuardMixin})。其余覆写者(如 {@code CrafterSlot})
 * 都走 {@code super.mayPlace}(实测 {@code invokespecial Slot.mayPlace})⇒ 仍被本注入点拦住。
 * 原版容器菜单的槽(箱子/木桶/漏斗/发射器/末影箱/熔炉)一律走基类;1.21.1 **没有**
 * {@code ContainerSlot}(本 jar 内无 {@code net/minecraft/world/inventory/ContainerSlot.class})。
 * 其余覆写 {@code mayPlace} 的原版槽({@code ResultSlot} / {@code FurnaceFuelSlot} /
 * {@code MerchantResultSlot} / {@code BeaconMenu} / {@code BrewingStandMenu} 的匿名槽)
 * 本来就对任意卡牌返回 {@code false},无需处理。
 * 两线覆盖面对等性说明见交付报告。
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
