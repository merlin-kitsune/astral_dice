package com.merlinkitsune.astral_dice.init;

import com.merlinkitsune.astral_dice.AstralDiceMod;
import com.merlinkitsune.astral_dice.particle.GlowingDustOptions;
import com.mojang.serialization.Codec;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.registries.Registries;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

/**
 * 本模组自有的粒子类型（目前只有一枚）。
 *
 * <h2>为什么需要它</h2>
 * <p>「飞星」拖尾原先直接用原版 {@link DustParticleOptions}（可染色），但原版 dust 粒子**不发光**
 * （世界光照 + 不透明混合），而「活体书页」拖尾用的 {@code END_ROD} 是全亮的 ⇒ 两处观感不一致
 * （用户 2026-09-25 裁决「飞星筹码的粒子效果没有发光效果，但是活体书页有，应当补齐发光」）。
 * 原版粒子类里**没有「可染色 + 全亮」的那一个**（1.21.1 侧逐类实查 47 个：可染色的 dust / spell 走世界光照；
 * 全亮的 {@code END_ROD} / {@code FLASH} / {@code SCULK_CHARGE} / {@code GUST} 等都不接受颜色参数），
 * 故这里注册一枚自有类型：选项 {@link GlowingDustOptions}、客户端渲染 {@code client/GlowingDustParticle}
 * （全亮 + 半透明混合）、贴图清单 {@code assets/astral_dice/particles/glowing_dust.json}。
 *
 * <p>⚠️ 选项类型刻意**复用** {@link DustParticleOptions}（与「原版 dust 携带什么数据」保持一致），
 * 只把反序列化器换成 {@link GlowingDustOptions} 的版本，好让解出的实例 {@code getType()} 指向本类型。
 */

public final class ModParticles {
    public static final DeferredRegister<ParticleType<?>> PARTICLE_TYPES =
            DeferredRegister.create(Registries.PARTICLE_TYPE, AstralDiceMod.MODID);

    /** 自发光「发光尘」：飞星拖尾用（客户端 provider 见 {@code client/ModClientEvents#registerParticleProviders}）。 */
    public static final RegistryObject<GlowingDustType> GLOWING_DUST =
            PARTICLE_TYPES.register("glowing_dust", GlowingDustType::new);

    private ModParticles() {
    }

    /** {@code astral_dice:glowing_dust} 的类型对象（选项即 {@link DustParticleOptions} 的颜色 + 缩放）。 */
    public static final class GlowingDustType extends ParticleType<DustParticleOptions> {
        private GlowingDustType() {
            super(false, GlowingDustOptions.DESERIALIZER);
        }

        @Override
        public Codec<DustParticleOptions> codec() {
            return GlowingDustOptions.CODEC;
        }
    }
}
