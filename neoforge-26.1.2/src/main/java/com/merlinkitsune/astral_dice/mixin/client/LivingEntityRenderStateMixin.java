package com.merlinkitsune.astral_dice.mixin.client;

import com.merlinkitsune.astral_dice.client.render.TranslucentRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/**
 * 给原版 {@link LivingEntityRenderState} 挂一个 26.1.2 渲染管线需要的「半透明」标记。
 *
 * <p>为什么不用 ThreadLocal：render state 会跨帧复用（每种实体一个），标记必须跟着 state 走，
 * 否则「上一次渲染的玩家」的状态会污染下一次；写进 state 后由
 * {@code LivingEntityRenderer#extractRenderState} 每帧重算，天然不会串味。
 */
@Mixin(LivingEntityRenderState.class)
public abstract class LivingEntityRenderStateMixin implements TranslucentRenderState {

    @Unique
    private boolean astral$translucent;

    @Override
    public boolean astral$isTranslucent() {
        return this.astral$translucent;
    }

    @Override
    public void astral$setTranslucent(boolean translucent) {
        this.astral$translucent = translucent;
    }
}
