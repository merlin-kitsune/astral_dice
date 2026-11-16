package com.merlinkitsune.astral_dice.target;

import com.merlinkitsune.starengine.target.TargetType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 目标选择器 ActionBar 的「目标类别」词表（2026-11-14 用户裁决）。
 *
 * <p>选择中的提示只显示「选择的目标准类」，**不再拼接技能 / 效果牌名字**。
 * 允许的词只有六种：{@link #HOSTILE}（敌对）、{@link #FRIENDLY}（友方）、
 * {@link #NEUTRAL}（中立）、{@link #ANY}（任意）、{@link #PLAYER}（玩家）、
 * {@link #MAID}（女仆）。
 *
 * <p>默认类别按会话 {@link TargetType} 推导（见 {@link #DEFAULT_BY_TYPE}）；
 * 附属模组可对**自己的动作 id** 覆写类别 —— 例：TLMDice 的女仆选择动作注册
 * {@link #MAID}。覆写只影响文案，不改变过滤语义。
 */
public final class TargetPromptCategories {

    private static final Logger LOGGER = LoggerFactory.getLogger(TargetPromptCategories.class);

    public static final String HOSTILE = "hostile";
    public static final String FRIENDLY = "friendly";
    public static final String NEUTRAL = "neutral";
    public static final String ANY = "any";
    public static final String PLAYER = "player";
    public static final String MAID = "maid";

    /** 词表闭集：只有这六个值能进 lang 键。 */
    private static final Set<String> ALLOWED =
            Set.of(HOSTILE, FRIENDLY, NEUTRAL, ANY, PLAYER, MAID);

    /** actionId → 类别（附属模组覆写；同一 id 后注册者覆盖先注册者）。 */
    private static final Map<String, String> BY_ACTION = new ConcurrentHashMap<>();

    /**
     * TargetType → 默认类别。
     *
     * <p>口径（2026-11-14）：ENEMY / ENEMY_OR_RIVAL → 敌对；CREATURE / CREATURE_OR_RIVAL → 中立；
     * NON_HOSTILE → 友方；LIVING → 任意；PLAYER → 玩家。CREATURE 实际含敌对生物，词表里没有更贴近的
     * 单词，暂按「中立」显示；语义仍以 {@link TargetType} 过滤为准。
     */
    private static final Map<TargetType, String> DEFAULT_BY_TYPE = Map.of(
            TargetType.PLAYER, PLAYER,
            TargetType.ENEMY, HOSTILE,
            TargetType.ENEMY_OR_RIVAL, HOSTILE,
            TargetType.LIVING, ANY,
            TargetType.CREATURE, NEUTRAL,
            TargetType.CREATURE_OR_RIVAL, NEUTRAL,
            TargetType.NON_HOSTILE, FRIENDLY
    );

    private TargetPromptCategories() {
    }

    /**
     * 为某个动作 id 覆写提示类别（附属模组在客户端初始化时调用）。
     *
     * @param actionId {@code TargetSelectionRegistry} 里的动作 id
     * @param category 六种词之一；非法值记录 {@code AP_TARGET_CATEGORY_REJECT} 并忽略
     */
    public static void registerActionCategory(String actionId, String category) {
        if (actionId == null || actionId.isBlank() || category == null || !ALLOWED.contains(category)) {
            LOGGER.warn("AP_TARGET_CATEGORY_REJECT action={} category={}", actionId, category);
            return;
        }
        BY_ACTION.put(actionId, category);
        LOGGER.info("AP_TARGET_CATEGORY: action={} category={}", actionId, category);
    }

    /** 本次会话的提示类别：动作覆写优先，否则按 TargetType 推导。 */
    public static String categoryFor(String actionId, TargetType type) {
        String override = actionId == null ? null : BY_ACTION.get(actionId);
        if (override != null) {
            return override;
        }
        return DEFAULT_BY_TYPE.getOrDefault(type, ANY);
    }
}
