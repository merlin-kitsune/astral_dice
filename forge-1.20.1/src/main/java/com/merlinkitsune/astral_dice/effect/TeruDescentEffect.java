package com.merlinkitsune.astral_dice.effect;

import com.merlinkitsune.starenginelib.event.ModEffectRemoval;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.player.Player;

/**
 * 「降神」(教主立牌 teru 主动):施加在**被指定目标**身上的常驻时长状态载体。
 *
 * <p><b>为什么用常驻(无限)时长</b>:需求口径是「持续到**下一次骰神赐福结束**」,不是固定时长 ——
 * 用 {@link Integer#MAX_VALUE}(前置库 {@code EffectTimerGuard} 视其为永续,不做计时守卫,与
 * {@link ZhaoBlessingEffect}、{@code effect/ChargeEffect} 同一写法)表达"没有自己的倒计时",
 * 移除时机完全由 {@code item/sign/TeruSignItem#tick} 的**骰神赐福下降沿**状态机决定
 * (两分支见 {@code ModAttachments#TERU_DESCENT_SKIP_CYCLES});玩家级 tick 每 tick 调 {@link #refresh} 续期/补齐。
 *
 * <p><b>数值不挂在本效果上</b>:给施法者的 50% 攻击/防御加成、狐光攻击基数、已攻击目标集全部是
 * **目标身上的附件**({@code ModAttachments#TERU_DESCENT_*});本效果只是玩家可见载体(图标 + 名称),
 * 被移除(含外力移除 / 死亡清场 / 状态机收尾)时由 {@code TeruSignItem#endDescent} 统一收敛。
 *
 * <p>图标 = {@code images/教主立牌.png}(与立牌本体同一张图;实装路径
 * {@code textures/mob_effect/teru_descent.png})。
 */
public class TeruDescentEffect extends MobEffect {
    /** 效果时长(无限;移除由骰神赐福结束/目标死亡/目标登出等钩子驱动,而非倒计时) */
    public static final int DURATION_TICKS = Integer.MAX_VALUE;

    public TeruDescentEffect() {
        super(MobEffectCategory.BENEFICIAL, 0xC77DFF);
    }

    /** 是否已获得「降神」(唯一判据:效果实例是否存在) */
    public static boolean has(Player player) {
        return player != null && player.hasEffect(ModEffects.TERU_DESCENT.get());
    }

    /** 施加「降神」(已存在时不重复施加) */
    public static void apply(Player player) {
        if (player == null || player.level().isClientSide()) return;
        if (player.hasEffect(ModEffects.TERU_DESCENT.get())) return;
        player.addEffect(new MobEffectInstance(ModEffects.TERU_DESCENT.get(),
                DURATION_TICKS, 0, false, false, true));
    }

    /**
     * 玩家级 tick 的**续期/补齐**:效果缺失则重新施加;时长意外变短(外力改写 / 旧存档)则补回常驻值。
     *
     * <p>本效果的时长是常驻值,故"续期"在正常路径上是空操作;真正常用的是第一分支
     * (与真值 {@code teru_descent_caster} 自检配对:真值仍在而效果没了 ⇒ 立刻补回)。
     */
    public static void refresh(Player player) {
        if (player == null || player.level().isClientSide()) return;
        MobEffectInstance existing = player.getEffect(ModEffects.TERU_DESCENT.get());
        if (existing == null) {
            apply(player);
            return;
        }
        if (existing.getDuration() < DURATION_TICKS) {
            player.addEffect(new MobEffectInstance(ModEffects.TERU_DESCENT.get(),
                    DURATION_TICKS, 0, false, false, true));
        }
    }

    /** 移除「降神」(走本模组统一移除通道,保证回收逻辑照常触发) */
    public static void remove(Player player) {
        if (player == null || player.level().isClientSide()) return;
        ModEffectRemoval.remove(player, ModEffects.TERU_DESCENT.get());
    }
}
