package com.merlinkitsune.astral_dice.effect;

import com.merlinkitsune.starenginelib.event.ModEffectRemoval;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.player.Player;

/**
 * 「真龙形态」(蛟龙立牌 mamushi 的**锁存态**):无层数、无属性修饰符的**状态载体**。
 *
 * <p><b>为什么用常驻(无限)时长</b>:形态是锁存态(「觉醒 ≥ 8 且佩戴立牌」),没有自己的倒计时 ——
 * 用 {@link Integer#MAX_VALUE}(前置库 {@code EffectTimerGuard} 视其为永续、不做计时守卫,
 * 与 {@code effect/ChargeEffect}、{@link ZhaoBlessingEffect} 同一写法)表达"没有自己的倒计时",
 * 施加/移除时机完全由 {@code item/sign/MamushiSignItem#onCurioTick} 的
 * {@code isDragonForm} 判定驱动(满足即 {@link #refresh} 续期,不满足即 {@link #remove})。
 *
 * <p>⚠️ **不做** {@code EffectTimerGuard} 登记:守卫按 {@code INFINITE_THRESHOLD} 自动跳过无限时长效果,
 * 本效果既不必也不得走守卫(规格 §1 效果表:不登记 {@code EffectTimerGuard})。
 *
 * <p>形态收益(攻击力 +5、主动技能取消范围限制、撕咬转龙之咆哮)一律**不挂在本效果上**:
 * 攻击修饰器读实时谓词 {@code MamushiSignItem#isDragonForm}(裁决 4 的实时口径),
 * 牌转换由 {@code MamushiSignItem#transformToDragon} 完成 ⇒ 本效果只承担玩家可见的图标与状态显示。
 *
 * <p>图标 = {@code images/蛟龙立牌.png}(与立牌本体同一张图;实装路径
 * {@code textures/mob_effect/mamushi_dragon.png},与 {@code textures/item/mamushi_sign.png} 逐字节相同,
 * 先例:nardis / zhao / teru 三批的 {@code *_privilege/_blessing/_descent.png})。
 */
public class MamushiDragonEffect extends MobEffect {
    /** 效果时长(无限;移除由 {@code MamushiSignItem#isDragonForm} 的 tick 判定驱动,而非倒计时) */
    public static final int DURATION_TICKS = Integer.MAX_VALUE;

    public MamushiDragonEffect() {
        super(MobEffectCategory.BENEFICIAL, 0x2E8B57);
    }

    /** 是否处于「真龙形态」(可见载体口径:效果实例是否存在;真正判据见 {@code MamushiSignItem#isDragonForm}) */
    public static boolean has(Player player) {
        return player != null && player.hasEffect(ModEffects.MAMUSHI_DRAGON);
    }

    /** 施加「真龙形态」(已存在时不重复施加) */
    public static void apply(Player player) {
        if (player == null || player.level().isClientSide()) return;
        if (player.hasEffect(ModEffects.MAMUSHI_DRAGON)) return;
        player.addEffect(new MobEffectInstance(ModEffects.MAMUSHI_DRAGON,
                DURATION_TICKS, 0, false, false, true));
    }

    /**
     * 立牌 tick 的**续期/补齐**(每 tick 调用):效果缺失则重新施加;时长被外力改写/旧存档变短则补回常驻值。
     *
     * <p>时长是常驻值,故正常路径上"续期"只是补回被 {@code MobEffectInstance} 每 tick 递减掉的 1 tick
     * (口径与 {@link ZhaoBlessingEffect#refresh} 逐字相同)。
     */
    public static void refresh(Player player) {
        if (player == null || player.level().isClientSide()) return;
        MobEffectInstance existing = player.getEffect(ModEffects.MAMUSHI_DRAGON);
        if (existing == null) {
            apply(player);
            return;
        }
        if (existing.getDuration() < DURATION_TICKS) {
            player.addEffect(new MobEffectInstance(ModEffects.MAMUSHI_DRAGON,
                    DURATION_TICKS, 0, false, false, true));
        }
    }

    /**
     * 移除「真龙形态」(走本模组统一移除通道:外部清除拦截器会拦下 {@code astral_dice:*} 效果的移除,
     * 内部通道才放行 —— 同 {@link ZhaoBlessingEffect#remove})。
     */
    public static void remove(Player player) {
        if (player == null || player.level().isClientSide()) return;
        ModEffectRemoval.remove(player, ModEffects.MAMUSHI_DRAGON);
    }
}
