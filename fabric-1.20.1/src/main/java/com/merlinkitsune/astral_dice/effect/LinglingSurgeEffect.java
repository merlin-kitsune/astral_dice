package com.merlinkitsune.astral_dice.effect;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;

/**
 * 「能量突击」:怪力乱神立牌(lingling)主动施加于**自身**的有限时长效果(1:00 = 1200 tick)。
 *
 * <p>技能原文(规格 §5):「立即获得 1 张强化拒止;当前充能层数转化为攻击力与移动速度
 * (每层攻击力 +1、移动速度 +1%),持续 1:00」。
 *
 * <p><b>本效果自身不带任何属性修饰器</b> —— 与 1.21.1 蓝本
 * {@code neoforge-1.21.1/.../effect/LinglingSurgeEffect.java} 同口径:层数会随「强化拒止吸附目标数」
 * 实时变化,而原版属性修饰器只在施加时结算,故加成由
 * {@code LinglingSignManager#refreshActiveModifiers(Player)} 按**实时**层数重算
 * ({@code ATTACK_DAMAGE} ADDITION +charge、{@code MOVEMENT_SPEED} MULTIPLY_TOTAL +charge/100,
 * 固定修饰器 id,刷新间隔 10 tick)。
 *
 * <p>时长为普通有限时长,走 {@code EffectTimerGuard} 记账,HUD 计时器按 20 t/s 流动。
 * 配色 {@link MobEffectCategory#BENEFICIAL};图标 = 立牌本体图
 * ({@code textures/mob_effect/lingling_surge.png},自 1.21.1 侧逐字节复制,照 hanna_float / ame_overdose 先例)。
 *
 * <p><b>平台差异(1.20.1 Forge 移植版)</b>:{@code ModEffects.LINGLING_SURGE} 是
 * {@code RegistryObject} ⇒ 全部引用处必须 {@code .get()}(1.21.1 侧是 {@code Holder<MobEffect>})。
 */
public class LinglingSurgeEffect extends MobEffect {
    /** 效果时长:1:00 = 1200 tick(规格 §5「持续 1200t」) */
    public static final int DURATION_TICKS = 1200;

    public LinglingSurgeEffect() {
        super(MobEffectCategory.BENEFICIAL, 0xFF6B2A);
    }
}
