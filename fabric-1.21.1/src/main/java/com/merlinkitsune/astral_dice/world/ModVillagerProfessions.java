package com.merlinkitsune.astral_dice.world;

import com.google.common.collect.ImmutableSet;
import com.merlinkitsune.astral_dice.AstralDiceMod;
import com.merlinkitsune.astral_dice.platform.registry.DeferredRegister;
import com.merlinkitsune.astral_dice.platform.registry.RegistryObject;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.npc.VillagerProfession;

/**
 * 村民职业注册：新职业「卡牌交易员」（{@code astral_dice:card_trader}）与「筹码商人」（{@code astral_dice:chip_trader}）。
 *
 * <p>职业显示名（也就是交易界面标题）的 lang 键由原版 {@code Villager#getTypeName()} 拼接而成：
 * {@code entity.minecraft.villager.<命名空间>.<路径>} ⇒
 * {@code entity.minecraft.villager.astral_dice.card_trader} / {@code ...chip_trader}。
 *
 * <p>工作站点贴图：{@code astral_dice:textures/entity/villager/profession/card_trader.png} /
 * {@code ...chip_trader.png}（路径由 {@code VillagerProfessionLayer#getResourceLocation} 按职业注册 id 拼接，
 * 等级徽章仍用原版贴图）。
 *
 * <p>交易表由 {@code trade/CardTraderTrades#register()} 与 {@code trade/ChipTraderTrades#register()} 填充。
 *
 * <h2>Fabric 1.21.1 平台面</h2>
 *
 * <p>职业的构造是**纯原版 API**：{@code VillagerProfession(String, Predicate<Holder<PoiType>>,
 * Predicate<Holder<PoiType>>, ImmutableSet<Item>, ImmutableSet<Block>, SoundEvent)}（javap 确认 6 参 record
 * 构造器是 {@code public}）⇒ 与 Forge / NeoForge 两线逐字一致，无需 AW / mixin。
 * 注册走本仓自建的 {@link DeferredRegister} shim（提交即 {@code Registry.register}），
 * 与 {@code ModItems} 等 8 个注册类同一形态。
 *
 * <p>⚠️ 时序：{@code RegistryObject#get()} 在本 {@code DeferredRegister} 提交前会抛异常 ⇒
 * 交易表注册（{@code trade/*Trades#register()}）必须在本类的 {@link #bootstrap()} 之后调用。
 */
public final class ModVillagerProfessions {

    public static final DeferredRegister<VillagerProfession> PROFESSIONS =
            DeferredRegister.create(Registries.VILLAGER_PROFESSION, AstralDiceMod.MODID);

    public static final RegistryObject<VillagerProfession> CARD_TRADER =
            PROFESSIONS.register("card_trader", () -> new VillagerProfession(
                    "card_trader",
                    // heldJobSite：本站点就是卡牌商店
                    holder -> holder.is(ModPoiTypes.CARD_SHOP_KEY),
                    // acquirableJobSite：**专用**标签（只含 card_shop）—— 共用原版共享标签会让本职业去认领
                    // 别人家的工作站，且 JEI（Just Enough Professions）按该谓词枚举站点，会把两个职业画成同一个方块。
                    holder -> holder.is(ModPoiTypes.CARD_TRADER_JOB_SITES),
                    ImmutableSet.of(),
                    ImmutableSet.of(),
                    null));

    /** 筹码商人：工作站点 = 筹码商店，交易表见 {@code trade/ChipTraderTrades}。 */
    public static final RegistryObject<VillagerProfession> CHIP_TRADER =
            PROFESSIONS.register("chip_trader", () -> new VillagerProfession(
                    "chip_trader",
                    // heldJobSite：本站点就是筹码商店
                    holder -> holder.is(ModPoiTypes.CHIP_SHOP_KEY),
                    // acquirableJobSite：**专用**标签（只含 chip_shop），口径同 CARD_TRADER
                    holder -> holder.is(ModPoiTypes.CHIP_TRADER_JOB_SITES),
                    ImmutableSet.of(),
                    ImmutableSet.of(),
                    null));

    private ModVillagerProfessions() {
    }

    /**
     * 入口类调用的单点接线：提交两个职业的注册。
     *
     * <p>提交是幂等的（{@code DeferredRegister#commit()} 自带 {@code committed} 守卫），
     * 因此入口类也可以按既有写法在 {@code commitRegistrations()} 里
     * {@code ModVillagerProfessions.PROFESSIONS.register(BUS)}，两者不会重复注册。
     */
    public static void bootstrap() {
        PROFESSIONS.commit();
    }
}
