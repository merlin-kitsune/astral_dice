package com.merlinkitsune.astral_dice.mixin.bridge;

import com.merlinkitsune.astral_dice.platform.event.LoaderBus;
import com.merlinkitsune.astral_dice.platform.event.entity.ProjectileImpactEvent;

import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ThrownEnderpearl;
import net.minecraft.world.phys.HitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * {@code Projectile#onHit(HitResult)} → {@link ProjectileImpactEvent}。
 *
 * <p>2026-09-29 补:该事件此前**无任何派发源** ⇒
 * {@code NancyLuSignItem#onNancyLuEnderPearlImpact}(末影珍珠落地时的免疫窗口)**永不执行**。
 *
 * <p>注入点选用 {@code onHit} 而非 {@code onHitEntity} / {@code onHitBlock}:
 * Forge 的 {@code ProjectileImpactEvent} 同样位于 {@code onHit}(命中物/方块/其它 三路汇聚点)。
 *
 * <p>⚠️ <b>末影珍珠必须在这里被跳过</b>。{@code ThrownEnderpearl#onHit} 的字节码<b>偏移 2</b> 就是
 * {@code invokespecial super.onHit(HitResult)};而 {@code ThrowableItemProjectile} / {@code ThrowableProjectile}
 * 都<b>没有</b>声明 onHit(javap 计数 0)⇒ 该 super 调用实际解析到本 mixin 注入的
 * {@code Projectile#onHit},于是珍珠落地会**两次**派发 {@code ProjectileImpactEvent}
 * (被 {@code EntityTeleportEvent.EnderPearl} 取消时只派发一次 —— 次数随路径变化)。
 * ⇒ 珍珠那条路由 {@link EnderPearlTeleportBridgeMixin} 单独派发(含取消路径),本 mixin 显式 return。
 */
@Mixin(Projectile.class)
public abstract class ProjectileImpactBridgeMixin {

    @Inject(method = "onHit(Lnet/minecraft/world/phys/HitResult;)V", at = @At("HEAD"))
    private void astralDice$onProjectileImpact(HitResult hitResult, CallbackInfo ci) {
        Projectile self = (Projectile) (Object) this;
        // 末影珍珠的 super.onHit 会落到这里(见类注释),它由 EnderPearlTeleportBridgeMixin
        // 无条件派发 ⇒ 这里跳过,避免同一次落地被派发两次。
        if (self instanceof ThrownEnderpearl) {
            return;
        }
        LoaderBus.INSTANCE.post(new ProjectileImpactEvent(self, hitResult));
    }
}
