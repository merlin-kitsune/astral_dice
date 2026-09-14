package com.merlinkitsune.astral_dice.damage;

import net.minecraft.world.entity.LightningBolt;

import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * 电磁炮雷击的识别标记:记录**由本模组电磁炮降下的闪电实体**,供
 * {@code mixin.EntityThunderHitMixin} 只对这些闪电把伤害类型换成真伤,而不影响
 * 原版/其它模组的闪电(雷暴、引雷三叉戟等)。
 *
 * <p>用弱引用集合:{@link LightningBolt} 存活期间由世界强引用,条目稳定存在;
 * 闪电被移除并被 GC 后条目自动消失,无需手动清理。仅在服务端线程读写
 * (闪电由 {@code RailgunChipItem#strike} 在服务端创建),故不加同步。
 */
public final class RailgunBolts {
    private static final Set<LightningBolt> RAILGUN_BOLTS =
            Collections.newSetFromMap(new WeakHashMap<>());

    private RailgunBolts() {
    }

    /** 标记该闪电为电磁炮雷击(须在 {@code level.addFreshEntity(bolt)} 之前调用)。 */
    public static void mark(LightningBolt bolt) {
        if (bolt != null) {
            RAILGUN_BOLTS.add(bolt);
        }
    }

    /**
     * 这道电磁炮雷击是否应当命中该实体:**敌对生物**,或**已被激怒的中立生物**(如被攻击后的末影人)。
     *
     * <p>原版 {@code LightningBolt#tick} 对判定箱内**所有存活实体**一律调用 {@code thunderHit}
     * (箱体 ±3 格、垂直 +6+3),没有任何阵营过滤——会把攻击者自己、友方宠物、中立动物一起打,
     * 还会顺手点燃它们。本模组按用户裁决收窄为"仅对敌对目标(含被激怒的中立目标)生效":
     * 其余实体在 {@code EntityThunderHitMixin} 里整段取消——**既不受伤也不被点燃**。
     */
    public static boolean isValidLightningTarget(net.minecraft.world.entity.Entity target) {
        if (target == null) return false;
        if (target instanceof net.minecraft.world.entity.monster.Enemy) return true;
        return target instanceof net.minecraft.world.entity.NeutralMob neutral && neutral.isAngry();
    }

    /** 该闪电是否为本模组电磁炮降下的雷击。 */
    public static boolean isRailgunBolt(LightningBolt bolt) {
        return bolt != null && RAILGUN_BOLTS.contains(bolt);
    }
}
