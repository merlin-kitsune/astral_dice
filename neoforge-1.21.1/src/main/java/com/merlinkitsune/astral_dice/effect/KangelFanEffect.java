package com.merlinkitsune.astral_dice.effect;

import com.merlinkitsune.starenginelib.event.ModEffectRemoval;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;

/**
 * 「粉丝」:超天酱立牌(kangel)被动「小天使请安」施加在**目标**身上的标记效果,
 * 同时**复用**为佩戴者自身的粉丝数指示器。
 *
 * <h3>两种用法(同一个注册 id)</h3>
 * <ul>
 *   <li><b>目标侧标记</b>:佩戴者近战命中、或佩戴者(及其一心同体的地雷女队友)受到敌对攻击时,
 *       由 {@code KangelSignItem} 施加到**攻击方/被攻击目标**身上,{@code amplifier = 0}、
 *       **无限时长**({@code MobEffectInstance.INFINITE_DURATION} ⇒ 目标死亡才自然消失);</li>
 *   <li><b>佩戴者侧计数指示器</b>:{@code amplifier = 粉丝数 - 1}(**等级 = 粉丝数**)、**无限时长**,
 *       粉丝数归 0 或卸下立牌时移除(见 {@link #mirrorCount})。</li>
 * </ul>
 *
 * <p>效果本身**不提供任何属性修饰器、不产生周期反噬** —— 它只是一个可丢弃、可重建的
 * 状态载体(真值在玩家附件 {@code ModAttachments#KANGEL_FAN_COUNT} / {@code KANGEL_FAN_TARGETS}),
 * 与其「减伤 1 点」的判定落在 {@code event/ChipDamageHandler} 的固定点数减法段
 * (读取本效果是否仍在目标身上,而非读任何附件)。
 *
 * <p>配色:{@link MobEffectCategory#HARMFUL}(对目标而言是负面标记;裁决 #5)。
 * 图标 = {@code images/粉丝.png}(实装路径 {@code textures/mob_effect/kangel_fan.png},
 * 逐字节复制)。
 */
public class KangelFanEffect extends MobEffect {
    /** 无限时长(计数不随时间衰减;目标死亡自然消失) */
    public static final int DURATION_TICKS = MobEffectInstance.INFINITE_DURATION;

    public KangelFanEffect() {
        super(MobEffectCategory.HARMFUL, 0xFF8FB1);
    }

    /** 当前指示器层数(= 粉丝数镜像;无效果为 0) */
    public static int getCount(net.minecraft.world.entity.player.Player player) {
        if (player == null) return 0;
        net.minecraft.world.effect.MobEffectInstance instance = player.getEffect(ModEffects.KANGEL_FAN);
        return instance != null ? instance.getAmplifier() + 1 : 0;
    }

    /** 把粉丝数镜像为 HUD 指示器(**等级 = 粉丝数**,即 {@code amplifier = count - 1})。 */
    public static void mirrorCount(net.minecraft.world.entity.player.Player player, int count) {
        if (player == null || player.level().isClientSide()) return;
        int current = player.getEffect(ModEffects.KANGEL_FAN) != null
                ? player.getEffect(ModEffects.KANGEL_FAN).getAmplifier() + 1 : 0;
        if (current == count) return;
        ModEffectRemoval.remove(player, ModEffects.KANGEL_FAN);
        if (count > 0) {
            player.addEffect(new MobEffectInstance(ModEffects.KANGEL_FAN,
                    DURATION_TICKS, count - 1, false, false, true));
        }
    }

    /** 移除指示器/标记(卸下立牌或计数归 0 时) */
    public static void clear(net.minecraft.world.entity.player.Player player) {
        if (player == null || player.level().isClientSide()) return;
        ModEffectRemoval.remove(player, ModEffects.KANGEL_FAN);
    }

    /** 目标是否带有「粉丝」标记({@code ChipDamageHandler} 的 -1 固定点数减法判据) */
    public static boolean has(LivingEntity target) {
        return target != null && target.hasEffect(ModEffects.KANGEL_FAN);
    }
}
