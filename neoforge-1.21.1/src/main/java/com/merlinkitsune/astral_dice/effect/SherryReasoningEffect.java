package com.merlinkitsune.astral_dice.effect;

import com.merlinkitsune.starenginelib.event.ModEffectRemoval;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.player.Player;

/**
 * 「推理时间」:怪力侦探立牌(sherry)专属资源的**层数镜像效果**。
 *
 * <p><b>层数真值 == 附件 {@code ModAttachments#SHERRY_REASONING_LAYERS}</b>(层数 = {@code amplifier + 1},
 * 上限 {@code SherrySignItem.MAX_REASONING} = 4);本效果**不提供任何属性修饰符、不产生周期反噬**,
 * 只把附件真值镜像成 HUD 图标 + 层数,层数归 0 时由镜像方移除(与「狐光」{@link HuguangEffect} 同款写法)。
 *
 * <p><b>为什么层数不放效果里(与「弱点识破」{@link WeaknessRevealEffect} 的关键差异)</b>:
 * 用户要求「玩家**死亡不清**推理时间」,而 mob effect 实例在死亡重生时会被清空 ⇒ 真值只能放附件
 * (该键带 {@code .copyOnDeath()})。本效果纯粹是可丢弃、可重建的**显示载体**。
 *
 * <p><b>降层必须"先移除再施加"</b>:原版 {@code MobEffectInstance#update} 只接受更高的 amplifier
 * (低层实例只会被塞进 {@code hiddenEffect}),直接 {@code addEffect} 低层实例会让可见层数永不下降。
 *
 * <p>图标 = 与立牌相同的图(实装路径 {@code textures/mob_effect/sherry_reasoning.png},
 * 自 {@code textures/item/sherry_sign.png} 逐字节复制)。
 */
public class SherryReasoningEffect extends MobEffect {
    /** 效果时长(无限;层数归 0 时由镜像方移除) */
    public static final int DURATION_TICKS = Integer.MAX_VALUE;

    public SherryReasoningEffect() {
        super(MobEffectCategory.BENEFICIAL, 0x8C5A3C);
    }

    /** 当前「推理时间」层数(无效果为 0) */
    public static int getStacks(Player player) {
        if (player == null) return 0;
        MobEffectInstance instance = player.getEffect(ModEffects.SHERRY_REASONING);
        return instance != null ? instance.getAmplifier() + 1 : 0;
    }

    /**
     * 把「推理时间」层数镜像为 {@code count}(0 = 移除效果)。
     * 层数已等于目标值时不做任何写入(避免每 tick 的重复同步包)。
     */
    public static void mirror(Player player, int count) {
        if (player == null || player.level().isClientSide()) return;
        int current = getStacks(player);
        if (current == count) return;
        ModEffectRemoval.remove(player, ModEffects.SHERRY_REASONING);
        if (count > 0) {
            player.addEffect(new MobEffectInstance(ModEffects.SHERRY_REASONING,
                    DURATION_TICKS, count - 1, false, false, true));
        }
    }

    /** 清空效果实例(层数真值另由附件负责;卸下立牌与测试脚手架用) */
    public static void clear(Player player) {
        if (player == null || player.level().isClientSide()) return;
        ModEffectRemoval.remove(player, ModEffects.SHERRY_REASONING);
    }
}
