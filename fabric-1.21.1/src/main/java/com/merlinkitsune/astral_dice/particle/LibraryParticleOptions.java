package com.merlinkitsune.astral_dice.particle;

import com.merlinkitsune.astral_dice.init.ModParticles;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

/**
 * 像素粒子库粒子的选项：{@code variant} 变体下标（见 {@link LibraryVariants}）、{@code scale} 缩放、{@code rgb} 颜色。
 *
 * <p>贴图来自 <a href="https://github.com/shuimo0413/Iron-Elden-Ring-Particle-library">Iron-Elden-Ring-Particle-library</a>
 * （MIT License, Copyright (c) 2026 shuimo0413），由 {@code tools/gen_particle_library.py} 生成的
 * {@code assets/astral_dice/particles/particle_library.json} 列出；渲染见 {@code client/LibraryParticle}。
 *
 * <p>指令示例（1.21.1 起选项是 SNBT 结构，字段名即本类 {@link #CODEC} 的字段名，三项全部可省但建议都写）：
 * {@code /particle astral_dice:particle_library ~ ~ ~ 0 0 0 0 1 {variant:12,scale:1.5,rgb:16776960}}
 */
public record LibraryParticleOptions(int variant, float scale, int rgb) implements ParticleOptions {
    /** JSON / 指令 NBT 形态：{@code variant} 必填，{@code scale} 与 {@code rgb} 可省。 */
    public static final MapCodec<LibraryParticleOptions> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Codec.INT.fieldOf("variant").forGetter(LibraryParticleOptions::variant),
            Codec.FLOAT.optionalFieldOf("scale", 1.0F).forGetter(LibraryParticleOptions::scale),
            Codec.INT.optionalFieldOf("rgb", 0xFFFFFF).forGetter(LibraryParticleOptions::rgb)
    ).apply(instance, LibraryParticleOptions::new));

    /**
     * 网络形态（1.21.1 走 {@link ParticleType#streamCodec()}，不再是 1.20.1 的 {@code writeToNetwork}）：
     * 线格式为 varInt 下标 + float 缩放 + varInt 颜色。
     */
    public static final StreamCodec<RegistryFriendlyByteBuf, LibraryParticleOptions> STREAM_CODEC = StreamCodec.of(
            (buffer, options) -> {
                buffer.writeVarInt(options.variant());
                buffer.writeFloat(options.scale());
                buffer.writeVarInt(options.rgb());
            },
            buffer -> new LibraryParticleOptions(buffer.readVarInt(), buffer.readFloat(), buffer.readVarInt()));

    /** 下标与缩放先归一，避免越界贴图与零尺寸。 */
    public LibraryParticleOptions {
        variant = Math.floorMod(variant, LibraryVariants.COUNT);
        scale = Math.max(0.05F, scale);
    }

    @Override
    public ParticleType<?> getType() {
        return ModParticles.PARTICLE_LIBRARY.get();
    }
}
