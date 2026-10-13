package com.merlinkitsune.astral_dice.world;

import com.google.common.collect.ImmutableSet;
import com.merlinkitsune.astral_dice.AstralDiceMod;
import com.merlinkitsune.astral_dice.block.ModBlocks;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

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
 * <p>平台差异：
 * <ul>
 *   <li>注册表句柄：1.20.1 用 {@code ForgeRegistries.POI_TYPES}（{@code IForgeRegistry}，与本线
 *       {@code block/ModBlocks} 同款写法）；1.21.1 用 {@code Registries.POINT_OF_INTEREST_TYPE}
 *       （{@code ResourceKey}）。两者指向同一张注册表
 *       （ForgeRegistries.java:88 {@code POI_TYPES} / Keys:170 {@code key("point_of_interest_type")}）。</li>
 *   <li>返回值类型：1.20.1 只有 {@code RegistryObject<PoiType>}（无 {@code DeferredHolder}）。</li>
 *   <li><b>状态 → POI 的映射无需手工登记</b>：Forge 1.20.1 把 {@code PoiTypes.TYPE_BY_STATE} 换成了
 *       {@code GameData.getBlockStatePointOfInterestTypeMap()}（PoiTypes.java:51-53），该 slave map 由
 *       POI_TYPES 注册表的 onAdd 回调按各 PoiType 的 matchingStates 自动填充
 *       （GameData.java:509-525），因此 DeferredRegister 登记即生效，不必调
 *       {@code PoiTypes.bootstrap}/{@code registerBlockStates}（后者在 1.20.1 已是空方法，
 *       PoiTypes.java:70-71）。</li>
 * </ul>
 */
public final class ModPoiTypes {

    public static final DeferredRegister<PoiType> POI_TYPES =
            DeferredRegister.create(ForgeRegistries.POI_TYPES, AstralDiceMod.MODID);

    /** 工作站点 id（职业的 heldJobSite 谓词按它判定）—— 必须与标签文件里的 id 一致。 */
    public static final ResourceKey<PoiType> CARD_SHOP_KEY =
            ResourceKey.create(Registries.POINT_OF_INTEREST_TYPE,
                    ResourceLocation.fromNamespaceAndPath(AstralDiceMod.MODID, "card_shop"));

    /** 卡牌商店工作站点（1 张票券 / 1 格搜索半径，与原版工作站点同一取值）。 */
    public static final RegistryObject<PoiType> CARD_SHOP = POI_TYPES.register("card_shop",
            () -> new PoiType(ImmutableSet.copyOf(ModBlocks.CARD_SHOP.get().getStateDefinition().getPossibleStates()),
                    1, 1));

    /** 工作站点 id（职业的 heldJobSite 谓词按它判定）—— 必须与标签文件里的 id 一致。 */
    public static final ResourceKey<PoiType> CHIP_SHOP_KEY =
            ResourceKey.create(Registries.POINT_OF_INTEREST_TYPE,
                    ResourceLocation.fromNamespaceAndPath(AstralDiceMod.MODID, "chip_shop"));

    /** 筹码商店工作站点（1 张票券 / 1 格搜索半径，与卡牌商店同一取值）。 */
    public static final RegistryObject<PoiType> CHIP_SHOP = POI_TYPES.register("chip_shop",
            () -> new PoiType(ImmutableSet.copyOf(ModBlocks.CHIP_SHOP.get().getStateDefinition().getPossibleStates()),
                    1, 1));

    private ModPoiTypes() {
    }
}
