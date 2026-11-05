package com.merlinkitsune.astral_dice.mixin.bridge;

import com.merlinkitsune.astral_dice.platform.event.LoaderBus;
import com.merlinkitsune.astral_dice.platform.event.entity.EntityTravelToDimensionEvent;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.portal.DimensionTransition;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * {@code Entity#changeDimension(DimensionTransition)} → {@link EntityTravelToDimensionEvent}(非玩家实体)。
 *
 * <p>2026-09-29 补:Fabric API 1.20.1 **没有**「实体/玩家切换维度」回调
 * ({@code ENTITY_LOAD} 无法区分「同维度重载」与「跨维度」,用它派发会给出错误的 from/to)⇒
 * 只能走 mixin。此前该事件无派发源 ⇒ {@code WarpEngineChipItem#onDimensionTravel}
 * (跃迁引擎筹码经维度门给充能)**永不执行**。
 *
 * <p>⚠️ 玩家走 {@code ServerPlayer#changeDimension}(覆写,不调 super)⇒
 * 由 {@link ServerPlayerDimensionTravelBridgeMixin} 单独注入。
 *
 * <p><b>1.21.1 签名迁移</b>: Mojang 在 1.21.1 把
 * {@code changeDimension(ServerLevel)} 改成 {@code changeDimension(DimensionTransition)}
 * (javap 实证: 1.21.1 {@code Entity} 只有
 * {@code changeDimension(Lnet/minecraft/world/level/portal/DimensionTransition;)Lnet/minecraft/world/entity/Entity;},
 * 旧的 {@code (ServerLevel)} 重载已不存在)。目的地维度改由
 * {@code transition.newLevel()} 取 —— 语义与原 {@code destination} 参数一致
 * (工厂方法 {@code DimensionTransition.(ServerLevel, ...)} 的第一个分量就是目标 {@code ServerLevel})。
 */
@Mixin(Entity.class)
public abstract class EntityDimensionTravelBridgeMixin {

    @Inject(
            method = "changeDimension(Lnet/minecraft/world/level/portal/DimensionTransition;)Lnet/minecraft/world/entity/Entity;",
            at = @At("HEAD"),
            cancellable = true)
    private void astralDice$onChangeDimension(DimensionTransition transition,
                                              CallbackInfoReturnable<Entity> cir) {
        Entity self = (Entity) (Object) this;
        ServerLevel destination = transition.newLevel();
        EntityTravelToDimensionEvent event =
                new EntityTravelToDimensionEvent(self, destination.dimension());
        LoaderBus.INSTANCE.post(event);
        if (event.isCanceled()) {
            // 对齐 Forge:取消即不传送(Forge 的 ForgeHooks.onTravelToDimension 取消时同样返回 null)
            cir.setReturnValue(null);
        }
    }
}
