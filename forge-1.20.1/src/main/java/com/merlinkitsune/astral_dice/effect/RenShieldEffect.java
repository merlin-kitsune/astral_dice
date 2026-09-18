package com.merlinkitsune.astral_dice.effect;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;

/**
 * 鼠鼠护盾(游戏大师立牌 ren)。
 *
 * <p><b>为什么要有这个效果</b>:「是否持有鼠鼠护盾」的唯一真值(总开关)。效果实例由服务端施加,
 * 原版会把 mob effect 同步给**所有能看到该实体的客户端** ⇒ 第三人称可见的能量球护盾渲染可以直接以
 * {@code entity.hasEffect(REN_SHIELD)} 为条件源(1.20.1 的附件同步只发给本人,不能用作此条件)。
 *
 * <p><b>平台差异(有意为之,与 1.21.1 侧同名类不同)</b>:1.20.1 没有 {@code generic.max_absorption}
 * 属性、{@code setAbsorptionAmount} 也只把负值钳到 0 ⇒ 本类**不需要**也不应加 MAX_ABSORPTION 修饰器;
 * 1.21.1 侧则必须由效果自带该修饰器,否则黄心会被属性上限静默钳成 0。
 */
public class RenShieldEffect extends MobEffect {
    public RenShieldEffect() {
        // 淡蓝(与能量球护盾同一色系);关粒子、留 HUD 图标的口径由施加时的六参 MobEffectInstance 决定
        super(MobEffectCategory.BENEFICIAL, 0x55FFFF);
    }
}
