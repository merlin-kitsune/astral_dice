package com.merlinkitsune.astral_dice.client;

import com.merlinkitsune.astral_dice.entity.GrapplingHookEntity;
import com.merlinkitsune.astral_dice.item.ModItems;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomModelData;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * 抓钩原型渲染器。
 *
 * <p>不加载任何实体贴图/自定义模型：管道用 {@code vacuum_piece} 逐节渲染，钩头用
 * {@code vacuum_piece} + {@code custom_model_data=1} 走 {@code vacuum_head} override。
 * 全部通过 {@link ItemRenderer#renderStatic} 绘制，因此不需要 LayerDefinition。
 */
public class GrapplingHookRenderer extends EntityRenderer<GrapplingHookEntity> {

    private static final double HEAD_BACK = 0.55D;
    private static final float HEAD_SCALE_XZ = 1.8F;
    private static final float HEAD_SCALE_Y = 2.6F;
    private static final double MIN_SEGMENT = 0.02D;
    private static final ResourceLocation FALLBACK_TEXTURE =
            ResourceLocation.withDefaultNamespace("textures/misc/white.png");

    private final ItemRenderer itemRenderer;
    private final ItemStack pipeStack;
    private final ItemStack headStack;

    public GrapplingHookRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.itemRenderer = context.getItemRenderer();
        this.pipeStack = new ItemStack(ModItems.VACUUM_PIECE.get());
        this.headStack = new ItemStack(ModItems.VACUUM_PIECE.get());
        this.headStack.set(DataComponents.CUSTOM_MODEL_DATA, new CustomModelData(1));
    }

    @Override
    public ResourceLocation getTextureLocation(GrapplingHookEntity entity) {
        // 本渲染器不采样实体贴图；返回原版白色贴图只为满足 EntityRenderer 的抽象方法。
        return FALLBACK_TEXTURE;
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
        Vec3 chest = owner.position().add(0.0D, owner.getBbHeight() * 0.65D, 0.0D);
        return frustum.isVisible(new AABB(head.x, head.y, head.z, chest.x, chest.y, chest.z));
    }

    @Override
    public void render(GrapplingHookEntity entity, float entityYaw, float partialTick, PoseStack poseStack,
                       MultiBufferSource bufferSource, int packedLight) {
        Entity owner = entity.getRenderOwner();
        if (owner == null) {
            return;
        }

        // EntityRenderDispatcher 已经把 PoseStack 移到实体位置，下面全部换算成相对坐标。
        Vec3 origin = interpolated(entity, 0.0D, partialTick);
        Vec3 start = interpolated(owner, owner.getBbHeight() * 0.65D, partialTick).subtract(origin);
        Vec3 end = interpolated(entity, 0.0D, partialTick).subtract(origin);
        Vec3 direction = end.subtract(start);
        if (direction.lengthSqr() < 1.0E-6D) {
            return;
        }

        direction = direction.normalize();
        Vec3 head = end.subtract(direction.scale(HEAD_BACK));
        int light = LightTexture.pack(10, 15);
        this.drawPipe(start, head, direction, poseStack, bufferSource, light, entity);
        this.drawHead(head, direction, poseStack, bufferSource, light, entity);
    }

    private void drawPipe(Vec3 start, Vec3 end, Vec3 direction, PoseStack poseStack,
                          MultiBufferSource bufferSource, int light, GrapplingHookEntity entity) {
        double length = start.distanceTo(end);
        if (length < MIN_SEGMENT) {
            return;
        }
        int segmentCount = Math.max(1, Mth.ceil(length));
        double segmentLength = length / segmentCount;
        for (int i = 0; i < segmentCount; i++) {
            Vec3 center = start.add(direction.scale((i + 0.5D) * segmentLength));
            poseStack.pushPose();
            poseStack.translate(center.x, center.y, center.z);
            rotateToDirection(poseStack, direction);
            poseStack.scale(1.0F, 1.0F, (float) segmentLength);
            this.itemRenderer.renderStatic(this.pipeStack, ItemDisplayContext.FIXED, light,
                    OverlayTexture.NO_OVERLAY, poseStack, bufferSource, entity.level(), 0);
            poseStack.popPose();
        }
    }

    private void drawHead(Vec3 center, Vec3 direction, PoseStack poseStack,
                          MultiBufferSource bufferSource, int light, GrapplingHookEntity entity) {
        poseStack.pushPose();
        poseStack.translate(center.x, center.y, center.z);
        rotateToDirection(poseStack, direction);
        poseStack.scale(HEAD_SCALE_XZ, HEAD_SCALE_Y, HEAD_SCALE_XZ);
        this.itemRenderer.renderStatic(this.headStack, ItemDisplayContext.FIXED, light,
                OverlayTexture.NO_OVERLAY, poseStack, bufferSource, entity.level(), 0);
        poseStack.popPose();
    }

    /** 把模型 +Z 方向转到 world 方向。 */
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
}
