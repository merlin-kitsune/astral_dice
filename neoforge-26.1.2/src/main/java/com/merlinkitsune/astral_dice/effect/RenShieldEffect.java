package com.merlinkitsune.astral_dice.effect;

import com.merlinkitsune.astral_dice.AstralDiceMod;
import net.minecraft.resources.Identifier;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

/**
 * 鼠鼠护盾(游戏大师立牌 ren)。
 *
 * <p><b>为什么要有这个效果</b>:「是否持有鼠鼠护盾」的唯一真值(总开关)。效果实例由服务端施加,
 * 原版会把 mob effect 同步给**所有能看到该实体的客户端** ⇒ 第三人称可见的能量球护盾渲染可以直接以
 * {@code entity.hasEffect(REN_SHIELD)} 为条件源(1.20.1 的附件同步只发给本人,不能用作此条件)。
 *
 * <p><b>MAX_ABSORPTION 修饰器(1.21.1 / 26.1.2 共有)</b>。{@code LivingEntity#setAbsorptionAmount}
 * 会把入参钳到 {@code [0, getMaxAbsorption()]},而 {@code generic.max_absorption} 的基础值是 0
 * ——若只调 {@code setAbsorptionAmount(20)},黄心会被**静默钳成 0**。故本效果在存续期间提供
 * {@code +10.0} 的 {@code MAX_ABSORPTION}(ADD_VALUE);效果被移除时修饰器随原版
 * {@code removeAttributeModifiers} 一并撤销,护盾因而无法再白拿吸收上限。
 *
 * <p>1.20.1 侧没有 {@code MAX_ABSORPTION} 属性、也不钳制吸收值,故该线的同名类**不含**修饰器,
 * 属**已登记的平台差异**(两边类内容不同是有意为之)。
 *
 * <p><b>26.1.2 平台适配</b>:{@code ResourceLocation} → {@code Identifier}
 * (见 {@code client/KeyBindingSetup.java:27}、{@code effect/BlueCurseEffect.java:4,18} 同形写法);
 * 钳制语义经 26.1.2 反编译源取证仍存在
 * ({@code LivingEntity.java:3427-3428 setAbsorptionAmount → internalSetAbsorptionAmount(Mth.clamp(…, 0, getMaxAbsorption()))},
 * {@code :2036-2037 getMaxAbsorption() = getAttributeValue(Attributes.MAX_ABSORPTION)},
 * {@code :338 .add(Attributes.MAX_ABSORPTION)}) ⇒ 本类与 1.21.1 基准**逐字同形**。
 */
public class RenShieldEffect extends MobEffect {
    /** 护盾提供的吸收上限与吸收量(5 黄心 = 10 点吸收) */
    public static final double ABSORPTION_AMOUNT = 10.0D;

    public RenShieldEffect() {
        // 淡蓝(与能量球护盾同一色系);关粒子、留 HUD 图标的口径由施加时的六参 MobEffectInstance 决定
        super(MobEffectCategory.BENEFICIAL, 0x55FFFF);
        this.addAttributeModifier(Attributes.MAX_ABSORPTION,
                Identifier.fromNamespaceAndPath(AstralDiceMod.MODID, "ren_shield_absorption"),
                AttributeModifier.Operation.ADD_VALUE,
                amp -> ABSORPTION_AMOUNT);
    }
}
