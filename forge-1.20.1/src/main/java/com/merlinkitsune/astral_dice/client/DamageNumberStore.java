package com.merlinkitsune.astral_dice.client;

import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 客户端伤害数字存储(替代库 {@code starenginelib} 的 {@code ClientDamageNumbers})。
 *
 * <h2>为什么不再直接用库那一份</h2>
 * 库的 {@code ClientDamageNumbers} 是 {@code Map<Integer, FloatingNumber>} —— <b>按 entityId 单键</b>、
 * 后到覆盖先到,且只存「数值 / 颜色 / 剩余 tick」。2026-09-26 用户裁决后有两项新要求它表达不了:
 * <ol>
 *   <li><b>同一目标要同时显示红、绿两个数字</b>(攻击力/伤害加成类 与 法伤/技能伤害类 各一条总值)
 *       ⇒ 必须按 (entityId, 组别) 双键存储。此处直接用**颜色**当第二键 —— 颜色天然唯一标识组别,
 *       于是网络载荷无需新增字段。</li>
 *   <li><b>数字不得再跟随目标跳动</b> ⇒ 需要一份**冻结的世界坐标**;又因为「移出扩散」要加
 *       ±30° 的扩散偏移,还需要为每条数字保存它自己的扩散角。</li>
 * </ol>
 *
 * <p>⚠️ **刻意不改库**:库的 {@code ClientDamageNumbers} 是它对外的既有契约(其它消费方可能仍在用),
 * 改它的键语义属于破坏性变更,会牵动「bump 版本 + publishToMavenLocal + 三线 pin + 五处文案」整条链。
 * 本类只是本模组自己的客户端展示层,故留在 mod 侧。库那一份不再被喂数据,保持原样即可。
 *
 * <h2>冻结坐标来自服务端</h2>
 * 坐标由 {@code combat/DamageNumberAggregator} 在**命中那一刻**取好随包下发(见
 * {@code network/DamageNumberPayload}) —— 不在客户端取实体位置,原因是聚合后的包在 tick 末尾才发,
 * 击杀那一下的目标在客户端可能已被移除(取不到实体 ⇒ 数字整条丢失)。直接收坐标同时也天然满足
 * 「不再跟随目标」。
 */
public final class DamageNumberStore {

    /** 单条数字的存活 tick 数(与改造前一致)。 */
    public static final int DURATION = 40;
    /** 「移出扩散」的扩散角上限(度):每条数字的方向在 ±该值内随机。 */
    public static final float MAX_SPREAD_DEGREES = 30.0f;

    private static final Map<Long, Entry> ACTIVE = new HashMap<>();

    private DamageNumberStore() {
    }

    /**
     * 收到一条服务端跳数字。
     *
     * @param entityId 受击者实体 id(仅用作分槽键)
     * @param damage   该组别本 tick 的总值
     * @param color    组别颜色(同时是第二分槽键)
     * @param x        命中那一刻冻结的世界坐标
     */
    public static void add(int entityId, int damage, int color, double x, double y, double z) {
        if (damage <= 0) return;
        ACTIVE.put(key(entityId, color),
                new Entry(damage, color, x, y, z, randomSpreadRadians(), DURATION));
    }

    /** 当前在显的数字(渲染顺序不重要,每条自带扩散角与冻结坐标)。 */
    public static Collection<Entry> active() {
        return ACTIVE.values();
    }

    public static boolean isEmpty() {
        return ACTIVE.isEmpty();
    }

    /** 每客户端 tick 推进一次(在 {@code ClientTickHandler} 里调用)。 */
    public static void tick() {
        Iterator<Map.Entry<Long, Entry>> it = ACTIVE.entrySet().iterator();
        while (it.hasNext()) {
            Entry entry = it.next().getValue();
            entry.remaining--;
            if (entry.remaining <= 0) {
                it.remove();
            }
        }
    }

    public static void clear() {
        ACTIVE.clear();
    }

    /** ±{@link #MAX_SPREAD_DEGREES} 度内的随机扩散角(弧度)。 */
    private static float randomSpreadRadians() {
        double degrees = (ThreadLocalRandom.current().nextDouble() * 2.0 - 1.0) * MAX_SPREAD_DEGREES;
        return (float) Math.toRadians(degrees);
    }

    private static long key(int entityId, int color) {
        return ((long) entityId << 32) | (color & 0xFFFFFFFFL);
    }

    /** 一条正在漂浮的伤害数字。字段全部 final(除剩余 tick)⇒ 坐标与扩散角在生成后不再变化。 */
    public static final class Entry {
        public final int damage;
        public final int color;
        /** 冻结的世界坐标(命中那一刻的目标眼部下移 0.5 格)。 */
        public final double x;
        public final double y;
        public final double z;
        /** 该条数字自己的扩散角(弧度,±30°)。 */
        public final float spreadRadians;
        public int remaining;

        Entry(int damage, int color, double x, double y, double z, float spreadRadians, int remaining) {
            this.damage = damage;
            this.color = color;
            this.x = x;
            this.y = y;
            this.z = z;
            this.spreadRadians = spreadRadians;
            this.remaining = remaining;
        }
    }
}
