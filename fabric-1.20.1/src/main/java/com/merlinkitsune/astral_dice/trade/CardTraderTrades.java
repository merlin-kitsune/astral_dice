package com.merlinkitsune.astral_dice.trade;

import com.merlinkitsune.astral_dice.combat.CardRegistry;
import com.merlinkitsune.astral_dice.item.ModItems;
import com.merlinkitsune.astral_dice.shop.CardShopCatalog;
import com.merlinkitsune.astral_dice.shop.CardTradeFilter;
import com.merlinkitsune.astral_dice.world.ModVillagerProfessions;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.object.builder.v1.trade.TradeOfferHelper;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.npc.VillagerTrades;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
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
 * （非战斗牌记 0），封顶档位上限，并不低于 {@link #MIN_EMERALD_PRICE}。抽不到可售卡就返回 {@code null} ——
 * 原版 {@code AbstractVillager#addOffersFromItemListings} 会继续抽下一条 listing（每级最多 2 条报价）。
 *
 * <h2>Fabric 1.20.1 的平台重建</h2>
 *
 * <p>Forge / NeoForge 两线是「监听 {@code VillagerTradesEvent} 后往该等级列表里 add」；
 * <b>Fabric 没有这个事件</b>（{@code porting/fabric-1.20.1/MAPPING-1.5.0.md} §2.1/§2.9）。
 * 等价替换点是 fabric-api 的 {@link TradeOfferHelper#registerVillagerOffers}：
 * 它内部对 {@code VillagerTrades.TRADES} 做 {@code computeIfAbsent} 后
 * {@code ArrayUtils.addAll}（{@code TradeOfferInternals#registerOffers}）⇒ **原地追加、调用即生效**，
 * 与 Forge 事件里 {@code event.getTrades().get(level).add(...)} 同语义、零 mixin。
 * ⚠️ {@code TradeOfferHelper#refreshOffers()} 是 {@code @Deprecated(forRemoval)} 的空操作，**不得调用**。
 *
 * <p>触发次数差异（三线对照）：Forge 1.20.1 只在 {@code ServerAboutToStartEvent} 发一次；
 * NeoForge 1.21.1 挂在 {@code TagsUpdatedEvent}(SERVER_DATA_LOAD) 上、datapack reload 会重发；
 * Fabric 本实现只在 mod 初始化期调用一次（{@code /reload} 不重放）—— 与 Forge 1.20.1 一致。
 * {@link #register()} 自带幂等守卫，重复调用不会把报价灌两遍。
 *
 * <p>调用时机：紧跟 {@code ModVillagerProfessions.bootstrap()} 之后（{@code .get()} 需要职业已提交）。
 * 此刻标签尚未绑定（mod 初始化早于 datapack 装载）⇒ 会把 {@link CardTradeFilter#validateExclusiveTag()}
 * 挂到 {@code ServerLifecycleEvents.SERVER_STARTED}，等 datapack 装载完再校验（2026-11-02 修正：
 * 此前在 mod init 直接调用，实测 server/client 都打「解析出 0 张物品」的假 ERROR）。
 * 真正的可售性判定发生在每次 {@code getOffer} 现算，故不影响成交。
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

    /** 兑换条目的费用与产出（1 绿宝石 → 1 星币，与 Forge / NeoForge 两线同值）。 */
    private static final int EXCHANGE_EMERALD_COST = 1;
    private static final int EXCHANGE_STAR_COIN_COUNT = 1;

    /** 兑换条目所在的村民等级：老手（3 级）解锁。 */
    private static final int EXCHANGE_LEVEL = 3;

    private static boolean registered;

    private CardTraderTrades() {
    }

    /**
     * 把 1-5 级的卡牌报价与 3 级的「绿宝石兑星币」写进 {@code VillagerTrades.TRADES}
     * （Forge 侧 {@code onVillagerTrades} 的等价物，一次调用即长期有效）。
     */
    public static void register() {
        if (registered) {
            return;
        }
        registered = true;
        VillagerProfession profession = ModVillagerProfessions.CARD_TRADER.get();
        // 报价表构建期的池校验（不依赖物品标签：只看 rarity 档位与权重，mod 初始化期即可判定）
        CardTradeFilter.validatePool("商店货架池", CardShopCatalog.entries());
        // 专属牌标签的 fail-loud 校验**不能**在 mod 初始化期跑：那时 datapack 还没装载，
        // `stack.is(ModTags.Items.IS_EXCLUSIVE)` 恒为 false ⇒ 必然打一条「解析出 0 张物品」的假 ERROR
        // （2026-11-02 实机实测：server/client 两条日志都在 mod init 阶段报 0，而真运行期标签是绑好的）。
        // 等价口径对齐 forge-1.20.1 的 ServerAboutToStartEvent / neoforge-1.21.1 的 TagsUpdatedEvent：
        // 都发生在 datapack 装载之后。Fabric 侧的等价时机是 SERVER_STARTED（与 AccessoriesCompat 同款）。
        ServerLifecycleEvents.SERVER_STARTED.register(server -> CardTradeFilter.validateExclusiveTag());

        int cardListings = 0;
        for (int level = 1; level <= 5; level++) {
            final int tradeLevel = level;
            List<CardShopCatalog.PoolEntry> pool = poolFor(tradeLevel);
            CardTradeFilter.validatePool("卡牌交易员 L" + tradeLevel + " 交易池", pool);
            if (pool.isEmpty()) {
                LOGGER.error("[card_trader] 等级 {} 的可售卡池为空，该级不会上架任何卡牌报价！", tradeLevel);
                continue;
            }
            TradeOfferHelper.registerVillagerOffers(profession, tradeLevel, listings ->
                    listings.add(new CardTraderListing(pool, MIN_PRICE[tradeLevel], MAX_PRICE[tradeLevel],
                            VILLAGER_XP[tradeLevel])));
            cardListings++;
        }

        // 老手级解锁「1 绿宝石 → 1 星币」；佩戴绿宝石骰子的玩家会在发包/结算路径上被过滤掉（见 EmeraldDiceTrade）
        TradeOfferHelper.registerVillagerOffers(profession, EXCHANGE_LEVEL, listings ->
                listings.add(new FixedItemListing(Items.EMERALD, EXCHANGE_EMERALD_COST,
                        ModItems.STAR_COIN.get(), EXCHANGE_STAR_COIN_COUNT,
                        EXCHANGE_MAX_USES, EXCHANGE_XP, EXCHANGE_PRICE_MULTIPLIER)));

        LOGGER.info("[card_trader] 卡牌交易员交易表已注册：卡牌 listing {} 条 + 绿宝石兑星币 1 条（{} 级解锁）。",
                cardListings, EXCHANGE_LEVEL);
    }

    /** 取某一级的可售卡池：档位属于该级允许集 ∧ 通过 {@link CardTradeFilter#isSellable}（排除专属/巅峰/奇特）。 */
    private static List<CardShopCatalog.PoolEntry> poolFor(int level) {
        List<CardShopCatalog.Tier> tiers = LEVEL_TIERS.get(level);
        List<CardShopCatalog.PoolEntry> pool = new ArrayList<>();
        for (CardShopCatalog.PoolEntry entry : CardShopCatalog.entries()) {
            CardShopCatalog.Tier tier = CardShopCatalog.tierOf(entry);
            if (tier == null || !tiers.contains(tier)) {
                continue;
            }
            if (!CardTradeFilter.isSellable(entry.item().get())) {
                continue;
            }
            pool.add(entry);
        }
        return pool;
    }

    /** 单个等级的卡牌报价：按池内权重抽一张可售卡，价格按卡面点数微调。 */
    private record CardTraderListing(List<CardShopCatalog.PoolEntry> pool, int minPrice, int maxPrice, int villagerXp)
            implements VillagerTrades.ItemListing {

        @Override
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
            // 1.20.1 的 MerchantOffer 只有 ItemStack 版费用（1.21.1 是 ItemCost 版）
            return new MerchantOffer(new ItemStack(Items.EMERALD, priceFor(result)), result,
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
