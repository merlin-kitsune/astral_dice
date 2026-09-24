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
 * 上限 {@code TeruSignItem.MAX_HUGUANG} = 20);本效果不提供任何属性修饰符、不产生周期反噬,
 * 只是把附件真值镜像成 HUD 图标 + 层数,层数归 0 时由镜像方移除(与「厄运」{@link MisfortuneEffect}、
 * 「反击」{@link RenCounterEffect} 同款写法;1.20.1 侧与 1.21.1 逐字同形,仅注册项类型不同)。
 *
 * <p><b>增层是事件式的、且带两条防刷守卫</b>(见 {@code item/sign/TeruSignItem}):
 * ① 拾取地面攻击牌**不计层**;② 装备计层按「历史同时装备张数水位」去重
 * ⇒ 「丢弃→捡起」与「反复插入→卸除」都刷不出层数。
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

    /** HUD 图标可显示的层数上限:原版只在 {@code amplifier ∈ [1,9]} 时画罗马数字
     *  ⇒ 可见数字只覆盖「层数 2..10」。层数超过本值时图标固定显示 §eX§7(=「10 层及以上」),
     *  真实层数见立牌物品说明的「狐光:x / 20」。 */
    public static final int MAX_ICON_LAYERS = 10;

    /** 当前**图标**层数(无效果为 0;饱和度见 {@link #MAX_ICON_LAYERS}) */
    public static int getStacks(Player player) {
        if (player == null) return 0;
        MobEffectInstance instance = player.getEffect(ModEffects.TERU_HUGUANG.get());
        return instance != null ? instance.getAmplifier() + 1 : 0;
    }

    /**
     * 把「狐光」层数镜像为 HUD 效果:目标图标层数 = {@code min(count, }{@link #MAX_ICON_LAYERS}{@code )},
     * {@code 0} = 移除效果。图标层数已等于目标值时不做写入(避免每 tick 的重复同步包)。
     *
     * <p>⚠️ 移除走 {@link #clearFully}:原版 {@code MobEffectInstance#update} 只接受更高的 amplifier,
     * 因此**降层必须"先移除再施加"**(低层实例直接 addEffect 会被塞进 hiddenEffect,可见层数永不下降)。
     */
    public static void mirror(Player player, int count) {
        if (player == null || player.level().isClientSide()) return;
        int wanted = Math.max(0, Math.min(count, MAX_ICON_LAYERS));
        if (getStacks(player) == wanted) return;
        clearFully(player);
        if (wanted > 0) {
            player.addEffect(new MobEffectInstance(ModEffects.TERU_HUGUANG.get(),
                    DURATION_TICKS, wanted - 1, false, false, true));
        }
    }

    /**
     * 清到**真的没有实例**为止(防御残留 / 隐藏层链):单次 removeEffect 成功后仍可能有实例被顶回可见层,
     * 故循环到 {@code hasEffect} 为假(上限 8 轮,纯防御性,正常路径 1 轮即结束)。
     */
    private static void clearFully(Player player) {
        for (int guard = 0; guard < 8 && player.hasEffect(ModEffects.TERU_HUGUANG.get()); guard++) {
            ModEffectRemoval.remove(player, ModEffects.TERU_HUGUANG.get());
        }
    }

    /** 清空「狐光」效果实例(层数真值另由附件负责;测试脚手架/异常收敛用) */
    public static void clear(Player player) {
        if (player == null || player.level().isClientSide()) return;
        ModEffectRemoval.remove(player, ModEffects.TERU_HUGUANG.get());
    }
}
