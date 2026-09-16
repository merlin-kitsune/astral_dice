package com.merlinkitsune.astral_dice.damage;

import com.merlinkitsune.astral_dice.combat.HostileTargets;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.OwnableEntity;

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
     * 这道电磁炮雷击是否应当命中该实体(完整口径,**必须带闪电实例调用**)。
     *
     * <p>① 先过统一入口 {@link HostileTargets#isHostile(net.minecraft.world.entity.Entity, net.minecraft.world.entity.Entity)}
     * (双参口径,{@code viewer} = 落雷来源玩家 = {@code LightningBolt#getCause()},由
     * {@code RailgunChipItem#strike} 的 {@code bolt.setCause(cause)} 写入):
     * **敌对生物(`Enemy`)或已被激怒的中立生物(`NeutralMob#isAngry()`)**,以及
     * **「非同队伍、且曾主动攻击过施放者的玩家」**——与其它 AOE 的双参敌对口径完全一致
     * (2026-09-15 用户裁决:电磁炮落雷同样参与"双向意图记录 + 仅同队豁免"口径);
     * 平静的狼/铁傀儡/北极熊/蜜蜂、攻击者自己、中立动物、盔甲架等一律不算。
     *
     * <p>② 再排除**施放者自己拥有的宠物**({@link OwnableEntity} 的 owner == 闪电的
     * {@code cause}):被激怒的已驯服宠物(如自己养的狼)属"友方宠物",永不挨自己的雷击
     * (2026-09-14 用户裁决)。其它玩家的宠物不在排除范围内。
     *
     * <p>原版 {@code LightningBolt#tick} 对判定箱内**所有存活实体**一律调用 {@code thunderHit}
     * (箱体 ±3 格、垂直 +6+3),没有任何阵营过滤——会把攻击者自己、友方宠物、中立动物一起打,
     * 还会顺手点燃它们,并触发 {@code onEntityStruckByLightning} 事件与
     * {@code CHANNELED_LIGHTNING}("Very Very Frightening")成就、把村民转成女巫、把猪转成
     * 僵尸猪灵、秒杀海龟(这些覆写 {@code thunderHit} 的生物**不经过** {@code Entity#thunderHit},
     * 所以在该方法里拦截根本挡不住)。故本模组把白名单**上移到
     * {@code LightningBolt#tick} 的目标筛选**——见 {@code mixin/LightningBoltStrikeScopeMixin}:
     * 非敌对目标**在进入循环之前就被剔除**,既不受伤、也不被转化、也不触发事件与成就。
     * 方块着火/避雷针等落雷的世界行为按原版保留。
     */
    public static boolean isValidLightningTarget(net.minecraft.world.entity.Entity target, LightningBolt bolt) {
        if (target == null) return false;
        // 「视谁为敌」的上下文 = 落雷来源玩家(无闪电实例时 viewer 为 null ⇒ 玩家一律不计入敌对)
        ServerPlayer cause = bolt == null ? null : bolt.getCause();
        if (!HostileTargets.isHostile(cause, target)) return false;
        if (target instanceof OwnableEntity ownable) {
            // 26.1.2:OwnableEntity 不再暴露 getOwnerUUID(),改为 getOwner()/getOwnerReference()
            var owner = ownable.getOwner();
            if (cause != null && owner != null && cause.getUUID().equals(owner.getUUID())) return false;
        }
        return true;
    }

    /** 无闪电实例时的退化口径(拿不到来源玩家 ⇒ viewer 为 null,玩家不计入敌对;也不做宠物排除)。 */
    public static boolean isValidLightningTarget(net.minecraft.world.entity.Entity target) {
        return isValidLightningTarget(target, null);
    }

    /** 该闪电是否为本模组电磁炮降下的雷击。 */
    public static boolean isRailgunBolt(LightningBolt bolt) {
        return bolt != null && RAILGUN_BOLTS.contains(bolt);
    }
}
