package com.merlinkitsune.astral_dice.effect;

import com.merlinkitsune.starenginelib.event.ModEffectRemoval;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.player.Player;

/**
 * 「厄运」:符卡-祸(zhao 专属牌)的**层数镜像效果**。
 *
 * <p><b>层数 == 持有者当前持有的符卡-祸张数</b>(层数 = {@code amplifier + 1});
 * 层数只由 {@code item/card/HuoCardItem#refreshCurseState} 按背包实际张数镜像,
 * 与「每 2:00 周期伤害」的计时器**完全解耦**(计时器见
 * {@code ModAttachments#HUO_CARD_NEXT_DAMAGE_TICK}:张数增减不改写它,伤害取结算时刻的张数)。
 *
 * <p>与「反击」({@link RenCounterEffect})同款:本效果**不提供任何属性修饰符、不产生周期反噬**,
 * 只是把服务端附件/背包真相镜像成 HUD 图标 + 层数;张数归 0 时由镜像方移除
 * (N=0 ⇒ 既无该效果、也无周期伤害)。
 *
 * <p><b>降层必须"先移除再施加"</b>:原版 {@code MobEffectInstance#update} 只接受**更高**的 amplifier
 * (低层实例只会被塞进 {@code hiddenEffect}),直接 {@code addEffect} 低层实例会让可见层数永不下降
 * —— 与 {@code effect/ChargeEffect} 同一坑同一写法。
 *
 * <p>图标 = {@code images/厄运.png}(实装路径 {@code textures/mob_effect/misfortune.png})。
 */
public class MisfortuneEffect extends MobEffect {
    /** 效果时长(无限;层数归 0 时由镜像方移除) */
    public static final int DURATION_TICKS = Integer.MAX_VALUE;

    public MisfortuneEffect() {
        super(MobEffectCategory.HARMFUL, 0x5B2C6F);
    }

    /** 当前「厄运」层数(无效果为 0) */
    public static int getStacks(Player player) {
        if (player == null) return 0;
        MobEffectInstance instance = player.getEffect(ModEffects.MISFORTUNE.get());
        return instance != null ? instance.getAmplifier() + 1 : 0;
    }

    /**
     * 把「厄运」层数镜像为 {@code count}(0 = 移除效果)。
     * 层数已等于目标值时不做任何写入(避免每 20 tick 的重复同步包)。
     */
    public static void mirror(Player player, int count) {
        if (player == null || player.level().isClientSide()) return;
        int current = getStacks(player);
        if (current == count) return;
        ModEffectRemoval.remove(player, ModEffects.MISFORTUNE.get());
        if (count > 0) {
            player.addEffect(new MobEffectInstance(ModEffects.MISFORTUNE.get(),
                    DURATION_TICKS, count - 1, false, false, true));
        }
    }

    /** 清空「厄运」效果(测试脚手架 / 死亡清场用) */
    public static void clear(Player player) {
        if (player == null || player.level().isClientSide()) return;
        ModEffectRemoval.remove(player, ModEffects.MISFORTUNE.get());
    }
}
