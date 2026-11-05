package com.merlinkitsune.astral_dice.platform.util;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.effect.MobEffect;

/**
 * {@code MobEffect} ↔ {@code Holder<MobEffect>} 的桥(1.21 起原版特效 API 全面 Holder 化)。
 *
 * <p>本模组的注册表镜像 {@code RegistryObject<MobEffect>#get()} 返回裸 {@code MobEffect},
 * 而 1.21.1 的 {@code MobEffectInstance}/{@code LivingEntity#hasEffect}/{@code removeEffect}
 * 都只收 {@code Holder}。{@link #holder(MobEffect)} 用
 * {@code BuiltInRegistries.MOB_EFFECT.wrapAsHolder} 取**注册表里的真 Holder**
 * (已注册 ⇒ {@code Holder.Reference},未注册 ⇒ {@code Holder.direct}),
 * 从而不需要在业务层区分「是不是本模组注册的」。
 *
 * <p>两个重载并存的意义:{@link #holder(Holder)} 让「已经是 Holder」的调用点
 * 也能安全套一层,机械改写时不必逐处判类型。
 */
public final class Effects {

    private Effects() {
    }

    public static Holder<MobEffect> holder(MobEffect effect) {
        return BuiltInRegistries.MOB_EFFECT.wrapAsHolder(effect);
    }

    public static Holder<MobEffect> holder(Holder<MobEffect> effect) {
        return effect;
    }
}
