package com.merlinkitsune.astral_dice.client;

import com.merlinkitsune.starengine.geckolib.constant.DataTickets;
import com.merlinkitsune.starengine.geckolib.renderer.GeoEntityRenderer;
import com.merlinkitsune.starengine.geckolib.renderer.base.GeoRenderState;
import com.merlinkitsune.starengine.geckolib.renderer.base.RenderPassInfo;
import com.merlinkitsune.astral_dice.entity.SouriDrinkEntity;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;

/**
 * 金鲤漂浮饮品渲染器：在 {@link GeoEntityRenderer} 之上施加
 * 「像原版掉落物一样旋转 + 上下浮动」的视觉变换（不改实体碰撞箱，也不写动画文件）。
 *
 * <p><b>26.1.2 线 = GeckoLib 5</b>（javap 实证）：
 * <ul>
 *   <li>{@code GeoEntityRenderer} 在 v5 有**两个**类型参数
 *       {@code <T extends Entity & GeoAnimatable, R extends EntityRenderState>}；本实体是
 *       {@code Entity} 而非 {@code LivingEntity} ⇒ 按 {@code createRenderState(T, Void)} 的字节码
 *       （非 LivingEntity 分支 new 的是 {@code EntityRenderState}）取 {@link EntityRenderState}；</li>
 *   <li>v5 **没有** {@code render(PoseStack, MultiBufferSource, int)} 可覆写（26.x 渲染走
 *       {@code submit(R, PoseStack, SubmitNodeCollector, CameraRenderState)}）⇒ 位移/旋转改在
 *       {@link #adjustRenderPose} 里对 {@code RenderPassInfo#poseStack()} 施加；</li>
 *   <li>自发光（暗处不被压暗）改为把 {@code DataTickets.PACKED_LIGHT} 覆写成
 *       {@code 0xF000F0}（全亮），在 {@link #extractRenderState} 里写进渲染态
 *       —— 等价 v4 里 {@code super.render(..., 0xF000F0)} 的硬编码亮度。</li>
 * </ul>
 */
public class SouriDrinkRenderer extends GeoEntityRenderer<SouriDrinkEntity, EntityRenderState> {

    public SouriDrinkRenderer(EntityRendererProvider.Context context) {
        super(context, new SouriDrinkModel());
    }

    @Override
    public void extractRenderState(SouriDrinkEntity entity, EntityRenderState renderState, float partialTick) {
        super.extractRenderState(entity, renderState, partialTick);
        // 自发光：饮品在暗处也保持全亮（用户需求「10 级自发光」= 不被环境光压暗）
        ((GeoRenderState) renderState).addGeckolibData(DataTickets.PACKED_LIGHT, 0xF000F0);
    }

    @Override
    @SuppressWarnings("rawtypes")
    public void adjustRenderPose(RenderPassInfo passInfo) {
        super.adjustRenderPose(passInfo);
        // 上下浮动 + 绕 Y 轴匀速自转（年龄已含 partialTick，取自 GeoRenderState）
        // 注：v5 的 RenderPassInfo<R extends GeoRenderState> 与 GeoEntityRenderer<T,R extends EntityRenderState>
        // 边界不一致（GeoRenderState 由 mixin 注入到 EntityRenderState），这里用原始类型绕过编译期边界检查，
        // 运行时 R 就是 EntityRenderState（已由 mixin 实现 GeoRenderState），强转安全。
        double age = ((GeoRenderState) passInfo.renderState()).getAnimatableAge();
        double bob = Math.sin(age * 0.1D) * 0.08D + 0.12D;
        passInfo.poseStack().translate(0.0D, bob, 0.0D);
        passInfo.poseStack().mulPose(Axis.YP.rotationDegrees((float) (age * 2.5D)));
    }
}
