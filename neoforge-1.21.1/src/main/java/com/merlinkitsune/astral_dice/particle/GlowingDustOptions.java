package com.merlinkitsune.astral_dice.particle;

import com.merlinkitsune.astral_dice.init.ModParticles;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import org.joml.Vector3f;

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
    /** JSON / 指令形态：转发原版 dust 的字段（{@code color} + {@code scale}），只把解出的实例包成本类。 */
    public static final MapCodec<DustParticleOptions> CODEC = DustParticleOptions.CODEC.xmap(
            options -> new GlowingDustOptions(options.getColor(), options.getScale()),
            options -> options);

    /** 网络形态：转发原版 dust 的线格式（Vector3f 颜色 + float 缩放），同样把解出的实例包成本类。 */
    public static final StreamCodec<RegistryFriendlyByteBuf, DustParticleOptions> STREAM_CODEC =
            DustParticleOptions.STREAM_CODEC.map(
                    options -> new GlowingDustOptions(options.getColor(), options.getScale()),
                    options -> options);

    public GlowingDustOptions(Vector3f color, float scale) {
        super(color, scale);
    }

    @Override
    public ParticleType<DustParticleOptions> getType() {
        return ModParticles.GLOWING_DUST.get();
    }
}
