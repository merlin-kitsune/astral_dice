package com.merlinkitsune.astral_dice.mixin.client;

import com.merlinkitsune.astral_dice.client.TargetOutlineCapture;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 26.1.2「可见外框捕获」钩子（目标选择器 B3 批，2026-09-18）。
 *
 * <h2>为什么必须用 mixin（1.21.1 → 26.1.2 的结构断裂）</h2>
 * 1.21.1 侧的外框实测挂在 {@code RenderLivingEvent.Post} 上，事件自带 {@code getEntity()}，
 * 且该事件在 {@code LivingEntityRenderer#render} 的 {@code popPose()} **之后**派发 ⇒ 1.21.1 的
 * {@code TargetOutlineCapture} 只能**自己重放**底座变换（`translate`/`scale`/`mulPose`/翻转/`-1.501`），
 * 因此漏掉各渲染器覆写的 {@code scale()}/{@code setupRotations()} 分支。
 *
 * <p>26.1.2 有两处结构性变化，使「重放 + 事件」这条路彻底不可行：
 * <ul>
 *   <li>{@code RenderLivingEvent.Post} **不再携带 entity**（只有 {@code getRenderState()}），
 *       且 {@code EntityRenderState} 无通用 entity id（只有玩家专用的 {@code AvatarRenderState.id}）；</li>
 *   <li>真正的模型姿态消费点移入了延迟特性渲染器（{@code ModelFeatureRenderer#renderModel}），
 *       公开事件侧拿不到「本帧最终模型变换」。</li>
 * </ul>
 * ⇒ 唯一能拿到**原版自己算好的最终姿态**的位置，是 {@code LivingEntityRenderer#submit} 内部的
 * {@code poseStack.popPose()} **之前**那一刻（见下）。
 *
 * <h2>注入点为什么选这里（逐条取证）</h2>
 * <ul>
 *   <li><b>姿态完整</b>：26.1.2 反编译源码 {@code LivingEntityRenderer.java:74-111} 的顺序为
 *       {@code :76 pushPose()} → {@code :87 setupRotations(...)} → {@code :89 this.scale(state, poseStack)}
 *       → {@code translate(0, EntityModel.MODEL_Y_OFFSET=-1.501F, 0)} → … → {@code :111 popPose()}。
 *       注入点在 {@code :111} 之前，故 {@code poseStack} 正是「实体世界位置基座 + 缩放 + 旋转 + 子类
 *       {@code scale()} 覆写 + 模型 Y 偏移」的**最终模型变换**，含 1.21.1 重放方案覆盖不到的子类分支。</li>
 *   <li><b>唯一</b>：{@code javap -p -c} 实测 `LivingEntityRenderer` 全类
 *       {@code PoseStack.popPose} 出现次数 = <b>1</b>（字节码偏移 338）。</li>
 *   <li><b>无条件到达</b>：该点位于 {@code renderType == null}（隐形且不发光）分支**之后**，
 *       因此比 {@code submitModel} 调用点（源码 {@code :98}，{@code renderType == null} 时会跳过）覆盖更全；
 *       与 `RenderLivingEvent.Pre` 被取消时的早退分支一致（覆盖范围与 1.21.1 的 {@code RenderLivingEvent.Post} 相同）。</li>
 * </ul>
 *
 * <h2>方法描述符为什么必须写全</h2>
 * 目标类有**同名桥方法**（泛型擦除产生）：
 * <pre>
 * public void submit(LivingEntityRenderState, PoseStack, SubmitNodeCollector, CameraRenderState);
 * public void submit(EntityRenderState,       PoseStack, SubmitNodeCollector, CameraRenderState);  // 桥
 * </pre>
 * 只写 {@code "submit"} 会同时命中两个候选 ⇒ 必须带完整描述符定位到前者。
 * 注解里的字符串**不受编译器保护**，故配 {@code require = 1, expect = 1}：
 * 注入点 0 处（描述符失配）或 ≥2 处（原版新增 popPose）都会在**加载期硬失败**并点名字符串，
 * 配合 {@code astral_dice.mixins.json} 的 {@code "required": true}，**绝不静默退化**。
 *
 * <p><b>实体身份不在这里反查</b>：由 {@code RegisterRenderStateModifiersEvent} 在渲染状态提取阶段
 * 写入 {@code EntityRenderState} 的自定义槽位（见
 * {@code client/OutlineCaptureRenderStateModifier} 与 {@link TargetOutlineCapture#ENTITY_KEY}）。
 */
@Mixin(LivingEntityRenderer.class)
public abstract class LivingEntityOutlineCaptureMixin {

    /**
     * 目标类字段 {@code protected M model}（{@code LivingEntityRenderer.java:44}）。
     *
     * <p>泛型 {@code M extends EntityModel<? super S>} 擦除后的描述符是
     * {@code Lnet/minecraft/client/model/EntityModel;}；这里用通配符 {@code EntityModel<?>} 声明，
     * 擦除后描述符一致，且无需 {@code @SuppressWarnings("rawtypes")}。
     *
     * <p><b>为什么用 {@code @Shadow} 字段而不用 {@code getModel()}</b>：{@code LivingEntityRenderer}
     * 只有 {@code public M getModel()}（源码 {@code :60-62}），其返回类型带 {@code M extends EntityModel<? super S>}
     * 的递归泛型；shadow 字段（擦除到 {@code EntityModel}）最直接，也与原版 {@code submit} 内部读
     * {@code this.model} 的方式一致。
     */
    @Shadow
    protected EntityModel<?> model;

    @Inject(
            method = "submit(Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;"
                    + "Lcom/mojang/blaze3d/vertex/PoseStack;"
                    + "Lnet/minecraft/client/renderer/SubmitNodeCollector;"
                    + "Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V",
            at = @At(value = "INVOKE",
                    target = "Lcom/mojang/blaze3d/vertex/PoseStack;popPose()V"),
            require = 1,
            expect = 1)
    private void astralDice$captureVisibleOutline(LivingEntityRenderState state,
                                                  PoseStack poseStack,
                                                  SubmitNodeCollector submitNodeCollector,
                                                  CameraRenderState camera,
                                                  CallbackInfo ci) {
        TargetOutlineCapture.onSubmit(state, poseStack, this.model);
    }
}
