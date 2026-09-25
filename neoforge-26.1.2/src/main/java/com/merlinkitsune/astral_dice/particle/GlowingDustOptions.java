package com.merlinkitsune.astral_dice.particle;

import com.merlinkitsune.astral_dice.init.ModParticles;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.ScalableParticleOptionsBase;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.ExtraCodecs;

/**
 * 「发光尘」粒子选项 —— 与 {@link DustParticleOptions} **同形**（同一条**网络线格式**：颜色 + 缩放），
 * 唯一差异是 {@link #getType()} 指向本模组注册的 {@code astral_dice:glowing_dust}。
 *
 * <h2>为什么要新建一个选项类型（而不是继续用原版 dust）</h2>
 * <p>客户端是按 {@code options.getType()} 挑 provider 的（{@code ParticleEngine#createParticle}），
 * 而原版 {@link DustParticleOptions#getType()} **硬编码**返回 {@code ParticleTypes.DUST} ⇒ 复用原版 dust
 * 选项永远只能拿到原版 {@code DustParticle}：它的光照走**世界光照**（{@code Particle#getLightColor}，
 * 该基类并未覆写）且渲染类型是 {@code PARTICLE_SHEET_OPAQUE}（不混合）⇒ 暗处只是「发暗的硬边色点」，
 * 不会像「活体书页」的 {@code END_ROD}（{@code SimpleAnimatedParticle#getLightColor} 恒返回全亮
 * {@code 15728880}）那样自发光 —— 这正是用户 2026-09-25 裁决「应当补齐发光」所指。
 * 想让它发光就必须换一个 {@link ParticleType}；粒子本体见 {@code client/GlowingDustParticle}。
 *
 * <p>⚠️ 编解码器**必须自己写**：解码时若直接交给原版 dust 的编解码器，解出的实例
 * {@code getType()} 又会变回 {@code DUST} ⇒ 客户端退回原版 dust（不发光）。
 */

public final class GlowingDustOptions extends DustParticleOptions {
    /**
     * JSON / 指令形态。⚠️ 本线的 {@code DustParticleOptions} 把颜色存成 **private int**（且没有 int 读取口），
     * 无法像 1.21.1 那样「转发原版 codec + 包一层」，故这里自带一个 int 字段自行编解码
     * （字段名与取值域与 {@code DustParticleOptions.CODEC} 一致）。
     */
    public static final MapCodec<DustParticleOptions> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                    ExtraCodecs.RGB_COLOR_CODEC.fieldOf("color").forGetter(GlowingDustOptions::rgbOf),
                    Codec.FLOAT.fieldOf("scale").forGetter(ScalableParticleOptionsBase::getScale))
            .apply(instance, (color, scale) -> (DustParticleOptions) new GlowingDustOptions(color, scale)));

    /** 网络形态：RGB(int) + 缩放(float) —— 与 {@code DustParticleOptions.STREAM_CODEC} 逐字段同序同型。 */
    public static final StreamCodec<RegistryFriendlyByteBuf, DustParticleOptions> STREAM_CODEC =
            StreamCodec.composite(ByteBufCodecs.INT, GlowingDustOptions::rgbOf,
                    ByteBufCodecs.FLOAT, ScalableParticleOptionsBase::getScale,
                    GlowingDustOptions::new);

    private final int rgb;

    public GlowingDustOptions(int color, float scale) {
        super(color, scale);
        this.rgb = color;
    }

    @Override
    public ParticleType<DustParticleOptions> getType() {
        return ModParticles.GLOWING_DUST.get();
    }

    /** 编码用取色（本模组只编码本类实例；非本类时退回白色作防御）。 */
    private static int rgbOf(DustParticleOptions options) {
        return options instanceof GlowingDustOptions glowing ? glowing.rgb : 0xFFFFFF;
    }
}
