package com.merlinkitsune.astral_dice.item;

import com.merlinkitsune.astral_dice.component.GameplayConstants;
import com.merlinkitsune.astral_dice.effect.ModEffects;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;

/**
 * "标记"管理器(目标身上的效果,与具体饰品/卡牌解耦)。
 * 数据存储于目标身上的 MARKED 效果(amplifier = 层数-1),本类统一提供施加(层数递增/上限)与读取。
 * 施加来源:普通瞄具/鹰眼瞄具攻击、标靶定时、活体书页远程伤害等。
 */
public final class MarkManager {
    // 标记持续时间(tick):默认 60 秒
    public static final int MARK_DURATION_TICKS = 1200;

    private MarkManager() {
    }

    // 施加一层标记(层数+1,上限 MAX_MARKER),返回施加后的层数
    public static int apply(LivingEntity target) {
        return apply(target, MARK_DURATION_TICKS);
    }

    // 施加一层标记,指定持续时间;同时使目标获得"高亮"。
    // 发光与标记保持同一寿命(时长一致),不再使用无限时长——标记自然结束时发光随之一同消失,
    // 不依赖 Expired 事件链的清理(多层标记由 onMarkExpired 同步刷新);玩家目标经计时器守卫记录。
    // 发光效果 showIcon=false:不显示 HUD 效果标识器(仅保留轮廓视觉,不占效果图标栏)。
    public static int apply(LivingEntity target, int durationTicks) {
        var existing = target.getEffect(ModEffects.MARKED);
        int level = existing != null ? Math.min(existing.getAmplifier() + 1, GameplayConstants.MAX_MARKER - 1) : 0;
        target.addEffect(new MobEffectInstance(ModEffects.MARKED, durationTicks, level, false, true));
        com.merlinkitsune.astral_dice.event.EffectTimerGuard.apply(target,
                new MobEffectInstance(net.minecraft.world.effect.MobEffects.GLOWING, durationTicks, 0, false, false));
        return level + 1;
    }

    // 读取目标当前标记层数(无标记返回 0)
    public static int getLevel(LivingEntity target) {
        var existing = target.getEffect(ModEffects.MARKED);
        return existing != null ? existing.getAmplifier() + 1 : 0;
    }
}
