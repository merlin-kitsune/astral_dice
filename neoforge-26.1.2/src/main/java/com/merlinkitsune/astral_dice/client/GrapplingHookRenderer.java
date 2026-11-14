package com.merlinkitsune.astral_dice.client;

import com.merlinkitsune.astral_dice.entity.GrapplingHookEntity;
import com.merlinkitsune.astral_dice.item.ModItems;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.component.DataComponents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomModelData;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Z3000 抓钩渲染器（26.1.2）。
 *
 * <p>26.x 的实体渲染改为 render state + submit：本类在 {@code extractRenderState} 里
 * 解析管道/头部的物品模型，在 {@code submit} 里按相对坐标逐节提交。管道用
 * {@code vacuum_piece}，钩头用同一物品的 {@code custom_model_data=1} override。
 */
public class GrapplingHookRenderer extends EntityRenderer<GrapplingHookEntity, GrapplingHookRenderer.State> {

    private static final double HEAD_BACK = 0.55D;
    private static final float HEAD_SCALE_XZ = 1.8F;
    private static final float HEAD_SCALE_Y = 2.6F;
    private static final double MIN_SEGMENT = 0.02D;
    /** 管道起点在玩家碰撞箱高度上的比例；0.45 取正中心下方，避免从胸口射出。 */
    private static final double START_HEIGHT_FACTOR = 0.45D;
    /**
     * vacuum_piece 的模型中轴在 x=0（普通物品模型在 x=8）；ItemRenderer 会统一
     * translate(-0.5)，管道因此横偏半格。这里补回半格，让管道正对钩头中心。
     */
    private static final double PIPE_X_CENTER_FIX = 0.5D;

    private final ItemModelResolver itemModelResolver;
    private ItemStack pipeStack;
    private ItemStack headStack;

    public GrapplingHookRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.itemModelResolver = context.getItemModelResolver();
    }

    /**
     * 26.x 在资源重载阶段就会构造实体渲染器，此时物品组件尚未绑定；
     * 若在构造器里 new ItemStack 会抛 {@code Components not bound yet}。
     * 因此推迟到首次 extractRenderState（世界已加载、组件已绑定）再创建。
     */
    private void ensureStacks() {
        if (this.pipeStack != null) {
            return;
        }
        this.pipeStack = new ItemStack(ModItems.VACUUM_PIECE.get());
        this.headStack = new ItemStack(ModItems.VACUUM_PIECE.get());
        this.headStack.set(DataComponents.CUSTOM_MODEL_DATA,
                new CustomModelData(java.util.List.of(1.0F), java.util.List.of(), java.util.List.of(), java.util.List.of()));
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(GrapplingHookEntity entity, State state, float partialTick) {
        super.extractRenderState(entity, state, partialTick);
        ensureStacks();
        state.valid = false;
        Entity owner = entity.getRenderOwner();
        if (owner == null) {
            return;
        }

        Vec3 origin = new Vec3(state.x, state.y, state.z);
        Vec3 start = interpolated(owner, owner.getBbHeight() * START_HEIGHT_FACTOR, partialTick).subtract(origin);
        Vec3 end = Vec3.ZERO;
        Vec3 direction = end.subtract(start);
        if (direction.lengthSqr() < 1.0E-6D) {
            return;
        }

        direction = direction.normalize();
        state.start = start;
        state.direction = direction;
        state.head = end.subtract(direction.scale(HEAD_BACK));
        state.valid = true;
        this.itemModelResolver.updateForNonLiving(state.pipe, this.pipeStack, ItemDisplayContext.FIXED, entity);
        this.itemModelResolver.updateForNonLiving(state.headModel, this.headStack, ItemDisplayContext.FIXED, entity);
    }

    @Override
    public boolean shouldRender(GrapplingHookEntity entity, Frustum frustum, double camX, double camY, double camZ) {
        if (super.shouldRender(entity, frustum, camX, camY, camZ)) {
            return true;
        }
        Entity owner = entity.getRenderOwner();
        if (owner == null) {
            return false;
        }
        Vec3 head = entity.position();
        Vec3 chest = owner.position().add(0.0D, owner.getBbHeight() * START_HEIGHT_FACTOR, 0.0D);
        return frustum.isVisible(new AABB(head.x, head.y, head.z, chest.x, chest.y, chest.z));
    }

    @Override
    public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
        if (!state.valid) {
            return;
        }
        double length = state.start.distanceTo(state.head);
        if (length >= MIN_SEGMENT) {
            int segmentCount = Math.max(1, Mth.ceil(length));
            double segmentLength = length / segmentCount;
            for (int i = 0; i < segmentCount; i++) {
                Vec3 center = state.start.add(state.direction.scale((i + 0.5D) * segmentLength));
                poseStack.pushPose();
                poseStack.translate(center.x, center.y, center.z);
                rotateToDirection(poseStack, state.direction);
                poseStack.translate(PIPE_X_CENTER_FIX, 0.0D, 0.0D);
                poseStack.scale(1.0F, 1.0F, (float) segmentLength);
                state.pipe.submit(poseStack, collector, state.lightCoords, OverlayTexture.NO_OVERLAY, 0);
                poseStack.popPose();
            }
        }

        poseStack.pushPose();
        poseStack.translate(state.head.x, state.head.y, state.head.z);
        rotateToDirection(poseStack, state.direction);
        poseStack.scale(HEAD_SCALE_XZ, HEAD_SCALE_Y, HEAD_SCALE_XZ);
        state.headModel.submit(poseStack, collector, state.lightCoords, OverlayTexture.NO_OVERLAY, 0);
        poseStack.popPose();
    }

    private static void rotateToDirection(PoseStack poseStack, Vec3 direction) {
        float yaw = (float) Math.toDegrees(Math.atan2(direction.x, direction.z));
        float pitch = (float) -Math.toDegrees(Math.asin(Mth.clamp(direction.y, -1.0D, 1.0D)));
        poseStack.mulPose(Axis.YP.rotationDegrees(yaw));
        poseStack.mulPose(Axis.XP.rotationDegrees(pitch));
    }

    private static Vec3 interpolated(Entity entity, double yOffset, float partialTick) {
        return new Vec3(
                Mth.lerp(partialTick, entity.xOld, entity.getX()),
                Mth.lerp(partialTick, entity.yOld, entity.getY()) + yOffset,
                Mth.lerp(partialTick, entity.zOld, entity.getZ()));
    }

    public static class State extends EntityRenderState {
        private final ItemStackRenderState pipe = new ItemStackRenderState();
        private final ItemStackRenderState headModel = new ItemStackRenderState();
        private Vec3 start = Vec3.ZERO;
        private Vec3 head = Vec3.ZERO;
        private Vec3 direction = Vec3.ZERO;
        private boolean valid;
    }
}
