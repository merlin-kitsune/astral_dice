package com.merlinkitsune.astral_dice.mixin.bridge;

import com.merlinkitsune.astral_dice.platform.event.LoaderBus;
import com.merlinkitsune.astral_dice.platform.event.entity.EntityTravelToDimensionEvent;
import com.merlinkitsune.astral_dice.platform.event.entity.player.PlayerEvent;

import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * {@code ServerPlayer#changeDimension(ServerLevel)} → {@link EntityTravelToDimensionEvent}(玩家,
 * **切换之前**,可取消)+ {@link PlayerEvent.PlayerChangedDimensionEvent}(**切换之后**)。
 *
 * <p>与 {@link EntityDimensionTravelBridgeMixin} 成对:javap 实证 {@code ServerPlayer}
 * **覆写了** {@code changeDimension} 且不调 super ⇒ 只注入 {@code Entity} 覆盖不到玩家,
 * 而玩家恰恰是 {@code WarpEngineChipItem#onDimensionTravel} 的主要关注对象。
 *
 * <p>2026-10-01 追加:同一方法上补一个 {@code @At("RETURN")} 注入派发
 * {@code PlayerChangedDimensionEvent} —— 该事件此前在本线**没有任何订阅者**,故按
 * {@code FabricBridges#installPlayerLifecycle} 当时留下的口径「需要时在对应 mixin 的
 * {@code @At("RETURN")} 处补一行即可」补上。触发方是医疗箱筹码的「切换维度后完整触发一次治愈」
 * (见 {@code HealingManager#triggerMedkitOnEquip})。
 */
@Mixin(ServerPlayer.class)
public abstract class ServerPlayerDimensionTravelBridgeMixin {

    /** HEAD 处记下的出发维度,供 RETURN 处派发 {@code PlayerChangedDimensionEvent}。 */
    @Unique
    private ResourceKey<Level> astralDice$dimensionOrigin;

    @Inject(
            method = "changeDimension(Lnet/minecraft/server/level/ServerLevel;)Lnet/minecraft/world/entity/Entity;",
            at = @At("HEAD"),
            cancellable = true)
    private void astralDice$onPlayerChangeDimension(ServerLevel destination,
                                                    CallbackInfoReturnable<Entity> cir) {
        ServerPlayer self = (ServerPlayer) (Object) this;
        astralDice$dimensionOrigin = self.level().dimension();
        EntityTravelToDimensionEvent event =
                new EntityTravelToDimensionEvent(self, destination.dimension());
        LoaderBus.INSTANCE.post(event);
        if (event.isCanceled()) {
            cir.setReturnValue(null);
        }
    }

    @Inject(
            method = "changeDimension(Lnet/minecraft/server/level/ServerLevel;)Lnet/minecraft/world/entity/Entity;",
            at = @At("RETURN"))
    private void astralDice$afterPlayerChangeDimension(ServerLevel destination,
                                                       CallbackInfoReturnable<Entity> cir) {
        ResourceKey<Level> from = astralDice$dimensionOrigin;
        astralDice$dimensionOrigin = null;
        // ⚠️ HEAD 的 setReturnValue 会让方法直接返回、RETURN 注入点根本不执行 ⇒ 取消路径天然不派发;
        // 这条 null 判断只是防御「别的注入点改写返回值」。
        if (from == null || cir.getReturnValue() == null) return;
        ServerPlayer self = (ServerPlayer) (Object) this;
        ResourceKey<Level> to = self.level().dimension();
        if (from.equals(to)) return;
        LoaderBus.INSTANCE.post(new PlayerEvent.PlayerChangedDimensionEvent(self, from, to));
    }
}
