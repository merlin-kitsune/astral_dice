package com.merlinkitsune.astral_dice.effect;

import com.merlinkitsune.starenginelib.event.ModEffectRemoval;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.player.Player;

/**
 * 「人偶制作」:人偶师立牌(hanna)专属资源的**层数镜像效果**(1.20.1 Forge 移植版)。
 *
 * <p><b>层数真值 == 附件 {@code ModAttachments#HANNA_DOLL_CRAFT_LAYERS}</b>(层数 = {@code amplifier + 1},
 * 上限 {@code HannaSignItem.MAX_CRAFT} = 7);本效果**不提供任何属性修饰符、不产生周期反噬**,
 * 只把附件真值镜像成 HUD 图标 + 层数,层数归 0 时由镜像方移除(与「狐光」「推理时间」同款写法)。
 *
 * <p><b>满层转换</b>:层数达 7 时由 {@code HannaSignItem#setCraftLayers} 归零本效果并改挂
 * {@link HannaDollCompleteEffect}(用户 2026-09-21 裁决:归零并转为「人偶完成」)。
 *
 * <p><b>降层必须"先移除再施加"</b>:原版 {@code MobEffectInstance#update} 只接受更高的 amplifier
 * (低层实例只会被塞进 {@code hiddenEffect}),直接 {@code addEffect} 低层实例会让可见层数永不下降。
 *
 * <p><b>平台差异(与 1.21.1 逐字等价)</b>:本线 {@code ModEffects.HANNA_DOLL_CRAFT} 是
 * {@code RegistryObject},故 {@code getEffect}` / `addEffect` / `ModEffectRemoval.remove` 一律取
 * {@code .get()}(1.21.1 侧传 {@code DeferredHolder} 本身,即 Holder)。
 *
 * <p>图标 = {@code images/人偶制作.png}(实装路径 {@code textures/mob_effect/hanna_doll_craft.png})。
 */
public class HannaDollCraftEffect extends MobEffect {
    /** 效果时长(无限;层数归 0 / 满层转换时由镜像方移除) */
    public static final int DURATION_TICKS = Integer.MAX_VALUE;

    public HannaDollCraftEffect() {
        super(MobEffectCategory.BENEFICIAL, 0xC9A227);
    }

    /** 当前「人偶制作」层数(无效果为 0) */
    public static int getStacks(Player player) {
        if (player == null) return 0;
        MobEffectInstance instance = player.getEffect(ModEffects.HANNA_DOLL_CRAFT.get());
        return instance != null ? instance.getAmplifier() + 1 : 0;
    }

    /**
     * 把「人偶制作」层数镜像为 {@code count}(0 = 移除效果)。
     * 层数已等于目标值时不做任何写入(避免每 tick 的重复同步包)。
     */
    public static void mirror(Player player, int count) {
        if (player == null || player.level().isClientSide()) return;
        int current = getStacks(player);
        if (current == count) return;
        ModEffectRemoval.remove(player, ModEffects.HANNA_DOLL_CRAFT.get());
        if (count > 0) {
            player.addEffect(new MobEffectInstance(ModEffects.HANNA_DOLL_CRAFT.get(),
                    DURATION_TICKS, count - 1, false, false, true));
        }
    }

    /** 清空效果实例(层数真值另由附件负责;卸下立牌与测试脚手架用) */
    public static void clear(Player player) {
        if (player == null || player.level().isClientSide()) return;
        ModEffectRemoval.remove(player, ModEffects.HANNA_DOLL_CRAFT.get());
    }
}
