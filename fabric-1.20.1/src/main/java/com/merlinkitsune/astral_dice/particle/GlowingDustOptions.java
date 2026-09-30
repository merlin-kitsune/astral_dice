package com.merlinkitsune.astral_dice.particle;

import com.merlinkitsune.astral_dice.init.ModParticles;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.serialization.Codec;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.network.FriendlyByteBuf;
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
    /**
     * 指令 / 网络解析（1.20.1 的网络路径走 {@code ParticleType#getDeserializer()}）：
     * 转发原版 dust 的解析器，再把结果包成本类 —— 唯一目的就是让 {@code getType()} 指向自有类型。
     */
    public static final ParticleOptions.Deserializer<DustParticleOptions> DESERIALIZER =
            new ParticleOptions.Deserializer<DustParticleOptions>() {
                @Override
                public DustParticleOptions fromCommand(ParticleType<DustParticleOptions> type, StringReader reader)
                        throws CommandSyntaxException {
                    return wrap(DustParticleOptions.DESERIALIZER.fromCommand(type, reader));
                }

                @Override
                public DustParticleOptions fromNetwork(ParticleType<DustParticleOptions> type, FriendlyByteBuf buffer) {
                    return wrap(DustParticleOptions.DESERIALIZER.fromNetwork(type, buffer));
                }
            };

    /** JSON / 指令形态：转发原版 dust 的字段（{@code color} + {@code scale}）。 */
    public static final Codec<DustParticleOptions> CODEC = DustParticleOptions.CODEC.xmap(
            options -> new GlowingDustOptions(options.getColor(), options.getScale()),
            options -> options);

    public GlowingDustOptions(Vector3f color, float scale) {
        super(color, scale);
    }

    @Override
    public ParticleType<DustParticleOptions> getType() {
        return ModParticles.GLOWING_DUST.get();
    }

    private static DustParticleOptions wrap(DustParticleOptions options) {
        return options instanceof GlowingDustOptions
                ? options
                : new GlowingDustOptions(options.getColor(), options.getScale());
    }
}
