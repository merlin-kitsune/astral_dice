package com.merlinkitsune.astral_dice.client;

import com.merlinkitsune.astral_dice.AstralDiceMod;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 目标选择器高亮描边渲染（仅选择者本地可见）。
 *
 * 在 {@link RenderLevelStageEvent.Stage#AFTER_ENTITIES} 阶段沿当前目标的 AABB 画线框：
 * - 友方绿 / 敌对红 / 中立黄（颜色取 {@link TargetSelectionClient#highlightColor}）；
 * - 该事件在 vanilla {@code LevelRenderer.renderLevel} 内派发（Sodium 不替换该方法整体、
 *   Iris 保留事件点），故与 Sodium 0.8.13 + Iris 1.8.14-beta 兼容（自动化测试 TC11 验证）。
 */
@Mod.EventBusSubscriber(modid = AstralDiceMod.MODID, value = Dist.CLIENT)
public final class TargetSelectionHighlighter {
    private static final Logger LOGGER = LoggerFactory.getLogger(TargetSelectionHighlighter.class);

    private TargetSelectionHighlighter() {
    }

    @SubscribeEvent
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES) return;
        LivingEntity target = TargetSelectionClient.currentTarget();
        if (target == null) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || mc.level.getEntity(target.getId()) != target) return;

        var poseStack = event.getPoseStack();
        if (poseStack == null) return;

        int color = TargetSelectionClient.highlightColor(target);
        float r = ((color >> 16) & 0xFF) / 255.0F;
        float g = ((color >> 8) & 0xFF) / 255.0F;
        float b = (color & 0xFF) / 255.0F;

        poseStack.pushPose();
        Vec3 cam = event.getCamera().getPosition();
        poseStack.translate(-cam.x, -cam.y, -cam.z);
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        VertexConsumer lines = buffers.getBuffer(RenderType.lines());
        LevelRenderer.renderLineBox(poseStack, lines, target.getBoundingBox(), r, g, b, 1.0F);
        buffers.endBatch();
        poseStack.popPose();
    }
}
