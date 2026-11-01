package com.merlinkitsune.astral_dice.client.render;

/**
 * 26.1.2 渲染两段式的「半透明」标记载体。
 *
 * <p>26.1.2 起实体渲染是 {@code extractRenderState → submit(state, …)} 两段式：
 * {@code submit} 只能拿到 {@link net.minecraft.client.renderer.entity.state.LivingEntityRenderState}，
 * 拿不到 Entity，因此「这名玩家该不该半透明」必须在 {@code extractRenderState} 阶段
 * 写进 render state，{@code getRenderType} / {@code submit} 再读回来。
 *
 * <p>实现方是 {@code com.merlinkitsune.astral_dice.mixin.client.LivingEntityRenderStateMixin}
 * （给原版 state 挂一个 {@code @Unique} 字段）。
 *
 * <p>注意：本接口**不能**放在 {@code ...astral_dice.mixin} 包下——Mixin 只允许该包内放
 * {@code @Mixin} 类，否则启动即 {@code IllegalClassLoadError: … is in a defined mixin package}。
 */
public interface TranslucentRenderState {

    boolean astral$isTranslucent();

    void astral$setTranslucent(boolean translucent);
}
