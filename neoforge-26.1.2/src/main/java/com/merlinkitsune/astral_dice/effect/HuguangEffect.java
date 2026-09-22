package com.merlinkitsune.astral_dice.effect;

import com.merlinkitsune.starenginelib.event.ModEffectRemoval;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.player.Player;

/**
 * 「狐光」:教主立牌(teru)专属资源的**层数镜像效果**。
 *
 * <p><b>层数 == 施法者附件 {@code ModAttachments#TERU_HUGUANG_LAYERS}</b>(层数 = {@code amplifier + 1},
 * 上限 {@code TeruSignItem.MAX_HUGUANG} = 20);本效果不提供任何属性修饰符、不产生周期反噬,只是把附件真值
 * 镜像成 HUD 图标 + 层数,层数归 0 时由镜像方移除(与「厄运」{@link MisfortuneEffect}、
 * 「反击」{@link RenCounterEffect} 同款写法)。
 *
 * <p><b>增层是事件式的、且带两条防刷守卫</b>(见 {@code item/sign/TeruSignItem}):
 * ① 拾取地面攻击牌**不计层**;② 装备计层按「历史同时装备张数水位」去重 ⇒
 * 「丢弃→捡起」与「反复插入→卸除」都刷不出层数。
 *
 * <p><b>降层必须"先移除再施加"</b>:原版 {@code MobEffectInstance#update} 只接受更高的 amplifier
 * (低层实例只会被塞进 {@code hiddenEffect}),直接 {@code addEffect} 低层实例会让可见层数永不下降。
 *
 * <p>图标 = {@code images/狐光.png}(实装路径 {@code textures/mob_effect/teru_huguang.png})。
 */
public class HuguangEffect extends MobEffect {
    /** 效果时长(无限;层数归 0 时由镜像方移除) */
    public static final int DURATION_TICKS = Integer.MAX_VALUE;

    public HuguangEffect() {
        super(MobEffectCategory.BENEFICIAL, 0xFFA64D);
    }

    /** 当前「狐光」层数(无效果为 0) */
    public static int getStacks(Player player) {
        if (player == null) return 0;
        MobEffectInstance instance = player.getEffect(ModEffects.TERU_HUGUANG);
        return instance != null ? instance.getAmplifier() + 1 : 0;
    }

    /**
     * 把「狐光」层数镜像为 {@code count}(0 = 移除效果)。
     * 层数已等于目标值时不做任何写入(避免每 tick 的重复同步包)。
     */
    public static void mirror(Player player, int count) {
        if (player == null || player.level().isClientSide()) return;
        int current = getStacks(player);
        if (current == count) return;
        ModEffectRemoval.remove(player, ModEffects.TERU_HUGUANG);
        if (count > 0) {
            player.addEffect(new MobEffectInstance(ModEffects.TERU_HUGUANG,
                    DURATION_TICKS, count - 1, false, false, true));
        }
    }

    /** 清空「狐光」效果实例(层数真值另由附件负责;测试脚手架/异常收敛用) */
    public static void clear(Player player) {
        if (player == null || player.level().isClientSide()) return;
        ModEffectRemoval.remove(player, ModEffects.TERU_HUGUANG);
    }
}
