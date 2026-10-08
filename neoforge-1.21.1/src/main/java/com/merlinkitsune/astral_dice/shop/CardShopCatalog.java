package com.merlinkitsune.astral_dice.shop;

import com.merlinkitsune.astral_dice.item.ModItems;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * 卡牌商店的货架抽池与定价口径（2026-10-04 v3 计划，用户批准）。
 *
 * <p><b>抽池</b>：22 张已实装卡牌，权重按稀有度逐张加权、**不重复抽**（同一货架不会出现两张同名卡）：
 * rare = {@value #WEIGHT_RARE} / epic = {@value #WEIGHT_EPIC} / legendary = {@value #WEIGHT_LEGENDARY}
 * ⇒ 单格边际概率 ≈ 稀有 72.7% / 史诗 21.2% / 传奇 6.1%。
 *
 * <p><b>星级门槛</b>（用户 2026-10-04 m00635 原话「其中『蓄力』卡牌必须在玩家骰子星级为 2 级后才能出现」）：
 * {@code attack_card_charge} 的 {@link PoolEntry#minStarLevel()} = {@value #CHARGE_MIN_STAR_LEVEL}，
 * 抽池前先按 {@code DiceCurioItem.configStarLevel(ItemStack)}（下界之星骰子恒 3，否则 clamp 0..3）
 * 过滤 —— 门槛判定**只在开店抽池那一刻**做一次（D11）。
 *
 * <p><b>格子数</b>= 基础 {@value #BASE_SLOTS} + 「商店大酬宾」事件 {@value #PROMOTION_BONUS_SLOTS}
 * + 团队内有经商立牌玩家 {@value #TEAM_BONUS_SLOTS}（后者**只出战斗牌**，且同样计入星级门槛）。
 * 「商店大酬宾」本批只留钩子（附件 {@code shop_promotion_until}）；事件卡系统尚未实装。
 *
 * <p><b>价格</b>：基础 {@value #BASE_PRICE} 星币；团队内有经商立牌玩家时每张各有
 * {@code 10%} 概率降为 {@value #DISCOUNT_PRICE} 星币（D2/用户追加口径）。
 */
public final class CardShopCatalog {

    public static final int BASE_SLOTS = 3;
    public static final int PROMOTION_BONUS_SLOTS = 2;
    public static final int TEAM_BONUS_SLOTS = 1;
    public static final int MAX_SLOTS = BASE_SLOTS + PROMOTION_BONUS_SLOTS + TEAM_BONUS_SLOTS;

    public static final int BASE_PRICE = 3;
    public static final int DISCOUNT_PRICE = 1;
    /** 团队有经商立牌时，每张牌降价到 {@link #DISCOUNT_PRICE} 的概率 */
    public static final float DISCOUNT_CHANCE = 0.10F;

    /** 「蓄力」(attack_card_charge) 出现所需的最低骰子星级 */
    public static final int CHARGE_MIN_STAR_LEVEL = 2;

    /** 稀有度权重（可调；见类头边际概率） */
    public static final int WEIGHT_RARE = 6;
    public static final int WEIGHT_EPIC = 3;
    public static final int WEIGHT_LEGENDARY = 2;

    /**
     * 抽池条目。
     *
     * @param item         卡牌物品（延迟解析，注册期不取 .get()）
     * @param weight       加权随机权重
     * @param combat       是否「战斗牌」（{@code attack_card_*} + {@code defense_card_*}）
     * @param minStarLevel 出现所需的最低骰子星级（0 = 无门槛）
     */
    public record PoolEntry(Supplier<? extends Item> item, int weight, boolean combat, int minStarLevel) {}

    private static final List<PoolEntry> POOL = List.of(
            // ── 稀有（rare，权重 6）× 12 ─────────────────────────────────────
            new PoolEntry(ModItems.ATTACK_CARD_MEDIUM::get, WEIGHT_RARE, true, 0),
            new PoolEntry(ModItems.ATTACK_CARD_LARGE::get, WEIGHT_RARE, true, 0),
            new PoolEntry(ModItems.ATTACK_CARD_EPIC::get, WEIGHT_RARE, true, 0),
            new PoolEntry(ModItems.ATTACK_CARD_SHADOW_STRIKE::get, WEIGHT_RARE, true, 0),
            new PoolEntry(ModItems.DEFENSE_CARD_MEDIUM::get, WEIGHT_RARE, true, 0),
            new PoolEntry(ModItems.DEFENSE_CARD_LARGE::get, WEIGHT_RARE, true, 0),
            new PoolEntry(ModItems.DEFENSE_CARD_EPIC::get, WEIGHT_RARE, true, 0),
            new PoolEntry(ModItems.EFFECT_CARD_BERSERK::get, WEIGHT_RARE, false, 0),
            new PoolEntry(ModItems.EFFECT_CARD_FIGHT_POISON_WITH_POISON::get, WEIGHT_RARE, false, 0),
            new PoolEntry(ModItems.MONSTER_LASER_CARD::get, WEIGHT_RARE, false, 0),
            new PoolEntry(ModItems.CHOCOLATE_CAKE::get, WEIGHT_RARE, false, 0),
            new PoolEntry(ModItems.YOU_HAVE_I_HAVE::get, WEIGHT_RARE, false, 0),
            // ── 史诗（epic，权重 3）× 7 ──────────────────────────────────────
            new PoolEntry(ModItems.ATTACK_CARD_MEITO::get, WEIGHT_EPIC, true, 0),
            new PoolEntry(ModItems.EFFECT_CARD_KING_POWER::get, WEIGHT_EPIC, false, 0),
            new PoolEntry(ModItems.EFFECT_CARD_UNWAVERING::get, WEIGHT_EPIC, false, 0),
            new PoolEntry(ModItems.MONSTER_BRICK_CARD::get, WEIGHT_EPIC, false, 0),
            new PoolEntry(ModItems.HAMBURGER::get, WEIGHT_EPIC, false, 0),
            new PoolEntry(ModItems.LUXURY_FEAST::get, WEIGHT_EPIC, false, 0),
            new PoolEntry(ModItems.EXPRESS_DELIVERY::get, WEIGHT_EPIC, false, 0),
            // ── 传奇（legendary，权重 2）× 3 ────────────────────────────────
            new PoolEntry(ModItems.ATTACK_CARD_CHARGE::get, WEIGHT_LEGENDARY, true, CHARGE_MIN_STAR_LEVEL),
            new PoolEntry(ModItems.ORBITAL_STRIKE_CARD::get, WEIGHT_LEGENDARY, false, 0),
            new PoolEntry(ModItems.DIRECTIONAL_BLAST_CARD::get, WEIGHT_LEGENDARY, false, 0)
    );

    private CardShopCatalog() {}

    /** 按星级门槛过滤后的可选池（每次抽池现算，门槛只用开店那一刻的星级） */
    public static List<PoolEntry> availableEntries(int starLevel) {
        List<PoolEntry> available = new ArrayList<>();
        for (PoolEntry entry : POOL) {
            if (starLevel >= entry.minStarLevel()) {
                available.add(entry);
            }
        }
        return available;
    }

    /**
     * 抽出一个货架（顺序 = 基础格 → 促销格 → 团队战斗牌格；不重复）。
     *
     * @param random     随机源
     * @param starLevel  当前骰子星级
     * @param teamBonus  团队内是否有经商立牌玩家（额外 1 张**战斗牌**）
     * @param promotion  是否处于「商店大酬宾」窗口（额外 {@value #PROMOTION_BONUS_SLOTS} 张）
     */
    public static List<Item> rollShelf(RandomSource random, int starLevel, boolean teamBonus, boolean promotion) {
        List<PoolEntry> available = availableEntries(starLevel);
        List<Item> shelf = new ArrayList<>();
        for (int i = 0; i < BASE_SLOTS; i++) {
            drawInto(random, available, shelf);
        }
        if (promotion) {
            for (int i = 0; i < PROMOTION_BONUS_SLOTS; i++) {
                drawInto(random, available, shelf);
            }
        }
        if (teamBonus) {
            // 团队加成的这一张**只从战斗牌里抽**，且必须是已通过星级门槛的牌
            List<PoolEntry> combat = new ArrayList<>();
            for (PoolEntry entry : available) {
                if (entry.combat()) {
                    combat.add(entry);
                }
            }
            drawInto(random, combat, shelf);
        }
        return shelf;
    }

    /** 卡池档位 —— 与池内权重常量一一对应（稀有 6 / 史诗 3 / 传奇 2）。 */
    public enum Tier { RARE, EPIC, LEGENDARY }

    /** 卡池只读视图（村民交易表复用同一份池子与权重，不另造第二套分级）。 */
    public static List<PoolEntry> entries() {
        return POOL;
    }

    /** 由池内权重反查档位 —— 直接复用商店抽卡的权重口径；权重不是三档常量时返回 null。 */
    public static Tier tierOf(PoolEntry entry) {
        if (entry == null) {
            return null;
        }
        return switch (entry.weight()) {
            case WEIGHT_RARE -> Tier.RARE;
            case WEIGHT_EPIC -> Tier.EPIC;
            case WEIGHT_LEGENDARY -> Tier.LEGENDARY;
            default -> null;
        };
    }

    /** 按权重抽一张（不修改传入列表）—— 商店货架与村民交易共用同一套抽卡口径。 */
    public static PoolEntry pickWeighted(RandomSource random, List<PoolEntry> pool) {
        return drawWeighted(random, pool);
    }

    private static void drawInto(RandomSource random, List<PoolEntry> pool, List<Item> out) {
        PoolEntry picked = drawWeighted(random, pool);
        if (picked == null) {
            return;
        }
        pool.remove(picked);
        out.add(picked.item().get());
    }

    private static PoolEntry drawWeighted(RandomSource random, List<PoolEntry> pool) {
        if (pool.isEmpty()) {
            return null;
        }
        int total = 0;
        for (PoolEntry entry : pool) {
            total += entry.weight();
        }
        int roll = random.nextInt(total);
        for (PoolEntry entry : pool) {
            roll -= entry.weight();
            if (roll < 0) {
                return entry;
            }
        }
        return pool.get(pool.size() - 1);
    }

    /** 该张牌是否降价（只有团队有经商立牌时才会调用） */
    public static boolean rollDiscount(RandomSource random) {
        return random.nextFloat() < DISCOUNT_CHANCE;
    }
}
