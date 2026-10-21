package com.merlinkitsune.astral_dice.shop;

import com.merlinkitsune.astral_dice.item.CurioSlotUtil;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 筹码候选抽取器（判据正本 = {@code docs/chip-shop-logic-plan.md} §2）。
 *
 * <p><b>算法</b>：先按「已拥有」过滤出可用池，再逐格（slot 0 → 1 → 2）独立掷品质档、
 * 在「本档仍有候选」的档位上按比例重分配概率，最后按 {@code 同流派权重 × 类别权重} 加权抽一枚；
 * 抽中的筹码**立即从池中移除**（三格天然去重）；池空即 {@code break}
 * （缺口只会出现在尾部：先缺第 3 格、再缺第 2 格）。
 *
 * <p><b>已拥有（R5/Q11）</b> = {@code chip} 饰品槽 ∪ 玩家物品栏 36 格（<b>不含副手</b>）。
 *
 * <p><b>本类不做任何落盘 / 不碰附件</b>：调用方（{@link ChipShopOpener#ensureOffers}、
 * {@link ChipShopMenu#handleRefresh}、购买成功分支）负责把结果写回 {@link ChipShopState} ——
 * 这也是守门 G19「重抽入口只有三处」的落地方式。
 *
 * <p><b>随机源</b> = {@link Player#getRandom()}（服务端玩家实例），不用静态 {@code Random}
 * ⇒ 同一玩家不同会话不共享流，且天然只在服务端调用（客户端不掷骰）。
 */
public final class ChipShopRoller {

    /** 筹码饰品栏 id（与 {@code BaseChipItem} 的 {@code canEquip} 口径一致） */
    private static final String CHIP_SLOT_ID = "chip";
    /** 玩家物品栏参与「已拥有」判定的格数：0..8 快捷栏 + 9..35 主物品栏 */
    private static final int INVENTORY_SLOTS = 36;

    private ChipShopRoller() {
    }

    /**
     * 掷一批候选（最多 {@link ChipShopCatalog#OFFER_COUNT} 枚，可能不足 —— 池不够时尾部留空）。
     *
     * @param player    服务端玩家（提供星阶、已拥有集合、已装备流派与随机源）
     * @param lastPick  上次购买筹码的类别；{@code null} ⇒ 无类别偏置（R10）
     */
    public static List<ChipShopCatalog.Entry> roll(Player player, @Nullable ChipShopCatalog.Category lastPick) {
        return roll(player, player.getRandom(), lastPick);
    }

    /** 可注入随机源的版本（测试 / 复算用；生产走 {@link #roll(Player, ChipShopCatalog.Category)}） */
    public static List<ChipShopCatalog.Entry> roll(Player player, RandomSource random,
                                                   @Nullable ChipShopCatalog.Category lastPick) {
        List<ChipShopCatalog.Entry> pool = availablePool(player);
        Map<ChipShopCatalog.School, Integer> equippedSchools = equippedSchoolCounts(player);
        int star = starLevel(player);
        double[] qualityProb = ChipShopCatalog.QUALITY_PROB[star];

        List<ChipShopCatalog.Entry> picked = new ArrayList<>(ChipShopCatalog.OFFER_COUNT);
        for (int slot = 0; slot < ChipShopCatalog.OFFER_COUNT; slot++) {
            if (pool.isEmpty()) {
                break;
            }
            int tier = pickTier(pool, qualityProb, random);
            if (tier <= 0) {
                break;
            }
            ChipShopCatalog.Entry chosen = pickWeighted(pool, tier, slot, lastPick, equippedSchools, random);
            if (chosen == null) {
                break;
            }
            picked.add(chosen);
            pool.remove(chosen);
        }
        return picked;
    }

    /** 可用池 = 全池 − 已拥有（顺序保持 {@link ChipShopCatalog#POOL} 的注册顺序，便于复算） */
    public static List<ChipShopCatalog.Entry> availablePool(Player player) {
        Set<Item> owned = ownedChipItems(player);
        List<ChipShopCatalog.Entry> pool = new ArrayList<>(ChipShopCatalog.POOL.size());
        for (ChipShopCatalog.Entry entry : ChipShopCatalog.POOL) {
            if (!owned.contains(entry.item())) {
                pool.add(entry);
            }
        }
        return pool;
    }

    /** 玩家是否已拥有全部筹码（R6：全拥有 ⇒ 不开店 + actionbar 红字） */
    public static boolean ownsEverything(Player player) {
        return availablePool(player).isEmpty();
    }

    /**
     * 当前骰子签名 = {@code <物品 id>|<星阶>}（2026-10-21 用户裁决：换骰子或升星都必须重抽筹码商店）。
     *
     * <p>用物品 id 而不只是星阶：同为 2★ 的「绿宝石骰子 → 黑曜石骰子」也算换骰子。
     * 未装备骰子时返回 {@code "none"}。
     */
    public static String diceKey(Player player) {
        if (player == null) return "none";
        var curios = top.theillusivec4.curios.api.CuriosApi.getCuriosInventory(player);
        if (curios.isEmpty()) return "none";
        var dice = curios.get().findFirstCurio(
                com.merlinkitsune.astral_dice.item.dice.DiceCurioItem::isDiceItem);
        if (dice.isEmpty()) return "none";
        net.minecraft.resources.ResourceLocation id = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(
                dice.get().stack().getItem());
        return (id == null ? "unknown" : id.toString()) + "|" + starLevel(player);
    }

    /** 骰子星阶（复用卡牌商店口径：下界之星骰恒 3，否则 clamp 0..3） */
    public static int starLevel(Player player) {
        return Math.max(0, Math.min(3, CardShopShelf.currentStarLevel(player)));
    }

    /**
     * 掷品质档：只在「池中仍有该档候选」的档位上归一化概率 —— 某档无候选时把它那份概率
     * **按比例重分配**给其它档（R4），避免出现空格。返回 0 表示无档可选（池已空）。
     */
    private static int pickTier(List<ChipShopCatalog.Entry> pool, double[] qualityProb, RandomSource random) {
        // 容量按「品质档数」定容（下标 = tier-1）；不可借用 OFFER_COUNT（那是货位数，语义不同，曾被复核指为维度错配）
        int[] available = new int[ChipShopCatalog.TIER_LEGENDARY + 1];
        double total = 0.0;
        for (int tier = ChipShopCatalog.TIER_RARE; tier <= ChipShopCatalog.TIER_LEGENDARY; tier++) {
            available[tier - 1] = countTier(pool, tier);
            if (available[tier - 1] > 0) {
                total += qualityProb[tier - 1];
            }
        }
        if (total <= 0.0) {
            return 0;
        }
        double roll = random.nextDouble() * total;
        for (int tier = ChipShopCatalog.TIER_RARE; tier <= ChipShopCatalog.TIER_LEGENDARY; tier++) {
            if (available[tier - 1] <= 0) {
                continue;
            }
            roll -= qualityProb[tier - 1];
            if (roll < 0.0) {
                return tier;
            }
        }
        // 浮点残差兜底：返回最高可用档
        for (int tier = ChipShopCatalog.TIER_LEGENDARY; tier >= ChipShopCatalog.TIER_RARE; tier--) {
            if (available[tier - 1] > 0) {
                return tier;
            }
        }
        return 0;
    }

    private static int countTier(List<ChipShopCatalog.Entry> pool, int tier) {
        int count = 0;
        for (ChipShopCatalog.Entry entry : pool) {
            if (entry.tier() == tier) {
                count++;
            }
        }
        return count;
    }

    /** 在指定品质档内按 {@code 同流派权重 × 类别权重} 加权抽一枚；该档无候选时返回 null */
    private static ChipShopCatalog.Entry pickWeighted(List<ChipShopCatalog.Entry> pool, int tier, int slot,
                                                      @Nullable ChipShopCatalog.Category lastPick,
                                                      Map<ChipShopCatalog.School, Integer> equippedSchools,
                                                      RandomSource random) {
        double total = 0.0;
        for (ChipShopCatalog.Entry entry : pool) {
            if (entry.tier() != tier) {
                continue;
            }
            total += weightOf(entry, slot, lastPick, equippedSchools);
        }
        if (total <= 0.0) {
            return null;
        }
        double roll = random.nextDouble() * total;
        ChipShopCatalog.Entry fallback = null;
        for (ChipShopCatalog.Entry entry : pool) {
            if (entry.tier() != tier) {
                continue;
            }
            fallback = entry;
            roll -= weightOf(entry, slot, lastPick, equippedSchools);
            if (roll < 0.0) {
                return entry;
            }
        }
        return fallback;
    }

    private static double weightOf(ChipShopCatalog.Entry entry, int slot,
                                   @Nullable ChipShopCatalog.Category lastPick,
                                   Map<ChipShopCatalog.School, Integer> equippedSchools) {
        int sameSchool = equippedSchools.getOrDefault(entry.school(), 0);
        return ChipShopCatalog.schoolWeight(sameSchool)
                * ChipShopCatalog.categoryWeight(slot, entry.category(), lastPick);
    }

    /** 该筹码是否已被玩家拥有（R5/Q11 口径：chip 饰品槽 ∪ 物品栏 36 格，不含副手） */
    public static boolean isOwned(Player player, ChipShopCatalog.Entry entry) {
        return ownedChipItems(player).contains(entry.item());
    }

    /** 「已拥有」物品集合 = chip 饰品槽 ∪ 物品栏 36 格（不含副手） */
    public static Set<Item> ownedChipItems(Player player) {
        Set<Item> owned = new HashSet<>();
        CurioSlotUtil.forEachCurioStack(player, CHIP_SLOT_ID, stack -> owned.add(stack.getItem()));
        Inventory inventory = player.getInventory();
        for (int i = 0; i < INVENTORY_SLOTS; i++) {
            ItemStack stack = inventory.getItem(i);
            if (!stack.isEmpty()) {
                owned.add(stack.getItem());
            }
        }
        return owned;
    }

    /** 已装备筹码的**流派计数**（只算 chip 饰品槽；用于同流派权重） */
    private static Map<ChipShopCatalog.School, Integer> equippedSchoolCounts(Player player) {
        Map<ChipShopCatalog.School, Integer> counts = new EnumMap<>(ChipShopCatalog.School.class);
        CurioSlotUtil.forEachCurioStack(player, CHIP_SLOT_ID, stack ->
                ChipShopCatalog.schoolOf(stack).ifPresent(school -> counts.merge(school, 1, Integer::sum)));
        return counts;
    }
}
