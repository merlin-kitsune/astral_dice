/**
 * 玩家侧效果保护闸门(2026-10-23 用户裁决)。
 *
 * <p><b>裁决原文</b>:「修补玩家侧所有裸施问题,玩家侧效果保持前后端分离。除非该效果被声明可以被清除
 * (用牛奶、蜂蜜瓶等),否则不能受到任何外部影响」。生物(非玩家)侧不在本闸门范围内 ——
 * 那边按同一裁决**保持原版口径**(允许女巫 / 诡厄巫法的术士等 AI 自清效果,否则它们会反复喝奶导致卡顿)。
 *
 * <p><b>三层职责</b>:
 * <ol>
 *   <li><b>计时权威</b>:{@link #apply} 包一层 {@link EffectTimerGuard#apply} —— 玩家身上由本模组施加的
 *       <em>原版</em>效果也进入 20 tick/秒 的 wall-clock 校正(此前只有 {@code astral_dice:} 命名空间的效果
 *       由 {@code MobEffectEvent.Added} 自动登记,原版效果散落各处的裸 {@code addEffect} 完全没有权威);</li>
 *   <li><b>抗外部清除</b>:本类为经 {@link #apply} 施加的每个(玩家,效果)留下一份
 *       「结束 tick + amplifier + 显示标志」记录。外部清除(牛奶、蜂蜜瓶、{@code /effect clear}、
 *       第三方净化)发生时,{@code astral_dice:} 命名空间的效果由
 *       {@code ModEffectEvents#onModEffectRemovalPrevented} 直接取消;<b>原版命名空间</b>无法按实例取消
 *       ({@code MobEffectEvent.Remove} 只带「效果类型」),因此在下一 tick 按剩余时长**原样补回**
 *       —— 玩家的自有药水照常被奶清掉,本模组那一段窗口不受影响;</li>
 *   <li><b>可清除声明</b>:{@link #DECLARED_CLEARABLE} 是「允许被外部清除」的显式白名单,默认空(除历史裁决
 *       允许的标记)。要放开某个效果,只在此处加一行并写清裁决来源即可,不要在事件处理器里写特例。</li>
 * </ol>
 *
 * <p><b>为什么不做「原版效果一律取消移除」</b>:{@code MobEffectEvent.Remove} 不带实例,
 * 按类型取消会连玩家自己喝的同类药水一起锁住(牛奶失效),属于对原版玩法的破坏;补回法没有这个副作用。
 *
 * <p><b>不处理的情况</b>:死亡、本模组内部移除({@code ModEffectRemoval})、计时守卫强制移除
 * (时长校正)、无限时长效果(无窗口可守)。
 *
 * <p><b>平台差异</b>:1.20.1 没有 {@code Holder<MobEffect>},效果注册名经 {@code ForgeRegistries.MOB_EFFECTS} 反查。
 */

package com.merlinkitsune.astral_dice.event;

import com.merlinkitsune.starengine.event.EffectTimerGuard;
import com.merlinkitsune.starengine.event.ModEffectRemoval;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class PlayerEffectProtection {
    /**
     * 「允许被外部清除」的显式白名单 —— 默认一律保护。
     *
     * <p>现有条目:
     * <ul>
     *   <li>{@code astral_dice:marked} —— 2026-09-30 用户裁决:标记是施加给<em>目标</em>的减益,
     *       强度由层数承载,允许玩家用牛奶 / {@code /effect clear} 清掉;其伴随的发光仍按「同寿命」规则
     *       由 {@code ModEffectEvents} 与标记绑定。</li>
     * </ul>
     * 新增条目请附上裁决日期与理由,不要只加 id。
     */
    private static final Set<String> DECLARED_CLEARABLE = Set.of(
            "astral_dice:marked"
    );

    /** 受保护记录:玩家 UUID → 效果注册名 → 快照 */
    private static final Map<UUID, Map<String, Observed>> OBSERVED = new HashMap<>();
    /** 待补回队列:外部清除发生在事件处理期间(可能正在遍历效果表),补回一律推迟到下一 tick 的 Pre 阶段 */
    private static final Map<UUID, Set<String>> PENDING_RESTORE = new HashMap<>();

    /** 单个受保护效果的快照:记录时必须带足够信息,以便外部清除后原样补回 */
    private record Observed(MobEffect effect, long endTick, int amplifier,
                            boolean ambient, boolean visible, boolean showIcon) {
    }

    private PlayerEffectProtection() {
    }

    /**
     * 施加一个效果(仅服务端)。行为等同 {@link EffectTimerGuard#apply},额外对**玩家目标**做保护登记。
     *
     * <p>这是本模组施加效果的唯一推荐入口:自定义命名空间的效果同时会由 {@code MobEffectEvent.Added}
     * 自动进入计时表,这里再登记一份保护快照,两者互不冲突(都在同一时间基上取 max)。
     */
    public static boolean apply(LivingEntity target, MobEffectInstance instance) {
        boolean applied = EffectTimerGuard.apply(target, instance);
        if (applied && target instanceof Player player) {
            observe(player, instance);
        }
        return applied;
    }

    /** 该效果是否被显式声明为「允许外部清除」 */
    public static boolean isDeclaredClearable(String effectId) {
        return effectId != null && DECLARED_CLEARABLE.contains(effectId);
    }

    /** 登记保护快照(有限时长、玩家目标、非显式可清除) */
    private static void observe(Player player, MobEffectInstance instance) {
        if (player.level().isClientSide()) return;
        if (instance == null || instance.getEffect() == null) return;
        int duration = instance.getDuration();
        if (duration == MobEffectInstance.INFINITE_DURATION || duration >= EffectTimerGuard.INFINITE_THRESHOLD) return;
        String id = effectIdOf(instance);
        if (id == null || isDeclaredClearable(id)) return;
        long end = player.level().getGameTime() + duration;
        Map<String, Observed> map = OBSERVED.computeIfAbsent(player.getUUID(), k -> new HashMap<>());
        Observed prev = map.get(id);
        if (prev != null && prev.endTick() > end) {
            end = prev.endTick();
        }
        map.put(id, new Observed(instance.getEffect(), end, instance.getAmplifier(),
                instance.isAmbient(), instance.isVisible(), instance.showIcon()));
    }

    /**
     * 外部清除(非本模组主动移除 / 非死亡 / 非守卫校正)发生时的回调。
     *
     * <p>由 {@code ModEffectEvents#onExternalRemovalRestore}(LOWEST) 在**取消判定之后**调用:
     * 已经被取消的移除不会走到这里;本方法只负责把「有记录、且未声明可清除」的效果排进补回队列。
     */
    public static void onExternalRemoval(Player player, String effectId) {
        if (player.level().isClientSide() || effectId == null) return;
        UUID uuid = player.getUUID();
        if (isDeclaredClearable(effectId)) {
            forget(player, effectId);
            return;
        }
        Map<String, Observed> map = OBSERVED.get(uuid);
        if (map == null || !map.containsKey(effectId)) return;
        PENDING_RESTORE.computeIfAbsent(uuid, k -> new HashSet<>()).add(effectId);
    }

    /** 每 tick(玩家 Pre)在 {@link EffectTimerGuard#tick} 之前调用:补回被外部清除的效果,并清理过期记录 */
    public static void tick(Player player) {
        if (player.level().isClientSide()) return;
        UUID uuid = player.getUUID();
        Set<String> pending = PENDING_RESTORE.remove(uuid);
        long now = player.level().getGameTime();
        Map<String, Observed> map = OBSERVED.get(uuid);
        if (map == null || map.isEmpty()) {
            return;
        }
        if (pending != null) {
            for (String key : pending) {
                Observed obs = map.get(key);
                if (obs == null) continue;
                long remaining = obs.endTick() - now;
                if (remaining <= 0) {
                    map.remove(key);
                    continue;
                }
                if (player.isDeadOrDying() || player.hasEffect(obs.effect())) {
                    continue;
                }
                restore(player, obs, (int) Math.min(Integer.MAX_VALUE - 1L, remaining));
            }
        }
        map.entrySet().removeIf(e -> e.getValue().endTick() + 40L < now);
    }

    private static void restore(Player player, Observed obs, int remaining) {
        PlayerEffectProtection.apply(player, new MobEffectInstance(obs.effect(), remaining,
                obs.amplifier(), obs.ambient(), obs.visible(), obs.showIcon()));
    }

    /**
     * 本模组主动移除一个玩家侧效果(窗口结束 / 层数归零等,**非外部清除**)。
     *
     * <p>必须走本方法,不要裸调 {@code entity.removeEffect(...)}:否则该次移除会被
     * {@link #onExternalRemoval} 当成外部清除,下一 tick 把刚结束的效果原样补回
     * (标记发光、护盾抗性、隐身都踩过这个坑)。内部先丢弃保护记录,再走
     * {@link ModEffectRemoval#remove} 的内部通道(它的 Remove 事件带 internal 标志,不会被补回)。
     */
    public static void removeNow(LivingEntity target, MobEffect effect) {
        if (effect == null) return;
        if (!(target instanceof Player player)) {
            // 非玩家侧不受本闸门保护(裁决:mob 保持原版口径)⇒ 直接移除即可
            target.removeEffect(effect);
            return;
        }
        forget(player, net.minecraftforge.registries.ForgeRegistries.MOB_EFFECTS.getKey(effect).toString());
        ModEffectRemoval.remove(player, effect);
    }

    /** 丢弃某个(玩家,效果)的保护记录与待补回标记 */
    private static void forget(Player player, String effectId) {
        UUID uuid = player.getUUID();
        Map<String, Observed> map = OBSERVED.get(uuid);
        if (map != null) {
            map.remove(effectId);
        }
        Set<String> pending = PENDING_RESTORE.get(uuid);
        if (pending != null) {
            pending.remove(effectId);
        }
    }

    /** 死亡 / 登出等清场场景:放弃所有保护记录(不跨生命周期补回) */
    public static void clear(Player player) {
        UUID uuid = player.getUUID();
        OBSERVED.remove(uuid);
        PENDING_RESTORE.remove(uuid);
    }

    private static String effectIdOf(MobEffectInstance instance) {
        return net.minecraftforge.registries.ForgeRegistries.MOB_EFFECTS.getKey(instance.getEffect()).toString();
    }
}
