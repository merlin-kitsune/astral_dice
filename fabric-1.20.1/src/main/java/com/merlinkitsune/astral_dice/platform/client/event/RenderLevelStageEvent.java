package com.merlinkitsune.astral_dice.platform.client.event;

import com.merlinkitsune.astral_dice.platform.event.Event;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Camera;
import org.joml.Matrix4f;

/**
 * 世界渲染阶段事件(Fabric 侧最小实现)。
 *
 * <p>本模组只用到 {@code Stage.AFTER_ENTITIES} 以及
 * {@code getStage/getPoseStack/getPartialTick/getCamera}。
 * Fabric 侧由 `AstralDiceClient` 在 FAPI 的 {@code WorldRenderEvents.AFTER_ENTITIES}
 * 回调里构造并派发(见该类注释里对 {@code WorldRenderContext} 字段的映射)。
 *
 * <p>⚠️ 与 Forge 的差异(已登记):Forge 在**每个**阶段各派发一次;Fabric 侧只在
 * {@code AFTER_ENTITIES} 派发(本模组注册的两个监听器都靠 {@code getStage()} 早退,
 * 故行为等价)。若将来需要其它阶段,必须在 `AstralDiceClient` 里补对应回调。
 */
public class RenderLevelStageEvent extends Event {

    /** 渲染阶段(本模组只用 AFTER_ENTITIES)。 */
    public enum Stage {
        AFTER_SKY,
        AFTER_SOLID_BLOCKS,
        AFTER_CUTOUT_MIPPED_BLOCKS_BLOCKS,
        AFTER_CUTOUT_BLOCKS,
        AFTER_ENTITIES,
        AFTER_BLOCK_ENTITIES,
        AFTER_TRANSLUCENT_BLOCKS,
        AFTER_TRIPWIRE_BLOCKS,
        AFTER_PARTICLES,
        AFTER_WEATHER,
        AFTER_LEVEL
    }

    private final Stage stage;
    private final PoseStack poseStack;
    private final float partialTick;
    private final Camera camera;
    private final Matrix4f projectionMatrix;

    public RenderLevelStageEvent(Stage stage, PoseStack poseStack, float partialTick,
                                 Camera camera, Matrix4f projectionMatrix) {
        this.stage = stage;
        this.poseStack = poseStack;
        this.partialTick = partialTick;
        this.camera = camera;
        this.projectionMatrix = projectionMatrix;
    }

    public Stage getStage() {
        return stage;
    }

    public PoseStack getPoseStack() {
        return poseStack;
    }

    public float getPartialTick() {
        return partialTick;
    }

    public Camera getCamera() {
        return camera;
    }

    public Matrix4f getProjectionMatrix() {
        return projectionMatrix;
    }
}
