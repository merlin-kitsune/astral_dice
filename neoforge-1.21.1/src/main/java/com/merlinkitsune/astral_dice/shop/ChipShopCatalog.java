package com.merlinkitsune.astral_dice.shop;

import com.merlinkitsune.astral_dice.AstralDiceMod;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * 筹码商店的**静态池与全部冻结常量**（判据正本 = {@code docs/chip-shop-logic-plan.md} §1 / §2）。
 *
 * <p><b>池 = 67 枚</b>：{@code ModItems} 中除 {@code blank_chip}（合成材料，不入池、也不参与「已拥有」判定）
 * 之外的**全部** {@code *_chip} 物品；品质分布 稀有 19 / 史诗 29 / 传奇 19（已与 {@code ModItems} 的
 * {@code AstralRarities} 逐条交叉核对一致）。品质码 1/2/3 的档位口径（rare/epic/legendary）与 {@code ChipShopClientState#qualityFrameIndexOf}
 * 的返回值一致（两者互不调用：品质码由服务端按候选现算、经 DataSlot 同步），**永不出现普通 / 巅峰 / 奇特**（R20）；
 * 2026-10-22 起屏幕恢复绘制品质外框（{@code chipshop_quality_1/2/3} 回到绘制路径；2026-10-21 的「无边框」只针对图标素材本身）。
 *
 * <p><b>流派</b>（{@link School}）的权威来源 = 帕秋莉手册条目的 {@code category} 字段；
 * <b>类别</b>（{@link Category}）是商店「上次购买类别加成」用的五分类。两者都必须**每枚恰一类**（G10）。
 *
 * <p><b>常数一律 {@code public static final}，不做配置项</b>（用户裁决）：
 * <ul>
 *   <li>{@link #QUALITY_PROB}：按骰子星阶（0..3★）查的品质概率表，逐字落地 60/37/3 · 60/37/3 · 40/45/15 · 25/50/25；</li>
 *   <li>{@link #SCHOOL_ALPHA}=0.60（同流派已装备 N 枚 ⇒ 权重 ×(1+0.60N)）、{@link #CATEGORY_ALPHA}=2.0、
 *       {@link #SLOT_DECAY}=0.25（slot1/2 用 {@code (factor-1)×0.25+1}）；</li>
 *   <li>{@link #PRICE_INITIAL}/{@link #PRICE_STEP}/{@link #PRICE_MAX} = 10/5/100；
 *       {@link #REFRESH_INITIAL}/{@link #REFRESH_MAX}/{@link #REFRESH_REGEN_TICKS} = 3/5/36000。</li>
 * </ul>
 *
 * <p><b>图标路径一律 ASCII</b>：素材原文件名是中文（{@code ATM机.png}），而 {@code ResourceLocation}
 * 只接受 {@code [a-z0-9/._-]} ⇒ 入库时**按筹码 id 重命名**为
 * {@code assets/astral_dice/textures/gui/chip_shop/chips/<chip_id>.png}，故本类里**没有任何中文字面量**。
 */
public final class ChipShopCatalog {

    /** 货位数（条带 cell1..cell3） */
    public static final int OFFER_COUNT = 3;

    /**
     * 筹码合成总开关的**权威默认值**(与 {@code config.ModCommonConfig#ENABLE_CHIP_RECIPES} 对应:
     * 该配置项以此为默认定义 —— 关闭时 67 枚筹码无法通过合成获得)。
     */
    public static final boolean CHIP_RECIPES_DEFAULT = false;

    /** 品质码：稀有 / 史诗 / 传奇（= 品质外框 quality_1/2/3 的序号） */
    public static final int TIER_RARE = 1;
    public static final int TIER_EPIC = 2;
    public static final int TIER_LEGENDARY = 3;

    /** 村民交易用稀有度权重（与卡牌交易员一致：稀有 6 / 史诗 3 / 传奇 2）。 */
    public static final int WEIGHT_RARE = 6;
    public static final int WEIGHT_EPIC = 3;
    public static final int WEIGHT_LEGENDARY = 2;

    /** 品质概率表，下标 = 骰子星阶 0..3★（0★ 与 1★ 同表）；三档 = 稀有 / 史诗 / 传奇 */
    public static final double[][] QUALITY_PROB = {
            {0.60, 0.37, 0.03},
            {0.60, 0.37, 0.03},
            {0.40, 0.45, 0.15},
            {0.25, 0.50, 0.25},
    };

    /** 同流派已装备 N 枚 ⇒ 该流派候选权重 ×(1 + SCHOOL_ALPHA × N) */
    public static final double SCHOOL_ALPHA = 0.60;
    /** 「上次购买类别」同类别候选的权重倍率（仅 slot0 全强度） */
    public static final double CATEGORY_ALPHA = 2.0;
    /** slot1/2 的类别加成衰减：用 (factor - 1) × SLOT_DECAY + 1 */
    public static final double SLOT_DECAY = 0.25;

    /** 价格 = min(PRICE_MAX, PRICE_INITIAL + PRICE_STEP × 累计购买次数) */
    public static final int PRICE_INITIAL = 10;
    public static final int PRICE_STEP = 5;
    public static final int PRICE_MAX = 100;

    /** 刷新次数：初值 / 上限 / 恢复周期（36000 ticks = 30:00） */
    public static final int REFRESH_INITIAL = 3;
    public static final int REFRESH_MAX = 5;
    public static final int REFRESH_REGEN_TICKS = 36000;

    /** 筹码图标的贴图目录（相对 {@code assets/astral_dice/}） */
    public static final String ICON_DIR = "textures/gui/chip_shop/chips/";

    /** 流派（权威 = 帕秋莉手册条目的 category） */
    public enum School {
        /** 星光 */
        STARLIGHT,
        /** 治愈 */
        HEALING,
        /** 标记 */
        MARK,
        /** 充能 */
        CHARGE,
        /** 泛用 */
        GENERIC;

        public String key() {
            return name().toLowerCase(Locale.ROOT);
        }

        public static Optional<School> byKey(String key) {
            if (key == null) {
                return Optional.empty();
            }
            for (School value : values()) {
                if (value.key().equals(key)) {
                    return Optional.of(value);
                }
            }
            return Optional.empty();
        }
    }

    /** 类别（商店「上次购买类别」加成用；每枚筹码恰一类） */
    public enum Category {
        /** 攻击力 */
        ATTACK,
        /** 强化 */
        REINFORCE,
        /** 辅助 */
        SUPPORT,
        /** 治疗 */
        HEAL,
        /** 经济 */
        ECONOMY;

        public String key() {
            return name().toLowerCase(Locale.ROOT);
        }

        public static Optional<Category> byKey(String key) {
            if (key == null) {
                return Optional.empty();
            }
            for (Category value : values()) {
                if (value.key().equals(key)) {
                    return Optional.of(value);
                }
            }
            return Optional.empty();
        }
    }

    /** 池内一枚筹码：id（= 物品注册路径）、品质码、流派、类别 */
    public record Entry(String id, int tier, School school, Category category) {

        public ResourceLocation itemId() {
            return ResourceLocation.fromNamespaceAndPath(AstralDiceMod.MODID, id);
        }

        /** 物品（注册表缺失时退化为 {@code minecraft:air}，不抛异常 —— 便于旧存档 / 裁剪线安全降级） */
        public Item item() {
            return BuiltInRegistries.ITEM.getOptional(itemId()).orElse(Items.AIR);
        }

        public ItemStack stack() {
            return new ItemStack(item());
        }

        /** 图标贴图路径（相对 {@code assets/astral_dice/}，一律 ASCII） */
        public String iconPath() {
            return ICON_DIR + id + ".png";
        }

        /** 图标 ResourceLocation（客户端 blit 用） */
        public ResourceLocation iconLocation() {
            return ResourceLocation.fromNamespaceAndPath(AstralDiceMod.MODID, iconPath());
        }
    }

    /** 67 枚池（顺序 = 计划表 §1 的顺序；与 ModItems / 手册三方 67/67 吻合） */
    public static final List<Entry> POOL = List.of(
            e("atm_chip", TIER_RARE, School.STARLIGHT, Category.ECONOMY),
            e("bank_card_high_chip", TIER_EPIC, School.STARLIGHT, Category.ECONOMY),
            e("bank_card_low_chip", TIER_RARE, School.STARLIGHT, Category.ECONOMY),
            e("bank_card_unlimited_chip", TIER_LEGENDARY, School.STARLIGHT, Category.ECONOMY),
            e("eight_sided_dice_chip", TIER_RARE, School.STARLIGHT, Category.ECONOMY),
            e("extra_battery_chip", TIER_EPIC, School.GENERIC, Category.SUPPORT),
            e("flashlight_burst_chip", TIER_LEGENDARY, School.STARLIGHT, Category.ATTACK),
            e("flashlight_chip", TIER_EPIC, School.STARLIGHT, Category.ATTACK),
            e("fortune_stick_chip", TIER_EPIC, School.STARLIGHT, Category.ATTACK),
            e("golden_shooting_star_chip", TIER_LEGENDARY, School.STARLIGHT, Category.ATTACK),
            e("purple_shooting_star_chip", TIER_EPIC, School.STARLIGHT, Category.ATTACK),
            e("star_coin_hammer_chip", TIER_LEGENDARY, School.STARLIGHT, Category.ATTACK),
            e("abyss_reagent_chip", TIER_EPIC, School.HEALING, Category.HEAL),
            e("big_bowl_stew_chip", TIER_LEGENDARY, School.HEALING, Category.HEAL),
            e("buffer_shield_chip", TIER_RARE, School.HEALING, Category.HEAL),
            e("candy_chip", TIER_EPIC, School.HEALING, Category.HEAL),
            e("cutter_blade_chip", TIER_LEGENDARY, School.HEALING, Category.ATTACK),
            e("cutter_chip", TIER_EPIC, School.HEALING, Category.ATTACK),
            e("friendship_badge_chip", TIER_EPIC, School.HEALING, Category.HEAL),
            e("medkit_complete_chip", TIER_LEGENDARY, School.HEALING, Category.HEAL),
            e("medkit_emergency_chip", TIER_RARE, School.HEALING, Category.HEAL),
            e("vitamin_pill_chip", TIER_EPIC, School.HEALING, Category.HEAL),
            e("eagle_scope_chip", TIER_LEGENDARY, School.MARK, Category.ATTACK),
            e("hand_fan_big_chip", TIER_EPIC, School.MARK, Category.SUPPORT),
            e("hand_fan_small_chip", TIER_RARE, School.MARK, Category.SUPPORT),
            e("magic_quiver_chip", TIER_EPIC, School.MARK, Category.SUPPORT),
            e("marker_sprayer_chip", TIER_RARE, School.MARK, Category.SUPPORT),
            e("ninja_star_chip", TIER_LEGENDARY, School.MARK, Category.ATTACK),
            e("scope_chip", TIER_EPIC, School.MARK, Category.ATTACK),
            e("sensor_chip", TIER_EPIC, School.MARK, Category.SUPPORT),
            e("target_chip", TIER_RARE, School.MARK, Category.ATTACK),
            e("advanced_peripherals_chip", TIER_EPIC, School.CHARGE, Category.ATTACK),
            e("airbag_chip", TIER_EPIC, School.CHARGE, Category.SUPPORT),
            e("current_core_chip", TIER_EPIC, School.CHARGE, Category.REINFORCE),
            e("electric_glove_chip", TIER_EPIC, School.CHARGE, Category.ATTACK),
            e("electric_sword_chip", TIER_RARE, School.CHARGE, Category.ATTACK),
            e("energy_recycler_chip", TIER_RARE, School.CHARGE, Category.REINFORCE),
            e("infinite_grid_chip", TIER_EPIC, School.CHARGE, Category.REINFORCE),
            e("perpetual_motion_chip", TIER_LEGENDARY, School.CHARGE, Category.REINFORCE),
            e("primordial_core_chip", TIER_LEGENDARY, School.CHARGE, Category.REINFORCE),
            e("railgun_chip", TIER_LEGENDARY, School.CHARGE, Category.ATTACK),
            e("warp_engine_chip", TIER_RARE, School.CHARGE, Category.REINFORCE),
            e("adrenaline_high_chip", TIER_LEGENDARY, School.GENERIC, Category.ATTACK),
            e("adrenaline_low_chip", TIER_EPIC, School.GENERIC, Category.ATTACK),
            e("big_backpack_chip", TIER_EPIC, School.GENERIC, Category.REINFORCE),
            e("bookmark_chip", TIER_RARE, School.GENERIC, Category.ATTACK),
            e("boxing_gloves_high_chip", TIER_LEGENDARY, School.GENERIC, Category.ATTACK),
            e("boxing_gloves_low_chip", TIER_RARE, School.GENERIC, Category.ATTACK),
            e("boxing_gloves_medium_chip", TIER_EPIC, School.GENERIC, Category.ATTACK),
            e("cursed_sword_chip", TIER_RARE, School.GENERIC, Category.ATTACK),
            e("magic_tome_chip", TIER_EPIC, School.GENERIC, Category.SUPPORT),
            e("member_recommendation_chip", TIER_RARE, School.GENERIC, Category.ECONOMY),
            e("moto_helmet_high_chip", TIER_LEGENDARY, School.GENERIC, Category.REINFORCE),
            e("moto_helmet_low_chip", TIER_RARE, School.GENERIC, Category.REINFORCE),
            e("moto_helmet_medium_chip", TIER_EPIC, School.GENERIC, Category.REINFORCE),
            e("piercing_gun_chip", TIER_LEGENDARY, School.GENERIC, Category.ATTACK),
            e("piggy_bank_chip", TIER_RARE, School.GENERIC, Category.ECONOMY),
            e("revenge_halberd_chip", TIER_EPIC, School.GENERIC, Category.ATTACK),
            e("sandwich_high_chip", TIER_LEGENDARY, School.GENERIC, Category.REINFORCE),
            e("sandwich_low_chip", TIER_RARE, School.GENERIC, Category.REINFORCE),
            e("sandwich_medium_chip", TIER_EPIC, School.GENERIC, Category.REINFORCE),
            e("satellite_chip", TIER_LEGENDARY, School.GENERIC, Category.SUPPORT),
            e("smart_watch_chip", TIER_EPIC, School.GENERIC, Category.SUPPORT),
            e("speed_skates_high_chip", TIER_LEGENDARY, School.GENERIC, Category.REINFORCE),
            e("speed_skates_low_chip", TIER_RARE, School.GENERIC, Category.REINFORCE),
            e("speed_skates_medium_chip", TIER_EPIC, School.GENERIC, Category.REINFORCE),
            e("whetstone_chip", TIER_EPIC, School.GENERIC, Category.ATTACK)
    );

    private static final Map<String, Entry> BY_ID = buildIndex();

    private ChipShopCatalog() {
    }

    private static Entry e(String id, int tier, School school, Category category) {
        return new Entry(id, tier, school, category);
    }

    private static Map<String, Entry> buildIndex() {
        Map<String, Entry> map = new LinkedHashMap<>();
        for (Entry entry : POOL) {
            map.put(entry.id(), entry);
        }
        return Map.copyOf(map);
    }

    /** id → 池内条目（未知 id 返回空，不抛异常） */
    public static Optional<Entry> byId(String id) {
        return id == null ? Optional.empty() : Optional.ofNullable(BY_ID.get(id));
    }

    /** 物品 → 池内条目（非筹码 / air 返回空） */
    public static Optional<Entry> byItem(Item item) {
        if (item == null || item == Items.AIR) {
            return Optional.empty();
        }
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(item);
        if (!AstralDiceMod.MODID.equals(id.getNamespace())) {
            return Optional.empty();
        }
        return byId(id.getPath());
    }

    public static Optional<Entry> byStack(ItemStack stack) {
        return stack == null || stack.isEmpty() ? Optional.empty() : byItem(stack.getItem());
    }

    public static Optional<School> schoolOf(ItemStack stack) {
        return byStack(stack).map(Entry::school);
    }

    public static Optional<Category> categoryOf(ItemStack stack) {
        return byStack(stack).map(Entry::category);
    }

    /** 价格（裁决 1：三格同价、无任何折扣路径） */
    public static int priceOf(int purchasedCount) {
        int count = Math.max(0, purchasedCount);
        return Math.min(PRICE_MAX, PRICE_INITIAL + PRICE_STEP * count);
    }

    /** 同流派加成权重：已装备 N 枚同流派筹码 ⇒ ×(1 + SCHOOL_ALPHA × N) */
    public static double schoolWeight(int equippedSameSchool) {
        return 1.0 + SCHOOL_ALPHA * Math.max(0, equippedSameSchool);
    }

    /**
     * 类别加成权重（R10/R11）：{@code lastPick == null} 或类别不同 ⇒ 恒 1.0；
     * 否则 slot0 用 {@link #CATEGORY_ALPHA}、slot1/2 衰减为 {@code (factor - 1) × SLOT_DECAY + 1}。
     */
    public static double categoryWeight(int slot, Category candidate, Category lastPick) {
        if (lastPick == null || candidate == null || candidate != lastPick) {
            return 1.0;
        }
        if (slot <= 0) {
            return CATEGORY_ALPHA;
        }
        return (CATEGORY_ALPHA - 1.0) * SLOT_DECAY + 1.0;
    }

    /** 池只读视图（村民交易表复用同一份池子）。 */
    public static List<Entry> entries() {
        return POOL;
    }

    /** 按品质码反查档位（1/2/3；异常返回 -1）。 */
    public static int tierOf(Entry entry) {
        return entry == null ? -1 : entry.tier();
    }

    /** 品质码 → 村民交易权重。 */
    public static int weightOf(int tier) {
        return switch (tier) {
            case TIER_RARE -> WEIGHT_RARE;
            case TIER_EPIC -> WEIGHT_EPIC;
            case TIER_LEGENDARY -> WEIGHT_LEGENDARY;
            default -> 0;
        };
    }

    /** 按品质权重抽一枚（不修改传入列表；池空返回 null）—— 村民交易与商店共用同一权重口径。 */
    public static Entry pickWeighted(RandomSource random, List<Entry> pool) {
        if (pool.isEmpty()) {
            return null;
        }
        int total = 0;
        for (Entry entry : pool) {
            total += weightOf(entry.tier());
        }
        int roll = random.nextInt(total);
        for (Entry entry : pool) {
            roll -= weightOf(entry.tier());
            if (roll < 0) {
                return entry;
            }
        }
        return pool.get(pool.size() - 1);
    }
}
