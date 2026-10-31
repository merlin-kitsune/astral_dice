package com.merlinkitsune.astral_dice.datagen;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.merlinkitsune.astral_dice.AstralDiceMod;
import com.merlinkitsune.astral_dice.trade.CardTraderTrades;
import com.merlinkitsune.astral_dice.trade.ChipTraderTrades;
import com.merlinkitsune.astral_dice.trade.VillagerTradeEntry;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.data.PackOutput;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.TradeCost;
import net.minecraft.world.item.trading.VillagerTrade;

/**
 * 村民交易的数据生成：把 {@code trade/} 下的静态报价条目写成 26.1.2 的数据驱动三件套。
 *
 * <p><b>为什么需要它</b>：26.1.2 删除了 {@code VillagerTradesEvent} / {@code VillagerTrades.ItemListing} /
 * {@code BasicItemListing}，村民交易改成纯数据驱动 ——
 * <ul>
 *   <li>{@code data/<ns>/villager_trade/<职业>/<等级>/<名字>.json}：一条 {@link VillagerTrade}（wants/gives/max_uses/xp…）；</li>
 *   <li>{@code data/<ns>/tags/villager_trade/<职业>/level_<n>.json}：该等级可随机到的 trade 集合；</li>
 *   <li>{@code data/<ns>/trade_set/<职业>/level_<n>.json}：{@code amount} 条 + 指向上面的标签；</li>
 * </ul>
 * 再由 {@code world/ModVillagerProfessions} 把 {@code ResourceKey<TradeSet>} 绑到职业的 1-5 级。
 *
 * <p>本提供器把 {@link CardTraderTrades#tradeEntries()} / {@link ChipTraderTrades#tradeEntries()} 展开成
 * 「每件商品 × 它所在的等级」的 JSON，因此**价格逻辑仍只有一份**（在 {@code trade/} 里），数据只是它的投影。
 *
 * <p><b>与原版的分布差异</b>：{@code TradeSet} 只做**等概率**抽取（{@code HolderSet#getRandomElement}），
 * 没有权重概念；1.21.1 的加权抽卡（稀有 6 / 史诗 3 / 传奇 2）无法在数据里表达。由于每个等级内的档位是
 * 固定的（1 级只出稀有、3 级只出史诗、5 级只出传奇），只有 2 级与 4 级会混档，混档时改为等概率。
 * 用「同一张卡写多份 trade」来伪造权重会让同一等级出现两条一模一样的报价（{@code allow_duplicates=false}
 * 也只按条目去重、不比对报价内容），所以这里选择等概率并在 {@code docs/migration-26.1.2-rc7-mapping.md} 登记。
 */
public class ModVillagerTradeProvider implements DataProvider {

    /** 每个等级上架的报价条数（与 1.21.1 的「每级最多 2 条」一致）。 */
    private static final int OFFERS_PER_LEVEL = 2;

    private final PackOutput output;
    private final CompletableFuture<HolderLookup.Provider> lookupProvider;

    public ModVillagerTradeProvider(PackOutput output, CompletableFuture<HolderLookup.Provider> lookupProvider) {
        this.output = output;
        this.lookupProvider = lookupProvider;
    }

    @Override
    public CompletableFuture<?> run(CachedOutput cache) {
        Map<Identifier, VillagerTrade> trades = new LinkedHashMap<>();
        Map<Identifier, JsonObject> tags = new LinkedHashMap<>();
        Map<Identifier, JsonObject> tradeSets = new LinkedHashMap<>();

        addProfession(trades, tags, tradeSets, "card_trader", CardTraderTrades.tradeEntries(),
                CardTraderTrades.exchangeEntry());
        addProfession(trades, tags, tradeSets, "chip_trader", ChipTraderTrades.tradeEntries(),
                ChipTraderTrades.exchangeEntry());

        HolderLookup.Provider registries = this.lookupProvider.join();
        PackOutput.PathProvider tradePath = this.output.createRegistryElementsPathProvider(Registries.VILLAGER_TRADE);
        PackOutput.PathProvider tagPath = this.output.createRegistryTagsPathProvider(Registries.VILLAGER_TRADE);
        PackOutput.PathProvider tradeSetPath = this.output.createRegistryElementsPathProvider(Registries.TRADE_SET);

        List<CompletableFuture<?>> futures = new ArrayList<>();
        for (Map.Entry<Identifier, VillagerTrade> entry : trades.entrySet()) {
            futures.add(DataProvider.saveStable(cache, registries, VillagerTrade.CODEC, entry.getValue(),
                    tradePath.json(entry.getKey())));
        }
        for (Map.Entry<Identifier, JsonObject> entry : tags.entrySet()) {
            futures.add(DataProvider.saveStable(cache, entry.getValue(), tagPath.json(entry.getKey())));
        }
        for (Map.Entry<Identifier, JsonObject> entry : tradeSets.entrySet()) {
            futures.add(DataProvider.saveStable(cache, entry.getValue(), tradeSetPath.json(entry.getKey())));
        }
        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
    }

    @Override
    public String getName() {
        return "Astral Dice villager trades";
    }

    /**
     * 展开一个职业：每个等级一个 trade_set + 一个标签，所有报价写进 {@code villager_trade/<职业>/level_<n>/}。
     *
     * @param profession 职业路径（同时是数据包里的目录名）
     * @param entries    该职业在 1-5 级的静态报价（来自 {@code trade/} 的构建器）
     * @param exchange   老手级固定的「1 绿宝石 → 1 星币」兑换条目
     */
    private static void addProfession(Map<Identifier, VillagerTrade> trades, Map<Identifier, JsonObject> tags,
            Map<Identifier, JsonObject> tradeSets, String profession,
            List<VillagerTradeEntry> entries, VillagerTradeEntry exchange) {
        for (int level = 1; level <= 5; level++) {
            JsonArray values = new JsonArray();
            for (VillagerTradeEntry entry : entries) {
                if (entry.level() != level) {
                    continue;
                }
                Identifier id = id(profession + "/level_" + level + "/" + pathOf(entry.item()));
                trades.put(id, toTrade(entry));
                values.add(id.toString());
            }
            if (level == exchange.level()) {
                Identifier id = id(profession + "/level_" + level + "/emerald_to_star_coin");
                trades.put(id, toTrade(exchange));
                values.add(id.toString());
            }
            if (values.isEmpty()) {
                // 职业侧对 1-5 级**全部**绑了 TradeSet 键，缺任何一级都会让数据包加载时报缺失注册项；
                // 而静默跳过会变成「村民升级却永远不出新报价」的隐性缺陷 —— 直接失败更好排查。
                throw new IllegalStateException("[" + profession + "] 等级 " + level
                        + " 没有任何可售商品，无法生成 TradeSet（检查 CardTraderTrades/ChipTraderTrades 的档位池）");
            }
            JsonObject tag = new JsonObject();
            tag.add("values", values);
            tags.put(id(profession + "/level_" + level), tag);

            JsonObject set = new JsonObject();
            // 写成 2.0 而不是 2：与官方 trade_set 的 JSON 形态一致（NumberProvider 也可接受整数）。
            set.addProperty("amount", (float) OFFERS_PER_LEVEL);
            set.addProperty("trades", "#" + AstralDiceMod.MODID + ":" + profession + "/level_" + level);
            tradeSets.put(id(profession + "/level_" + level), set);
        }
    }

    /** 一条静态报价 → 原版 {@link VillagerTrade}（wants 固定为绿宝石，gives 为商品，其余沿用 trade/ 的常量）。 */
    private static VillagerTrade toTrade(VillagerTradeEntry entry) {
        TradeCost wants = new TradeCost(Items.EMERALD, entry.emeraldPrice());
        ItemStackTemplate gives = new ItemStackTemplate(entry.item(), 1);
        return new VillagerTrade(wants, gives, entry.maxUses(), entry.xp(), entry.reputationDiscount(),
                Optional.empty(), List.of());
    }

    /** 物品的注册路径（{@code astral_dice:attack_card_charge} → {@code attack_card_charge}）。 */
    private static String pathOf(Item item) {
        Identifier key = BuiltInRegistries.ITEM.getKey(item);
        return key == null ? "unknown" : key.getPath();
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(AstralDiceMod.MODID, path);
    }
}
