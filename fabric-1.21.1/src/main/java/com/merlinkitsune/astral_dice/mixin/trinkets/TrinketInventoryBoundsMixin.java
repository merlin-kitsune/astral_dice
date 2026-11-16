package com.merlinkitsune.astral_dice.mixin.trinkets;

import dev.emi.trinkets.api.TrinketInventory;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Trinkets 饰品栏「运行期缩容」越界保护。
 *
 * <p>背景:本模组的筹码栏(chip)是可动态伸缩的 —— {@code DiceCurioItem#applySlotCount} 用
 * 绝对 {@code AttributeModifier} + {@code TrinketInventory#update()} 改变栏位数量,等价于 Curios
 * 在 1.20.1/1.21.1 两线用的同一套做法。但 Trinkets 的 {@code TrinketInventory} 允许运行期改变
 * 尺寸,而**已经注入到菜单里的 Slot 仍持有旧索引**:玩家背包菜单({@code InventoryMenu})在打开时
 * 按当时的栏位数建好 Slot,缩容后这些 Slot 的索引就越界。
 *
 * <p>崩溃路径(实机 crash-report 取证 2026-11-02):
 * <pre>
 * ServerPlayer.tick -> AbstractContainerMenu.broadcastChanges -> Slot.getItem
 *   -> TrinketInventory.getItem(TrinketInventory.java:74) -> NonNullList.get
 *   -> ArrayIndexOutOfBoundsException: Index 0 out of bounds for length 0
 * </pre>
 * 同一份日志另有 {@code Index 1 out of bounds for length 1}(缩容到 1 时)⇒ 只要缩容就必崩,
 * 与具体目标值无关。
 *
 * <p>修法:对读/写/删三条容器入口做越界短路 —— 越界读返回空栈、越界写与删直接丢弃。残留的
 * 幽灵 Slot 会显示为空栏位(直到重开界面),但不再影响服务端 tick。本类不改变 Trinkets 的其它
 * 语义:任何在界内的调用都原样放行。
 */
@Mixin(TrinketInventory.class)
public abstract class TrinketInventoryBoundsMixin {

    private boolean astral$outOfRange(int index) {
        int size = ((Container) (Object) this).getContainerSize();
        return index < 0 || index >= size;
    }

    @Inject(method = "getItem", at = @At("HEAD"), cancellable = true)
    private void astral$guardGetItem(int index, CallbackInfoReturnable<ItemStack> cir) {
        if (astral$outOfRange(index)) {
            cir.setReturnValue(ItemStack.EMPTY);
        }
    }

    @Inject(method = "setItem", at = @At("HEAD"), cancellable = true)
    private void astral$guardSetItem(int index, ItemStack stack, CallbackInfo ci) {
        if (astral$outOfRange(index)) {
            ci.cancel();
        }
    }

    @Inject(method = "removeItem", at = @At("HEAD"), cancellable = true)
    private void astral$guardRemoveItem(int index, int amount, CallbackInfoReturnable<ItemStack> cir) {
        if (astral$outOfRange(index)) {
            cir.setReturnValue(ItemStack.EMPTY);
        }
    }

    @Inject(method = "removeItemNoUpdate", at = @At("HEAD"), cancellable = true)
    private void astral$guardRemoveItemNoUpdate(int index, CallbackInfoReturnable<ItemStack> cir) {
        if (astral$outOfRange(index)) {
            cir.setReturnValue(ItemStack.EMPTY);
        }
    }
}