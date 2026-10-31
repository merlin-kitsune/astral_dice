package com.merlinkitsune.astral_dice.trade;

import com.merlinkitsune.astral_dice.AstralDiceMod;
import com.merlinkitsune.astral_dice.item.ModItems;
import com.merlinkitsune.astral_dice.shop.ChipShopCatalog;
import com.merlinkitsune.astral_dice.world.ModVillagerProfessions;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.VillagerTrades;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.BasicItemListing;
import net.neoforged.neoforge.event.village.VillagerTradesEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 村民职业「筹码商人」（{@code astral_dice:chip_trader}）的交易表 —— **镜像卡牌交易员**
 * （{@code trade/CardTraderTrades}），只把商品从卡牌换成筹码（{@link ChipShopCatalog#entries()} 的 66 枚池）。
 *
 * <p>落表（用户批准口径，价格为绿宝石）：
 * <pre>
 * 等级              上架筹码               价格                        出现权重
 * 新手 Novice(1)     稀有                   20-24                       6
 * 学徒 Apprentice(2) 稀有 + 少量史诗         24-32（稀有 24 / 史诗 28）  6 / 3
 * 老手 Journeyman(3) 史诗 + 绿宝石→星币 1:1  36（史诗）                   3（兑换为固定条目）
 * 专家 Expert(4)     史诗 / 传奇             40-48（史诗 44 / 传奇 48）   3 / 2
 * 大师 Master(5)     传奇                   48-64（实际 64）             2
 * </pre>
 *
 * <p><b>品质与等级绑定</b>：{@link #LEVEL_TIERS} 逐级限定可上架的品质档位，档位用
 * {@link ChipShopCatalog#TIER_RARE}/{@link ChipShopCatalog#TIER_EPIC}/{@link ChipShopCatalog#TIER_LEGENDARY}
 * 的**品质码**表示；权重取 {@link ChipShopCatalog#weightOf}（稀有 6 / 史诗 3 / 传奇 2，与商店抽卡同一口径），
 * 抽卡走 {@link ChipShopCatalog#pickWeighted}。
 *
 * <p><b>定价口径（设计决策）</b>：筹码没有「卡面点数」概念（卡牌交易员的定价 = 档位下限 + 卡面固定点数），
 * 因此这里改为 **按品质档位在区间内等距取点**：
 * {@code priceFor = clamp(minPrice + round((maxPrice - minPrice) * (tier - 1) / 2), minPrice, maxPrice)}
 * ⇒ 稀有 = 下限 / 史诗 = 中点 / 传奇 = 上限（2026-10-29 用户裁决：整体上调，全局区间 20-64 绿宝石）。
 * 抽不到（池空）就返回 {@code null} —— 原版
 * {@code AbstractVillager#addOffersFromItemListings} 会继续抽下一条 listing（每级最多 2 条报价）。
 *
 * <p><b>绿宝石兑换 + 骰子屏蔽</b>：老手级（3 级）解锁「1 绿宝石 → 1 星币」；佩戴绿宝石骰子的玩家由
 * {@code trade/EmeraldDiceTrade} 的 mixin 在发包/结算路径上**通用**剔除（对全部村民职业生效，含本职业），
 * 故此处无需为本条兑换另写屏蔽逻辑。
 *
 * <p>本处理器挂在 {@code NeoForge.EVENT_BUS}（游戏总线）：{@code VillagerTradingManager} 对**每个已注册职业**
 * （含本 mod 的职业）各发一次该事件，并把结果写回 {@code VillagerTrades.TRADES}；触发时机是数据包重载
 * （{@code TagsUpdatedEvent} 的 SERVER_DATA_LOAD），因此校验时数据包标签已绑定。
 */
@EventBusSubscriber(modid = AstralDiceMod.MODID)
public final class ChipTraderTrades {

    private static final Logger LOGGER = LoggerFactory.getLogger("astral_dice/chip_trader");

    /** 每级价格下限（绿宝石），索引 = 村民等级 1-5。 */
    private static final int[] MIN_PRICE = {0, 20, 24, 32, 40, 48};

    /** 每级价格上限（绿宝石），索引 = 村民等级 1-5。 */
    private static final int[] MAX_PRICE = {0, 24, 32, 40, 48, 64};

    /** 每级的职业经验（沿用原版「越高级给得越多」的梯度）。 */
    private static final int[] VILLAGER_XP = {0, 2, 5, 10, 15, 30};

    /** 每级允许出现的品质档位（用 ChipShopCatalog 的品质码 1/2/3；权重见 ChipShopCatalog#weightOf）。索引 = 村民等级 1-5。 */
    private static final List<List<Integer>> LEVEL_TIERS = List.of(
            List.of(),
            List.of(ChipShopCatalog.TIER_RARE),
            List.of(ChipShopCatalog.TIER_RARE, ChipShopCatalog.TIER_EPIC),
            List.of(ChipShopCatalog.TIER_EPIC),
            List.of(ChipShopCatalog.TIER_EPIC, ChipShopCatalog.TIER_LEGENDARY),
            List.of(ChipShopCatalog.TIER_LEGENDARY));

    /** 筹码报价的售出上限与经验倍率（沿用原版村民交易常用值，与卡牌交易员一致）。 */
    private static final int CHIP_MAX_USES = 12;
    private static final float CHIP_PRICE_MULTIPLIER = 0.05F;

    /** 「1 绿宝石 → 1 星币」兑换：12 次 / 1 经验 / **价格倍率 0** —— 倍率非 0 时卖完后 demand 累积会把 1 抬到 2。 */
    private static final int EXCHANGE_MAX_USES = 12;
    private static final int EXCHANGE_XP = 1;
    private static final float EXCHANGE_PRICE_MULTIPLIER = 0.0F;

    /** 兑换条目所在的村民等级：老手（3 级）解锁。 */
    private static final int EXCHANGE_LEVEL = 3;

    private ChipTraderTrades() {
    }

    @SubscribeEvent
    public static void onVillagerTrades(VillagerTradesEvent event) {
        if (event.getType() != ModVillagerProfessions.CHIP_TRADER.get()) {
            return;
        }
        int chipListings = 0;
        for (int level = 1; level <= 5; level++) {
            List<ChipShopCatalog.Entry> pool = poolFor(level);
            if (pool.isEmpty()) {
                LOGGER.error("[chip_trader] 等级 {} 的可售筹码池为空，该级不会上架任何筹码报价！", level);
                continue;
            }
            event.getTrades().get(level)
                    .add(new ChipTraderListing(pool, MIN_PRICE[level], MAX_PRICE[level], VILLAGER_XP[level]));
            chipListings++;
        }

        // 老手级解锁「1 绿宝石 → 1 星币」；佩戴绿宝石骰子的玩家会在发包/结算路径上被过滤掉（见 EmeraldDiceTrade）
        event.getTrades().get(EXCHANGE_LEVEL).add(new BasicItemListing(new ItemStack(Items.EMERALD),
                new ItemStack(ModItems.STAR_COIN.get()), EXCHANGE_MAX_USES, EXCHANGE_XP, EXCHANGE_PRICE_MULTIPLIER));

        LOGGER.info("[chip_trader] 筹码商人交易表已注册：筹码 listing {} 条 + 绿宝石兑星币 1 条（{} 级解锁）。",
                chipListings, EXCHANGE_LEVEL);
    }

    /** 取某一级的可售筹码池：品质档位属于该级允许集（筹码无「可售性」过滤，全部可售）。 */
    private static List<ChipShopCatalog.Entry> poolFor(int level) {
        List<Integer> tiers = LEVEL_TIERS.get(level);
        List<ChipShopCatalog.Entry> pool = new ArrayList<>();
        for (ChipShopCatalog.Entry entry : ChipShopCatalog.entries()) {
            int tier = ChipShopCatalog.tierOf(entry);
            if (tier < 0 || !tiers.contains(tier)) {
                continue;
            }
            pool.add(entry);
        }
        return pool;
    }

    /** 单个等级的筹码报价：按品质权重抽一枚，价格按品质档位在区间内等距取点。 */
    private record ChipTraderListing(List<ChipShopCatalog.Entry> pool, int minPrice, int maxPrice, int villagerXp)
            implements VillagerTrades.ItemListing {

        @Override
        public MerchantOffer getOffer(Entity trader, RandomSource random) {
            ChipShopCatalog.Entry picked = ChipShopCatalog.pickWeighted(random, this.pool);
            if (picked == null) {
                return null;
            }
            return new MerchantOffer(new ItemCost(Items.EMERALD, priceFor(picked)),
                    new ItemStack(picked.item()), CHIP_MAX_USES, this.villagerXp, CHIP_PRICE_MULTIPLIER);
        }

        /** 按品质档位在区间内**等距取点**（稀有 = 下限 / 史诗 = 中点 / 传奇 = 上限；筹码无卡面点数），结果夹在区间内。 */
        private int priceFor(ChipShopCatalog.Entry entry) {
            int span = this.maxPrice - this.minPrice;
            int offset = Math.round(span * (entry.tier() - 1) / 2.0F);
            return Math.max(this.minPrice, Math.min(this.maxPrice, this.minPrice + offset));
        }
    }
}
