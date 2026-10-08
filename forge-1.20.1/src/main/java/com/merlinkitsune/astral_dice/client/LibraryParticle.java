package com.merlinkitsune.astral_dice.client;

import com.merlinkitsune.astral_dice.particle.LibraryParticleOptions;
import com.merlinkitsune.astral_dice.particle.LibraryVariants;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * 像素粒子库粒子的客户端渲染：按 {@code variant} 取贴图（一图一帧，不播动画）、全亮、半透明混合。
 *
 * <p>贴图来自 <a href="https://github.com/shuimo0413/Iron-Elden-Ring-Particle-library">Iron-Elden-Ring-Particle-library</a>
 * （MIT License, Copyright (c) 2026 shuimo0413）。
 */
@OnlyIn(Dist.CLIENT)
public final class LibraryParticle extends TextureSheetParticle {
    /** 全亮（{@code 0xF000F0}）—— 与 {@code SimpleAnimatedParticle}（{@code END_ROD} 的基类）取值一致。 */
    private static final int FULL_BRIGHT = 15728880;
    /** 尺寸系数：{@code scale * 该系数} 就是贴图边长（格）。 */
    private static final float QUAD_SIZE_FACTOR = 0.35F;

    private final float spin;

    private LibraryParticle(ClientLevel level, double x, double y, double z,
                            double xSpeed, double ySpeed, double zSpeed,
                            LibraryParticleOptions options, SpriteSet sprites) {
        super(level, x, y, z, xSpeed, ySpeed, zSpeed);
        this.spin = (this.random.nextFloat() - 0.5F) * 0.1F;
        this.setColor(((options.rgb() >> 16) & 0xFF) / 255.0F,
                ((options.rgb() >> 8) & 0xFF) / 255.0F,
                (options.rgb() & 0xFF) / 255.0F);
        this.setSprite(sprites.get(options.variant(), Math.max(1, LibraryVariants.COUNT - 1)));
        this.quadSize = options.scale() * QUAD_SIZE_FACTOR;
        this.setSize(options.scale() * 0.25F, options.scale() * 0.25F);
        this.lifetime = 12 + this.random.nextInt(9);
        this.gravity = 0.0F;
        this.friction = 0.96F;
    }

    @Override
    protected int getLightColor(float partialTick) {
        return FULL_BRIGHT;
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }

    @Override
    public void tick() {
        super.tick();
        this.oRoll = this.roll;
        this.roll += this.spin;
    }

    /** 客户端 provider：精灵集合来自 {@code assets/astral_dice/particles/particle_library.json}。 */
    public static final class Provider implements ParticleProvider<LibraryParticleOptions> {
        private final SpriteSet sprites;

        public Provider(SpriteSet sprites) {
            this.sprites = sprites;
        }

        @Override
        public Particle createParticle(LibraryParticleOptions options, ClientLevel level, double x, double y, double z,
                                       double xSpeed, double ySpeed, double zSpeed) {
            return new LibraryParticle(level, x, y, z, xSpeed, ySpeed, zSpeed, options, this.sprites);
        }
    }
}
