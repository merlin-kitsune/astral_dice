package com.merlinkitsune.astral_dice.platform.client.particle;

import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.core.particles.ParticleOptions;

/**
 * 「按贴图集合创建 provider」的函数式接口。
 *
 * <p>Forge 的 {@code RegisterParticleProvidersEvent#registerSpriteSet} 用的是
 * {@code ParticleEngine.SpriteParticleRegistration},而该类在 **vanilla 1.20.1 里是 private**
 * (javac 实测:`SpriteParticleRegistration 在 ParticleEngine 中是 private 访问控制`)
 * ⇒ 无法直接引用,故在此声明等价形状,并由 `AstralDiceClient` 转接到 FAPI 的
 * {@code ParticleFactoryRegistry}。
 */
@FunctionalInterface
public interface SpriteParticleRegistration<T extends ParticleOptions> {
    ParticleProvider<T> create(SpriteSet sprites);
}
