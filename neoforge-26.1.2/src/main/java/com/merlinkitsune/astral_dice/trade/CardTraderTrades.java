package com.merlinkitsune.astral_dice.trade;

import com.merlinkitsune.astral_dice.combat.CardRegistry;
import com.merlinkitsune.astral_dice.item.ModItems;
import com.merlinkitsune.astral_dice.shop.CardShopCatalog;
import com.merlinkitsune.astral_dice.shop.CardTradeFilter;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 村民职业「卡牌交易员」（{@code astral_dice:card_trader}）的交易表。
 *
 * <p>落表（用户批准口径，价格为绿宝石）：
 * <pre>
 * 等级              上架卡牌               价格    出现权重
 * 新手 Novice(1)     稀有                   5-6     6
 * 学徒 Apprentice(2) 稀有 + 少量史诗         6-9     6 / 3
 * 老手 Journeyman(3) 史诗 + 绿宝石→星币 1:1  9-12    3（兑换为固定条目）
 * 专家 Expert(4)     史诗 / 传奇             12-16   3 / 2
 * 大师 Master(5)     传奇                   16-20   2
 * </pre>
 *
 * <p>价格为绿宝石基础价（整体区间 5-20），佩戴绿宝石骰子时按 1:1 折算为星币（基础价 5 ⇒ 5 星币）；
 * 原版声望/英雄之息折扣仍由原版结算，可能把实际支付压到基础价以下（与筹码商人同口径，不在本批处理）。
 *
 * <p>权重直接复用 {@link CardShopCatalog} 池内的 weight（稀有 6 / 史诗 3 / 传奇 2，与商店抽卡同一口径），
 * 抽卡走 {@link CardShopCatalog#pickWeighted}；价格 = 档位下限 + 该卡在 {@link CardRegistry} 的固定点数
 * （非战斗牌记 0），封顶档位上限，并不低于 {@link #MIN_EMERALD_PRICE}。抽不到可售卡就返回 {@code null} —— 原版
 * {@code AbstractVillager#addOffersFromItemListings} 会继续抽下一条 listing（每级最多 2 条报价）。
 *
 * <p>本处理器挂在 {@code NeoForge.EVENT_BUS}（游戏总线）：{@code VillagerTradingManager} 对**每个已注册职业**
 * （含本 mod 的职业）各发一次该事件，并把结果写回 {@code VillagerTrades.TRADES}；触发时机是数据包重载
 * （{@code TagsUpdatedEvent} 的 SERVER_DATA_LOAD），因此校验时数据包标签已绑定、
 * {@code CardRegistry.init()}（FMLCommonSetup）也早已执行（{@code itemToType} 可用）。
 */
public final class CardTraderTrades {

    private static final Logger LOGGER = LoggerFactory.getLogger("astral_dice/card_trader");

    /** 每级价格下限（绿宝石），索引 = 村民等级 1-5。 */
    private static final int[] MIN_PRICE = {0, 5, 6, 9, 12, 16};

    /** 每级价格上限（绿宝石），索引 = 村民等级 1-5。 */
    private static final int[] MAX_PRICE = {0, 6, 9, 12, 16, 20};

    /** 卡牌报价的绝对下限（绿宝石）：用户口径「最低售价不低于 5 绿宝石」；星币支付按 {@code EmeraldDiceTrade} 的 1:1 折算 ⇒ 最低 5 星币，满足「≥3 星币」口径。 */
    private static final int MIN_EMERALD_PRICE = 5;

    /** 每级的职业经验（沿用原版「越高级给得越多」的梯度）。 */
    private static final int[] VILLAGER_XP = {0, 2, 5, 10, 15, 30};

    /** 每级允许出现的档位（权重取池内 weight，与商店一致）。索引 = 村民等级 1-5。 */
    private static final List<List<CardShopCatalog.Tier>> LEVEL_TIERS = List.of(
            List.of(),
            List.of(CardShopCatalog.Tier.RARE),
            List.of(CardShopCatalog.Tier.RARE, CardShopCatalog.Tier.EPIC),
            List.of(CardShopCatalog.Tier.EPIC),
            List.of(CardShopCatalog.Tier.EPIC, CardShopCatalog.Tier.LEGENDARY),
            List.of(CardShopCatalog.Tier.LEGENDARY));

    /** 卡牌报价的售出上限与经验倍率（沿用原版村民交易常用值）。 */
    private static final int CARD_MAX_USES = 12;
    private static final float CARD_PRICE_MULTIPLIER = 0.05F;

    /** 「1 绿宝石 → 1 星币」兑换：12 次 / 1 经验 / **价格倍率 0** —— 倍率非 0 时卖完后 demand 累积会把 1 抬到 2。 */
    private static final int EXCHANGE_MAX_USES = 12;
    private static final int EXCHANGE_XP = 1;
    private static final float EXCHANGE_PRICE_MULTIPLIER = 0.0F;

    /** 兑换条目所在的村民等级：老手（3 级）解锁。 */
    private static final int EXCHANGE_LEVEL = 3;

        /**
     * 26.1.2 的报价装载表：把 1-5 级的卡池展开成「每张卡 × 它所在的等级」的静态条目。
     *
     * <p>原版每条 {@code VillagerTrade} 在数据包里有一份固定 JSON，价格不能按卡面点数在运行时算，
     * 所以这里把 {@link #poolFor(int)} 的每一张卡连同它在该级的定价一并摊平成条目，
     * 由 {@code datagen/ModVillagerTradeProvider} 写成 {@code data/astral_dice/villager_trade/card_trader/level_N/*.json}。
     * 同一张稀有卡在 1 级和 2 级的价位不同，因此在两个等级下各有一份条目。
     */
    public static List<VillagerTradeEntry> tradeEntries() {
        List<VillagerTradeEntry> entries = new ArrayList<>();
        for (int level = 1; level <= 5; level++) {
            // datagen 期没有组件绑定：见 poolFor(level, stackFilter) 的说明。
            for (CardShopCatalog.PoolEntry entry : poolFor(level, false)) {
                Item item = entry.item().get();
                entries.add(new VillagerTradeEntry(level, item, emeraldPriceFor(level, item), CARD_MAX_USES,
                        VILLAGER_XP[level], CARD_PRICE_MULTIPLIER));
            }
        }
        return entries;
    }

    /** 「1 绿宝石 → 1 星币」兑换的静态条目（老手级解锁）。 */
    public static VillagerTradeEntry exchangeEntry() {
        return new VillagerTradeEntry(EXCHANGE_LEVEL, ModItems.STAR_COIN.get(), 1, EXCHANGE_MAX_USES, EXCHANGE_XP,
                EXCHANGE_PRICE_MULTIPLIER);
    }

    /** 绿宝石价 = 档位下限 + 卡面固定点数，夹在档位区间内，且不低于 {@link #MIN_EMERALD_PRICE}。 */
    static int emeraldPriceFor(int level, Item item) {
        // 不构造 ItemStack：datagen 期组件未绑定（见 CardRegistry#itemToType(Item)）。
        String typeId = CardRegistry.itemToType(item);
        int points = typeId == null ? 0 : CardRegistry.maxRoll(typeId);
        int price = Math.max(MIN_PRICE[level], Math.min(MAX_PRICE[level], MIN_PRICE[level] + points));
        return Math.max(price, MIN_EMERALD_PRICE);
    }
    private CardTraderTrades() {
    }

    /**
     * 构建 1-5 级全部卡牌报价。
     *
     * <p><b>26.1.2 行为差异</b>：{@code VillagerTradesEvent} / {@code VillagerTrades.ItemListing} /
     * {@code BasicItemListing} 在 26.1.2 全部删除，村民交易改成数据驱动
     * （{@code Registries.VILLAGER_TRADE} 的 {@code VillagerTrade} + {@code TradeSet} 数据包，
     * 由 {@code VillagerProfession#tradeSetsByLevel} 绑定到职业等级）。
     * 本类保留 1.21.1 的报价构建逻辑（常量表 → 分档卡池 → 加权抽卡 → 定价），做成**纯构建器**，
     * 供 P6 的挂载层复用（挂载层需要生成 {@code villager_trade} JSON 或注册自定义 {@code VillagerTrade}）。
     * <p>原来：服务端启动后由事件把报价写进 {@code VillagerTrades.TRADES}，村民立刻有货。
     * 现在：本方法不会向任何职业写入报价，村民运行时**没有**本模组的交易项（P6 补齐）。
     */
    public static List<CardTraderListing> listings() {
        // 报价表构建期的 fail-loud 校验（1.21.1 上由 VillagerTradesEvent 的数据包重载时机保证标签已绑定）
        CardTradeFilter.validateExclusiveTag();
        CardTradeFilter.validatePool("商店货架池", CardShopCatalog.entries());

        List<CardTraderListing> listings = new ArrayList<>();
        for (int level = 1; level <= 5; level++) {
            List<CardShopCatalog.PoolEntry> pool = poolFor(level);
            CardTradeFilter.validatePool("卡牌交易员 L" + level + " 交易池", pool);
            if (pool.isEmpty()) {
                LOGGER.error("[card_trader] 等级 {} 的可售卡池为空，该级不会上架任何卡牌报价！", level);
                continue;
            }
            listings.add(new CardTraderListing(pool, MIN_PRICE[level], MAX_PRICE[level], VILLAGER_XP[level]));
        }
        LOGGER.info("[card_trader] 交易表构建器就绪：卡牌 listing {} 条（26.1.2 数据驱动挂载待 P6）。", listings.size());
        return listings;
    }

    /**
     * 「1 绿宝石 → 1 星币」兑换条目（老手级解锁）。
     *
     * <p>26.1.2 用 {@link MerchantOffer} 直接构造，等价于 1.21.1 的
     * {@code new BasicItemListing(new ItemStack(EMERALD), new ItemStack(STAR_COIN), 12, 1, 0.0F)}。
     * 佩戴绿宝石骰子的玩家会在发包/结算路径上被过滤掉（见 {@code EmeraldDiceTrade}）。
     */
    public static MerchantOffer exchangeOffer() {
        return new MerchantOffer(new ItemCost(Items.EMERALD, 1), new ItemStack(ModItems.STAR_COIN.get()),
                EXCHANGE_MAX_USES, EXCHANGE_XP, EXCHANGE_PRICE_MULTIPLIER);
    }

    /** 取某一级的可售卡池：档位属于该级允许集 ∧ 通过 {@link CardTradeFilter#isSellable}（排除专属/巅峰/奇特）。 */
    private static List<CardShopCatalog.PoolEntry> poolFor(int level) {
        return poolFor(level, true);
    }

    /**
     * 同上，但可以跳过 {@link CardTradeFilter#isSellable}。
     *
     * <p><b>什么时候能跳</b>：datagen 期 {@code ItemStack} 根本构造不出来 ——
     * NeoForge 26.1 的数据生成跑在组件绑定之前，任何 {@code new ItemStack(...)} 都会抛
     * {@code NullPointerException: Components not bound yet}（实机日志 `temp/datagen-trades2.log`）。
     * 而 {@code isSellable} 依赖 {@code stack.is(tag)} 与 {@code stack.getRarity()}，两者都要组件。
     * <p><b>为什么这里跳了也安全</b>：卡池 {@code CardShopCatalog.POOL} 是 23 张人工策展的卡，
     * 其中不含专属（{@code IS_EXCLUSIVE} 标签）/巅峰/奇特卡；{@code isSellable} 在这条路径上本就是兜底。
     * 运行时路径（{@code listings()} / 商店抽卡）仍然带着它。
     */
    private static List<CardShopCatalog.PoolEntry> poolFor(int level, boolean stackFilter) {
        List<CardShopCatalog.Tier> tiers = LEVEL_TIERS.get(level);
        List<CardShopCatalog.PoolEntry> pool = new ArrayList<>();
        for (CardShopCatalog.PoolEntry entry : CardShopCatalog.entries()) {
            CardShopCatalog.Tier tier = CardShopCatalog.tierOf(entry);
            if (tier == null || !tiers.contains(tier)) {
                continue;
            }
            if (stackFilter && !CardTradeFilter.isSellable(entry.item().get())) {
                continue;
            }
            pool.add(entry);
        }
        return pool;
    }

    /** 单个等级的卡牌报价：按池内权重抽一张可售卡，价格按卡面点数微调。 */
    public record CardTraderListing(List<CardShopCatalog.PoolEntry> pool, int minPrice, int maxPrice, int villagerXp) {

        /** 抽一只并生成报价（签名与 1.21.1 的 {@code ItemListing#getOffer} 一致，便于 P6 挂载层直接复用）。 */
        public MerchantOffer getOffer(Entity trader, RandomSource random) {
            CardShopCatalog.PoolEntry picked = CardShopCatalog.pickWeighted(random, this.pool);
            if (picked == null) {
                return null;
            }
            ItemStack result = new ItemStack(picked.item().get());
            if (!CardTradeFilter.isSellable(result)) {
                // 双保险：池子构建时已过滤，这里再兜一次 —— 被排除项绝不上架
                LOGGER.error("[card_trader] 抽中被排除项，本次报价作废：{}", CardTradeFilter.describe(result));
                return null;
            }
            return new MerchantOffer(new ItemCost(Items.EMERALD, priceFor(result)), result,
                    CARD_MAX_USES, this.villagerXp, CARD_PRICE_MULTIPLIER);
        }

        /** 档位下限 + 该卡在 combat/CardRegistry 的固定点数（非战斗牌记 0），封顶档位上限。 */
        private int priceFor(ItemStack stack) {
            String typeId = CardRegistry.itemToType(stack);
            int points = typeId == null ? 0 : CardRegistry.maxRoll(typeId);
            int price = Math.max(this.minPrice, Math.min(this.maxPrice, this.minPrice + points));
            return Math.max(price, MIN_EMERALD_PRICE);
        }
    }
}
