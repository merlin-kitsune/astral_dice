package com.merlinkitsune.astral_dice.mixin.bridge;

import com.merlinkitsune.astral_dice.platform.event.LoaderBus;
import com.merlinkitsune.astral_dice.platform.event.entity.EntityTeleportEvent;
import com.merlinkitsune.astral_dice.platform.event.entity.ProjectileImpactEvent;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.projectile.ThrownEnderpearl;
import net.minecraft.world.phys.HitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * {@code ThrownEnderpearl#onHit(HitResult)} → {@link ProjectileImpactEvent} + {@link EntityTeleportEvent.EnderPearl}。
 *
 * <p>2026-09-29 补:珍珠落地这条路径在 Forge 上会触发**两个**事件,而 Fabric 侧此前一个都没有 ⇒
 * <ul>
 *   <li>{@code NancyLuSignItem#onNancyLuEnderPearlImpact}(记录免疫窗口)不执行;</li>
 *   <li>{@code WarpEngineChipItem#onEnderPearlTeleport}(跃迁引擎筹码给充能)不执行。</li>
 * </ul>
 *
 * <p><b>为何在这里同时派发 {@code ProjectileImpactEvent}</b>:
 * {@code ThrownEnderpearl} 覆写了 {@code onHit} 且**不调用 super**(javap 实证)⇒
 * {@link ProjectileImpactBridgeMixin} 注入的 {@code Projectile#onHit} 对珍珠**不会执行**。
 * 两者目标类互斥,故不会重复派发。
 *
 * <p><b>取消语义</b>:{@code EntityTeleportEvent.EnderPearl} 可取消(Forge 据此阻止传送);
 * {@code ci.cancel()} 会让珍珠不完成落地结算(不传送、不破碎)。
 */
@Mixin(ThrownEnderpearl.class)
public abstract class EnderPearlTeleportBridgeMixin {

    @Inject(
            method = "onHit(Lnet/minecraft/world/phys/HitResult;)V",
            at = @At("HEAD"),
            cancellable = true)
    private void astralDice$onEnderPearlHit(HitResult hitResult, CallbackInfo ci) {
        ThrownEnderpearl self = (ThrownEnderpearl) (Object) this;

        // 先派发投射物命中(两端都要:免疫窗口是服务端附件,客户端不涉及)
        LoaderBus.INSTANCE.post(new ProjectileImpactEvent(self, hitResult));

        if (self.level().isClientSide()) {
            return;
        }
        if (!(self.getOwner() instanceof ServerPlayer player)) {
            return;
        }
        // targetXYZ 取珍珠当前位置 —— Forge 的 EnderPearl 事件由调用方按传送目标计算,
        // 而本模组的消费方(WarpEngineChipItem)只读 getPlayer()/isCanceled(),
        // 位置仅作事件数据的完整性填充,不参与判定。
        EntityTeleportEvent.EnderPearl event = new EntityTeleportEvent.EnderPearl(
                player, self.getX(), self.getY(), self.getZ(), self, 0.0F, hitResult);
        LoaderBus.INSTANCE.post(event);
        if (event.isCanceled()) {
            ci.cancel();
        }
    }
}
