package com.merlinkitsune.astral_dice.api.combat;

import net.minecraft.world.damagesource.DamageSource;

/**
 * 伤害分类器：由具体加载器线安装，供公共 API 与 KubeJS 共用同一套判据。
 *
 * <p>实现必须是纯函数、无副作用，并可在服务端任意线程调用。
 */
@FunctionalInterface
public interface DamageCategoryResolver {

    /** 返回该伤害所属的大类；无法判定时返回 {@link DamageCategory#OTHER}。 */
    DamageCategory classify(DamageSource source);
}
