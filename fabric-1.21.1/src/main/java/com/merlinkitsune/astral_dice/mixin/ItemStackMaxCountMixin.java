package com.merlinkitsune.astral_dice.mixin;

import com.merlinkitsune.astral_dice.platform.item.StackCountOverrideItem;

import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 栈级堆叠上限 —— Forge 的 {@code Item#getMaxStackSize(ItemStack)} 补丁的 Fabric 等价物。
 *
 * <p>vanilla 的 {@code ItemStack#getMaxStackSize()} 只是转发 {@code item.getMaxStackSize()}
 * （类型级）。Forge 把它改成把自身传下去，于是物品能按栈决定上限。本 mixin 在 HEAD 处
 * 对实现了 {@link StackCountOverrideItem} 的物品复刻该行为；其余物品**完全不受影响**
 * （不改返回值 ⇒ 原版路径字节码不变）。
 */
@Mixin(ItemStack.class)
public abstract class ItemStackMaxCountMixin {

    @Inject(method = "getMaxStackSize", at = @At("HEAD"), cancellable = true)
    private void astralDice$stackAwareMaxStackSize(CallbackInfoReturnable<Integer> cir) {
        ItemStack self = (ItemStack) (Object) this;
        if (self.getItem() instanceof StackCountOverrideItem override) {
            cir.setReturnValue(Math.max(1, override.maxStackSize(self)));
        }
    }
}
