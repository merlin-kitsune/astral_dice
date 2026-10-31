package com.merlinkitsune.astral_dice.trade;

import com.merlinkitsune.astral_dice.AstralDiceMod;
import com.merlinkitsune.astral_dice.combat.CardRegistry;
import com.merlinkitsune.astral_dice.item.ModItems;
import com.merlinkitsune.astral_dice.shop.CardShopCatalog;
import com.merlinkitsune.astral_dice.shop.CardTradeFilter;
import com.merlinkitsune.astral_dice.world.ModVillagerProfessions;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.VillagerTrades;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraftforge.common.BasicItemListing;
import net.minecraftforge.event.village.VillagerTradesEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
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
 * <p>平台差异（1.20.1）：
 * <ul>
 *   <li>总线：1.20.1 的 {@code VillagerTradesEvent} 挂在 {@code MinecraftForge.EVENT_BUS}
 *       （VillagerTradesEvent.java:21 类注释明写），故用 {@code @Mod.EventBusSubscriber(bus = Bus.FORGE)}
 *       （1.21.1 是 {@code NeoForge.EVENT_BUS} + {@code @EventBusSubscriber}）。</li>
 *   <li>事件载荷：{@code getTrades()} 同为 {@code Int2ObjectMap<List<VillagerTrades.ItemListing>>}
 *       （VillagerTradesEvent.java:41），逐级 add 的写法两线一致；{@code getType()} 返回
 *       {@code VillagerProfession}（:46），而 1.20.1 的 {@code VillagerProfession} 也是同签名 record
 *       （VillagerProfession.java:25）⇒ {@code !=} 值比较的口径相同。</li>
 *   <li>{@code ItemListing#getOffer(Entity, RandomSource)}：1.20.1 已是 {@code RandomSource} 签名
 *       （VillagerTrades.java:141），无废弃的 {@code Random} 重载，方法体逐字一致。</li>
 *   <li>报价构造：1.20.1 的 {@code MerchantOffer} 只有 {@code (ItemStack, ItemStack, int, int, float)}
 *       一类的栈式构造（MerchantOffer.java:56），没有 1.21.1 的 {@code ItemCost} 版本
 *       ⇒ 费用写成 {@code new ItemStack(Items.EMERALD, 价格)}。</li>
 *   <li>{@code BasicItemListing} 取自 {@code net.minecraftforge.common}（BasicItemListing.java:39
 *       的 5 参构造与 1.21.1 的 NeoForge 版同签名）。</li>
 * </ul>
 *
 * <p>触发时机（1.20.1 取证）：{@code net.minecraftforge.common.VillagerTradingManager} 的
 * {@code loadTrades(ServerAboutToStartEvent)}（VillagerTradingManager.java:41-45；注册点
 * ForgeMod.java:463 {@code MinecraftForge.EVENT_BUS.addListener(VillagerTradingManager::loadTrades)}）
 * 在开服时调用 {@code postVillagerEvents()}（VillagerTradingManager.java:61-83）：遍历
 * {@code ForgeRegistries.VILLAGER_PROFESSIONS}（:66，含本 mod 职业）→ 为 1-5 级各建一个非空
 * {@code NonNullList}（:70-73）→ {@code MinecraftForge.EVENT_BUS.post(new VillagerTradesEvent(...))}（:78）
 * → 把结果写回 {@code VillagerTrades.TRADES}（:81）。触发点 = {@code ServerAboutToStartEvent}
 * （ServerLifecycleHooks.java:98，由 DedicatedServer.java:162 调用，早于 :164 {@code this.loadLevel()}）。
 * 该时点物品标签已绑定：{@code WorldLoader.load} 在返回前即调 {@code updateRegistryTags}
 * （WorldLoader.java:43-46），而 MinecraftServer 直到 MinecraftServer.java:275 才拿到该 WorldStem，
 * 故 {@link CardTradeFilter#validateExclusiveTag()} 能读到 {@code astral_dice:is_exclusive}；
 * {@code CardRegistry.init()}（FMLCommonSetup）同样早于此。副作用说明：该事件每次开服只发一次，
 * {@code /reload} 不会重发（VillagerTrades.TRADES 已被就地改写）。
 *
 * <p>与 1.21.1 的差异（同批取证）：NeoForge 的同名类改挂 {@code TagsUpdatedEvent}（仅
 * {@code UpdateCause.SERVER_DATA_LOAD} 分支处理）——{@code neoforge-21.1.235-sources.jar 内
 * net/neoforged/neoforge/common/VillagerTradingManager.java:37-42}，遍历
 * {@code BuiltInRegistries.VILLAGER_PROFESSION}（:61）；故 1.21.1 在服务器数据包标签加载（含
 * {@code /reload}）时重发，本线只在开服时发一次。两线发报时点的物品标签都已绑定（1.21.1 事件本身
 * 就是标签更新回调；1.20.1 见上 WorldLoader.java:43-46），因此「专属牌严格排除」的判据口径一致。
 */
@Mod.EventBusSubscriber(modid = AstralDiceMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
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

    private CardTraderTrades() {
    }

    @SubscribeEvent
    public static void onVillagerTrades(VillagerTradesEvent event) {
        if (event.getType() != ModVillagerProfessions.CARD_TRADER.get()) {
            return;
        }
        // 报价表构建期的 fail-loud 校验（此刻数据包标签已绑定）
        CardTradeFilter.validateExclusiveTag();
        CardTradeFilter.validatePool("商店货架池", CardShopCatalog.entries());

        int cardListings = 0;
        for (int level = 1; level <= 5; level++) {
            List<CardShopCatalog.PoolEntry> pool = poolFor(level);
            CardTradeFilter.validatePool("卡牌交易员 L" + level + " 交易池", pool);
            if (pool.isEmpty()) {
                LOGGER.error("[card_trader] 等级 {} 的可售卡池为空，该级不会上架任何卡牌报价！", level);
                continue;
            }
            event.getTrades().get(level)
                    .add(new CardTraderListing(pool, MIN_PRICE[level], MAX_PRICE[level], VILLAGER_XP[level]));
            cardListings++;
        }

        // 老手级解锁「1 绿宝石 → 1 星币」；佩戴绿宝石骰子的玩家会在发包/结算路径上被过滤掉（见 EmeraldDiceTrade）
        event.getTrades().get(EXCHANGE_LEVEL).add(new BasicItemListing(new ItemStack(Items.EMERALD),
                new ItemStack(ModItems.STAR_COIN.get()), EXCHANGE_MAX_USES, EXCHANGE_XP, EXCHANGE_PRICE_MULTIPLIER));

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
