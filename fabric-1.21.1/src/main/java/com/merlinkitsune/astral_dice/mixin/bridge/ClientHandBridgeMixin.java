package com.merlinkitsune.astral_dice.mixin.bridge;

import com.merlinkitsune.astral_dice.platform.client.event.RenderHandEvent;
import com.merlinkitsune.astral_dice.platform.event.LoaderBus;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * {@code RenderHandEvent} 桥 —— 「第一人称手持物即将渲染」，<b>可取消</b>。
 *
 * <p>Forge 通过 {@code ForgeHooksClient.renderSpecificFirstPersonHand} 在
 * {@code ItemInHandRenderer#renderHandsWithItems} 的**逐手**渲染里派发；取消后该手持物不再渲染
 * （原版只对「手臂」与「地图手」做隐身抑制，非空手持物不受隐身影响 —— 本模组的骇客立牌
 * `nancy_lu` 正是靠这个事件在自己的主动技能期间藏掉手持物）。
 *
 * <p>这里注入真正干活的那个 private 方法 {@code renderArmWithItem}：它**本身就是逐手调用**的，
 * 比注入 {@code renderHandsWithItems}（那是双臂总入口，还要自己拆手）更贴近 Forge 的派发点。
 * <p>⚠️ 9 个构造参数全部来自该方法的实参，逐一同名映射，不做任何推导。
 */
@Mixin(ItemInHandRenderer.class)
public abstract class ClientHandBridgeMixin {

    @Inject(method = "renderArmWithItem", at = @At("HEAD"), cancellable = true)
    private void astralDice$bridgeRenderHand(AbstractClientPlayer player, float partialTicks, float pitch,
                                             InteractionHand hand, float swingProgress, ItemStack stack,
                                             float equipProgress, PoseStack poseStack,
                                             MultiBufferSource buffer, int packedLight, CallbackInfo ci) {
        if (stack.isEmpty()) {
            // 空手不属于「手持物渲染」(与 Forge 侧一致:原版对空手只画手臂)
            return;
        }
        RenderHandEvent event = new RenderHandEvent(hand, poseStack, buffer, packedLight,
                partialTicks, pitch, swingProgress, equipProgress, stack);
        LoaderBus.INSTANCE.post(event);
        if (event.isCanceled()) {
            ci.cancel();
        }
    }
}
