package com.merlinkitsune.astral_dice.effect;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;

/**
 * 「反击」:游戏大师立牌 ren 的鼠鼠护盾自带的那 <b>1 层一次性反击</b>的可见载体。
 *
 * <p>反击本身**不是**独立流派/效果体系({@code combat/DiceCombatEvents#injectCounterDamage} 只是
 * 单次伤害注入,没有层数、没有周期反噬);本效果只把服务端的层数附件
 * {@code ren_counter_charges} **镜像成 HUD 图标**:层数 > 0 时由
 * {@code item/RenShieldManager} 施加,层数被消耗或护盾被打空时立即移除。
 *
 * <p>图标 = {@code images/反击.png}(实装路径 {@code textures/mob_effect/ren_counter.png})。
 * 无属性修饰符、无粒子({@code visible=false},但 {@code showIcon=true})。
 *
 * <p><b>26.1.2 平台适配</b>:无 —— 本类与 1.21.1 基准逐字相同(仅 {@code MobEffect} 的
 * {@code (MobEffectCategory, int)} 构造器,该签名经 26.1.2 反编译源
 * {@code world/effect/MobEffect.java:54} 取证仍在)。
 */
public class RenCounterEffect extends MobEffect {
    public RenCounterEffect() {
        super(MobEffectCategory.BENEFICIAL, 0xFF7A2E);
    }
}
