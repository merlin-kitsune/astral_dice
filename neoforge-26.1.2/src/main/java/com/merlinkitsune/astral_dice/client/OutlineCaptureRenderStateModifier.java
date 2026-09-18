package com.merlinkitsune.astral_dice.client;

import com.merlinkitsune.astral_dice.AstralDiceMod;
import com.google.common.reflect.TypeToken;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.renderstate.RegisterRenderStateModifiersEvent;

/**
 * 「可见外框捕获」的**实体身份绑定**（目标选择器 B3 批，2026-09-18）。
 *
 * <h2>为什么需要它</h2>
 * 26.1.2 的 {@code LivingEntityRenderer#submit} 只拿到 {@code LivingEntityRenderState}，
 * 而 {@code EntityRenderState} **没有通用 entity id**（只有玩家专用的 {@code AvatarRenderState.id}）；
 * 外框缓存按**实体对象身份**建键（{@code IdentityHashMap}，与 1.21.1 基准一致），
 * 故必须在渲染状态提取阶段把 entity 写进 state，供 mixin 侧取回。
 *
 * <p>本类是该绑定的**唯一**落点：把 {@code (entity, state)} 写进
 * {@link TargetOutlineCapture#ENTITY_KEY}。
 *
 * <h2>时机与「不泄漏」保证（逐条取证）</h2>
 * <ul>
 *   <li>{@code RegisterRenderStateModifiersEvent} 是 {@code IModBusEvent}（26.1.2
 *       {@code RegisterRenderStateModifiersEvent.java:36}），在**模组事件总线**上派发、仅逻辑客户端
 *       ⇒ 与同版本既有 {@code client/ModClientEvents}（{@code RegisterGuiLayersEvent} /
 *       {@code RegisterKeyMappingsEvent}）**同一种订阅方式**：{@code @EventBusSubscriber(modid=…, value=Dist.CLIENT)}
 *       （26.1.2 的该注解只剩 {@code value()/modid()}）。</li>
 *   <li>执行时机：{@code RenderStateExtensions#onUpdateEntityRenderState}（26.1.2 NeoForge 源
 *       {@code :38-52}）**先** {@code renderState.resetRenderData()}，**再**按渲染器类（含其父类注册的
 *       修改器）逐个调用 modifier ⇒ 每次提取都从空槽开始，**每帧自清、不会静态泄漏**上一帧的实体
 *       （{@code BaseRenderState#resetRenderData} 即 {@code extensions.clear()}）。</li>
 *   <li>该方法随后由 {@code EntityRenderer#createRenderState(T, float)} 调用
 *       （26.1.2 {@code EntityRenderer.java:165}），故**注入的 state 与同一帧 {@code submit} 收到的是同一个对象**。</li>
 *   <li>命中范围：{@code RenderStateExtensions#registerEntity} 用
 *       {@code entry.getKey().isAssignableFrom(renderer.getClass())}（同文件 {@code :43}）匹配
 *       ⇒ 注册 {@code LivingEntityRenderer.class} 对**全部**活体渲染器子类生效（含玩家 {@code AvatarRenderer}）。</li>
 * </ul>
 *
 * <h2>泛型推断说明（实测结论，含失败写法记录）</h2>
 * 26.1.2 提供两个重载（同文件 {@code :58} / {@code :82}）：
 * <ul>
 *   <li>{@code registerEntityModifier(Class<? extends EntityRenderer<? extends E, ? extends S>>, BiConsumer<E,S>)}
 *       —— <b>实测编不过</b>。传裸 {@code LivingEntityRenderer.class}（类型为 raw
 *       {@code Class<LivingEntityRenderer>}）时，即使加显式类型见证
 *       {@code event.<LivingEntity, LivingEntityRenderState>registerEntityModifier(...)}，
 *       javac 仍报「找不到合适的方法」：形参类型是 {@code Class<? extends EntityRenderer<? extends E, ? extends S>>}，
 *       而 raw {@code Class<LivingEntityRenderer>} 无法作为
 *       {@code Class<? extends EntityRenderer<? extends LivingEntity, ? extends LivingEntityRenderState>>}
 *       使用（{@code EntityRenderer} 的类型实参是 {@code T extends Entity}/{@code S extends EntityRenderState}，
 *       raw 类的超类型化不可捕获，且形参带嵌套通配符）。
 *       —— 该重载只适合「渲染器类本身不带泛型」的场景（其 javadoc 的例子正是 {@code PlayerRenderer.class}，
 *       与 {@code AvatarRenderStateModifier} 的存在互为印证）。</li>
 *   <li>{@code registerEntityModifier(TypeToken<? extends EntityRenderer<? extends E, ? extends S>>, BiConsumer<E,S>)}
 *       —— <b>本类采用</b>。{@code TypeToken} 子类携带完整的泛型实参
 *       （{@code LivingEntityRenderer} 的 {@code S} 上界就是 {@code LivingEntityRenderState}，
 *       {@code M} 的 {@code ? super S} 由捕获满足），无 raw 类型参与，且 NeoForge 自己的 javadoc
 *       示例（同文件 {@code :46-51}）就是这种写法。</li>
 * </ul>
 */
@EventBusSubscriber(modid = AstralDiceMod.MODID, value = Dist.CLIENT)
public final class OutlineCaptureRenderStateModifier {

    /** 渲染器泛型实参：{@code E = LivingEntity}、{@code S = LivingEntityRenderState}（{@code M} 由捕获满足） */
    private static final TypeToken<LivingEntityRenderer<LivingEntity, LivingEntityRenderState, ?>> LIVING_RENDERER =
            new TypeToken<>() {
            };

    private OutlineCaptureRenderStateModifier() {
    }

    @SubscribeEvent
    public static void onRegisterRenderStateModifiers(RegisterRenderStateModifiersEvent event) {
        // 第一次实测的失败写法（保留记录，勿回退）：
        //   event.<LivingEntity, LivingEntityRenderState>registerEntityModifier(
        //           LivingEntityRenderer.class, (entity, state) -> …);   // ← javac: 找不到合适的方法
        event.registerEntityModifier(
                LIVING_RENDERER,
                (entity, state) -> state.setRenderData(TargetOutlineCapture.ENTITY_KEY, entity));
    }
}
