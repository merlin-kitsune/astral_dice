package com.merlinkitsune.astral_dice.world;

import com.google.common.collect.ImmutableSet;
import com.merlinkitsune.astral_dice.AstralDiceMod;
import java.util.Set;
import net.fabricmc.fabric.api.object.builder.v1.world.poi.PointOfInterestHelper;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 兴趣点（POI）注册：把「卡牌商店 / 筹码商店」登记为村民可认领的**工作站点**。
 *
 * <p>只有 POI 还不够：村民认领走的是原版标签 {@code minecraft:acquirable_job_site}
 * （见 {@code VillagerProfession.ALL_ACQUIRABLE_JOBS} 与 {@code Villager.POI_MEMORIES} 的
 * {@code POTENTIAL_JOB_SITE} 判据），因此本模组另外提供了
 * {@code data/minecraft/tags/point_of_interest_type/acquirable_job_site.json} 把
 * {@code astral_dice:card_shop} / {@code astral_dice:chip_shop} 并入该标签 —— 少了这一步村民永不转职。
 *
 * <p>⚠️ 2026-10-23 起还额外提供两条**职业专用**标签 {@code #astral_dice:card_trader_job_site} /
 * {@code #astral_dice:chip_trader_job_site}（见下方常量），职业的 {@code acquirableJobSite} 改用它 ——
 * 共用原版共享标签会让村民抢别人家工作站，并让 JEI（Just Enough Professions）把两个职业画成同一个工作方块。
 *
 * <h2>Fabric 1.20.1 的平台重建（关键差异，不可照搬 Forge 语义）</h2>
 *
 * <p>原版 {@code PoiTypes.TYPE_BY_STATE} 是 {@code private static final Map<BlockState, Holder<PoiType>>}，
 * 唯一写入入口是 {@code private static PoiType register(Registry, ResourceKey, Set<BlockState>, int, int)}
 * —— 它在字节码里一次做完三件事：{@code new PoiType(...)} → {@code Registry.register(...)}
 * → {@code registry.getHolderOrThrow(key)} → {@code registerBlockStates(holder, states)}。
 * 也就是说：**只把 PoiType 注册进注册表而不写 {@code TYPE_BY_STATE}，村民认不出方块**
 * （{@code PoiTypes.forState(state)} 查的就是这张 map）。
 *
 * <p>Forge 1.20.1 用 AT 把 {@code registerBlockStates} 打成**空方法**、改由
 * {@code GameData.getBlockStatePointOfInterestTypeMap()} 承担映射；<b>Fabric 走的是原版语义</b>，
 * 那条差异绝不能带进来。本类因此**不用自建 shim 的 {@code DeferredRegister}**
 * （它只调 {@code Registry.register}，不写 {@code TYPE_BY_STATE}），改用 fabric-api 的
 * {@link PointOfInterestHelper#register(ResourceLocation, int, int, Iterable)}：
 * 它靠 fabric 自带的 access widener 调上面那个私有 {@code PoiTypes.register}，
 * ⇒ 模组侧**零 AW、零 mixin**，注册表与映射一次写完。
 *
 * <p>方块状态来源：{@code ModBlocks.CARD_SHOP} / {@code ModBlocks.CHIP_SHOP} 的全部
 * {@code BlockState}（与 Forge / NeoForge 两线同口径：{@code getStateDefinition().getPossibleStates()}）。
 * 本批次 {@code block/ModBlocks} 由另一子代理产出，故这里**不硬依赖**该类：默认按注册 id
 * （{@code astral_dice:card_shop} / {@code astral_dice:chip_shop}）从 {@code BuiltInRegistries.BLOCK} 解析，
 * 需要显式传入方块时用 {@link #register(Block, Block)}。
 *
 * <p>调用时机（由入口类接线）：<b>必须</b>在 {@code ModBlocks} 提交之后、且不晚于 mod 初始化结束
 * （{@code BuiltInRegistries.freeze()} 之前）；{@code TYPE_BY_STATE} 永不重建，故一次注册即长期有效。
 */
public final class ModPoiTypes {

    private static final Logger LOGGER = LoggerFactory.getLogger("astral_dice/poi");

    /** 工作站点 id（= 方块注册 id；职业的 heldJobSite 谓词按 {@link #CARD_SHOP_KEY} 判定）。 */
    public static final ResourceLocation CARD_SHOP_ID = ResourceLocation.fromNamespaceAndPath(AstralDiceMod.MODID, "card_shop");

    /** 工作站点 id（= 方块注册 id）。 */
    public static final ResourceLocation CHIP_SHOP_ID = ResourceLocation.fromNamespaceAndPath(AstralDiceMod.MODID, "chip_shop");

    /** 卡牌商店 POI 的注册键 —— 必须与标签文件里的 id 一致。 */
    public static final ResourceKey<PoiType> CARD_SHOP_KEY =
            ResourceKey.create(Registries.POINT_OF_INTEREST_TYPE, CARD_SHOP_ID);

    /** 筹码商店 POI 的注册键 —— 必须与标签文件里的 id 一致。 */
    public static final ResourceKey<PoiType> CHIP_SHOP_KEY =
            ResourceKey.create(Registries.POINT_OF_INTEREST_TYPE, CHIP_SHOP_ID);

    /**
     * 「卡牌交易员」的**专用**可认领工作站点标签（{@code #astral_dice:card_trader_job_site}，
     * 内容 = {@code astral_dice:card_shop}）。
     *
     * <p>为什么不能沿用原版共享标签 {@code #minecraft:acquirable_job_site}
     * （{@code VillagerProfession.ALL_ACQUIRABLE_JOBS}）：职业的 {@code acquirableJobSite} 谓词被三处消费 ——
     * {@code VillagerGoalPackages#getCorePackage} 的 {@code ValidateNearbyPoi(POTENTIAL_JOB_SITE)} /
     * {@code AcquirePoi(JOB_SITE)}，以及 JEI 附属 <b>Just Enough Professions</b> 用它枚举
     * 「该职业能在哪些方块前工作」。共享标签对**所有**工作站点都为真 ⇒ ① 本职业村民会去认领别人家的工作站；
     * ② JEI 里两个职业的方块列表完全相同（界面只显示第一项）。
     *
     * <p>共享标签**必须保留**（{@code data/minecraft/tags/point_of_interest_type/acquirable_job_site.json}）：
     * 无业村民（职业 {@code none}）的核心行为包用的是 {@code NONE.acquirableJobSite()} = 共享标签，
     * 少了它本模组的工作站永远不会被发现，也就永不转职。⇒ 分工：共享标签 = 能否被发现并转职；
     * 本专用标签 = 转职后只认本站点。
     */
    public static final TagKey<PoiType> CARD_TRADER_JOB_SITES =
            TagKey.create(Registries.POINT_OF_INTEREST_TYPE,
                    ResourceLocation.fromNamespaceAndPath(AstralDiceMod.MODID, "card_trader_job_site"));

    /** 「筹码商人」的专用可认领工作站点标签；口径同 {@link #CARD_TRADER_JOB_SITES}。 */
    public static final TagKey<PoiType> CHIP_TRADER_JOB_SITES =
            TagKey.create(Registries.POINT_OF_INTEREST_TYPE,
                    ResourceLocation.fromNamespaceAndPath(AstralDiceMod.MODID, "chip_trader_job_site"));

    private static boolean registered;
    private static PoiType cardShop;
    private static PoiType chipShop;

    private ModPoiTypes() {
    }

    /**
     * 入口类调用的单点接线：从 {@code BuiltInRegistries.BLOCK} 解析本模组的两个商店方块后注册 POI。
     *
     * <p>⚠️ 必须晚于 {@code block/ModBlocks} 的提交（否则方块查不到 ⇒ 状态集为空 ⇒ 村民仍认不出），
     * 且早于 mod 初始化结束（注册表冻结前）。
     */
    public static void bootstrap() {
        register(resolveBlock(CARD_SHOP_ID, "card_shop"), resolveBlock(CHIP_SHOP_ID, "chip_shop"));
    }

    /**
     * 显式提供方块版本的注册（等价于 Forge / NeoForge 两线的
     * {@code POI_TYPES.register("card_shop", () -> new PoiType(states, 1, 1))}）。
     *
     * @param cardShopBlock 卡牌商店方块（{@code ModBlocks.CARD_SHOP.get()}）
     * @param chipShopBlock 筹码商店方块（{@code ModBlocks.CHIP_SHOP.get()}）
     */
    public static void register(Block cardShopBlock, Block chipShopBlock) {
        if (registered) {
            LOGGER.warn("[poi] POI 已注册过，本次调用被忽略（原版 TYPE_BY_STATE 不可重建）。");
            return;
        }
        registered = true;
        cardShop = registerOne("card_shop", CARD_SHOP_ID, cardShopBlock);
        chipShop = registerOne("chip_shop", CHIP_SHOP_ID, chipShopBlock);
    }

    /** 注册单个工作站点：ticketCount=1、searchDistance=1（与 Forge / NeoForge 两线同取值）。 */
    private static PoiType registerOne(String name, ResourceLocation id, Block block) {
        Set<BlockState> states = statesOf(block, name);
        PoiType poiType = PointOfInterestHelper.register(id, 1, 1, states);
        LOGGER.info("[poi] 工作站点 {} 已注册：{} 个方块状态（ticketCount=1, searchDistance=1）。", id, states.size());
        return poiType;
    }

    /** 取方块的**全部** {@code BlockState}（与两线 Forge/NeoForge 的 {@code getPossibleStates()} 同口径）。 */
    private static Set<BlockState> statesOf(Block block, String name) {
        if (block == null || block == Blocks.AIR) {
            LOGGER.error("[poi] 方块 astral_dice:{} 尚未注册 —— 本次只注册 POI 条目，"
                    + "TYPE_BY_STATE 里不会有它的任何方块状态（村民将认不出该工作方块）。"
                    + "请把 ModPoiTypes 的调用移到 ModBlocks 提交之后。", name);
            return Set.of();
        }
        return ImmutableSet.copyOf(block.getStateDefinition().getPossibleStates());
    }

    /** 按注册 id 解析方块（缺失时返回 {@link Blocks#AIR}，由 {@link #statesOf} 打出错误日志）。 */
    private static Block resolveBlock(ResourceLocation id, String name) {
        Registry<Block> registry = BuiltInRegistries.BLOCK;
        Block block = registry.getOptional(id).orElse(Blocks.AIR);
        if (block == Blocks.AIR) {
            LOGGER.error("[poi] BuiltInRegistries.BLOCK 里找不到 astral_dice:{}（方块批次尚未注册？）。", name);
        }
        return block;
    }

    /** 卡牌商店工作站点（注册后可用；未注册时抛 {@link IllegalStateException}，与 shim 的 RegistryObject 同纪律）。 */
    public static PoiType cardShop() {
        if (cardShop == null) {
            throw new IllegalStateException("astral_dice:card_shop POI 尚未注册（先调用 ModPoiTypes.bootstrap()）");
        }
        return cardShop;
    }

    /** 筹码商店工作站点（注册后可用）。 */
    public static PoiType chipShop() {
        if (chipShop == null) {
            throw new IllegalStateException("astral_dice:chip_shop POI 尚未注册（先调用 ModPoiTypes.bootstrap()）");
        }
        return chipShop;
    }
}
