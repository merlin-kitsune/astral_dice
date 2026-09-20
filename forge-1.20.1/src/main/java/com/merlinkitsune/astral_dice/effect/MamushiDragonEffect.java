package com.merlinkitsune.astral_dice.effect;

import com.merlinkitsune.starenginelib.event.ModEffectRemoval;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.player.Player;

/**
 * 「真龙形态」(蛟龙立牌 mamushi 的**锁存态载体**,2026-09-27,规格 §2.2)。
 *
 * <h2>为什么用无限时长</h2>
 * 判据是「觉醒 ≥ {@code MamushiSignItem.AWAKEN_MAX}(8) **且**佩戴立牌」,不是一个固定时长 —— 用
 * {@link Integer#MAX_VALUE} 表达"没有自己的倒计时"(先例:{@link ZhaoBlessingEffect} 的白泽赐福、
 * {@code effect/ChargeEffect}),移除时机完全由立牌 tick 的
 * {@code isDragonForm(player)} 谓词决定:{@code true} ⇒ {@link #refresh},否则 {@link #remove}。
 *
 * <p><b>不登记 {@code EffectTimerGuard}</b>:守卫只服务"有限时长效果严格按 20 t/s 流动"的语义,
 * 常驻效果(无穷时长)不在其管辖范围内(规格 §1 效果表明确要求不登记)。因此本效果**没有**
 * 被守卫按 {@code endTick} 裁回的风险,{@link #refresh} 只需处理"缺失则补、时长异常则补回常驻值"。
 *
 * <p><b>六参构造</b>:{@code (effect, ticks, amp, false, false, true)} —— 关粒子、**留图标**。
 * 1.20.1 的五参构造等价于 {@code showIcon = visible}({@code MobEffectInstance.java:46}),
 * 若用五参并把 visible 设为 false 会连 HUD 图标一起隐藏(踩过的坑)⇒ 必须六参。
 *
 * <p><b>图标</b> = {@code images/蛟龙立牌.png}(与立牌本体同一张图;实装路径
 * {@code textures/mob_effect/mamushi_dragon.png},与 {@code textures/item/mamushi_sign.png}
 * 逐字节相同,先例:nardis / zhao / teru 三批的 {@code *_privilege.png} / {@code *_blessing.png} /
 * {@code *_descent.png})。
 *
 * <h2>1.20.1 平台适配</h2>
 * 构造口径与 1.21.1 **完全相同**({@code (MobEffectCategory, int)});唯一差异是 {@link ModEffects}
 * 里常量类型是 {@code RegistryObject<MobEffect>} ⇒ 所有引用处必须 {@code .get()}
 * (见 {@link #has(Player)} 与 {@code item/sign/MamushiSignItem})。
 */
public class MamushiDragonEffect extends MobEffect {
    /** 效果时长(无限;移除由立牌 tick 的层数/佩戴判据驱动,而非倒计时) */
    public static final int DURATION_TICKS = Integer.MAX_VALUE;

    public MamushiDragonEffect() {
        super(MobEffectCategory.BENEFICIAL, 0x8B00FF);
    }

    /** 是否已获得「真龙形态」(唯一可见判据:效果实例是否存在) */
    public static boolean has(Player player) {
        return player != null && player.hasEffect(ModEffects.MAMUSHI_DRAGON.get());
    }

    /** 施加无限时长的「真龙形态」(已存在时不重复施加) */
    public static void apply(Player player) {
        if (player == null || player.level().isClientSide()) return;
        if (player.hasEffect(ModEffects.MAMUSHI_DRAGON.get())) return;
        player.addEffect(new MobEffectInstance(ModEffects.MAMUSHI_DRAGON.get(),
                DURATION_TICKS, 0, false, false, true));
    }

    /**
     * 立牌 tick 的**续期/补齐**(幂等):效果缺失则重新施加;时长意外变短(外力改写 / 旧存档)则补回常驻值。
     *
     * <p>本效果时长是常驻值,故"续期"在正常路径上是空操作 —— 真正常用的是第一分支
     * (与层数判据自检配对:仍处于真龙形态而效果没了 ⇒ 立刻补回,不给"状态与可见载体不一致"留窗口)。
     *
     * <p>施加走原版 {@code addEffect}:已存在时其内部 {@code MobEffectInstance#update} 只在
     * "新时长严格更长且放大器相同"时改写剩余时长 —— 本效果时长恒为 {@code MAX_VALUE},
     * 因此刷新不会把已有的更长值改短(不会与外部施加打架)。
     */
    public static void refresh(Player player) {
        if (player == null || player.level().isClientSide()) return;
        MobEffectInstance existing = player.getEffect(ModEffects.MAMUSHI_DRAGON.get());
        if (existing == null) {
            apply(player);
            return;
        }
        if (existing.getDuration() < DURATION_TICKS) {
            player.addEffect(new MobEffectInstance(ModEffects.MAMUSHI_DRAGON.get(),
                    DURATION_TICKS, 0, false, false, true));
        }
    }

    /** 移除「真龙形态」(走本模组统一移除通道,保证 {@code MobEffectEvent.Remove} 的既有回收逻辑照常触发) */
    public static void remove(Player player) {
        if (player == null || player.level().isClientSide()) return;
        ModEffectRemoval.remove(player, ModEffects.MAMUSHI_DRAGON.get());
    }
}
