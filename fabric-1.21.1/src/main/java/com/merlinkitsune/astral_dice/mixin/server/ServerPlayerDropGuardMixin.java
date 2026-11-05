package com.merlinkitsune.astral_dice.mixin.server;

import com.merlinkitsune.astral_dice.platform.item.DropGuardItem;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 服务端 Q 键丢弃守卫 —— Forge 在 {@code ServerPlayer#drop(boolean)} 里插入的
 * {@code selected.onDroppedByPlayer(this)} 判定的 Fabric 等价物。
 *
 * <p><b>为什么必须在 HEAD 而不是更里面</b>:vanilla 的 {@code ServerPlayer#drop(boolean)}
 * 第一步就是 {@code inventory.removeFromSelected(...)} —— 一旦执行,物品**已经离手**,
 * 此时再拒绝只会让它「凭空消失」(客户端 {@code remoteSlots} 与服务端仍相等 ⇒
 * 永不补发 {@code ClientboundContainerSetSlotPacket})。所以在 HEAD 拦、
 * 直接返回 {@code null}(不产生 {@code ItemEntity})。
 */
@Mixin(ServerPlayer.class)
public abstract class ServerPlayerDropGuardMixin {

    // ⚠️ ServerPlayer **继承** Player,所以它同样能看到 drop 的多个重载
    //    (自己的 drop(boolean) + 继承来的 drop(ItemStack,boolean) / drop(ItemStack,boolean,boolean))
    //    ⇒ 只写纯名 "drop" 会被 Mixin 判定为歧义并与签名不符的那个相撞而失败
    //    (实测 2026-09-29)。这里写**完整描述符**锁定 ServerPlayer 自己的那个。
    // ⚠️ 返回类型是 **boolean**(「是否真的丢出去了」),不是 ItemEntity ——
    //    javap 实证:`public boolean drop(boolean)`。写 ItemEntity 会得到
    //    「could not find any targets matching 'drop(Z)L...ItemEntity;'」。
    @Inject(method = "drop(Z)Z", at = @At("HEAD"), cancellable = true)
    private void astralDice$refuseGuardedDrop(boolean fullStack, CallbackInfoReturnable<Boolean> cir) {
        ServerPlayer self = (ServerPlayer) (Object) this;
        ItemStack selected = self.getInventory().getSelected();
        if (selected.getItem() instanceof DropGuardItem guard && !guard.onDroppedByPlayer(selected, self)) {
            cir.setReturnValue(false);
        }
    }
}
