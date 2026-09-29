package com.merlinkitsune.astral_dice.mixin.bridge;

import com.merlinkitsune.astral_dice.platform.client.event.RenderLivingEvent;
import com.merlinkitsune.astral_dice.platform.client.event.RenderPlayerEvent;
import com.merlinkitsune.astral_dice.platform.event.LoaderBus;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * {@code RenderPlayerEvent.Pre}（玩家渲染前）与 {@code RenderLivingEvent.Post}（生物渲染后）桥。
 *
 * <p>两者共用同一个注入点 —— {@code LivingEntityRenderer#render}：
 * <ul>
 *   <li>HEAD：若渲染器是 {@link PlayerRenderer} ⇒ 派发 {@code RenderPlayerEvent.Pre}
 *       （本模组用来抑制骇客立牌自己/被侵入目标的第一人称与第三人称渲染）；</li>
 *   <li>TAIL：派发 {@code RenderLivingEvent.Post}（本模组用它在渲染后画目标描边）。</li>
 * </ul>
 *
 * <p>⚠️ {@code LivingEntityRenderer#render} 在字节码里有**两个**方法：
 * 参数类型为 {@code LivingEntity} 的真实实现，以及参数为 {@code Entity} 的桥接方法
 * ⇒ 这里**必须写完整描述符**锁定前者，只写纯名会与桥接方法相撞而失败
 * （同类问题见 {@code PlayerItemDropGuardMixin} / {@code ServerPlayerDropGuardMixin}）。
 *
 * <p>⚠️ 泛型 {@code LivingEntityRenderer<T, M>} 的 {@code T} 上界是 {@link LivingEntity}
 * ⇒ 擦除后描述符里的实体类型就是 {@code LivingEntity}。
 */
@Mixin(LivingEntityRenderer.class)
public abstract class ClientEntityRenderBridgeMixin {

    @Inject(
            method = "render(Lnet/minecraft/world/entity/LivingEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at = @At("HEAD"))
    private void astralDice$bridgeRenderPlayerPre(LivingEntity entity, float entityYaw, float partialTicks,
                                                  PoseStack poseStack, MultiBufferSource buffer,
                                                  int packedLight, CallbackInfo ci) {
        if (!(entity instanceof AbstractClientPlayer player)) {
            return;
        }
        if (!(((EntityRenderer<?>) (Object) this) instanceof PlayerRenderer renderer)) {
            return;
        }
        LoaderBus.INSTANCE.post(new RenderPlayerEvent.Pre(
                player, renderer, partialTicks, poseStack, buffer, packedLight));
    }

    @Inject(
            method = "render(Lnet/minecraft/world/entity/LivingEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at = @At("TAIL"))
    private void astralDice$bridgeRenderLivingPost(LivingEntity entity, float entityYaw, float partialTicks,
                                                   PoseStack poseStack, MultiBufferSource buffer,
                                                   int packedLight, CallbackInfo ci) {
        LoaderBus.INSTANCE.post(new RenderLivingEvent.Post<>(
                entity, (LivingEntityRenderer) (Object) this, partialTicks, poseStack, buffer, packedLight));
    }
}
