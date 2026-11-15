package com.merlinkitsune.astral_dice.api.combat;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 骰战属性钩子注册表：按「属性 × 阶段」分组，优先级从大到小执行。
 *
 * <p>只有安装了战斗接线的加载器线（NeoForge 1.21.1 / Forge 1.20.1）才接受注册；
 * 其余线可以编译，但 {@link #register} 会抛 {@link IllegalStateException}。
 * 注册与执行都可能跨线程发生，内部用注册表锁保护快照。
 */
public final class CombatStatHooks {

    private static final Logger LOGGER = LoggerFactory.getLogger(CombatStatHooks.class);
    private static final Map<CombatStat, Map<CombatStatStage, List<Entry>>> ENTRIES =
            new EnumMap<>(CombatStat.class);
    private static volatile boolean installed;

    static {
        for (CombatStat stat : CombatStat.values()) {
            Map<CombatStatStage, List<Entry>> stages = new EnumMap<>(CombatStatStage.class);
            for (CombatStatStage stage : CombatStatStage.values()) {
                stages.put(stage, new ArrayList<>());
            }
            ENTRIES.put(stat, stages);
        }
    }

    private CombatStatHooks() {
    }

    /** 标记本线已接线；可重复调用。 */
    public static void install() {
        installed = true;
    }

    /** 本线是否已接线。 */
    public static boolean isInstalled() {
        return installed;
    }

    /** 注册一个属性钩子；未接线时抛异常，避免静默无效。 */
    public static CombatStatHandle register(CombatStat stat, CombatStatStage stage, int priority,
                                            CombatStatHook hook) {
        Objects.requireNonNull(stat, "stat");
        Objects.requireNonNull(stage, "stage");
        Objects.requireNonNull(hook, "hook");
        if (!installed) {
            throw new IllegalStateException(
                    "Combat stat hooks are not installed on this loader line (only NeoForge 1.21.1 / Forge 1.20.1).");
        }
        Entry entry = new Entry(priority, hook);
        synchronized (ENTRIES) {
            List<Entry> list = ENTRIES.get(stat).get(stage);
            list.add(entry);
            list.sort(Comparator.comparingInt((Entry value) -> value.priority).reversed());
        }
        return new CombatStatHandle(() -> {
            entry.active = false;
            synchronized (ENTRIES) {
                ENTRIES.get(stat).get(stage).remove(entry);
            }
        });
    }

    /** 按阶段链式执行钩子；未接线或显示用途时原样返回。 */
    public static double apply(CombatStatContext context, CombatStat stat, CombatStatStage stage, double value) {
        if (!installed || context == null || context.display()) {
            return value;
        }
        List<Entry> snapshot;
        synchronized (ENTRIES) {
            snapshot = List.copyOf(ENTRIES.get(stat).get(stage));
        }
        double result = value;
        for (Entry entry : snapshot) {
            if (!entry.active) {
                continue;
            }
            try {
                result = entry.hook.apply(context, result);
            } catch (Throwable throwable) {
                LOGGER.error("Combat stat hook failed during {} {}: {}", stat, stage, throwable.toString());
            }
        }
        return result;
    }

    /** 清空全部钩子；用于测试或显式重置。 */
    public static void clear() {
        synchronized (ENTRIES) {
            for (Map<CombatStatStage, List<Entry>> stages : ENTRIES.values()) {
                for (List<Entry> list : stages.values()) {
                    list.clear();
                }
            }
        }
    }

    private static final class Entry {
        private final int priority;
        private final CombatStatHook hook;
        private volatile boolean active = true;

        private Entry(int priority, CombatStatHook hook) {
            this.priority = priority;
            this.hook = hook;
        }
    }
}
