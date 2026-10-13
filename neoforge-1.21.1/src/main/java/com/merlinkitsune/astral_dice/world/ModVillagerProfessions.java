package com.merlinkitsune.astral_dice.world;

import com.google.common.collect.ImmutableSet;
import com.merlinkitsune.astral_dice.AstralDiceMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.npc.VillagerProfession;
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
 * <p>交易表由 {@code trade/CardTraderTrades} 在 {@code VillagerTradesEvent} 里填充。
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
                    "card_trader",
                    // heldJobSite：本站点就是卡牌商店
                    holder -> holder.is(ModPoiTypes.CARD_SHOP_KEY),
                    // acquirableJobSite：必须用原版标签口径（见 ModPoiTypes 的说明），否则村民不会认领
                    VillagerProfession.ALL_ACQUIRABLE_JOBS,
                    ImmutableSet.of(),
                    ImmutableSet.of(),
                    null));

    /** 筹码商人：工作站点 = 筹码商店，交易表见 {@code trade/ChipTraderTrades}。 */
    public static final DeferredHolder<VillagerProfession, VillagerProfession> CHIP_TRADER =
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
