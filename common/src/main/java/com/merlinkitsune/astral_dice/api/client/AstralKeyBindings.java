package com.merlinkitsune.astral_dice.api.client;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 附属按键注册表（版本中立，不引用 {@code KeyMapping}）。
 *
 * <p>common 不能引用客户端类，所以这里只存「id + 默认键码」。五条加载线在
 * {@code RegisterKeyMappingsEvent} 里为每条 {@link Request} 构造本线的 {@code KeyMapping}
 * 并注册；翻译键统一为 {@code key.astral_dice.<id>}，分类沿用本线既有分类
 * （1.20.1 / 1.21.1 为 {@code key.categories.astral_dice}，26.1.2 为
 * {@code KeyMapping.Category} 常量）。
 *
 * <p>必须在客户端初始化完成前登记（模组构造期 / client setup 早期），
 * 晚于按键注册事件就无法再进 {@code Options} 了。
 */
public final class AstralKeyBindings {

    private static final Logger LOGGER = LoggerFactory.getLogger(AstralKeyBindings.class);
    private static final List<Request> REQUESTS = new CopyOnWriteArrayList<>();

    private AstralKeyBindings() {
    }

    /**
     * 登记一个附属按键。
     *
     * @param id           唯一 id（同时用作翻译键后缀：{@code key.astral_dice.<id>}）
     * @param defaultKeyCode GLFW 键码（如 {@code GLFW.GLFW_KEY_K}）
     */
    public static void register(String id, int defaultKeyCode) {
        Objects.requireNonNull(id, "id");
        if (id.isBlank()) {
            throw new IllegalArgumentException("id must not be blank");
        }
        synchronized (REQUESTS) {
            for (Request existing : REQUESTS) {
                if (existing.id().equals(id)) {
                    LOGGER.warn("AP_KEY_API: duplicate id={} ignored", id);
                    return;
                }
            }
            REQUESTS.add(new Request(id, defaultKeyCode));
        }
        LOGGER.info("AP_KEY_API: registered id={}", id);
    }

    /** 当前已登记按键的只读快照（按登记顺序）。 */
    public static List<Request> registered() {
        return List.copyOf(REQUESTS);
    }

    /** 一条按键请求。 */
    public record Request(String id, int defaultKeyCode) {
        public Request {
            Objects.requireNonNull(id, "id");
            if (id.isBlank()) {
                throw new IllegalArgumentException("id must not be blank");
            }
        }

        /** 本线应使用的翻译键（只取 id 的 path 部分）。 */
        public String translationKey() {
            int colon = id.indexOf(':');
            return "key.astral_dice." + (colon < 0 ? id : id.substring(colon + 1));
        }
    }
}
