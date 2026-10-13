package com.merlinkitsune.astral_dice.world;

import com.google.common.collect.ImmutableSet;
import com.merlinkitsune.astral_dice.AstralDiceMod;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * 村民职业注册：新职业「卡牌交易员」（{@code astral_dice:card_trader}）。
 *
 * <p>职业显示名（也就是交易界面标题）的 lang 键由原版 {@code Villager#getTypeName()} 拼接而成：
 * {@code entity.minecraft.villager.<命名空间>.<路径>} ⇒ 本职业为
 * {@code entity.minecraft.villager.astral_dice.card_trader}。
 *
 * <p>1.20.1 的拼法已取证为同款（Villager.java:686-688：非 minecraft 命名空间拼
 * {@code getDescriptionId() + '.' + 命名空间 + '.' + 路径}），故 lang 键两线完全一致。
 *
 * <p>工作站点贴图：{@code astral_dice:textures/entity/villager/profession/card_trader.png}
 * （路径由 {@code VillagerProfessionLayer#getResourceLocation} 按职业注册 id 拼接，等级徽章仍用原版贴图）。
 *
 * <p>交易表由 {@code trade/CardTraderTrades} 在 {@code VillagerTradesEvent} 里填充。
 *
 * <p>筹码商人（{@code astral_dice:chip_trader}）同属本职业体系：工作站点 = 筹码商店
 * （{@code astral_dice:chip_shop}），交易表由 {@code trade/ChipTraderTrades} 填充；
 * 职业贴图 {@code astral_dice:textures/entity/villager/profession/chip_trader.png} 同样由
 * {@code VillagerProfessionLayer#getResourceLocation} 按职业注册 id 拼接（等级徽章仍用原版贴图）。
 *
 * <p>平台差异：{@code VillagerProfession} 在 1.20.1 是**同签名的 6 参 record**
 * （VillagerProfession.java:25），故构造调用与 1.21.1 逐字一致；差别仅在注册表句柄
 * （1.20.1 用 {@code ForgeRegistries.VILLAGER_PROFESSIONS}）与返回值类型
 * （{@code RegistryObject} 取代 {@code DeferredHolder}）。
 */
public final class ModVillagerProfessions {

    public static final DeferredRegister<VillagerProfession> PROFESSIONS =
            DeferredRegister.create(ForgeRegistries.VILLAGER_PROFESSIONS, AstralDiceMod.MODID);

    public static final RegistryObject<VillagerProfession> CARD_TRADER =
            PROFESSIONS.register("card_trader", () -> new VillagerProfession(
                    "card_trader",
                    // heldJobSite：本站点就是卡牌商店
                    holder -> holder.is(ModPoiTypes.CARD_SHOP_KEY),
                    // acquirableJobSite：必须用原版标签口径（见 ModPoiTypes 的说明），否则村民不会认领
                    VillagerProfession.ALL_ACQUIRABLE_JOBS,
                    ImmutableSet.of(),
                    ImmutableSet.of(),
                    null));

    /** 筹码商人：工作站点 = 筹码商店，交易表见 {@code trade/ChipTraderTrades}。 */
    public static final RegistryObject<VillagerProfession> CHIP_TRADER =
            PROFESSIONS.register("chip_trader", () -> new VillagerProfession(
                    "chip_trader",
                    // heldJobSite：本站点就是筹码商店
                    holder -> holder.is(ModPoiTypes.CHIP_SHOP_KEY),
                    // acquirableJobSite：必须用原版标签口径（见 ModPoiTypes 的说明），否则村民不会认领
                    VillagerProfession.ALL_ACQUIRABLE_JOBS,
                    ImmutableSet.of(),
                    ImmutableSet.of(),
                    null));

    private ModVillagerProfessions() {
    }
}
