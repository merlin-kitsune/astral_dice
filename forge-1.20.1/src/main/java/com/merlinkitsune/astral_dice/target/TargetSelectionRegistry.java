package com.merlinkitsune.astral_dice.target;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 目标选择动作注册表：立牌主动技能 / 效果牌按唯一 id 注册 {@link TargetSelectionAction}，
 * 触发方只需传 id 即可进入选择模式（与效果实现解耦）。
 */
public final class TargetSelectionRegistry {
    private static final Logger LOGGER = LoggerFactory.getLogger(TargetSelectionRegistry.class);

    private static final Map<String, TargetSelectionAction> ACTIONS = new ConcurrentHashMap<>();

    private TargetSelectionRegistry() {
    }

    /** 注册动作（重复 id 覆盖，记录调试日志） */
    public static void register(TargetSelectionAction action) {
        if (action == null || action.id() == null || action.id().isBlank()) {
            LOGGER.warn("[Astral Dice][TargetSelectionRegistry] 拒绝注册无效动作(id 为空)");
            return;
        }
        ACTIONS.put(action.id(), action);
        LOGGER.debug("[Astral Dice][TargetSelectionRegistry] register action={} type={}",
                action.id(), action.targetType());
    }

    /** 按 id 获取动作；未注册时记录 warn 并返回 null */
    public static TargetSelectionAction get(String id) {
        TargetSelectionAction action = id == null ? null : ACTIONS.get(id);
        if (action == null) {
            LOGGER.warn("[Astral Dice][TargetSelectionRegistry] action missing: {}", id);
        }
        return action;
    }
}
