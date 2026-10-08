package com.merlinkitsune.astral_dice.particle;

import com.merlinkitsune.astral_dice.init.ModParticles;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Locale;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.network.FriendlyByteBuf;

/**
 * 像素粒子库粒子的选项：{@code variant} 变体下标（见 {@link LibraryVariants}）、{@code scale} 缩放、{@code rgb} 颜色。
 *
 * <p>贴图来自 <a href="https://github.com/shuimo0413/Iron-Elden-Ring-Particle-library">Iron-Elden-Ring-Particle-library</a>
 * （MIT License, Copyright (c) 2026 shuimo0413），由 {@code tools/gen_particle_library.py} 生成的
 * {@code assets/astral_dice/particles/particle_library.json} 列出；渲染见 {@code client/LibraryParticle}。
 *
 * <p>指令示例（1.20.1 与其它粒子选项同形：三项以空格分隔、全部必填）：
 * {@code /particle astral_dice:particle_library 12 1.5 16776960}
 */
public record LibraryParticleOptions(int variant, float scale, int rgb) implements ParticleOptions {
    /** JSON / 指令 NBT 形态：{@code variant} 必填，{@code scale} 与 {@code rgb} 可省。 */
    public static final Codec<LibraryParticleOptions> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.INT.fieldOf("variant").forGetter(LibraryParticleOptions::variant),
            Codec.FLOAT.optionalFieldOf("scale", 1.0F).forGetter(LibraryParticleOptions::scale),
            Codec.INT.optionalFieldOf("rgb", 0xFFFFFF).forGetter(LibraryParticleOptions::rgb)
    ).apply(instance, LibraryParticleOptions::new));

    /**
     * 指令 / 网络解析（1.20.1 的网络路径走 {@code ParticleType#getDeserializer()}）：
     * 解出的实例**必须**是本类，否则 {@link #getType()} 会指回原版类型、客户端挑不到自有 provider。
     */
    public static final ParticleOptions.Deserializer<LibraryParticleOptions> DESERIALIZER =
            new ParticleOptions.Deserializer<LibraryParticleOptions>() {
                @Override
                public LibraryParticleOptions fromCommand(ParticleType<LibraryParticleOptions> type, StringReader reader)
                        throws CommandSyntaxException {
                    // 与其它 1.20.1 粒子选项一致：三项空格分隔、全部必填（省却会与随后坐标产生歧义）。
                    // Brigadier 的 readInt/readFloat 不跳过分隔空白（isAllowedNumber(' ') 为 false），
                    // 必须像原版 DustParticleOptionsBase#readVector3f 那样先吃掉空格，
                    // 否则会抛 readerExpectedInt（聊天栏报「应为整型」）。
                    reader.skipWhitespace();
                    int variant = reader.readInt();
                    reader.skipWhitespace();
                    float scale = reader.readFloat();
                    reader.skipWhitespace();
                    int rgb = reader.readInt();
                    return new LibraryParticleOptions(variant, scale, rgb);
                }

                @Override
                public LibraryParticleOptions fromNetwork(ParticleType<LibraryParticleOptions> type, FriendlyByteBuf buffer) {
                    return new LibraryParticleOptions(buffer.readVarInt(), buffer.readFloat(), buffer.readVarInt());
                }
            };

    /** 下标与缩放先归一，避免越界贴图与零尺寸。 */
    public LibraryParticleOptions {
        variant = Math.floorMod(variant, LibraryVariants.COUNT);
        scale = Math.max(0.05F, scale);
    }

    @Override
    public void writeToNetwork(FriendlyByteBuf buffer) {
        buffer.writeVarInt(this.variant);
        buffer.writeFloat(this.scale);
        buffer.writeVarInt(this.rgb);
    }

    @Override
    public String writeToString() {
        return String.format(Locale.ROOT, "%d %.2f %d", this.variant, this.scale, this.rgb);
    }

    @Override
    public ParticleType<?> getType() {
        return ModParticles.PARTICLE_LIBRARY.get();
    }
}
