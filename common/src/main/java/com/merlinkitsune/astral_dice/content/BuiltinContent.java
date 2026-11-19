package com.merlinkitsune.astral_dice.content;

import com.merlinkitsune.starengine.api.astral.AstralDiceApi;
import com.merlinkitsune.starengine.api.content.chip.ChipCategory;
import com.merlinkitsune.starengine.api.content.chip.ChipSchool;
import com.merlinkitsune.astral_dice.component.AstralGameplayConstants;

/**
 * 主模组内置立牌 / 筹码与 StarEngine 内容描述符的连接。
 *
 * <p>内容注册表本身只保存描述符，物品仍由各加载器线的 {@code ModItems} 注册；本类负责把
 * 主模组已经存在的内容登记进去，让附属模组、数据包工具与 KubeJS 脚本能按 id 查询到它们。
 *
 * <p>登记时机 = 各线 {@code onCommonSetup}（早于 KubeJS 服务器脚本），冻结时机 =
 * 服务器启动后（见各线 {@code AstralDiceMod#onServerStarted}）。因此内置内容对脚本可见，
 * 同时脚本仍可在冻结前追加自己的内容。
 *
 * <p>表内容与 {@code ModItems} / 商店默认池保持一致：
 * <ul>
 *   <li>立牌：{@code ModItems} 中全部 {@code *_sign}，排除合成材料 {@code blank_sign}；</li>
 *   <li>筹码：商店默认池的 70 枚，排除合成材料 {@code blank_chip}。</li>
 * </ul>
 * 新增 / 删除内置物品时必须同步维护这两张表（暂无自动校验）。
 *
 * <p>立牌在主模组里没有流派字段，故描述符统一使用 {@code generic}；冷却记录
 * {@link AstralGameplayConstants#SIGN_ACTIVE_COOLDOWN_TICKS} 的基准值，实际冷却仍由
 * {@code BaseSignItem} 按玩家状态计算。筹码价格使用商店基础价
 * {@code ChipShopCatalog.PRICE_INITIAL}=10，权重按品质使用村民交易同口径
 * {@code 6 / 3 / 2}。
 */
public final class BuiltinContent {

    /** 商店基础价（与 {@code ChipShopCatalog.PRICE_INITIAL} 对齐）。 */
    private static final int CHIP_BASE_PRICE = 10;

    /** 品质权重（与 {@code ChipShopCatalog.WEIGHT_*} 对齐）。 */
    private static final int WEIGHT_RARE = 6;
    private static final int WEIGHT_EPIC = 3;
    private static final int WEIGHT_LEGENDARY = 2;

    /** 主模组没有立牌流派数据，统一使用泛用流派。 */
    private static final String SIGN_SCHOOL = "generic";

    private static final ChipEntry[] CHIPS = {
            new ChipEntry("atm_chip", ChipSchool.STARLIGHT, ChipCategory.ECONOMY, 1),
            new ChipEntry("bank_card_high_chip", ChipSchool.STARLIGHT, ChipCategory.ECONOMY, 2),
            new ChipEntry("bank_card_low_chip", ChipSchool.STARLIGHT, ChipCategory.ECONOMY, 1),
            new ChipEntry("bank_card_unlimited_chip", ChipSchool.STARLIGHT, ChipCategory.ECONOMY, 3),
            new ChipEntry("eight_sided_dice_chip", ChipSchool.STARLIGHT, ChipCategory.ECONOMY, 1),
            new ChipEntry("extra_battery_chip", ChipSchool.GENERIC, ChipCategory.SUPPORT, 2),
            new ChipEntry("flashlight_burst_chip", ChipSchool.STARLIGHT, ChipCategory.ATTACK, 3),
            new ChipEntry("flashlight_chip", ChipSchool.STARLIGHT, ChipCategory.ATTACK, 2),
            new ChipEntry("fortune_stick_chip", ChipSchool.STARLIGHT, ChipCategory.ATTACK, 2),
            new ChipEntry("golden_shooting_star_chip", ChipSchool.STARLIGHT, ChipCategory.ATTACK, 3),
            new ChipEntry("purple_shooting_star_chip", ChipSchool.STARLIGHT, ChipCategory.ATTACK, 2),
            new ChipEntry("star_coin_hammer_chip", ChipSchool.STARLIGHT, ChipCategory.ATTACK, 3),
            new ChipEntry("abyss_reagent_chip", ChipSchool.HEALING, ChipCategory.HEAL, 2),
            new ChipEntry("big_bowl_stew_chip", ChipSchool.HEALING, ChipCategory.HEAL, 3),
            new ChipEntry("buffer_shield_chip", ChipSchool.HEALING, ChipCategory.HEAL, 1),
            new ChipEntry("candy_chip", ChipSchool.HEALING, ChipCategory.HEAL, 2),
            new ChipEntry("cutter_blade_chip", ChipSchool.HEALING, ChipCategory.ATTACK, 3),
            new ChipEntry("cutter_chip", ChipSchool.HEALING, ChipCategory.ATTACK, 2),
            new ChipEntry("friendship_badge_chip", ChipSchool.HEALING, ChipCategory.HEAL, 2),
            new ChipEntry("medkit_complete_chip", ChipSchool.HEALING, ChipCategory.HEAL, 3),
            new ChipEntry("medkit_emergency_chip", ChipSchool.HEALING, ChipCategory.HEAL, 1),
            new ChipEntry("vitamin_pill_chip", ChipSchool.HEALING, ChipCategory.HEAL, 2),
            new ChipEntry("eagle_scope_chip", ChipSchool.MARK, ChipCategory.ATTACK, 3),
            new ChipEntry("hand_fan_big_chip", ChipSchool.MARK, ChipCategory.SUPPORT, 2),
            new ChipEntry("hand_fan_small_chip", ChipSchool.MARK, ChipCategory.SUPPORT, 1),
            new ChipEntry("magic_quiver_chip", ChipSchool.MARK, ChipCategory.SUPPORT, 2),
            new ChipEntry("marker_sprayer_chip", ChipSchool.MARK, ChipCategory.SUPPORT, 1),
            new ChipEntry("ninja_star_chip", ChipSchool.MARK, ChipCategory.ATTACK, 3),
            new ChipEntry("scope_chip", ChipSchool.MARK, ChipCategory.ATTACK, 2),
            new ChipEntry("sensor_chip", ChipSchool.MARK, ChipCategory.SUPPORT, 2),
            new ChipEntry("target_chip", ChipSchool.MARK, ChipCategory.ATTACK, 1),
            new ChipEntry("advanced_peripherals_chip", ChipSchool.CHARGE, ChipCategory.ATTACK, 2),
            new ChipEntry("airbag_chip", ChipSchool.CHARGE, ChipCategory.SUPPORT, 2),
            new ChipEntry("current_core_chip", ChipSchool.CHARGE, ChipCategory.REINFORCE, 2),
            new ChipEntry("electric_glove_chip", ChipSchool.CHARGE, ChipCategory.ATTACK, 2),
            new ChipEntry("electric_sword_chip", ChipSchool.CHARGE, ChipCategory.ATTACK, 1),
            new ChipEntry("energy_recycler_chip", ChipSchool.CHARGE, ChipCategory.REINFORCE, 1),
            new ChipEntry("infinite_grid_chip", ChipSchool.CHARGE, ChipCategory.REINFORCE, 2),
            new ChipEntry("perpetual_motion_chip", ChipSchool.CHARGE, ChipCategory.REINFORCE, 3),
            new ChipEntry("primordial_core_chip", ChipSchool.CHARGE, ChipCategory.REINFORCE, 3),
            new ChipEntry("railgun_chip", ChipSchool.CHARGE, ChipCategory.ATTACK, 3),
            new ChipEntry("warp_engine_chip", ChipSchool.CHARGE, ChipCategory.REINFORCE, 1),
            new ChipEntry("adrenaline_high_chip", ChipSchool.GENERIC, ChipCategory.ATTACK, 3),
            new ChipEntry("adrenaline_low_chip", ChipSchool.GENERIC, ChipCategory.ATTACK, 2),
            new ChipEntry("big_backpack_chip", ChipSchool.GENERIC, ChipCategory.REINFORCE, 2),
            new ChipEntry("bookmark_chip", ChipSchool.GENERIC, ChipCategory.ATTACK, 1),
            new ChipEntry("boxing_gloves_high_chip", ChipSchool.GENERIC, ChipCategory.ATTACK, 3),
            new ChipEntry("boxing_gloves_low_chip", ChipSchool.GENERIC, ChipCategory.ATTACK, 1),
            new ChipEntry("boxing_gloves_medium_chip", ChipSchool.GENERIC, ChipCategory.ATTACK, 2),
            new ChipEntry("cursed_sword_chip", ChipSchool.GENERIC, ChipCategory.ATTACK, 1),
            new ChipEntry("magic_tome_chip", ChipSchool.GENERIC, ChipCategory.SUPPORT, 2),
            new ChipEntry("member_recommendation_chip", ChipSchool.GENERIC, ChipCategory.ECONOMY, 1),
            new ChipEntry("moto_helmet_high_chip", ChipSchool.GENERIC, ChipCategory.REINFORCE, 3),
            new ChipEntry("moto_helmet_low_chip", ChipSchool.GENERIC, ChipCategory.REINFORCE, 1),
            new ChipEntry("moto_helmet_medium_chip", ChipSchool.GENERIC, ChipCategory.REINFORCE, 2),
            new ChipEntry("perfect_branch_chip", ChipSchool.GENERIC, ChipCategory.REINFORCE, 3),
            new ChipEntry("piercing_gun_chip", ChipSchool.GENERIC, ChipCategory.ATTACK, 3),
            new ChipEntry("piggy_bank_chip", ChipSchool.GENERIC, ChipCategory.ECONOMY, 1),
            new ChipEntry("revenge_halberd_chip", ChipSchool.GENERIC, ChipCategory.ATTACK, 2),
            new ChipEntry("sandwich_high_chip", ChipSchool.GENERIC, ChipCategory.REINFORCE, 3),
            new ChipEntry("sandwich_low_chip", ChipSchool.GENERIC, ChipCategory.REINFORCE, 1),
            new ChipEntry("sandwich_medium_chip", ChipSchool.GENERIC, ChipCategory.REINFORCE, 2),
            new ChipEntry("satellite_chip", ChipSchool.GENERIC, ChipCategory.SUPPORT, 3),
            new ChipEntry("smart_watch_chip", ChipSchool.GENERIC, ChipCategory.SUPPORT, 2),
            new ChipEntry("speed_skates_high_chip", ChipSchool.GENERIC, ChipCategory.REINFORCE, 3),
            new ChipEntry("speed_skates_low_chip", ChipSchool.GENERIC, ChipCategory.REINFORCE, 1),
            new ChipEntry("speed_skates_medium_chip", ChipSchool.GENERIC, ChipCategory.REINFORCE, 2),
            new ChipEntry("sword_in_stone_epic_chip", ChipSchool.GENERIC, ChipCategory.ATTACK, 2),
            new ChipEntry("sword_in_stone_legendary_chip", ChipSchool.GENERIC, ChipCategory.ATTACK, 3),
            new ChipEntry("whetstone_chip", ChipSchool.GENERIC, ChipCategory.ATTACK, 2)
    };

    private static final String[] SIGNS = {
            "parunan_sign",
            "jasmine_sign",
            "misaki_sign",
            "mimi_sign",
            "lulu_sign",
            "komachi_sign",
            "padman_sign",
            "fanny_sign",
            "rin_sign",
            "haiqing_sign",
            "papara_sign",
            "bonnie_sign",
            "fen_sign",
            "nancy_lu_sign",
            "moses_sign",
            "pandaman_sign",
            "ren_sign",
            "zhao_sign",
            "teru_sign",
            "nardis_sign",
            "mamushi_sign",
            "sherry_sign",
            "hanna_sign",
            "kangel_sign",
            "ame_sign",
            "megas_sign",
            "lingling_sign",
            "jill_sign",
            "souri_sign",
            "al_sign",
            "inkshadow_sign",
            "dorothy_sign",
            "alana_sign",
            "deepseek_sign",
            "sykes_sign",
            "z3000_sign"
    };

    private BuiltinContent() {
    }

    /**
     * 登记全部内置描述符。只应在内容注册表冻结前调用；重复调用会按 id 替换，不会抛异常。
     */
    public static void register() {
        for (ChipEntry chip : CHIPS) {
            AstralDiceApi.registerChip(chip.id(), chip.school(), chip.category(),
                    CHIP_BASE_PRICE, weightOf(chip.tier()));
        }
        int cooldown = AstralGameplayConstants.SIGN_ACTIVE_COOLDOWN_TICKS;
        for (String sign : SIGNS) {
            AstralDiceApi.registerSign(sign, SIGN_SCHOOL, cooldown);
        }
    }

    private static int weightOf(int tier) {
        return switch (tier) {
            case 3 -> WEIGHT_LEGENDARY;
            case 2 -> WEIGHT_EPIC;
            default -> WEIGHT_RARE;
        };
    }

    private record ChipEntry(String id, ChipSchool school, ChipCategory category, int tier) {
    }
}
