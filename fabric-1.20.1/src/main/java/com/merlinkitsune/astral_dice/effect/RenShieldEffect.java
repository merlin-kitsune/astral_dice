package com.merlinkitsune.astral_dice.effect;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;

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
 * (由 {@code ModNetwork.RenShieldStateMessage} 维护),渲染见 {@code client/RenShieldRenderer}。
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
