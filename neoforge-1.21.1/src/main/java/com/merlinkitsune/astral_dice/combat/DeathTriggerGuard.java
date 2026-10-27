package com.merlinkitsune.astral_dice.combat;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import net.minecraft.world.entity.LivingEntity;

/**
 * 「同一次死亡只结算一次」的击杀奖励去重闸门。
 *
 * <p><b>为什么需要它</b>：平台的 {@code LivingDeathEvent} 派发点在 {@code LivingEntity#die()} 的
 * {@code !dead} 守卫<b>之前</b>（NeoForge {@code CommonHooks.onLivingDeath} / Forge
 * {@code ForgeHooks.onLivingDeath} 都是 die() 的第一句）⇒ <b>每一次</b> die() 调用都会派发一次事件。
 * 而本模组有多条「嵌套 hurt」路径：法伤加成段（{@code event/DamageEffectCardHandler} 的
 * {@code astral_dice:true_damage}）与额外加伤段（{@code combat/DiceCombatEvents} 的
 * {@code astral_dice:extra_damage}）。这两段的伤害在 {@code LivingDamageEvent} 阶段内<b>同步</b>打进目标，
 * 若它恰好致死：内层 hurt 触发内层 die() ⇒ 事件第一次；外层 hurt 继续扣血、发现血量为 0 再 die() ⇒
 * 事件第二次。判定只修「派发时机」做不到（那是平台语义），故在<b>消费侧</b>按「实体 + 频道」去重。
 *
 * <p><b>判据</b>：同一实体（UUID）在同一游戏刻（允许 1 刻抖动）内的第二次 claim 返回 false；
 * 下一次死亡必然晚于 1 刻，因此不会被误杀。条目按 key 覆盖，无需业务层清理，仅在表过大时顺带淘汰。
 *
 * <p><b>用法</b>：每个「一次死亡只该发一次奖」的处理器各用一个<b>独立频道名</b>，在方法体首句写
 * {@code if (!DeathTriggerGuard.claim(event.getEntity(), "频道")) return;}。
 * 已经是「每赐福周期一次」「共享标记一次性」的处理器（诅咒之剑、幸运棒、调查事件）无需使用。
 *
 * <p><b>线程</b>：只在服务端主线程调用（静态 Map，无同步）。
 */
public final class DeathTriggerGuard {

    /** 同一次死亡两次派发的最大时间差（刻）。嵌套 hurt 与外层 hurt 同步完成 ⇒ 必然同刻。 */
    private static final long SAME_DEATH_WINDOW_TICKS = 1L;

    /** 表过大时的顺带淘汰门槛与保留窗口（刻）。纯防泄漏，不参与判据。 */
    private static final int PRUNE_THRESHOLD = 4096;
    private static final long PRUNE_KEEP_TICKS = 400L;

    private static final Map<String, Long> LAST_CLAIM = new HashMap<>();

    private DeathTriggerGuard() {
    }

    /**
     * 为「实体 + 频道」领取本次死亡的处理权。
     *
     * @return true = 本次死亡的首个派发（应继续结算）；false = 同一次死亡的重复派发（应直接返回）
     */
    public static boolean claim(LivingEntity entity, String channel) {
        if (entity == null || channel == null) return false;
        if (entity.level().isClientSide()) return false;
        long now = entity.level().getGameTime();
        if (LAST_CLAIM.size() > PRUNE_THRESHOLD) prune(now);
        String key = entity.getUUID() + "|" + channel;
        Long last = LAST_CLAIM.get(key);
        if (last != null && now - last <= SAME_DEATH_WINDOW_TICKS) return false;
        LAST_CLAIM.put(key, now);
        return true;
    }

    private static void prune(long now) {
        Iterator<Map.Entry<String, Long>> it = LAST_CLAIM.entrySet().iterator();
        while (it.hasNext()) {
            if (now - it.next().getValue() > PRUNE_KEEP_TICKS) it.remove();
        }
    }
}
