package com.merlinkitsune.astral_dice.client;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.DustParticle;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.util.RandomSource;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public final class GlowingDustParticle extends DustParticle {
    /** 全亮（{@code 0xF000F0}）—— 与 {@code SimpleAnimatedParticle}（{@code END_ROD} 的基类）取值一致。 */
    private static final int FULL_BRIGHT = 15728880;

    private GlowingDustParticle(ClientLevel level, double x, double y, double z,
                                double xAux, double yAux, double zAux,
                                DustParticleOptions options, SpriteSet sprites) {
        super(level, x, y, z, xAux, yAux, zAux, options, sprites);
    }

    @Override
    public int getLightCoords(float partialTick) {
        return FULL_BRIGHT;
    }

    @Override
    public SingleQuadParticle.Layer getLayer() {
        return SingleQuadParticle.Layer.TRANSLUCENT;
    }

    public static final class Provider implements ParticleProvider<DustParticleOptions> {
        private final SpriteSet sprites;

        public Provider(SpriteSet sprites) {
            this.sprites = sprites;
        }

        @Override
        public Particle createParticle(DustParticleOptions options, ClientLevel level, double x, double y, double z,
                                       double xAux, double yAux, double zAux, RandomSource random) {
            return new GlowingDustParticle(level, x, y, z, xAux, yAux, zAux, options, this.sprites);
        }
    }
}
