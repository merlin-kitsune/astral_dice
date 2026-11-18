package com.merlinkitsune.astral_dice.api.client;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 附属 HUD 图层注册表（版本中立，不引用任何客户端类）。
 *
 * <p>五条加载线在各自的 {@code RegisterGuiLayersEvent} / {@code RegisterGuiOverlaysEvent}
 * 里遍历 {@link #registered()}，把每条图层包成本线的渲染器后挂到准星上方。
 *
 * <p>id 用字符串而不是 {@code ResourceLocation}：26.1.2 已把该类改名为
 * {@code Identifier}，common 源码要同时被五线编译，不能出现任一侧独有类型。
 * 各线适配器自行用本线的 id 类型解析（建议命名空间取本模组 MODID）。
 *
 * <p>只在客户端有意义；在专用服务器上注册不会报错，只是永远没人消费。
 */
public final class AstralHudLayers {

    private static final Logger LOGGER = LoggerFactory.getLogger(AstralHudLayers.class);
    private static final List<Registration> REGISTRATIONS = new CopyOnWriteArrayList<>();

    private AstralHudLayers() {
    }

    /**
     * 登记一条 HUD 图层。
     *
     * @param id    唯一 id，形如 {@code astral_dice:my_layer}
     * @param layer 渲染回调
     */
    public static void register(String id, AstralHudLayer layer) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(layer, "layer");
        if (id.isBlank()) {
            throw new IllegalArgumentException("id must not be blank");
        }
        synchronized (REGISTRATIONS) {
            for (Registration existing : REGISTRATIONS) {
                if (existing.id().equals(id)) {
                    LOGGER.warn("AP_HUD_API: duplicate id={} ignored", id);
                    return;
                }
            }
            REGISTRATIONS.add(new Registration(id, layer));
        }
        LOGGER.info("AP_HUD_API: registered id={}", id);
    }

    /**
     * 取 id 的 path 部分。
     *
     * <p>1.20.1 的 overlay 注册只接受 path（命名空间固定为本模组），其余线为了行为一致
     * 也统一按 path 注册；因此 {@code other_mod:foo} 与 {@code foo} 落在同一个位置。
     */
    public static String pathOf(String id) {
        Objects.requireNonNull(id, "id");
        int colon = id.indexOf(':');
        return colon < 0 ? id : id.substring(colon + 1);
    }

    /** 当前已登记图层的只读快照（按登记顺序）。 */
    public static List<Registration> registered() {
        return List.copyOf(REGISTRATIONS);
    }

    /** 一条已登记的图层。 */
    public record Registration(String id, AstralHudLayer layer) {
        public Registration {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(layer, "layer");
        }
    }
}
