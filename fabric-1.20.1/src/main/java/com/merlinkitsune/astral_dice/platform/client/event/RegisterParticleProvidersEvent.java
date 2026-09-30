package com.merlinkitsune.astral_dice.platform.client.event;

import java.util.ArrayList;
import java.util.List;

import com.merlinkitsune.astral_dice.platform.client.particle.SpriteParticleRegistration;
import com.merlinkitsune.astral_dice.platform.event.Event;
import com.merlinkitsune.astral_dice.platform.fml.event.IModBusEvent;

import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;

/**
 * 粒子 provider 注册事件(Fabric 侧手写)。形状对齐 Forge 的
 * {@code client.event.RegisterParticleProvidersEvent} 中本模组用到的部分。
 *
 * <p>注册结果由 `AstralDiceClient` 转接到 FAPI {@code ParticleFactoryRegistry}。
 */
public class RegisterParticleProvidersEvent extends Event implements IModBusEvent {

    /** 一条注册项。 */
    public record Entry<T extends ParticleOptions>(ParticleType<T> type,
                                                   SpriteParticleRegistration<T> registration) {
    }

    private final List<Entry<?>> entries = new ArrayList<>();

    public <T extends ParticleOptions> void registerSpriteSet(ParticleType<T> type,
                                                             SpriteParticleRegistration<T> registration) {
        entries.add(new Entry<>(type, registration));
    }

    public List<Entry<?>> getEntries() {
        return entries;
    }
}
