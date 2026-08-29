package com.merlinkitsune.astral_dice.resource;

import com.merlinkitsune.astral_dice.item.HealingManager;
import com.merlinkitsune.astral_dice.item.StarLightManager;

import java.util.EnumMap;

/**
 * 点数流派注册表:流派类型 → {@link PlayerResource} 实现的统一入口。
 * 接入新流派:实现 {@link PlayerResource} 后调用 {@link #register} 注册
 * (如未来"反击"流派:实现并注册到 {@link ResourceType#COUNTER})。
 */
public final class PlayerResourceRegistry {

    private static final EnumMap<ResourceType, PlayerResource> RESOURCES =
            new EnumMap<>(ResourceType.class);

    private PlayerResourceRegistry() {
    }

    // 注册流派实现
    public static void register(ResourceType type, PlayerResource resource) {
        RESOURCES.put(type, resource);
    }

    // 按类型获取流派实现(未注册返回 null)
    public static PlayerResource get(ResourceType type) {
        return RESOURCES.get(type);
    }

    // 是否已注册
    public static boolean isRegistered(ResourceType type) {
        return RESOURCES.containsKey(type);
    }

    static {
        register(ResourceType.HEALING, HealingManager.RESOURCE);
        register(ResourceType.STARLIGHT, StarLightManager.RESOURCE);
        // COUNTER(反击)为未来流派:实现 PlayerResource 后在此注册
    }
}
