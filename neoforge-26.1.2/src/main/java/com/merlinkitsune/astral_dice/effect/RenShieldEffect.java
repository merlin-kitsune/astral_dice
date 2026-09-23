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
 * <p><b>为什么要有这个效果</b>:「是否持有鼠鼠护盾」的唯一真值(总开关)。效果实例仅由服务端施加与读取。
 *
 * <p><b>⚠️ 它不能作为客户端渲染条件</b>:原版**不同步** mob effect 给「本人 + 自己乘客」以外的玩家
 * —— 全 jar 构造 {@code ClientboundUpdateMobEffectPacket} 只有 4 处,全部只发本人或乘客;
 * {@code ServerEntity} 内不含效果同步代码;原版为「他人可见」单开的发光轮廓与效果粒子两条通道
 * 都走 {@code SynchedEntityData}。⇒ 他人客户端 {@code entity.hasEffect(REN_SHIELD)} 恒为 false。
 * 第三人称可见性改走
 * {@link com.merlinkitsune.astral_dice.combat.RenShieldVisibility}
 * (由 {@code network.RenShieldStatePayload} 维护),渲染见 {@code client/RenShieldRenderer}。
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
