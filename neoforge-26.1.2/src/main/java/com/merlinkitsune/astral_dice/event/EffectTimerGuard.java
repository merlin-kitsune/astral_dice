package com.merlinkitsune.astral_dice.event;

import com.merlinkitsune.astral_dice.component.ModAttachments;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

import java.util.HashMap;
import java.util.Map;

/**
 * 计时器守卫:保证本模组所有有时长效果严格按 20 tick/秒 的速率流动。
 *
 * <p>外部 buff(如神秘遗物+ 的烈焰之核)可能加快或减慢目标身上效果的计时流动
 * (提前到期或逾期残留)。本守卫在每个游戏 tick 之前({@code PlayerTickEvent.Pre})
 * 把本模组记录的效果剩余时长强制校正回 gameTime 驱动的预期值:
 * <ul>
 *   <li>加速(剩余少于预期)→ 以预期剩余重新施加(延长);</li>
 *   <li>减速(剩余多于预期)→ 移除后按预期剩余重新施加(截断);</li>
 *   <li>到期后仍残留 → 移除并按 1 tick 重施加,使其本 tick 自然到期,
 *       确保 {@code MobEffectEvent.Expired} 及链式逻辑(赐福结束/标记递减等)正常触发。</li>
 * </ul>
 * 记录来源:
 * <ul>
 *   <li>本模组自定义效果(astral_dice:*)由 {@code MobEffectEvent.Added} 统一记录;</li>
 *   <li>本模组施加的原版效果(如完全隐身用的 INVISIBILITY)由 {@link #apply} 包装记录。</li>
 * </ul>
 */
public final class EffectTimerGuard {
    /** 视为“无限时长”的门槛(等于或大于该值时效果视为永续,不做计时守卫) */
    public static final int INFINITE_THRESHOLD = Integer.MAX_VALUE / 2;
    /** 减速截断容差(tick):剩余超出预期该值以上才移除重加,避免每 tick 抖动 */
    private static final int CLAMP_TOLERANCE = 20;

    // 守卫强制移除标志:效果移除拦截器(onModEffectRemovalPrevented)据此放行守卫自身的 removeEffect
    private static boolean forcedRemoval = false;

    /** 单个效果的计时记录:结束 tick + 重新施加所需的参数 */
    public record TimerEntry(long endTick, int amplifier, boolean ambient, boolean visible, boolean showIcon) {
        public static final Codec<TimerEntry> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.LONG.fieldOf("end").forGetter(TimerEntry::endTick),
                Codec.INT.fieldOf("amp").forGetter(TimerEntry::amplifier),
                Codec.BOOL.fieldOf("ambient").forGetter(TimerEntry::ambient),
                Codec.BOOL.fieldOf("visible").forGetter(TimerEntry::visible),
                Codec.BOOL.fieldOf("icon").forGetter(TimerEntry::showIcon)
        ).apply(i, TimerEntry::new));
    }

    private EffectTimerGuard() {
    }

    /** 是否为守卫发起的强制移除(供效果移除拦截器放行) */
    public static boolean isForcedRemoval() {
        return forcedRemoval;
    }

    /**
     * 应用一个有时长的效果并记录其结束时刻(仅玩家目标记录;非玩家目标/无限时长直接透传)。
     * 供本模组施加“原版效果”的调用点使用;自定义效果由 Added 事件统一记录,无需此包装。
     */
    public static boolean apply(LivingEntity target, MobEffectInstance instance) {
        boolean applied = target.addEffect(instance);
        if (applied && target instanceof Player player) {
            record(player, instance);
        }
        return applied;
    }

    /** 记录/更新效果的结束时刻(仅玩家、有限时长;结束时刻取 max,避免刷新时被缩短的记录覆盖) */
    public static void record(Player player, MobEffectInstance instance) {
        if (player.level().isClientSide()) return;
        if (instance == null || instance.getEffect() == null) return;
        int duration = instance.getDuration();
        if (duration >= INFINITE_THRESHOLD) return;
        Map<String, TimerEntry> map = new HashMap<>(player.getData(ModAttachments.EFFECT_TIMER_ENDS.get()));
        String key = instance.getEffect().getRegisteredName();
        TimerEntry prev = map.get(key);
        long end = player.level().getGameTime() + duration;
        if (prev != null && prev.endTick() > end) {
            end = prev.endTick();
        }
        map.put(key, new TimerEntry(end, instance.getAmplifier(),
                instance.isAmbient(), instance.isVisible(), instance.showIcon()));
        player.setData(ModAttachments.EFFECT_TIMER_ENDS.get(), map);
    }

    /** 每 tick({@code PlayerTickEvent.Pre})校正:把记录的效果剩余时长拉回 20t/s 预期值 */
    public static void tick(Player player) {
        if (player.level().isClientSide()) return;
        Map<String, TimerEntry> map = player.getData(ModAttachments.EFFECT_TIMER_ENDS.get());
        if (map == null || map.isEmpty()) return;
        long now = player.level().getGameTime();
        Registry<MobEffect> registry = player.level().registryAccess().registryOrThrow(Registries.MOB_EFFECT);
        Map<String, TimerEntry> updated = new HashMap<>(map);
        boolean dirty = false;
        var it = updated.entrySet().iterator();
        while (it.hasNext()) {
            var e = it.next();
            String key = e.getKey();
            TimerEntry timer = e.getValue();
            var holderOpt = registry.getHolder(ResourceLocation.parse(key));
            if (holderOpt.isEmpty()) {
                it.remove();
                dirty = true;
                continue;
            }
            Holder<MobEffect> holder = holderOpt.get();
            MobEffectInstance inst = player.getEffect(holder);
            long expectedRemaining = timer.endTick() - now;
            if (expectedRemaining <= 0) {
                if (inst != null) {
                    // 外部减速导致到期后仍残留:移除并按 1 tick 重施加,使其本 tick 自然到期(正常触发 Expired 逻辑)
                    forceRemove(player, holder);
                    player.addEffect(new MobEffectInstance(holder, 1, timer.amplifier(),
                            timer.ambient(), timer.visible(), timer.showIcon()));
                }
                it.remove();
                dirty = true;
            } else if (inst == null) {
                // 外部加速导致提前到期(或效果异常缺失):恢复预期剩余
                player.addEffect(new MobEffectInstance(holder, (int) expectedRemaining, timer.amplifier(),
                        timer.ambient(), timer.visible(), timer.showIcon()));
            } else {
                int actual = inst.getDuration();
                if (actual < expectedRemaining) {
                    // 加速:剩余少于预期 → 延长回预期
                    player.addEffect(new MobEffectInstance(holder, (int) expectedRemaining, timer.amplifier(),
                            timer.ambient(), timer.visible(), timer.showIcon()));
                } else if (actual > expectedRemaining + CLAMP_TOLERANCE) {
                    // 减速:剩余多于预期 → 移除后按预期截断
                    forceRemove(player, holder);
                    player.addEffect(new MobEffectInstance(holder, (int) expectedRemaining, timer.amplifier(),
                            timer.ambient(), timer.visible(), timer.showIcon()));
                }
            }
        }
        if (dirty) {
            player.setData(ModAttachments.EFFECT_TIMER_ENDS.get(), updated);
        }
    }

    /** 效果被成功移除(本模组主动移除)时遗忘记录,防止守卫把已结束的效果重新施加回来 */
    public static void forget(Player player, String effectKey) {
        if (player.level().isClientSide() || effectKey == null) return;
        Map<String, TimerEntry> map = player.getData(ModAttachments.EFFECT_TIMER_ENDS.get());
        if (map == null || !map.containsKey(effectKey)) return;
        Map<String, TimerEntry> updated = new HashMap<>(map);
        updated.remove(effectKey);
        player.setData(ModAttachments.EFFECT_TIMER_ENDS.get(), updated);
    }

    /** 清空计时记录(死亡/重登等清场场景) */
    public static void clear(Player player) {
        if (player.level().isClientSide()) return;
        player.setData(ModAttachments.EFFECT_TIMER_ENDS.get(), new HashMap<>());
    }

    private static void forceRemove(Player player, Holder<MobEffect> effect) {
        forcedRemoval = true;
        try {
            player.removeEffect(effect);
        } finally {
            forcedRemoval = false;
        }
    }
}
