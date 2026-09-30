package com.merlinkitsune.astral_dice.mixin.bridge;

import com.merlinkitsune.astral_dice.platform.event.LoaderBus;
import com.merlinkitsune.astral_dice.platform.event.entity.ProjectileImpactEvent;

import net.minecraft.world.entity.projectile.Projectile;
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
 * <p>⚠️ {@code ThrownEnderpearl} **覆写了 onHit 且不调 super** ⇒ 本 mixin 对末影珍珠**不触发**;
 * 珍珠那条路由 {@link EnderPearlTeleportBridgeMixin} 统一派发(含本事件)。
 * 两条注入互不重叠,不会重复派发。
 */
@Mixin(Projectile.class)
public abstract class ProjectileImpactBridgeMixin {

    @Inject(method = "onHit(Lnet/minecraft/world/phys/HitResult;)V", at = @At("HEAD"))
    private void astralDice$onProjectileImpact(HitResult hitResult, CallbackInfo ci) {
        Projectile self = (Projectile) (Object) this;
        LoaderBus.INSTANCE.post(new ProjectileImpactEvent(self, hitResult));
    }
}
