package com.merlinkitsune.astral_dice.client.render.ysm;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.util.ARGB;

/**
 * 逐顶点压 alpha 的 {@link VertexConsumer} 包装器。
 *
 * <p>只接管颜色写入（{@code setColor(int,int,int,int)} 与 {@code setColor(int)}），
 * 其余全部纯委托。之所以包 {@code VertexConsumer} 而不是改 YSM 的顶点数据：
 * YSM 的模型几何由它自己的 native 写出器落到 {@code VertexConsumer} 上
 * （方法签名收 {@code VertexConsumer} + 4 个 RGBA float，Java 侧改它的
 * {@code Vector4f} 要碰 100+ 个混淆类），包一层是唯一不依赖混淆名的介入点。
 */
public final class AlphaVertexConsumer implements VertexConsumer {

    private final VertexConsumer delegate;
    private final float alpha;

    public AlphaVertexConsumer(VertexConsumer delegate, float alpha) {
        this.delegate = delegate;
        this.alpha = alpha;
    }

    @Override
    public VertexConsumer addVertex(float x, float y, float z) {
        this.delegate.addVertex(x, y, z);
        return this;
    }

    @Override
    public VertexConsumer setColor(int red, int green, int blue, int alpha) {
        this.delegate.setColor(red, green, blue, (int) (alpha * this.alpha));
        return this;
    }

    @Override
    public VertexConsumer setColor(int argb) {
        this.delegate.setColor(ARGB.multiplyAlpha(argb, this.alpha));
        return this;
    }

    @Override
    public VertexConsumer setUv(float u, float v) {
        this.delegate.setUv(u, v);
        return this;
    }

    @Override
    public VertexConsumer setUv1(int u, int v) {
        this.delegate.setUv1(u, v);
        return this;
    }

    @Override
    public VertexConsumer setUv2(int u, int v) {
        this.delegate.setUv2(u, v);
        return this;
    }

    @Override
    public VertexConsumer setNormal(float x, float y, float z) {
        this.delegate.setNormal(x, y, z);
        return this;
    }

    @Override
    public VertexConsumer setLineWidth(float width) {
        this.delegate.setLineWidth(width);
        return this;
    }
}
