package com.merlinkitsune.astral_dice.event;

import com.merlinkitsune.astral_dice.event.api.AstralEvent;
import com.merlinkitsune.astral_dice.event.api.AstralEventEffect;
import com.merlinkitsune.astral_dice.event.api.AstralEventPolarity;
import com.merlinkitsune.astral_dice.event.api.AstralEventTrigger;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * 事件注册表(2026-10-10 事件体系重写)。
 *
 * <p>本轮注册 <b>13 条空壳事件</b>(12 个新事件 + 秘密侦探「调查阶段」),效果一律
 * {@link AstralEventEffect#NOOP}、默认计时器 0(用户裁决:暂时都不加计时器)。
 *
 * <p><b>启动自检</b>({@link #ensureInit()}):① id 唯一(注册时即校验);② 每条必须有非空 nameKey 与 icon;
 * ③ 图标材质文件存在 —— 默认只 <b>warn</b>(机器行),加 {@code -Dastral_dice.strictEventRegistry=true} 升级为致命错误;
 * ④ 打印机器行 {@code AP_EVENT_REG: count=…:pool=…} 供测试断言。三语 lang 键的齐全性由静态守门
 * {@code scripts/verify/verify_event_registry.ps1} 负责。
 */
public final class AstralEventRegistry {
    private static final Logger LOGGER = LoggerFactory.getLogger(AstralEventRegistry.class);

    private static final Map<ResourceLocation, AstralEvent> BY_ID = new LinkedHashMap<>();
    private static final List<AstralEvent> ORDER = new ArrayList<>();
    private static boolean initialized;

    private AstralEventRegistry() {
    }

    static {
        registerAll();
    }

    /** 注册一条事件(id 重复立即抛错,避免静默覆盖)。 */
    public static void register(AstralEvent event) {
        AstralEvent prev = BY_ID.putIfAbsent(event.id(), event);
        if (prev != null) {
            throw new IllegalStateException("[Astral Dice] 事件 id 重复注册: " + event.id());
        }
        ORDER.add(event);
    }

    /** 按 id 取事件(未注册返回 empty)。 */
    public static Optional<AstralEvent> get(ResourceLocation id) {
        ensureInit();
        return Optional.ofNullable(BY_ID.get(id));
    }

    /** 全部已注册事件(注册顺序)。 */
    public static Collection<AstralEvent> all() {
        ensureInit();
        return List.copyOf(ORDER);
    }

    /** 公共事件池:非 secretOnly 且 {@code inPublicPool}(可被「抽事件」物品/方块抽到)。 */
    public static List<AstralEvent> publicPool() {
        ensureInit();
        return ORDER.stream().filter(e -> e.inPublicPool() && !e.secretOnly()).toList();
    }

    /**
     * 从公共事件池随机抽一条。
     *
     * <p>Review Focus 1:池为空时必须<b>安全失败</b>(返回 empty,不抛异常),调用方记 warn 机器行。
     */
    public static Optional<AstralEvent> drawRandom(RandomSource random) {
        List<AstralEvent> pool = publicPool();
        if (pool.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(pool.get(random.nextInt(pool.size())));
    }

    /** 自检 + 机器行(幂等,首次访问时执行)。 */
    public static synchronized void ensureInit() {
        if (initialized) {
            return;
        }
        initialized = true;
        validate();
        LOGGER.info("AP_EVENT_REG: count={}:pool={}", ORDER.size(), publicPool().size());
    }

    /** 校验每条事件的必填字段;图标缺失默认 warn(严格模式抛错)。 */
    private static void validate() {
        boolean strict = Boolean.getBoolean("astral_dice.strictEventRegistry");
        for (AstralEvent event : ORDER) {
            if (event.nameKey() == null || event.nameKey().isBlank()) {
                throw new IllegalStateException("[Astral Dice] 事件缺少 nameKey: " + event.id());
            }
            if (event.icon() == null) {
                throw new IllegalStateException("[Astral Dice] 事件缺少 icon: " + event.id());
            }
            if (event.triggers().isEmpty()) {
                throw new IllegalStateException("[Astral Dice] 事件未声明任何触发器: " + event.id());
            }
            String path = "/assets/" + event.icon().getNamespace() + "/textures/gui/event/"
                    + event.icon().getPath() + ".png";
            if (AstralEventRegistry.class.getResource(path) == null) {
                String msg = "[Astral Dice] 事件图标缺失: " + event.id() + " -> " + path;
                if (strict) {
                    throw new IllegalStateException(msg);
                }
                LOGGER.warn("AP_EVENT_ICON_MISSING: {}", event.id());
                LOGGER.warn(msg);
            }
        }
    }

    // ────────────────────────── 13 条空壳注册(本轮) ──────────────────────────
    private static void registerAll() {
        double r = AstralEventSystem.EVENT_TARGET_RADIUS;
        // 12 个公开事件:本轮只注册元数据 + 触发器,效果一律 NOOP、无计时器。
        publicEvent(AstralEventIds.RED_HEAT_WARNING, AstralEventPolarity.NEGATIVE, r);
        publicEvent(AstralEventIds.SWIFT_STRIDE, AstralEventPolarity.POSITIVE, r);
        publicEvent(AstralEventIds.STORM_APPROACHING, AstralEventPolarity.NEGATIVE, r);
        publicEvent(AstralEventIds.LEARN_FROM_OTHERS, AstralEventPolarity.NEUTRAL, r);
        publicEvent(AstralEventIds.SHOP_BONANZA, AstralEventPolarity.POSITIVE, r);
        publicEvent(AstralEventIds.HAND_ERASURE, AstralEventPolarity.NEGATIVE, r);
        publicEvent(AstralEventIds.WORLD_COLLAPSE, AstralEventPolarity.NEGATIVE, r);
        publicEvent(AstralEventIds.GIFT_FROM_SKY, AstralEventPolarity.NEUTRAL, r);
        publicEvent(AstralEventIds.ANGEL_DESCENT, AstralEventPolarity.POSITIVE, r);
        publicEvent(AstralEventIds.PROBLEM_DELIVERY, AstralEventPolarity.NEUTRAL, r);
        publicEvent(AstralEventIds.CROWDED_PASSAGE, AstralEventPolarity.NEGATIVE, r);
        publicEvent(AstralEventIds.BATTLE_FRENZY, AstralEventPolarity.POSITIVE, r);

        // 秘密侦探专属:阶级事件。2026-10-10 用户裁决 —— 只有击杀「隐匿调查」能提升阶级;
        // 其它来源只触发当前阶级。本轮 inPublicPool=false(后续按特定条件再入池,届时改此标志)。
        register(new AstralEvent(
                AstralEventIds.INVESTIGATION_STAGE,
                "event.astral_dice.investigation_stage.name",
                AstralEventIds.id("investigation_stage"),
                AstralEventPolarity.POSITIVE,   // 该事件针对敌对目标 ⇒ 正面(用户 2026-10-10 裁定)
                true,
                false,
                Set.of(AstralEventTrigger.INVESTIGATION_KILL),
                r,
                AstralEventEffect.NOOP,
                0));
    }

    /** 公开事件池条目的统一构造(效果空壳、无计时器)。 */
    private static void publicEvent(ResourceLocation id, AstralEventPolarity polarity, double radius) {
        register(new AstralEvent(
                id,
                "event." + id.getNamespace() + "." + id.getPath() + ".name",
                id,
                polarity,
                false,
                true,
                Set.of(AstralEventTrigger.ITEM_USE, AstralEventTrigger.BLOCK_USE, AstralEventTrigger.CUSTOM),
                radius,
                AstralEventEffect.NOOP,
                0));
    }
}
