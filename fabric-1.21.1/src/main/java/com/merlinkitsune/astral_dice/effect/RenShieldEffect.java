package com.merlinkitsune.astral_dice.effect;

import com.merlinkitsune.astral_dice.AstralDiceMod;
import com.merlinkitsune.astral_dice.platform.util.Effects;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

/**
 * 鼠鼠护盾(游戏大师立牌 ren)。
 *
 * <p><b>为什么要有这个效果</b>:「是否持有鼠鼠护盾」的唯一真值(总开关)。效果实例仅由服务端施加与读取。
 *
 * <p><b>⚠️ 它不能作为客户端渲染条件</b>:原版**不同步** mob effect 给「本人 + 自己乘客」以外的玩家
 * —— 全 jar 构造 {@code ClientboundUpdateMobEffectPacket} 只有 4 处,全部只发本人或乘客;
 * {@code ServerEntity} 内不含效果同步代码;原版为「他人可见」单开的发光轮廓与效果粒子两条通道
 * 都走 {@code SynchedEntityData}。⇒ 他人客户端 {@code entity.hasEffect(Effects.holder(REN_SHIELD))} 恒为 false。
 * 第三人称可见性改走
 * {@link com.merlinkitsune.astral_dice.combat.RenShieldVisibility}
 * (由 {@code ModNetwork.RenShieldStateMessage} 维护),渲染见 {@code client/RenShieldRenderer}。
 *
 * <p><b>1.21.1 专属:MAX_ABSORPTION 修饰器</b>。1.21.1 的 {@code LivingEntity#setAbsorptionAmount}
 * 会被 {@code generic.max_absorption}(基础值 0)静默钳制 ⇒ 黄心写不进去。故本类自带
 * {@code +10.0} 的 {@code MAX_ABSORPTION}(ADD_VALUE);效果被移除时修饰器随原版
 * {@code onEffectRemoved} 一并撤下。1.20.1 侧没有该属性、也不钳制吸收值,故该线的同名类
 * **不含**修饰器——属已登记的平台差异(两边类内容不同是有意为之)。
 *
 * <p>2026-11-08 订正:fabric-1.21.1 移植时照抄了 1.20.1 的实现(漏掉修饰器),导致护盾吸收值
 * 恒被钳成 0、施加后下一 tick 即被清理——与注释自述的「1.21.1 必须自带」矛盾,现补齐。
 */
public class RenShieldEffect extends MobEffect {
    /** 护盾提供的吸收上限与吸收量(5 黄心 = 10 点吸收) */
    public static final double ABSORPTION_AMOUNT = 10.0D;

    public RenShieldEffect() {
        // 淡蓝(与能量球护盾同一色系);关粒子、留 HUD 图标的口径由施加时的六参 MobEffectInstance 决定
        super(MobEffectCategory.BENEFICIAL, 0x55FFFF);
        // 1.21.1 签名是 (Holder<Attribute>, ResourceLocation, double amount, Operation)
        // (1.21.2+ 才把 amount 换成 DoubleSupplier;1.20.1 侧则是 String uuid)。
        this.addAttributeModifier(Attributes.MAX_ABSORPTION,
                ResourceLocation.fromNamespaceAndPath(AstralDiceMod.MODID, "ren_shield_absorption"),
                ABSORPTION_AMOUNT,
                AttributeModifier.Operation.ADD_VALUE);
    }
}
