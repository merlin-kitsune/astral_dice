package com.merlinkitsune.astral_dice.mixin;

import com.merlinkitsune.astral_dice.platform.item.DropGuardItem;

import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * {@code Player#drop(ItemStack, boolean, boolean)} 守卫 —— 覆盖**不经 Q 键**的其它抛出路径。
 *
 * <p>对应 Forge 侧 {@code Player#drop(ItemStack, boolean, boolean)} 里的
 * {@code ForgeHooks.onPlayerTossEvent} 与 {@code event/TemporaryCardEvents} 的
 * {@code ItemTossEvent} 兜底。Forge 的兜底语义是「取消事件 + 尽力退还进物品栏」,
 * 本实现更直接:**根本不生成掉落物**(在 HEAD 返回 null),于是不存在「退还失败即销毁」
 * 的那条丢失路径 —— 语义比原实现更强,且与 {@link DropGuardItem} 的契约一致。
 */
@Mixin(Player.class)
public abstract class PlayerItemDropGuardMixin {

    // ⚠️ 这里**必须带描述符**,不能只写 "drop"。
    //    Player 有两个同名重载:
    //      ItemEntity drop(ItemStack, boolean)            // 2 参,内部只是转调 3 参版
    //      ItemEntity drop(ItemStack, boolean, boolean)   // 3 参,真正干活的那个
    //    只写纯名时 Mixin 会**逐个尝试所有重载**,遇到签名不符的那个立刻抛
    //    InvalidInjectionException ⇒ 整个 mod 的 mixin 应用失败、服务端起不来
    //    (实测 2026-09-29:「Expected (ItemStack;Z;CIR)V but found (ItemStack;ZZ;CIR)V」)。
    //    只注入 3 参版即可覆盖全部调用 —— 2 参版本身就是它的包装转发。
    @Inject(
            method = "drop(Lnet/minecraft/world/item/ItemStack;ZZ)Lnet/minecraft/world/entity/item/ItemEntity;",
            at = @At("HEAD"), cancellable = true)
    private void astralDice$refuseGuardedDrop(ItemStack stack, boolean dropAround, boolean noDelay,
                                              CallbackInfoReturnable<ItemEntity> cir) {
        Player self = (Player) (Object) this;
        if (stack.getItem() instanceof DropGuardItem guard && !guard.onDroppedByPlayer(stack, self)) {
            cir.setReturnValue(null);
        }
    }
}
