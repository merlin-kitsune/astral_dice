package com.merlinkitsune.astral_dice.world;

import com.google.common.collect.ImmutableSet;
import com.merlinkitsune.astral_dice.AstralDiceMod;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.item.trading.TradeSet;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * 村民职业注册：新职业「卡牌交易员」（{@code astral_dice:card_trader}）。
 *
 * <p>职业显示名（也就是交易界面标题）的 lang 键由原版 {@code Villager#getTypeName()} 拼接而成：
 * {@code entity.minecraft.villager.<命名空间>.<路径>} ⇒ 本职业为
 * {@code entity.minecraft.villager.astral_dice.card_trader}。
 *
 * <p>工作站点贴图：{@code astral_dice:textures/entity/villager/profession/card_trader.png}
 * （路径由 {@code VillagerProfessionLayer#getResourceLocation} 按职业注册 id 拼接，等级徽章仍用原版贴图）。
 *
 * <p><b>26.1.2 API 变更</b>：职业与交易表都改成数据驱动。{@code VillagerProfession} 变成 record
 * （7 个组件，含 {@code name} Component 与 {@code tradeSetsByLevel}），{@code VillagerTradesEvent} 已删除，
 * 报价改由 {@code Registries.VILLAGER_TRADE} 的 {@code VillagerTrade} + {@code TradeSet} 数据包提供。
 * 本文件只负责职业注册；{@code tradeSetsByLevel} 绑定到 datagen 生成的
 * {@code data/astral_dice/trade_set/<职业>/level_<n>.json}，报价内容仍由 {@code trade/} 下的构建器产出。
 *
 * <p>筹码商人（{@code astral_dice:chip_trader}）同属本职业体系：工作站点 = 筹码商店
 * （{@code astral_dice:chip_shop}），交易表由 {@code trade/ChipTraderTrades} 填充；
 * 职业贴图 {@code astral_dice:textures/entity/villager/profession/chip_trader.png} 同样由
 * {@code VillagerProfessionLayer#getResourceLocation} 按职业注册 id 拼接（等级徽章仍用原版贴图）。
 */
public final class ModVillagerProfessions {

    public static final DeferredRegister<VillagerProfession> PROFESSIONS =
            DeferredRegister.create(Registries.VILLAGER_PROFESSION, AstralDiceMod.MODID);

    public static final DeferredHolder<VillagerProfession, VillagerProfession> CARD_TRADER =
            PROFESSIONS.register("card_trader", () -> new VillagerProfession(
                    // 26.1.2：VillagerProfession 由 class 变成 record，职业显示名不再由注册 id 自动拼，
                    // 必须显式给 Component；键名与原版一致（entity.<ns>.villager.<path>）。
                    Component.translatable("entity.astral_dice.villager.card_trader"),
                    // heldJobSite：本站点就是卡牌商店
                    holder -> holder.is(ModPoiTypes.CARD_SHOP_KEY),
                    // acquirableJobSite：**专用**标签（只含 card_shop）—— 共用原版共享标签会让本职业去认领
                    // 别人家的工作站，且 JEI（Just Enough Professions）按该谓词枚举站点，会把两个职业画成同一个方块。
                    holder -> holder.is(ModPoiTypes.CARD_TRADER_JOB_SITES),
                    ImmutableSet.of(),
                    ImmutableSet.of(),
                    null,
                    // 26.1.2：交易表随职业一起变成数据驱动（Int2ObjectMap<ResourceKey<TradeSet>>）。
                    // 1-5 级各绑一个数据包里的 TradeSet；具体报价见 datagen/ModVillagerTradeProvider。
                    tradeSets("card_trader")));

    /** 筹码商人：工作站点 = 筹码商店，交易表见 {@code trade/ChipTraderTrades}。 */
    public static final DeferredHolder<VillagerProfession, VillagerProfession> CHIP_TRADER =
            PROFESSIONS.register("chip_trader", () -> new VillagerProfession(
                    Component.translatable("entity.astral_dice.villager.chip_trader"),
                    // heldJobSite：本站点就是筹码商店
                    holder -> holder.is(ModPoiTypes.CHIP_SHOP_KEY),
                    // acquirableJobSite：**专用**标签（只含 chip_shop），口径同 CARD_TRADER
                    holder -> holder.is(ModPoiTypes.CHIP_TRADER_JOB_SITES),
                    ImmutableSet.of(),
                    ImmutableSet.of(),
                    null,
                    // 26.1.2：交易表随职业一起变成数据驱动（Int2ObjectMap<ResourceKey<TradeSet>>）。
                    // 1-5 级各绑一个数据包里的 TradeSet；具体报价见 datagen/ModVillagerTradeProvider。
                    tradeSets("card_trader")));

    private ModVillagerProfessions() {
    }
    /**
     * 把 {@code 1-5} 级各绑到一个 {@code data/astral_dice/trade_set/<职业>/level_<n>.json}。
     *
     * <p>这里只算 {@link ResourceKey}，**不碰**任何物品注册表 —— 职业注册事件早于物品查询可用的时点，
     * 若在此处遍历卡池（{@code DeferredHolder#get()}）会撞上「unbound value」。
     * <p>对应 JSON 由 {@code datagen/ModVillagerTradeProvider} 生成；某一级若没有任何可售商品，
     * 该提供器会直接报错而不是跳过，避免这里绑出指向空数据的 {@code TradeSet}。
     */
    private static Int2ObjectMap<ResourceKey<TradeSet>> tradeSets(String profession) {
        Int2ObjectMap<ResourceKey<TradeSet>> byLevel = new Int2ObjectOpenHashMap<>();
        for (int level = 1; level <= 5; level++) {
            byLevel.put(level, ResourceKey.create(Registries.TRADE_SET,
                    Identifier.fromNamespaceAndPath(AstralDiceMod.MODID, profession + "/level_" + level)));
        }
        return byLevel;
    }
}
