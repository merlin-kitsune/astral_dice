package com.merlinkitsune.astral_dice.mixin.bridge;

import com.merlinkitsune.astral_dice.platform.event.LoaderBus;
import com.merlinkitsune.astral_dice.platform.event.entity.EntityTravelToDimensionEvent;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * {@code ServerPlayer#changeDimension(ServerLevel)} → {@link EntityTravelToDimensionEvent}(玩家)。
 *
 * <p>与 {@link EntityDimensionTravelBridgeMixin} 成对:javap 实证 {@code ServerPlayer}
 * **覆写了** {@code changeDimension} 且不调 super ⇒ 只注入 {@code Entity} 覆盖不到玩家,
 * 而玩家恰恰是 {@code WarpEngineChipItem#onDimensionTravel} 的主要关注对象。
 */
@Mixin(ServerPlayer.class)
public abstract class ServerPlayerDimensionTravelBridgeMixin {

    @Inject(
            method = "changeDimension(Lnet/minecraft/server/level/ServerLevel;)Lnet/minecraft/world/entity/Entity;",
            at = @At("HEAD"),
            cancellable = true)
    private void astralDice$onPlayerChangeDimension(ServerLevel destination,
                                                    CallbackInfoReturnable<Entity> cir) {
        ServerPlayer self = (ServerPlayer) (Object) this;
        EntityTravelToDimensionEvent event =
                new EntityTravelToDimensionEvent(self, destination.dimension());
        LoaderBus.INSTANCE.post(event);
        if (event.isCanceled()) {
            cir.setReturnValue(null);
        }
    }
}
