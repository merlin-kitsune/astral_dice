package com.merlinkitsune.astral_dice.world;

import com.google.common.collect.ImmutableSet;
import com.merlinkitsune.astral_dice.AstralDiceMod;
import com.merlinkitsune.astral_dice.block.ModBlocks;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * 兴趣点（POI）注册：把「卡牌商店」登记为村民可认领的**工作站点**。
 *
 * <p>只有 POI 还不够：村民认领走的是原版标签 {@code minecraft:acquirable_job_site}
 * （见 {@code VillagerProfession.ALL_ACQUIRABLE_JOBS} 与 {@code Villager.POI_MEMORIES} 的
 * {@code POTENTIAL_JOB_SITE} 判据），因此本模组另外提供了
 * {@code data/minecraft/tags/point_of_interest_type/acquirable_job_site.json} 把
 * {@code astral_dice:card_shop} 并入该标签 —— 少了这一步村民永不转职成「卡牌交易员」。
 *
 * <p>筹码商店（{@code astral_dice:chip_shop}）同为工作站点，供「筹码商人」职业认领，注册口径完全一致。
 *
 * <p>⚠️ 2026-10-23 起还额外提供两条**职业专用**标签 {@code #astral_dice:card_trader_job_site} /
 * {@code #astral_dice:chip_trader_job_site}（见下方常量），职业的 {@code acquirableJobSite} 改用它 ——
 * 共用原版共享标签会让村民抢别人家工作站，并让 JEI（Just Enough Professions）把两个职业画成同一个工作方块。
 */
public final class ModPoiTypes {

    public static final DeferredRegister<PoiType> POI_TYPES =
            DeferredRegister.create(Registries.POINT_OF_INTEREST_TYPE, AstralDiceMod.MODID);

    /** 工作站点 id（职业的 heldJobSite 谓词按它判定）—— 必须与标签文件里的 id 一致。 */
    public static final ResourceKey<PoiType> CARD_SHOP_KEY =
            ResourceKey.create(Registries.POINT_OF_INTEREST_TYPE,
                    ResourceLocation.fromNamespaceAndPath(AstralDiceMod.MODID, "card_shop"));

    /** 卡牌商店工作站点（1 张票券 / 1 格搜索半径，与原版工作站点同一取值）。 */
    public static final DeferredHolder<PoiType, PoiType> CARD_SHOP = POI_TYPES.register("card_shop",
            () -> new PoiType(ImmutableSet.copyOf(ModBlocks.CARD_SHOP.get().getStateDefinition().getPossibleStates()),
                    1, 1));

    /** 工作站点 id（职业的 heldJobSite 谓词按它判定）—— 必须与标签文件里的 id 一致。 */
    public static final ResourceKey<PoiType> CHIP_SHOP_KEY =
            ResourceKey.create(Registries.POINT_OF_INTEREST_TYPE,
                    ResourceLocation.fromNamespaceAndPath(AstralDiceMod.MODID, "chip_shop"));

    /** 筹码商店工作站点（1 张票券 / 1 格搜索半径，与卡牌商店同一取值）。 */
    public static final DeferredHolder<PoiType, PoiType> CHIP_SHOP = POI_TYPES.register("chip_shop",
            () -> new PoiType(ImmutableSet.copyOf(ModBlocks.CHIP_SHOP.get().getStateDefinition().getPossibleStates()),
                    1, 1));

    /**
     * 「卡牌交易员」的**专用**可认领工作站点标签（{@code #astral_dice:card_trader_job_site}，内容 = {@code astral_dice:card_shop}）。
     *
     * <p>为什么不能沿用原版共享标签 {@code #minecraft:acquirable_job_site}（{@code VillagerProfession.ALL_ACQUIRABLE_JOBS}）：
     * 职业的 {@code acquirableJobSite} 谓词被三处消费 —— {@code VillagerGoalPackages#getCorePackage} 的
     * {@code ValidateNearbyPoi(POTENTIAL_JOB_SITE)} / {@code AcquirePoi(JOB_SITE)}，以及 JEI 附属
     * <b>Just Enough Professions</b> 用它枚举「该职业能在哪些方块前工作」
     * （{@code NeoForgeProfessionPlugin#registerRecipes} 逐个 POI 调 {@code profession.acquirableJobSite().test(holder)}）。
     * 共享标签对**所有**工作站点都为真 ⇒ ① 本职业村民会去认领别人家的工作站（随后被
     * {@code AssignProfessionFromJobSite} / {@code ResetProfession} 反复重置）；② JEI 里「卡牌交易员」与
     * 「筹码商人」两条记录的方块列表完全相同（都等于共享标签里的全部站点，界面只显示第一项）——
     * 即 2026-10-23 用户报的「两个职业指向同一个工作方块」。
     *
     * <p>共享标签**必须保留**（{@code data/minecraft/tags/point_of_interest_type/acquirable_job_site.json}）：
     * 无业村民（职业 {@code none}）的核心行为包用的是 {@code NONE.acquirableJobSite()} = 共享标签，
     * 少了它本模组的工作站永远不会被发现，也就永不转职（{@code AssignProfessionFromJobSite} 再按各职业
     * {@code heldJobSite} 决定转成哪个职业）。⇒ 分工：共享标签 = 能否被发现并转职；本专用标签 = 转职后只认本站点。
     */
    public static final TagKey<PoiType> CARD_TRADER_JOB_SITES =
            TagKey.create(Registries.POINT_OF_INTEREST_TYPE,
                    ResourceLocation.fromNamespaceAndPath(AstralDiceMod.MODID, "card_trader_job_site"));

    /** 「筹码商人」的专用可认领工作站点标签（{@code #astral_dice:chip_trader_job_site}，内容 = {@code astral_dice:chip_shop}）；与 {@link #CARD_TRADER_JOB_SITES} 同一口径。 */
    public static final TagKey<PoiType> CHIP_TRADER_JOB_SITES =
            TagKey.create(Registries.POINT_OF_INTEREST_TYPE,
                    ResourceLocation.fromNamespaceAndPath(AstralDiceMod.MODID, "chip_trader_job_site"));

    private ModPoiTypes() {
    }
}
