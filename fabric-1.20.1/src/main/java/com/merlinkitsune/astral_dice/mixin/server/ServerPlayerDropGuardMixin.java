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

    // ⚠️ method 必须写**纯方法名**（不带描述符）：Loom 在 remapJar 阶段只重映射纯名
    //    （实测：`method_7914` = getMaxStackSize 被正确重映射，而 `drop(Z)Lnet/...ItemEntity;`
    //    原样保留 ⇒ 生产 intermediary 环境下找不到该方法、注入失败、defaultRequire=1 直接崩）。
    //    ServerPlayer 只声明一个 drop，纯名无歧义。
    @Inject(method = "drop", at = @At("HEAD"), cancellable = true)
    private void astralDice$refuseGuardedDrop(boolean fullStack, CallbackInfoReturnable<net.minecraft.world.entity.item.ItemEntity> cir) {
        ServerPlayer self = (ServerPlayer) (Object) this;
        ItemStack selected = self.getInventory().getSelected();
        if (selected.getItem() instanceof DropGuardItem guard && !guard.onDroppedByPlayer(selected, self)) {
            cir.setReturnValue(null);
        }
    }
}
