package com.merlinkitsune.astral_dice.event;

import net.minecraft.resources.Identifier;

/**
 * 全部事件 id 常量(2026-10-10 事件体系重写)。
 *
 * <p>共 <b>13 条</b>事件:用户点名的 12 个新事件(空壳) + 秘密侦探的「调查阶段」(一条事件关联 4 张图标,
 * {@code inPublicPool=true} 但需队内有秘密侦探才可能抽到)。
 * id 一律 ascii snake_case;图标 = {@code assets/astral_dice/textures/gui/event/<id>.png}。
 */
public final class AstralEventIds {
    private AstralEventIds() {
    }

    /** 事件 id 命名空间(本模组)。 */
    public static final String NAMESPACE = "astral_dice";

    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(NAMESPACE, path);
    }

    // ── 公开事件池(12 条空壳;另加下方 1 条秘密侦探专属 ⇒ 静态在池 13) ──
    public static final Identifier RED_HEAT_WARNING = id("red_heat_warning");
    public static final Identifier SWIFT_STRIDE = id("swift_stride");
    public static final Identifier STORM_APPROACHING = id("storm_approaching");
    public static final Identifier LEARN_FROM_OTHERS = id("learn_from_others");
    public static final Identifier SHOP_BONANZA = id("shop_bonanza");
    public static final Identifier HAND_ERASURE = id("hand_erasure");
    public static final Identifier WORLD_COLLAPSE = id("world_collapse");
    public static final Identifier GIFT_FROM_SKY = id("gift_from_sky");
    public static final Identifier ANGEL_DESCENT = id("angel_descent");
    public static final Identifier PROBLEM_DELIVERY = id("problem_delivery");
    public static final Identifier CROWDED_PASSAGE = id("crowded_passage");
    public static final Identifier BATTLE_FRENZY = id("battle_frenzy");

    // ── 秘密侦探专属(阶级事件;已 inPublicPool=true,额外条件是队内存在秘密侦探) ──
    public static final Identifier INVESTIGATION_STAGE = id("investigation_stage");
}
