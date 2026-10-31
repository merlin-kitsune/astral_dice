package com.merlinkitsune.astral_dice.shop;

import com.merlinkitsune.astral_dice.item.ModTags;
import com.merlinkitsune.starengine.item.AstralRarities;
import com.merlinkitsune.starengine.item.Rarity;
import java.util.Collection;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 卡牌「可交易性」的单一判据：商店货架与村民交易共用同一套排除规则。
 *
 * <p>被排除的三类（任务书第 1 节第 5 条）：
 * <ul>
 *   <li>专属牌 —— 数据标签 {@code astral_dice:is_exclusive}（7 张）；</li>
 *   <li>巅峰 pinnacle（全力攻击、下界之星骰子等）；</li>
 *   <li>奇特 bizarre（专属牌基本都是）。</li>
 * </ul>
 *
 * <p>档位判定只走库 API {@link AstralRarities#tierOf(net.minecraft.world.item.Rarity)}，不自己写 == 链
 * （库明确要求「消费方不要自己写 == 链 —— 那等于把档位映射抄到了库外」）；池内「权重 → 档位」则复用
 * {@link CardShopCatalog#tierOf}，与商店抽卡同一口径。
 *
 * <p>fail-loud 口径：{@link #validatePool} / {@link #validateExclusiveTag} 在**报价表构建期**
 * （数据包加载完成后的 {@code VillagerTradesEvent}）逐张校验，命中被排除项就打**错误级**日志（含物品 id），
 * 只报错不抛异常 —— 按仓库文化「fail-loud 但不崩服」。
 */
public final class CardTradeFilter {

    private static final Logger LOGGER = LoggerFactory.getLogger("astral_dice/card_trade");

    private CardTradeFilter() {
    }

    /** 单一判据：非专属标签 ∧ 档位不属于 {巅峰, 奇特}。 */
    public static boolean isSellable(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        if (stack.is(ModTags.Items.IS_EXCLUSIVE)) {
            return false;
        }
        Rarity tier = AstralRarities.tierOf(stack.getRarity());
        return tier != Rarity.PINNACLE && tier != Rarity.BIZARRE;
    }

    /** {@link #isSellable(ItemStack)} 的物品版本。 */
    public static boolean isSellable(Item item) {
        return item != null && isSellable(new ItemStack(item));
    }

    /** 判据明细（日志用）。 */
    public static String describe(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return "<空气>";
        }
        Rarity tier = AstralRarities.tierOf(stack.getRarity());
        return BuiltInRegistries.ITEM.getKey(stack.getItem())
                + "[档位=" + (tier == null ? "非本模组档位" : tier.name())
                + ", 专属=" + stack.is(ModTags.Items.IS_EXCLUSIVE)
                + ", 可售=" + isSellable(stack) + "]";
    }

    /** 专属牌标签自检：解析出 0 张说明资源路径错位，排除判据等于空转（fail-loud）。 */
    public static void validateExclusiveTag() {
        int tagged = 0;
        for (Item item : BuiltInRegistries.ITEM) {
            if (new ItemStack(item).is(ModTags.Items.IS_EXCLUSIVE)) {
                tagged++;
            }
        }
        if (tagged == 0) {
            LOGGER.error("[card_trade] 数据标签 {} 解析出 0 张物品 —— 资源路径可能错位"
                            + "（应位于 data/astral_dice/tags/item/is_exclusive.json），专属牌排除判据当前是空转！",
                    ModTags.Items.IS_EXCLUSIVE.location());
        } else {
            LOGGER.info("[card_trade] 专属牌标签 {} 解析出 {} 张物品。",
                    ModTags.Items.IS_EXCLUSIVE.location(), tagged);
        }
    }

    /**
     * 逐个校验一个卡池：被排除项（专属 / 巅峰 / 奇特）与「池内权重档位 ≠ 物品 rarity 档位」都打错误级日志。
     *
     * @param poolName 日志用的池名（商店货架池 / 卡牌交易员 L2 交易池 …）
     * @param pool     待校验的池
     * @return 命中问题的张数
     */
    public static int validatePool(String poolName, Collection<CardShopCatalog.PoolEntry> pool) {
        int bad = 0;
        for (CardShopCatalog.PoolEntry entry : pool) {
            ItemStack stack = new ItemStack(entry.item().get());
            if (!isSellable(stack)) {
                bad++;
                LOGGER.error("[card_trade] {} 命中被排除项：{}", poolName, describe(stack));
                continue;
            }
            CardShopCatalog.Tier byWeight = CardShopCatalog.tierOf(entry);
            Rarity byRarity = AstralRarities.tierOf(stack.getRarity());
            if (byWeight == null) {
                bad++;
                LOGGER.error("[card_trade] {} 无法由池内权重反查档位（weight={}）：{}",
                        poolName, entry.weight(), describe(stack));
            } else if (byRarity == null || !byWeight.name().equals(byRarity.name())) {
                bad++;
                LOGGER.error("[card_trade] {} 档位口径不一致：{} 池内权重档={} 而物品 rarity 档={}",
                        poolName, describe(stack), byWeight.name(), byRarity);
            }
        }
        LOGGER.info("[card_trade] {} 校验完成：共 {} 张，异常 {} 张。", poolName, pool.size(), bad);
        return bad;
    }
}
