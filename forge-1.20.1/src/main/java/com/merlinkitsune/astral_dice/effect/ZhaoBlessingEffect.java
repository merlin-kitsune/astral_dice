package com.merlinkitsune.astral_dice.effect;

import com.merlinkitsune.starenginelib.event.ModEffectRemoval;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.player.Player;

/**
 * 「白泽赐福」(风水师立牌 zhao 主动):无层数、无属性修饰符的**状态载体**。
 *
 * <p><b>为什么用无限时长</b>:需求口径是「持续到**下一次**骰神赐福结束」,不是固定时长 ——
 * 用 {@link Integer#MAX_VALUE}(前置库 {@code EffectTimerGuard} 视其为永续,不做计时守卫,与
 * {@code effect/ChargeEffect} 同一写法)表达"没有自己的倒计时",移除时机完全由
 * {@code item/sign/ZhaoSignItem#tickBlessing} 的**骰神赐福下降沿**状态机决定(两分支见
 * {@code ModAttachments#ZHAO_BLESSING_SKIP_CYCLES});玩家级 tick 每 tick 调 {@link #refresh}
 * 续期/补齐,故"时长"永远 ≥ 常驻值,不存在"自己倒计时到了"的路径。
 *
 * <p><b>溢出治疗 → 攻击力</b>的加成值不挂在本效果上,而是玩家附件
 * {@code ModAttachments#ZHAO_OVERFLOW_BONUS}(整数)+ {@code #ZHAO_OVERFLOW_REMAINDER}(余数);
 * 本效果被移除(含外力移除 / 死亡清场 / 状态机收尾)时由 {@code MobEffectEvent.Remove} 与 tick 自检
 * **幂等**回收 —— 回收动作单一,不留残留。
 *
 * <p>图标 = {@code images/风水师立牌.png}(与立牌本体同一张图;实装路径
 * {@code textures/mob_effect/zhao_blessing.png})。
 */
public class ZhaoBlessingEffect extends MobEffect {
    /** 效果时长(无限;移除由骰神赐福结束钩子驱动,而非倒计时) */
    public static final int DURATION_TICKS = Integer.MAX_VALUE;

    public ZhaoBlessingEffect() {
        super(MobEffectCategory.BENEFICIAL, 0x4FC3A1);
    }

    /** 是否已获得「白泽赐福」(唯一判据:效果实例是否存在) */
    public static boolean has(Player player) {
        return player != null && player.hasEffect(ModEffects.ZHAO_BLESSING.get());
    }

    /** 施加无限时长的「白泽赐福」(已存在时不重复施加) */
    public static void apply(Player player) {
        if (player == null || player.level().isClientSide()) return;
        if (player.hasEffect(ModEffects.ZHAO_BLESSING.get())) return;
        player.addEffect(new MobEffectInstance(ModEffects.ZHAO_BLESSING.get(),
                DURATION_TICKS, 0, false, false, true));
    }

    /**
     * 玩家级 tick 的**续期/补齐**:效果缺失则重新施加;时长意外变短(外力改写 / 旧存档)则补回常驻值。
     *
     * <p>本效果的时长是常驻值({@link #DURATION_TICKS} = {@link Integer#MAX_VALUE}),故"续期"在
     * 正常路径上是空操作 —— 真正常用的是第一分支(与真值 {@code zhao_blessing_active} 自检配对:
     * 真值仍在而效果没了 ⇒ 立刻补回,不给"状态与可见载体长期不一致"留窗口)。
     */
    public static void refresh(Player player) {
        if (player == null || player.level().isClientSide()) return;
        MobEffectInstance existing = player.getEffect(ModEffects.ZHAO_BLESSING.get());
        if (existing == null) {
            apply(player);
            return;
        }
        if (existing.getDuration() < DURATION_TICKS) {
            player.addEffect(new MobEffectInstance(ModEffects.ZHAO_BLESSING.get(),
                    DURATION_TICKS, 0, false, false, true));
        }
    }

    /** 移除「白泽赐福」(走本模组统一移除通道,保证 {@code MobEffectEvent.Remove} 的回收逻辑照常触发) */
    public static void remove(Player player) {
        if (player == null || player.level().isClientSide()) return;
        ModEffectRemoval.remove(player, ModEffects.ZHAO_BLESSING.get());
    }
}
