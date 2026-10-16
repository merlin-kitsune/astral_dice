package com.merlinkitsune.astral_dice.effect;

import com.merlinkitsune.starenginelib.event.ModEffectRemoval;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.player.Player;

/**
 * 「爱」:地雷女立牌(ame)被动「无法承受的爱」的资源**层数镜像效果**。
 *
 * <p><b>层数真值 == 附件 {@code ModAttachments#AME_LOVE_LAYERS}</b>(层数 = {@code amplifier + 1},
 * 上限 {@code ModAttachments#AME_LOVE_CAP} 默认 4、最高 {@code AmeSignItem.AME_LOVE_CAP_MAX} = 32);
 * 本效果**不提供任何属性修饰符、不产生周期反噬**,只把附件真值镜像成 HUD 图标 + 层数,
 * 层数归 0 时由镜像方移除(与「推理时间」{@link SherryReasoningEffect} 同款写法)。
 *
 * <p><b>与「推理时间」的唯一差异:本效果是无限时长、不走衰减链。</b>
 * 「推理时间」有 1 分钟衰减计时(自然到期 ⇒ 附件层数 -1,见 {@code DiceCombatEvents#onPassiveStackExpired}),
 * 而「爱」的层数**只**在两条明写路径上变化(受敌对攻击 +1 / 主动自然到期 -4),
 * 不存在「随时间掉层」语义 ⇒ 时长取 {@link MobEffectInstance#INFINITE_DURATION}
 * (前置库 {@code EffectTimerGuard} 视其为永续、不做计时守卫,照「人偶完成」{@link HannaDollCompleteEffect} 口径)。
 *
 * <p>攻+1/层、防-1/层(最低 0)、爱 &gt; 4 时最大生命 + (爱 - 4) 三条数值各自落在
 * {@code AmeSignItem#onCurioTick}(防御 + 最大生命,瞬态属性修饰器、幂等重挂)与
 * {@code combat/DiceCombatModifiers} 的骰战攻击修饰器注册点(攻击 +1/层)。
 *
 * <p>配色:{@link MobEffectCategory#BENEFICIAL}(裁决 #5)。图标 = {@code images/爱.png}
 * (实装路径 {@code textures/mob_effect/ame_love.png},逐字节复制)。
 */
public class AmeLoveEffect extends MobEffect {
    /** 无限时长(层数不随时间衰减;镜像只在层数变化时刷新) */
    public static final int DURATION_TICKS = MobEffectInstance.INFINITE_DURATION;

    public AmeLoveEffect() {
        super(MobEffectCategory.BENEFICIAL, 0xE0447A);
    }

    /** 当前「爱」层数镜像(目标层数 = {@code amplifier + 1};无效果为 0) */
    public static int getLayers(Player player) {
        if (player == null) return 0;
        MobEffectInstance instance = player.getEffect(ModEffects.AME_LOVE);
        return instance != null ? instance.getAmplifier() + 1 : 0;
    }

    /** 把层数镜像为 HUD 图标(**等级 = 层数**);层数 0 ⇒ 移除。 */
    public static void mirror(Player player, int layers) {
        if (player == null || player.level().isClientSide()) return;
        int current = getLayers(player);
        if (current == layers) return;
        ModEffectRemoval.remove(player, ModEffects.AME_LOVE);
        if (layers > 0) {
            player.addEffect(new MobEffectInstance(ModEffects.AME_LOVE,
                    DURATION_TICKS, layers - 1, false, false, true));
        }
    }

    /** 清空层数镜像(卸下立牌用;层数真值另由附件负责) */
    public static void clear(Player player) {
        if (player == null || player.level().isClientSide()) return;
        ModEffectRemoval.remove(player, ModEffects.AME_LOVE);
    }
}
