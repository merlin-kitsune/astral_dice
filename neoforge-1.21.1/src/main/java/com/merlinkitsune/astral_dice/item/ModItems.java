package com.merlinkitsune.astral_dice.item;

import com.merlinkitsune.astral_dice.AstralDiceMod;
import com.merlinkitsune.astral_dice.component.AppliedStone;
import com.merlinkitsune.astral_dice.component.ModDataComponents;
import com.merlinkitsune.starenginelib.item.AstralRarities;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;
import com.merlinkitsune.astral_dice.item.chip.StarCoinHammerChipItem;
import com.merlinkitsune.astral_dice.item.chip.ShootingStarChipItem;
import com.merlinkitsune.astral_dice.item.chip.EagleScopeChipItem;
import com.merlinkitsune.astral_dice.item.chip.MagicTomeChipItem;
import com.merlinkitsune.astral_dice.item.chip.BufferShieldChipItem;
import com.merlinkitsune.astral_dice.item.chip.EightSidedDiceChipItem;
import com.merlinkitsune.astral_dice.item.card.UnwaveringCardItem;
import com.merlinkitsune.astral_dice.item.card.FightPoisonWithPoisonCardItem;
import com.merlinkitsune.astral_dice.item.dice.DiceCurioItem;
import com.merlinkitsune.astral_dice.item.sign.FannySignItem;
import com.merlinkitsune.astral_dice.item.card.DirectionalBlastCardItem;
import com.merlinkitsune.astral_dice.item.card.BerserkCardItem;
import com.merlinkitsune.astral_dice.item.chip.MarkerSprayerChipItem;
import com.merlinkitsune.astral_dice.item.chip.CutterBladeChipItem;
import com.merlinkitsune.astral_dice.item.chip.FanBigChipItem;
import com.merlinkitsune.astral_dice.item.chip.FanSmallChipItem;
import com.merlinkitsune.astral_dice.item.card.LivingPageItem;
import com.merlinkitsune.astral_dice.item.sign.LuluSignItem;
import com.merlinkitsune.astral_dice.item.chip.MedkitCompleteChipItem;
import com.merlinkitsune.astral_dice.item.sign.FenSignItem;
import com.merlinkitsune.astral_dice.item.chip.MagicQuiverChipItem;
import com.merlinkitsune.astral_dice.item.card.ChocolateCakeCardItem;
import com.merlinkitsune.astral_dice.item.chip.BigBackpackChipItem;
import com.merlinkitsune.astral_dice.item.sign.HaiqingSignItem;
import com.merlinkitsune.astral_dice.item.sign.NancyLuSignItem;
import com.merlinkitsune.astral_dice.item.sign.MosesSignItem;
import com.merlinkitsune.astral_dice.item.sign.PandamanSignItem;
import com.merlinkitsune.astral_dice.item.card.ExpressDeliveryCardItem;
import com.merlinkitsune.astral_dice.item.sign.PaparaSignItem;
import com.merlinkitsune.astral_dice.item.card.LuxuryFeastCardItem;
import com.merlinkitsune.astral_dice.item.chip.AtmChipItem;
import com.merlinkitsune.astral_dice.item.chip.BankCardChipItem;
import com.merlinkitsune.astral_dice.item.sign.RinSignItem;
import com.merlinkitsune.astral_dice.item.sign.RenSignItem;
import com.merlinkitsune.astral_dice.item.sign.ZhaoSignItem;
import com.merlinkitsune.astral_dice.item.sign.TeruSignItem;
import com.merlinkitsune.astral_dice.item.sign.NardisSignItem;
import com.merlinkitsune.astral_dice.item.sign.MamushiSignItem;
import com.merlinkitsune.astral_dice.item.card.FuCardItem;
import com.merlinkitsune.astral_dice.item.card.HuoCardItem;
import com.merlinkitsune.astral_dice.item.dice.DiceTierRegistry;
import com.merlinkitsune.astral_dice.item.card.HamburgerCardItem;
import com.merlinkitsune.astral_dice.item.chip.TargetChipItem;
import com.merlinkitsune.astral_dice.item.chip.ScopeChipItem;
import com.merlinkitsune.astral_dice.item.chip.SandwichChipItem;
import com.merlinkitsune.astral_dice.item.chip.AdrenalineChipItem;
import com.merlinkitsune.astral_dice.item.sign.JasmineSignItem;
import com.merlinkitsune.astral_dice.item.chip.SpeedSkatesChipItem;
import com.merlinkitsune.astral_dice.item.sign.KomachiSignItem;
import com.merlinkitsune.astral_dice.item.sign.MimiSignItem;
import com.merlinkitsune.astral_dice.item.card.MonsterBrickCardItem;
import com.merlinkitsune.astral_dice.item.dice.DiceTier;
import com.merlinkitsune.astral_dice.item.card.YouHaveIHaveCardItem;
import com.merlinkitsune.astral_dice.item.card.OrbitalStrikeCardItem;
import com.merlinkitsune.astral_dice.item.card.EffectCardItem;
import com.merlinkitsune.astral_dice.item.card.CardItem;
import com.merlinkitsune.astral_dice.item.chip.MotoHelmetChipItem;
import com.merlinkitsune.astral_dice.item.chip.BoxingGlovesChipItem;
import com.merlinkitsune.astral_dice.item.sign.BonnieSignItem;
import com.merlinkitsune.astral_dice.item.sign.ParunanSignItem;
import com.merlinkitsune.astral_dice.item.sign.MisakiSignItem;
import com.merlinkitsune.astral_dice.item.card.MonsterLaserCardItem;
import com.merlinkitsune.astral_dice.item.chip.BankCardUnlimitedChipItem;
import com.merlinkitsune.astral_dice.item.card.FateGuidanceCardItem;
import com.merlinkitsune.astral_dice.item.chip.MedkitEmergencyChipItem;
import com.merlinkitsune.astral_dice.item.chip.VitaminPillChipItem;
import com.merlinkitsune.astral_dice.item.sign.PadmanSignItem;
import com.merlinkitsune.astral_dice.item.chip.CutterChipItem;
import com.merlinkitsune.astral_dice.item.chip.CursedSwordChipItem;
import com.merlinkitsune.astral_dice.item.chip.RevengeHalberdChipItem;
import com.merlinkitsune.astral_dice.item.chip.PiercingGunChipItem;
import com.merlinkitsune.astral_dice.item.chip.CandyChipItem;
import com.merlinkitsune.astral_dice.item.chip.FriendshipBadgeChipItem;
import com.merlinkitsune.astral_dice.item.chip.SatelliteChipItem;
import com.merlinkitsune.astral_dice.item.chip.WarpEngineChipItem;
import com.merlinkitsune.astral_dice.item.chip.EnergyRecyclerChipItem;
import com.merlinkitsune.astral_dice.item.chip.ElectricSwordChipItem;
import com.merlinkitsune.astral_dice.item.chip.PerpetualMotionChipItem;
import com.merlinkitsune.astral_dice.item.chip.CurrentCoreChipItem;
import com.merlinkitsune.astral_dice.item.chip.AdvancedPeripheralsChipItem;
import com.merlinkitsune.astral_dice.item.chip.BigBowlStewChipItem;
import com.merlinkitsune.astral_dice.item.chip.MemberRecommendationChipItem;
import com.merlinkitsune.astral_dice.item.chip.BookmarkChipItem;
import com.merlinkitsune.astral_dice.item.chip.PiggyBankChipItem;
import com.merlinkitsune.astral_dice.item.chip.SmartWatchChipItem;
import com.merlinkitsune.astral_dice.item.chip.ElectricGloveChipItem;
import com.merlinkitsune.astral_dice.item.chip.AirbagChipItem;
import com.merlinkitsune.astral_dice.item.chip.RailgunChipItem;
import com.merlinkitsune.astral_dice.item.chip.PrimordialCoreChipItem;
import com.merlinkitsune.astral_dice.item.chip.WhetstoneChipItem;
import com.merlinkitsune.astral_dice.item.chip.NinjaStarChipItem;
import com.merlinkitsune.astral_dice.item.chip.FlashlightChipItem;

public class ModItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(AstralDiceMod.MODID);

    private static final net.minecraft.tags.TagKey<Item> COMBAT_CARDS_TAG =
            net.minecraft.tags.ItemTags.create(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(
                    AstralDiceMod.MODID, "combat_cards"));
    private static final net.minecraft.tags.TagKey<Item> EFFECT_CARDS_TAG =
            net.minecraft.tags.ItemTags.create(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(
                    AstralDiceMod.MODID, "effect_cards"));

    // ═══════════════════════════════════════════════════════════════════════════
    // 本模组稀有度标准(2026-09-25 起改为**自有 5 档**;等级/常量名/颜色码的权威在前置库 starengine_lib):
    //   白 = 普通   → 原版 Rarity.COMMON(本模组**唯一**保留的原版档,直接写 Rarity.COMMON)
    //   水蓝 #55FFFF = 稀有 → AstralRarities.rare()      扩展常量 ASTRAL_DICE_RARE
    //   粉紫 #FF55FF = 史诗 → AstralRarities.epic()      扩展常量 ASTRAL_DICE_EPIC
    //   金   #FFC24B = 传奇 → AstralRarities.legendary() 扩展常量 ASTRAL_DICE_LEGENDARY
    //   亮红 #FF4D4D = 巅峰 → AstralRarities.pinnacle()  扩展常量 ASTRAL_DICE_PINNACLE
    //   彩虹(流动)   = 奇特 → AstralRarities.bizarre()   扩展常量 ASTRAL_DICE_BIZARRE
    //                 ⚠️ 奇特文字色 = 亮红(与巅峰同色 #FF4D4D);流动彩虹只在边框上(见下一行);
    //                    边框策略(2026-09-25 二次裁决):稀有/史诗随原版不干预;传奇/巅峰=单色、奇特=两色
    //                    流动渐变,由客户端 client/RarityTooltipFrame 经 RenderTooltipEvent.Color 写入(无 Mixin)。
    // 机制:库里的 item.Rarity 是**唯一色码权威**,其平台接线把这 5 档**扩展进原版 Rarity**
    //   (NeoForge 两线 = 本 mod 的 META-INF/enumextensions.json + 库 AstralRarities 的 EnumProxy 字段;
    //    Forge 1.20.1 = 库 AstralRarities 静态初始化里的 Rarity.create + IExtensibleEnum)
    //   ⇒ 原版 tooltip 链路(ItemStack#getTooltipLines → Rarity#getStyleModifier)会自动套用该颜色,
    //     本模组**不写任何 tooltip 染色代码**;改色 = 改库里 Rarity 的那一个常量。
    //   ⚠️ 附魔**不再**改变档位:原版「附魔升一档」的 switch 只覆盖原版 4 档,自有档走 default 原样返回。
    // Bountiful 赏金联动数据层(data/bountiful/bounty_pools/bountiful/astral_*):
    //   稀有 → "rarity": "RARE"、史诗 → "EPIC"、传奇 → "LEGENDARY"、普通 → "COMMON"(一一对应);
    //   传奇档的筹码与立牌不进入奖励池 astral_rews。
    //   ⚠️ 巅峰档**没有**数据层对应值 ⇒ 巅峰物品不得写入任何赏金池(守门脚本会报错,属预期的 fail-loud)。
    // 骰子品质按升级链配色:基础=普通(白)、黄金=稀有(浅蓝)、钻石=史诗(粉紫)、合金=传奇(金)、
    //   下界之星骰子=巅峰(亮红,T4 奇异品阶);合金与下界之星骰子均不参与赏金板。
    // 新增物品时按此标准选择 rarity,并保持与图标边框颜色一致;若参与赏金,同步维护 astral_objs/astral_rews。
    // ═══════════════════════════════════════════════════════════════════════════

    public static final DeferredItem<Item> DICE = registerItem("dice",
            () -> new DiceCurioItem(new Item.Properties()
                    .stacksTo(1)
                    .rarity(Rarity.COMMON)));

    // 黄金骰子:由基础骰子 + 4 星币 + 4 金锭升级而来,卡牌放置栏固定攻防各 4(共 8)
    public static final DeferredItem<Item> GOLDEN_DICE = registerItem("golden_dice",
            () -> new DiceCurioItem(new Item.Properties()
                    .stacksTo(1)
                    .rarity(AstralRarities.rare())));

    // 玻璃骰子:与黄金骰子同阶(tag dice_t1),可由黄金骰子升级;战斗牌点数始终取最大值,但死亡会丢失该骰子及已装备卡牌
    public static final DeferredItem<Item> GLASS_DICE = registerItem("glass_dice",
            () -> new DiceCurioItem(new Item.Properties()
                    .stacksTo(1)
                    .rarity(AstralRarities.rare())));

    // 钻石骰子:由黄金骰子 + 4 星盘 + 4 钻石升级而来,卡牌放置栏固定攻防各 5(共 10)
    public static final DeferredItem<Item> DIAMOND_DICE = registerItem("diamond_dice",
            () -> new DiceCurioItem(new Item.Properties()
                    .stacksTo(1)
                    .rarity(AstralRarities.epic())));

    // 合金骰子:由钻石骰子 + 4 黄金星盘 + 4 下界合金锭升级而来,卡牌放置栏为攻防各 6 个(共 12)
    public static final DeferredItem<Item> NETHERITE_DICE = registerItem("netherite_dice",
            () -> new DiceCurioItem(new Item.Properties()
                    .stacksTo(1)
                    .rarity(AstralRarities.legendary())));

    // 绿宝石骰子:与钻石骰子同阶(tag dice_t2),可由钻石骰子升级;佩戴后村民交易绿宝石费用改为星币并享 20% 折扣
    public static final DeferredItem<Item> EMERALD_DICE = registerItem("emerald_dice",
            () -> new DiceCurioItem(new Item.Properties()
                    .stacksTo(1)
                    .rarity(AstralRarities.epic())));

    // 黑曜石骰子:与钻石骰子同阶(tag dice_t2),可由钻石骰子升级;基础防御力 +3(折算 +6 护甲),火焰伤害 -70%
    public static final DeferredItem<Item> OBSIDIAN_DICE = registerItem("obsidian_dice",
            () -> new com.merlinkitsune.astral_dice.item.dice.ObsidianDiceItem(new Item.Properties()
                    .stacksTo(1)
                    .rarity(AstralRarities.epic())));

    // 下界岩骰子:与黄金骰子同阶(tag dice_t1),可由黄金骰子升级;下界挖矿概率掉星币/星盘,猪灵保持中立
    public static final DeferredItem<Item> NETHERRACK_DICE = registerItem("netherrack_dice",
            () -> new DiceCurioItem(new Item.Properties()
                    .stacksTo(1)
                    .rarity(AstralRarities.rare())));

    // 诡异骰子:与钻石骰子同阶(tag dice_t2),可由钻石骰子升级;立牌主动冷却 -50%,但战斗骰低点数(1-3)概率提升 50%
    public static final DeferredItem<Item> WEIRD_DICE = registerItem("weird_dice",
            () -> new DiceCurioItem(new Item.Properties()
                    .stacksTo(1)
                    .rarity(AstralRarities.epic())));

    // 绯红骰子:与下界合金骰子同阶(tag dice_t3),可由下界合金骰子升级;战斗骰高点数(4-6)概率提升 50%,但骰出 1 时立即受到 6 点伤害
    public static final DeferredItem<Item> CRIMSON_DICE = registerItem("crimson_dice",
            () -> new DiceCurioItem(new Item.Properties()
                    .stacksTo(1)
                    .rarity(AstralRarities.legendary())));

    // 紫晶骰子:与钻石骰子同阶(tag dice_t2),可由钻石骰子升级;远程/魔法攻击也触发战斗骰并追加骰点伤害(不触发骰神赐福)
    public static final DeferredItem<Item> AMETHYST_DICE = registerItem("amethyst_dice",
            () -> new DiceCurioItem(new Item.Properties()
                    .stacksTo(1)
                    .rarity(AstralRarities.epic())));

    // 末影骰子:与下界合金骰子同阶(tag dice_t3),可由下界合金骰子升级;致命伤害触发不死图腾效果(冷却 5:00),但雨中/水下受到的伤害 +40%
    public static final DeferredItem<Item> ENDER_DICE = registerItem("ender_dice",
            () -> new DiceCurioItem(new Item.Properties()
                    .stacksTo(1)
                    .rarity(AstralRarities.legendary())));

    // 下界之星骰子:T4 奇异品阶(tag dice_t4),可由任意 T3 骰子升级;卡牌槽/费用恒为最高档、筹码栏 +1,星级附加攻防
    public static final DeferredItem<Item> NETHER_STAR_DICE = registerItem("nether_star_dice",
            () -> new com.merlinkitsune.astral_dice.item.dice.NetherStarDiceItem(new Item.Properties()
                    .stacksTo(1)
                    .rarity(AstralRarities.pinnacle())));

    // === 骰子阶层注册表(槽位规则集中管理:新增骰子在此注册即可,无需修改 DiceCurioItem) ===
    // 立牌栏:固定 1(stand.json size=1,所有骰子一致);筹码栏:必须佩戴骰子才有(chip.json size=0)
    // 重要:item 参数必须传 Supplier 延迟解析(() -> X.get()),禁止在静态初始化阶段调用
    //      DeferredHolder.get()——注册表未加载完成时会抛 IllegalStateException。
    static {
        // 卡牌栏格数不再按品阶区分,仅由星级决定(见 DiceCurioItem.getCardSlots)
        // 基础骰子:筹码栏 0★0/1★1/2★2/3★3
        DiceTierRegistry.register(new DiceTier("dice", () -> DICE.get(),
                s -> s));
        // 金骰子:筹码栏 0★1/1★2/2★3/3★4
        DiceTierRegistry.register(new DiceTier("golden_dice", () -> GOLDEN_DICE.get(),
                s -> 1 + s));
        // 玻璃骰子:与金骰子同阶(筹码栏 0★1/1★2/2★3/3★4)
        DiceTierRegistry.register(new DiceTier("glass_dice", () -> GLASS_DICE.get(),
                s -> 1 + s));
        // 钻石骰子:筹码栏 0★2/1★3/2★4/3★5
        DiceTierRegistry.register(new DiceTier("diamond_dice", () -> DIAMOND_DICE.get(),
                s -> 2 + s));
        // 合金骰子:筹码栏 0★3/1★4/2★5/3★6
        DiceTierRegistry.register(new DiceTier("netherite_dice", () -> NETHERITE_DICE.get(),
                s -> 3 + s));
        // 绿宝石骰子:与钻石骰子同阶(筹码栏 0★2/1★3/2★4/3★5)
        DiceTierRegistry.register(new DiceTier("emerald_dice", () -> EMERALD_DICE.get(),
                s -> 2 + s));
        // 黑曜石骰子:与钻石骰子同阶(筹码栏 0★2/1★3/2★4/3★5)
        DiceTierRegistry.register(new DiceTier("obsidian_dice", () -> OBSIDIAN_DICE.get(),
                s -> 2 + s));
        // 下界岩骰子:与金骰子同阶(筹码栏 0★1/1★2/2★3/3★4)
        DiceTierRegistry.register(new DiceTier("netherrack_dice", () -> NETHERRACK_DICE.get(),
                s -> 1 + s));
        // 诡异骰子:与钻石骰子同阶(筹码栏 0★2/1★3/2★4/3★5)
        DiceTierRegistry.register(new DiceTier("weird_dice", () -> WEIRD_DICE.get(),
                s -> 2 + s));
        // 绯红骰子:与下界合金骰子同阶(筹码栏 0★3/1★4/2★5/3★6)
        DiceTierRegistry.register(new DiceTier("crimson_dice", () -> CRIMSON_DICE.get(),
                s -> 3 + s));
        // 紫晶骰子:与钻石骰子同阶(筹码栏 0★2/1★3/2★4/3★5)
        DiceTierRegistry.register(new DiceTier("amethyst_dice", () -> AMETHYST_DICE.get(),
                s -> 2 + s));
        // 末影骰子:与下界合金骰子同阶(筹码栏 0★3/1★4/2★5/3★6)
        DiceTierRegistry.register(new DiceTier("ender_dice", () -> ENDER_DICE.get(),
                s -> 3 + s));
        // 下界之星骰子:T4 奇异品阶(筹码栏 0★4/1★5/2★6/3★7,比 T3 多 1 格)
        DiceTierRegistry.register(new DiceTier("nether_star_dice", () -> NETHER_STAR_DICE.get(),
                s -> 4 + s));
    }

    public static final DeferredItem<Item> ATTACK_CARD_MEDIUM = registerItem("attack_card_medium",
            () -> new CardItem(new Item.Properties()
                    .stacksTo(64)
                    .component(ModDataComponents.CARD_USES.get(), AppliedStone.defaultUses("medium")), "medium"));

    public static final DeferredItem<Item> ATTACK_CARD_LARGE = registerItem("attack_card_large",
            () -> new CardItem(new Item.Properties()
                    .stacksTo(64)
                    .rarity(AstralRarities.rare())
                    .component(ModDataComponents.CARD_USES.get(), AppliedStone.defaultUses("large")), "large"));

    public static final DeferredItem<Item> ATTACK_CARD_EPIC = registerItem("attack_card_epic",
            () -> new CardItem(new Item.Properties()
                    .stacksTo(64)
                    .rarity(AstralRarities.epic())
                    .component(ModDataComponents.CARD_USES.get(), AppliedStone.defaultUses("epic")), "epic"));

    public static final DeferredItem<Item> ATTACK_CARD_SHADOW_STRIKE = registerItem("attack_card_shadow_strike",
            () -> new CardItem(new Item.Properties()
                    .stacksTo(64)
                    .rarity(AstralRarities.rare())
                    .component(ModDataComponents.CARD_USES.get(), AppliedStone.defaultUses("shadow_strike")), "shadow_strike"));

    public static final DeferredItem<Item> ATTACK_CARD_MEITO = registerItem("attack_card_meito",
            () -> new CardItem(new Item.Properties()
                    .stacksTo(64)
                    .rarity(AstralRarities.epic())
                    .component(ModDataComponents.CARD_USES.get(), AppliedStone.defaultUses("meito")), "meito"));

    public static final DeferredItem<Item> ATTACK_CARD_CHARGE = registerItem("attack_card_charge",
            () -> new CardItem(new Item.Properties()
                    .stacksTo(64)
                    .rarity(AstralRarities.legendary())
                    .component(ModDataComponents.CARD_USES.get(), AppliedStone.defaultUses("charge")), "charge"));

    public static final DeferredItem<Item> ATTACK_CARD_FULL_POWER = registerItem("attack_card_full_power",
            () -> new CardItem(new Item.Properties()
                    .stacksTo(64)
                    .rarity(AstralRarities.legendary())
                    .component(ModDataComponents.CARD_USES.get(), AppliedStone.defaultUses("full_power")), "full_power"));

    // 撕咬(蛟龙立牌 mamushi 专属战斗牌):费用 2 / 耐久 1 / 定值攻击 +3(对齐暗影突袭)。
    // 专属绑定:获得者由 ExclusiveCardUtil 绑定(owner_uuid),非获得者无法放入骰子卡牌栏;
    // 装备且触发骰神赐福时每张 +1 层觉醒并锁存撕咬加成(见 MamushiSignItem)。
    // 无配方、不进任何随机池/赏金池。
    public static final DeferredItem<Item> ATTACK_CARD_BITE = registerItem("attack_card_bite",
            () -> new CardItem(new Item.Properties()
                    .stacksTo(64)
                    .rarity(AstralRarities.bizarre())
                    .component(ModDataComponents.CARD_USES.get(), AppliedStone.defaultUses("bite")), "bite"));

    // 龙之咆哮(蛟龙立牌 mamushi 专属战斗牌):费用 3 / 耐久 5 / 定值攻击 +3。
    // 命中使目标 缓慢 III 1:00 并减 4 点防御 1:00(见 MamushiSignItem.applyRoarDebuff);
    // 只能由处于真龙形态的蛟龙立牌佩戴者获得(主动发放 / 撕咬转换),无配方、不进任何池。
    public static final DeferredItem<Item> ATTACK_CARD_DRAGON_ROAR = registerItem("attack_card_dragon_roar",
            () -> new CardItem(new Item.Properties()
                    .stacksTo(64)
                    .rarity(AstralRarities.bizarre())
                    .component(ModDataComponents.CARD_USES.get(), AppliedStone.defaultUses("dragon_roar")), "dragon_roar"));

    public static final DeferredItem<Item> DEFENSE_CARD_MEDIUM = registerItem("defense_card_medium",
            () -> new CardItem(new Item.Properties()
                    .stacksTo(64)
                    .component(ModDataComponents.CARD_USES.get(), AppliedStone.defaultUses("defense_medium")), "defense_medium"));

    public static final DeferredItem<Item> DEFENSE_CARD_LARGE = registerItem("defense_card_large",
            () -> new CardItem(new Item.Properties()
                    .stacksTo(64)
                    .rarity(AstralRarities.rare())
                    .component(ModDataComponents.CARD_USES.get(), AppliedStone.defaultUses("defense_large")), "defense_large"));

    public static final DeferredItem<Item> DEFENSE_CARD_EPIC = registerItem("defense_card_epic",
            () -> new CardItem(new Item.Properties()
                    .stacksTo(64)
                    .rarity(AstralRarities.epic())
                    .component(ModDataComponents.CARD_USES.get(), AppliedStone.defaultUses("defense_epic")), "defense_epic"));

    public static final DeferredItem<Item> EFFECT_CARD_KING_POWER = registerItem("effect_card_king_power",
            () -> new EffectCardItem(new Item.Properties()
                    .stacksTo(64)
                    .rarity(AstralRarities.legendary())));

    public static final DeferredItem<Item> EFFECT_CARD_BERSERK = registerItem("effect_card_berserk",
            () -> new BerserkCardItem(new Item.Properties()
                    .stacksTo(64)
                    .rarity(AstralRarities.epic())));

    public static final DeferredItem<Item> EFFECT_CARD_UNWAVERING = registerItem("effect_card_unwavering",
            () -> new UnwaveringCardItem(new Item.Properties()
                    .stacksTo(64)
                    .rarity(AstralRarities.epic())));

    // 以毒攻毒(效果牌):中毒 8 秒后移除负面效果并获得生命恢复 II 15 秒
    public static final DeferredItem<Item> EFFECT_CARD_FIGHT_POISON_WITH_POISON = registerItem("effect_card_fight_poison_with_poison",
            () -> new FightPoisonWithPoisonCardItem(new Item.Properties()
                    .stacksTo(64)
                    .rarity(AstralRarities.epic())));

    // 对怪激光(伤害效果牌):远程和魔法伤害 +4。品质:青(蓝)
    public static final DeferredItem<Item> MONSTER_LASER_CARD = registerItem("effect_card_monster_laser",
            () -> new MonsterLaserCardItem(new Item.Properties()
                    .stacksTo(64)
                    .rarity(AstralRarities.rare())));

    // 对怪板砖(伤害效果牌):远程和魔法伤害 +6
    public static final DeferredItem<Item> MONSTER_BRICK_CARD = registerItem("effect_card_monster_brick",
            () -> new MonsterBrickCardItem(new Item.Properties()
                    .stacksTo(64)
                    .rarity(AstralRarities.epic())));

    // 轨道炮(伤害效果牌):远程和魔法伤害 +8。品质:黄(金)
    public static final DeferredItem<Item> ORBITAL_STRIKE_CARD = registerItem("effect_card_orbital_strike",
            () -> new OrbitalStrikeCardItem(new Item.Properties()
                    .stacksTo(64)
                    .rarity(AstralRarities.legendary())));

    // 定向爆破(伤害效果牌):远程和魔法伤害 +5,并对目标周围 6 格敌对目标造成同样伤害。品质:黄(金)
    public static final DeferredItem<Item> DIRECTIONAL_BLAST_CARD = registerItem("effect_card_directional_blast",
            () -> new DirectionalBlastCardItem(new Item.Properties()
                    .stacksTo(64)
                    .rarity(AstralRarities.legendary())));

    public static final DeferredItem<Item> STAR_COIN = registerItem("star_coin",
            () -> new Item(new Item.Properties()
                    .stacksTo(64)
                    .rarity(AstralRarities.rare())));

    // 袋装星币:9 枚星币打包(可逆),便于批量携带
    public static final DeferredItem<Item> STAR_COIN_BAG = registerItem("star_coin_bag",
            () -> new Item(new Item.Properties()
                    .stacksTo(64)
                    .rarity(AstralRarities.rare())));

    public static final DeferredItem<Item> BLANK_SIGN = registerItem("blank_sign",
            () -> new Item(new Item.Properties()
                    .stacksTo(64)));

    public static final DeferredItem<Item> PARUNAN_SIGN = registerItem("parunan_sign",
            () -> new ParunanSignItem(new Item.Properties()
                    .stacksTo(1)
                    .rarity(AstralRarities.rare())));

    public static final DeferredItem<Item> JASMINE_SIGN = registerItem("jasmine_sign",
            () -> new JasmineSignItem(new Item.Properties()
                    .stacksTo(1)
                    .rarity(AstralRarities.rare())));

    public static final DeferredItem<Item> MISAKI_SIGN = registerItem("misaki_sign",
            () -> new MisakiSignItem(new Item.Properties()
                    .stacksTo(1)
                    .rarity(AstralRarities.legendary())));

    public static final DeferredItem<Item> MIMI_SIGN = registerItem("mimi_sign",
            () -> new MimiSignItem(new Item.Properties()
                    .stacksTo(1)
                    .rarity(AstralRarities.rare())));

    public static final DeferredItem<Item> LULU_SIGN = registerItem("lulu_sign",
            () -> new LuluSignItem(new Item.Properties()
                    .stacksTo(1)
                    .rarity(AstralRarities.rare())));

    public static final DeferredItem<Item> KOMACHI_SIGN = registerItem("komachi_sign",
            () -> new KomachiSignItem(new Item.Properties()
                    .stacksTo(1)
                    .rarity(AstralRarities.epic())));

    public static final DeferredItem<Item> FLASHLIGHT_CHIP = registerItem("flashlight_chip",
            () -> new FlashlightChipItem(new Item.Properties()
                    .stacksTo(1)
                    .rarity(AstralRarities.epic())));

    public static final DeferredItem<Item> CUTTER_CHIP = registerItem("cutter_chip",
            () -> new CutterChipItem(new Item.Properties()
                    .stacksTo(1)
                    .rarity(AstralRarities.epic())));

    // 美工刀-锋利:与美工刀-初级功能一致,基础攻击提高至 4 点;可与美工刀-初级同时装备
    public static final DeferredItem<Item> CUTTER_BLADE_CHIP = registerItem("cutter_blade_chip",
            () -> new CutterBladeChipItem(new Item.Properties()
                    .stacksTo(1)
                    .rarity(AstralRarities.legendary())));

    public static final DeferredItem<Item> BLANK_CHIP = registerItem("blank_chip",
            () -> new Item(new Item.Properties()
                    .stacksTo(64)));

    public static final DeferredItem<Item> SCOPE_CHIP = registerItem("scope_chip",
            () -> new ScopeChipItem(new Item.Properties()
                    .stacksTo(1)
                    .rarity(AstralRarities.epic())));

    public static final DeferredItem<Item> EAGLE_SCOPE_CHIP = registerItem("eagle_scope_chip",
            () -> new EagleScopeChipItem(new Item.Properties()
                    .stacksTo(1)
                    .rarity(AstralRarities.legendary())));

    public static final DeferredItem<Item> MEDKIT_EMERGENCY_CHIP = registerItem("medkit_emergency_chip",
            () -> new MedkitEmergencyChipItem(new Item.Properties()
                    .stacksTo(1)
                    .rarity(AstralRarities.rare())));

    public static final DeferredItem<Item> MEDKIT_COMPLETE_CHIP = registerItem("medkit_complete_chip",
            () -> new MedkitCompleteChipItem(new Item.Properties()
                    .stacksTo(1)
                    .rarity(AstralRarities.legendary())));

    // 维生素药丸:通过合成或奖励途径获得任意卡牌时,治愈 +1
    public static final DeferredItem<Item> VITAMIN_PILL_CHIP = registerItem("vitamin_pill_chip",
            () -> new VitaminPillChipItem(new Item.Properties()
                    .stacksTo(1)
                    .rarity(AstralRarities.epic())));

    public static final DeferredItem<Item> TARGET_CHIP = registerItem("target_chip",
            () -> new TargetChipItem(new Item.Properties()
                    .stacksTo(1)
                    .rarity(AstralRarities.rare())));

    // 标记喷罐:对目标造成远程或魔法伤害后,使目标获得一层"标记"
    public static final DeferredItem<Item> MARKER_SPRAYER_CHIP = registerItem("marker_sprayer_chip",
            () -> new MarkerSprayerChipItem(new Item.Properties()
                    .stacksTo(1)
                    .rarity(AstralRarities.rare())));

    // 魔法秘典:每使用 3 张效果牌,复制最后一张使用的效果牌并返回物品栏
    public static final DeferredItem<Item> MAGIC_TOME_CHIP = registerItem("magic_tome_chip",
            () -> new MagicTomeChipItem(new Item.Properties()
                    .stacksTo(1)
                    .rarity(AstralRarities.epic())));

    // 大背包:效果牌出牌数 +1(装备后生效)
    public static final DeferredItem<Item> BIG_BACKPACK_CHIP = registerItem("big_backpack_chip",
            () -> new BigBackpackChipItem(new Item.Properties()
                    .stacksTo(1)
                    .rarity(AstralRarities.epic())));

    // 忍术飞镖:效果牌出牌数+1;伤害效果牌生效期间远程/魔法伤害获得目标标记层数加成
    public static final DeferredItem<Item> NINJA_STAR_CHIP = registerItem("ninja_star_chip",
            () -> new NinjaStarChipItem(new Item.Properties()
                    .stacksTo(1)
                    .rarity(AstralRarities.legendary())));

    // 手持风扇-小:使用主动技能后对周围 16 格敌对目标施加标记
    public static final DeferredItem<Item> HAND_FAN_SMALL_CHIP = registerItem("hand_fan_small_chip",
            () -> new FanSmallChipItem(new Item.Properties()
                    .stacksTo(1)
                    .rarity(AstralRarities.rare())));

    // 手持风扇-大:使用主动技能后获得一张随机效果牌(不含专属),并对周围范围内敌对目标施加标记
    public static final DeferredItem<Item> HAND_FAN_BIG_CHIP = registerItem("hand_fan_big_chip",
            () -> new FanBigChipItem(new Item.Properties()
                    .stacksTo(1)
                    .rarity(AstralRarities.epic())));

    public static final DeferredItem<Item> STAR_PLATE = registerItem("star_plate",
            () -> new Item(new Item.Properties()
                    .stacksTo(64)
                    .rarity(AstralRarities.epic())));

    public static final DeferredItem<Item> GOLDEN_STAR_PLATE = registerItem("golden_star_plate",
            () -> new Item(new Item.Properties()
                    .stacksTo(64)
                    .rarity(AstralRarities.legendary())));

    // === 新材料(1.2.0):合成材料,本身不参与配方 ===
    // 再生试剂:再生相关的试剂(动态贴图,2 帧)
    public static final DeferredItem<Item> REGENERATION_REAGENT = registerItem("regeneration_reagent",
            () -> new Item(new Item.Properties()
                    .stacksTo(64)));

    // 导电线材:可导引电流的线材(动态贴图,10 帧)
    public static final DeferredItem<Item> CONDUCTIVE_WIRE = registerItem("conductive_wire",
            () -> new Item(new Item.Properties()
                    .stacksTo(64)));

    // 星币尘:星币研磨而成的粉末(动态贴图,7 帧)
    public static final DeferredItem<Item> STAR_COIN_DUST = registerItem("star_coin_dust",
            () -> new Item(new Item.Properties()
                    .stacksTo(64)));

    // 标记涂料:用于标记目标的涂料(静态贴图)
    public static final DeferredItem<Item> MARK_PAINT = registerItem("mark_paint",
            () -> new Item(new Item.Properties()
                    .stacksTo(64)));

    public static final DeferredItem<Item> EIGHT_SIDED_DICE = registerItem("eight_sided_dice_chip",
            () -> new EightSidedDiceChipItem(new Item.Properties()
                    .stacksTo(1)
                    .rarity(AstralRarities.rare())));

    // === 新筹码(ATM机/银行卡/拳击手套/速度轮滑/摩托头盔/夹心饼干/魔法箭袋/缓冲盾牌/星币锤) ===
    // ATM机:装备时星光 +1;星光兑换星币时额外增加 40% 的星光用于兑换
    public static final DeferredItem<Item> ATM = registerItem("atm_chip",
            () -> new AtmChipItem(new Item.Properties()
                    .stacksTo(1)
                    .rarity(AstralRarities.rare())));

    // 银行卡-余额少:装备期间星光基础值 +4(下限)
    public static final DeferredItem<Item> BANK_CARD_LOW = registerItem("bank_card_low_chip",
            () -> new BankCardChipItem(new Item.Properties()
                    .stacksTo(1)
                    .rarity(AstralRarities.rare()), BankCardChipItem.BASE_LOW));

    // 银行卡-余额多:装备期间星光基础值 +7(下限)
    public static final DeferredItem<Item> BANK_CARD_HIGH = registerItem("bank_card_high_chip",
            () -> new BankCardChipItem(new Item.Properties()
                    .stacksTo(1)
                    .rarity(AstralRarities.epic()), BankCardChipItem.BASE_HIGH));

    // 银行卡-用不完:装备时星光 +3;每次骰神赐福结束后,自身及团队所有成员获得 3 星币
    public static final DeferredItem<Item> BANK_CARD_UNLIMITED = registerItem("bank_card_unlimited_chip",
            () -> new BankCardUnlimitedChipItem(new Item.Properties()
                    .stacksTo(1)
                    .rarity(AstralRarities.legendary())));

    // 拳击手套-初级:骰神赐福攻击力 +2
    public static final DeferredItem<Item> BOXING_GLOVES_LOW = registerItem("boxing_gloves_low_chip",
            () -> new BoxingGlovesChipItem(new Item.Properties()
                    .stacksTo(1)
                    .rarity(AstralRarities.rare())));

    // 拳击手套-中级:骰神赐福攻击力 +4
    public static final DeferredItem<Item> BOXING_GLOVES_MEDIUM = registerItem("boxing_gloves_medium_chip",
            () -> new BoxingGlovesChipItem(new Item.Properties()
                    .stacksTo(1)
                    .rarity(AstralRarities.epic())));

    // 拳击手套-高级:骰神赐福攻击力 +8
    public static final DeferredItem<Item> BOXING_GLOVES_HIGH = registerItem("boxing_gloves_high_chip",
            () -> new BoxingGlovesChipItem(new Item.Properties()
                    .stacksTo(1)
                    .rarity(AstralRarities.legendary())));

    // 速度轮滑-初级:移动速度 +5%
    public static final DeferredItem<Item> SPEED_SKATES_LOW = registerItem("speed_skates_low_chip",
            () -> new SpeedSkatesChipItem(new Item.Properties()
                    .stacksTo(1)
                    .rarity(AstralRarities.rare()), SpeedSkatesChipItem.SPEED_LOW));

    // 速度轮滑-中级:移动速度 +15%
    public static final DeferredItem<Item> SPEED_SKATES_MEDIUM = registerItem("speed_skates_medium_chip",
            () -> new SpeedSkatesChipItem(new Item.Properties()
                    .stacksTo(1)
                    .rarity(AstralRarities.epic()), SpeedSkatesChipItem.SPEED_MEDIUM));

    // 速度轮滑-高级:移动速度 +25%
    public static final DeferredItem<Item> SPEED_SKATES_HIGH = registerItem("speed_skates_high_chip",
            () -> new SpeedSkatesChipItem(new Item.Properties()
                    .stacksTo(1)
                    .rarity(AstralRarities.legendary()), SpeedSkatesChipItem.SPEED_HIGH));

    // 摩托头盔-一般:防御力 +2(无盔甲韧性;代码折算护甲 +4)
    public static final DeferredItem<Item> MOTO_HELMET_LOW = registerItem("moto_helmet_low_chip",
            () -> new MotoHelmetChipItem(new Item.Properties()
                    .stacksTo(1)
                    .rarity(AstralRarities.rare()), MotoHelmetChipItem.DEFENSE_LOW, 0));

    // 摩托头盔-中级:防御力 +4(无盔甲韧性;代码折算护甲 +8)
    public static final DeferredItem<Item> MOTO_HELMET_MEDIUM = registerItem("moto_helmet_medium_chip",
            () -> new MotoHelmetChipItem(new Item.Properties()
                    .stacksTo(1)
                    .rarity(AstralRarities.epic()), MotoHelmetChipItem.DEFENSE_MEDIUM, 0));

    // 摩托头盔-高级:防御力 +6,盔甲韧性 +2(仅高级拥有韧性;代码折算护甲 +12)
    public static final DeferredItem<Item> MOTO_HELMET_HIGH = registerItem("moto_helmet_high_chip",
            () -> new MotoHelmetChipItem(new Item.Properties()
                    .stacksTo(1)
                    .rarity(AstralRarities.legendary()), MotoHelmetChipItem.DEFENSE_HIGH, MotoHelmetChipItem.TOUGHNESS_BONUS));

    // 夹心饼干-一般:最大生命值 +4
    public static final DeferredItem<Item> SANDWICH_LOW = registerItem("sandwich_low_chip",
            () -> new SandwichChipItem(new Item.Properties()
                    .stacksTo(1)
                    .rarity(AstralRarities.rare()), SandwichChipItem.HEALTH_LOW));

    // 夹心饼干-可口:最大生命值 +8
    public static final DeferredItem<Item> SANDWICH_MEDIUM = registerItem("sandwich_medium_chip",
            () -> new SandwichChipItem(new Item.Properties()
                    .stacksTo(1)
                    .rarity(AstralRarities.epic()), SandwichChipItem.HEALTH_MEDIUM));

    // 夹心饼干-美味:最大生命值 +8;最大生命值超过 20 点时,超出部分每 4 点 +1 攻击力
    public static final DeferredItem<Item> SANDWICH_HIGH = registerItem("sandwich_high_chip",
            () -> new SandwichChipItem(new Item.Properties()
                    .stacksTo(1)
                    .rarity(AstralRarities.legendary()), SandwichChipItem.HEALTH_HIGH));

    // 肾上腺素-一般:生命值为 50% 或更低时,攻击力/防御力 +3(史诗)
    public static final DeferredItem<Item> ADRENALINE_LOW = registerItem("adrenaline_low_chip",
            () -> new AdrenalineChipItem(new Item.Properties()
                    .stacksTo(1)
                    .rarity(AstralRarities.epic()), AdrenalineChipItem.BONUS_LOW));

    // 肾上腺素-高效:生命值为 50% 或更低时,攻击力/防御力 +8;触发加成时被敌方攻击,
    // 骰点 4-5 → 50% 闪避、6 → 100% 闪避本次伤害(传奇)
    public static final DeferredItem<Item> ADRENALINE_HIGH = registerItem("adrenaline_high_chip",
            () -> new AdrenalineChipItem(new Item.Properties()
                    .stacksTo(1)
                    .rarity(AstralRarities.legendary()), AdrenalineChipItem.BONUS_HIGH));

    // 魔法箭袋:使用过效果牌并对带标记目标造成法伤 → 施加标记并返还第一张使用的效果牌(每分钟一次)
    public static final DeferredItem<Item> MAGIC_QUIVER = registerItem("magic_quiver_chip",
            () -> new MagicQuiverChipItem(new Item.Properties()
                    .stacksTo(1)
                    .rarity(AstralRarities.epic())));

    // 缓冲盾牌:受到攻击时增加 2 点治愈与 3 星币(每 15 秒一次)
    public static final DeferredItem<Item> BUFFER_SHIELD = registerItem("buffer_shield_chip",
            () -> new BufferShieldChipItem(new Item.Properties()
                    .stacksTo(1)
                    .rarity(AstralRarities.rare())));

    // 星币锤:装备时星光 +5;持有星币超过 20 枚时,每次进入骰神赐福消耗 3 星币并按持有总数 30% 提升攻击力
    public static final DeferredItem<Item> STAR_COIN_HAMMER = registerItem("star_coin_hammer_chip",
            () -> new StarCoinHammerChipItem(new Item.Properties()
                    .stacksTo(1)
                    .rarity(AstralRarities.legendary())));

    // 诅咒之剑:装备时始终受到青之诅咒;骰神赐福期间每击杀 1 个不少于 20 血的敌对目标攻击力 +1(上限默认 16,最大 32)
    public static final DeferredItem<Item> CURSED_SWORD = registerItem("cursed_sword_chip",
            () -> new CursedSwordChipItem(new Item.Properties()
                    .stacksTo(1)
                    .rarity(AstralRarities.rare())));

    // 复仇之戟:拥有指定负面/诅咒效果时,攻击力/防御力 +6(每类只触发一次,不叠加)
    public static final DeferredItem<Item> REVENGE_HALBERD = registerItem("revenge_halberd_chip",
            () -> new RevengeHalberdChipItem(new Item.Properties()
                    .stacksTo(1)
                    .rarity(AstralRarities.epic())));

    // 贯穿之铳:伤害效果牌生效时,对敌对目标远程/魔法伤害额外增加目标防御力点数
    public static final DeferredItem<Item> PIERCING_GUN = registerItem("piercing_gun_chip",
            () -> new PiercingGunChipItem(new Item.Properties()
                    .stacksTo(1)
                    .rarity(AstralRarities.legendary())));

    // 可口糖果:每使用一张效果牌,治愈+1并恢复1点生命;满血使用时本轮出牌数+1(每轮一次)
    public static final DeferredItem<Item> CANDY_CHIP = registerItem("candy_chip",
            () -> new CandyChipItem(new Item.Properties()
                    .stacksTo(1)
                    .rarity(AstralRarities.epic())));

    // 友情徽章:对友方玩家施加治疗效果时,双方各获得 2 点治愈
    public static final DeferredItem<Item> FRIENDSHIP_BADGE = registerItem("friendship_badge_chip",
            () -> new FriendshipBadgeChipItem(new Item.Properties()
                    .stacksTo(1)
                    .rarity(AstralRarities.epic())));

    // 探天卫星:自动补充轨道炮;使用轨道炮后出牌数+1;轨道炮生效期间远程/魔法击杀给随机效果牌
    public static final DeferredItem<Item> SATELLITE_CHIP = registerItem("satellite_chip",
            () -> new SatelliteChipItem(new Item.Properties()
                    .stacksTo(1)
                    .rarity(AstralRarities.legendary())));

    // 跃迁引擎:触发指定传送后,获得 2 层充能并获得迅捷 0:10
    public static final DeferredItem<Item> WARP_ENGINE_CHIP = registerItem("warp_engine_chip",
            () -> new WarpEngineChipItem(new Item.Properties()
                    .stacksTo(1)
                    .rarity(AstralRarities.rare())));

    // 能量回收器:每移动 50 米获得 1 点充能
    public static final DeferredItem<Item> ENERGY_RECYCLER = registerItem("energy_recycler_chip",
            () -> new EnergyRecyclerChipItem(new Item.Properties()
                    .stacksTo(1)
                    .rarity(AstralRarities.rare())));

    // 电流剑:每 4 点充能攻击力 +1;击杀 10 个敌对目标后获得 2 点充能
    public static final DeferredItem<Item> ELECTRIC_SWORD = registerItem("electric_sword_chip",
            () -> new ElectricSwordChipItem(new Item.Properties()
                    .stacksTo(1)
                    .rarity(AstralRarities.rare())));

    // 高级外设:充能 ≥ 4 时攻击力 +4;每次触发骰神赐福移除 1 层充能
    public static final DeferredItem<Item> ADVANCED_PERIPHERALS = registerItem("advanced_peripherals_chip",
            () -> new AdvancedPeripheralsChipItem(new Item.Properties()
                    .stacksTo(1)
                    .rarity(AstralRarities.epic())));

    // 永动机:触发骰神赐福时,充能 +6
    public static final DeferredItem<Item> PERPETUAL_MOTION = registerItem("perpetual_motion_chip",
            () -> new PerpetualMotionChipItem(new Item.Properties()
                    .stacksTo(1)
                    .rarity(AstralRarities.legendary())));

    // 电流核心:使用主动技能时充能 +1;冷却中按下主动技能键时按剩余冷却占比消耗充能并立即使冷却完成
    public static final DeferredItem<Item> CURRENT_CORE_CHIP = registerItem("current_core_chip",
            () -> new CurrentCoreChipItem(new Item.Properties()
                    .stacksTo(1)
                    .rarity(AstralRarities.epic())));

    // 大碗炖肉:骰神赐福结束后,16 格范围内所有友方目标 +1 治愈并恢复 2 点生命值
    public static final DeferredItem<Item> BIG_BOWL_STEW_CHIP = registerItem("big_bowl_stew_chip",
            () -> new BigBowlStewChipItem(new Item.Properties()
                    .stacksTo(1)
                    .rarity(AstralRarities.legendary())));

    // 会员推荐信:每次触发骰神赐福时,获得一张随机卡牌
    public static final DeferredItem<Item> MEMBER_RECOMMENDATION_CHIP = registerItem("member_recommendation_chip",
            () -> new MemberRecommendationChipItem(new Item.Properties()
                    .stacksTo(1)
                    .rarity(AstralRarities.rare())));

    // 书签:使伤害效果牌伤害加成 +1
    public static final DeferredItem<Item> BOOKMARK_CHIP = registerItem("bookmark_chip",
            () -> new BookmarkChipItem(new Item.Properties()
                    .stacksTo(1)
                    .rarity(AstralRarities.rare())));

    // 小猪存钱罐:每使用 2 张效果牌后,获得 3 星币
    public static final DeferredItem<Item> PIGGY_BANK_CHIP = registerItem("piggy_bank_chip",
            () -> new PiggyBankChipItem(new Item.Properties()
                    .stacksTo(1)
                    .rarity(AstralRarities.rare())));

    // 智能手表:物品栏卡牌不足 10 张时,每击杀 1 个敌对目标获得一张随机卡牌
    public static final DeferredItem<Item> SMART_WATCH_CHIP = registerItem("smart_watch_chip",
            () -> new SmartWatchChipItem(new Item.Properties()
                    .stacksTo(1)
                    .rarity(AstralRarities.epic())));

    // 电击手套:使用效果牌时充能 +1;充能 ≥4 时使用伤害效果牌会消耗 4 层充能,使本周期内远程/魔法伤害
    // 同时命中目标 3 格范围内的其他敌对目标(每周期一次,史诗)
    public static final DeferredItem<Item> ELECTRIC_GLOVE_CHIP = registerItem("electric_glove_chip",
            () -> new ElectricGloveChipItem(new Item.Properties()
                    .stacksTo(1)
                    .rarity(AstralRarities.epic())));

    // 安全气囊:受到致命伤害时消耗 6 点充能无效化本次伤害(冷却 1:00,史诗)
    public static final DeferredItem<Item> AIRBAG_CHIP = registerItem("airbag_chip",
            () -> new AirbagChipItem(new Item.Properties()
                    .stacksTo(1)
                    .rarity(AstralRarities.epic())));

    // 电磁炮:充能 ≥6 时攻击力 +5;攻击敌对目标消耗 6 层充能,延迟 1 秒对其 3 格内敌对目标降下雷击(传奇)
    public static final DeferredItem<Item> RAILGUN_CHIP = registerItem("railgun_chip",
            () -> new RailgunChipItem(new Item.Properties()
                    .stacksTo(1)
                    .rarity(AstralRarities.legendary())));

    // 原初核心:每消耗 1 层充能获得 1 层赋能(每层 +1 攻击/防御,每 0:30 递减 1 层,传奇)
    public static final DeferredItem<Item> PRIMORDIAL_CORE_CHIP = registerItem("primordial_core_chip",
            () -> new PrimordialCoreChipItem(new Item.Properties()
                    .stacksTo(1)
                    .rarity(AstralRarities.legendary())));

    // 磨刀石:生命值为 50% 或更低时攻击力 +4、受到伤害 -2;生命值 >1 时受到的伤害不超过剩余生命值(史诗)
    public static final DeferredItem<Item> WHETSTONE_CHIP = registerItem("whetstone_chip",
            () -> new WhetstoneChipItem(new Item.Properties()
                    .stacksTo(1)
                    .rarity(AstralRarities.epic())));

    // 紫色飞星(史诗):路过敌对目标 ⇒ 使其受到 1 点伤害并自身 +1 层「星光」;每 10 秒触发一次。
    // 与金色飞星共用同一冷却计时器(用户裁决);两枚共用 ShootingStarChipItem,差异全在执行器 ShootingStarManager。
    public static final DeferredItem<Item> PURPLE_SHOOTING_STAR_CHIP = registerItem("purple_shooting_star_chip",
            () -> new ShootingStarChipItem(new Item.Properties()
                    .stacksTo(1)
                    .rarity(AstralRarities.epic())));

    // 金色飞星(传奇):同上,基础伤害 2 点;若目标为精英怪物或 Boss,额外造成自身当前「星光」层数的伤害。
    public static final DeferredItem<Item> GOLDEN_SHOOTING_STAR_CHIP = registerItem("golden_shooting_star_chip",
            () -> new ShootingStarChipItem(new Item.Properties()
                    .stacksTo(1)
                    .rarity(AstralRarities.legendary())));

    public static final DeferredItem<Item> PADMAN_SIGN = registerItem("padman_sign",
            () -> new PadmanSignItem(new Item.Properties()
                    .stacksTo(1)
                    .rarity(AstralRarities.rare())));

    public static final DeferredItem<Item> FANNY_SIGN = registerItem("fanny_sign",
            () -> new FannySignItem(new Item.Properties()
                    .stacksTo(1)
                    .rarity(AstralRarities.epic())));

    public static final DeferredItem<Item> RIN_SIGN = registerItem("rin_sign",
            () -> new RinSignItem(new Item.Properties()
                    .stacksTo(1)
                    .rarity(AstralRarities.legendary())));

    public static final DeferredItem<Item> LIVING_PAGE = registerItem("effect_card_living_page",
            () -> new LivingPageItem(new Item.Properties()
                    .stacksTo(64)
                    .rarity(AstralRarities.bizarre())));

    // 占星师立牌(命名:haiqing)
    public static final DeferredItem<Item> HAIQING_SIGN = registerItem("haiqing_sign",
            () -> new HaiqingSignItem(new Item.Properties()
                    .stacksTo(1)
                    .rarity(AstralRarities.epic())));

    // 命运的指引(专属功能效果牌,击杀带虚弱印记的目标获取)
    public static final DeferredItem<Item> FATE_GUIDANCE_CARD = registerItem("effect_card_fate_guidance",
            () -> new FateGuidanceCardItem(new Item.Properties()
                    .stacksTo(64)
                    .rarity(AstralRarities.bizarre())));

    // 吸血鬼立牌(命名:papara):配方=黄金骰子+星盘 → 史诗
    public static final DeferredItem<Item> PAPARA_SIGN = registerItem("papara_sign",
            () -> new PaparaSignItem(new Item.Properties()
                    .stacksTo(1)
                    .rarity(AstralRarities.epic())));

    // 秘密侦探立牌(命名:bonnie):配方=下界合金骰子 → 传奇
    public static final DeferredItem<Item> BONNIE_SIGN = registerItem("bonnie_sign",
            () -> new BonnieSignItem(new Item.Properties()
                    .stacksTo(1)
                    .rarity(AstralRarities.legendary())));

    // === 新效果牌(治疗/互动) ===
    // 巧克力蛋糕:使用后恢复 4 点生命值。品质:青(蓝)
    public static final DeferredItem<Item> CHOCOLATE_CAKE = registerItem("effect_card_chocolate_cake",
            () -> new ChocolateCakeCardItem(new Item.Properties()
                    .stacksTo(64)
                    .rarity(AstralRarities.rare())));

    // 汉堡:使用后恢复 8 点生命值
    public static final DeferredItem<Item> HAMBURGER = registerItem("effect_card_hamburger",
            () -> new HamburgerCardItem(new Item.Properties()
                    .stacksTo(64)
                    .rarity(AstralRarities.epic())));

    // 奢华大餐:治疗目标及周围 6 格内所有玩家 6 点生命值(可对自己/他人使用)
    public static final DeferredItem<Item> LUXURY_FEAST = registerItem("effect_card_luxury_feast",
            () -> new LuxuryFeastCardItem(new Item.Properties()
                    .stacksTo(64)
                    .rarity(AstralRarities.epic())));

    // 你有我有:仅能对其他玩家使用,自身与目标玩家各获得一张随机卡牌
    public static final DeferredItem<Item> YOU_HAVE_I_HAVE = registerItem("effect_card_you_have_i_have",
            () -> new YouHaveIHaveCardItem(new Item.Properties()
                    .stacksTo(64)
                    .rarity(AstralRarities.epic())));

    // 加急加快:使目标获得 迅捷 II 1:00(可对自己/他人使用)
    public static final DeferredItem<Item> EXPRESS_DELIVERY = registerItem("effect_card_express_delivery",
            () -> new ExpressDeliveryCardItem(new Item.Properties()
                    .stacksTo(64)
                    .rarity(AstralRarities.epic())));

    // 大当家立牌(命名:fen):养精蓄锐计数器 + 战斗爽主动;配方=钻石骰子+星盘 → 传奇
    public static final DeferredItem<Item> FEN_SIGN = registerItem("fen_sign",
            () -> new FenSignItem(new Item.Properties()
                    .stacksTo(1)
                    .rarity(AstralRarities.legendary())));

    // 骇客立牌(命名:nancy_lu):网络防火墙被动 + 远程侵入主动
    public static final DeferredItem<Item> NANCY_LU_SIGN = registerItem("nancy_lu_sign",
            () -> new NancyLuSignItem(new Item.Properties()
                    .stacksTo(1)
                    .rarity(AstralRarities.epic())));

    // 枪匠立牌(命名:moses):弱点识破 + 破绽;主动弱点反击,主动冷却 120 秒(史诗)
    public static final DeferredItem<Item> MOSES_SIGN = registerItem("moses_sign",
            () -> new MosesSignItem(new Item.Properties()
                    .stacksTo(1)
                    .rarity(AstralRarities.epic())));

    // 肉弹战车立牌(命名:pandaman,稀有):治疗/生命上限被动 + 大吃特吃主动
    public static final DeferredItem<Item> PANDAMAN_SIGN = registerItem("pandaman_sign",
            () -> new PandamanSignItem(new Item.Properties()
                    .stacksTo(1)
                    .rarity(AstralRarities.rare())));

    // 游戏大师立牌(命名:ren,史诗):鼠鼠救我被动(5:00 无盾自动补「1 张随机卡牌 + 护盾」)
    // + 熊孩子特权主动(选任意玩家或自身);盾 = 5 黄心 + 抗性提升 + 1 层反击;配方=基础骰子(纸×4 + 星币×3) → 史诗
    // (2026-09-25 修正:上一批误把本立牌改成「奇特」,本批按用户裁决改回史诗 —— 奇特的立牌是怪力侦探与人偶师。)
    public static final DeferredItem<Item> REN_SIGN = registerItem("ren_sign",
            () -> new RenSignItem(new Item.Properties()
                    .stacksTo(1)
                    .rarity(AstralRarities.epic())));

    // 风水师立牌(命名:zhao,传奇):被动「福祸相倚」(骰点 1→符卡-祸 / 6→符卡-福)
    // + 被动「完美帮手」(对装备大当家立牌者施加白泽赐福时给 1 层养精蓄锐)
    // + 主动「白泽赐福」(目标选择器;溢出治疗等量转攻击力;持续到下一次骰神赐福结束;
    //   施法者得 1 张符卡-福并把自身全部符卡-祸转为符卡-福)。
    // 传奇品质 = ASTRAL_DICE_LEGENDARY(本模组「金=传奇」映射,见本类顶部的稀有度标准)。
    public static final DeferredItem<Item> ZHAO_SIGN = registerItem("zhao_sign",
            () -> new ZhaoSignItem(new Item.Properties()
                    .stacksTo(1)
                    .rarity(AstralRarities.legendary())));

    // 教主立牌(命名:teru,传奇):被动「狐光」(层数资源;合成/获得/装备攻击牌 +1 层,带防刷守卫)
    // + 主动「降神」(目标选择器,只能选**其他玩家**:锁定目标 50% 攻防给自己,持续到目标下一次骰神赐福结束;
    //   目标每攻击一个新目标消耗 1 层狐光,按「狐光攻击基数 + 剩余层数」追加骰战攻击力)。
    // 传奇品质 = ASTRAL_DICE_LEGENDARY(本模组「金=传奇」映射,见本类顶部的稀有度标准)。
    public static final DeferredItem<Item> TERU_SIGN = registerItem("teru_sign",
            () -> new TeruSignItem(new Item.Properties()
                    .stacksTo(1)
                    .rarity(AstralRarities.legendary())));

    // 绿洲女王立牌(命名:nardis,稀有):被动「威压」(每装配 1 张攻击牌攻击力 +2 / 每装配 1 张防御牌防御力 +2;
    //   2026-09-21 用户裁决由 +1 小幅加强,常量 = NardisSignItem.BONUS_PER_CARD)
    // + 主动「女王特权」(立即获得 3 张随机临时牌,有效期 3:00;临时牌只能装备与使用,
    //   不可丢弃/不可放入其它容器,到期连同已装配的一并清除;效果 HUD 计时器图标 = 立牌贴图)。
    // 稀有品质 = ASTRAL_DICE_RARE;配方 = 黄金骰子(无星盘)档(同史莱姆 lulu / 上班族 padman)。
    public static final DeferredItem<Item> NARDIS_SIGN = registerItem("nardis_sign",
            () -> new NardisSignItem(new Item.Properties()
                    .stacksTo(1)
                    .rarity(AstralRarities.rare())));

    // 蛟龙立牌(命名:mamushi,传奇):**技能(主动/被动)待用户裁决** —— 本批只落资产与注册,
    // 物品类未覆写 handleUse ⇒ 主动暂无任何效果(契约兜底会打 WARN,见 MamushiSignItem 类 javadoc);
    // 技能定稿后在此补技能注释并在物品类内实现。
    // 传奇品质 = ASTRAL_DICE_LEGENDARY(本模组「金=传奇」映射);配方 = 钻石骰子 + 黄金星盘档(照大当家立牌 fen)。
    public static final DeferredItem<Item> MAMUSHI_SIGN = registerItem("mamushi_sign",
            () -> new MamushiSignItem(new Item.Properties()
                    .stacksTo(1)
                    .rarity(AstralRarities.legendary())));

    // 怪力侦探立牌(命名:sherry,奇特):主动「怪力投掷」把 12 格内全部敌对目标按抛物线扔到玩家面前 2 格,
    // **落地之后**造成 2 点伤害并施加 1 层「标记」(推理时间满 5 层 ⇒ 额外 5 点);被动「侦探出击」按
    // 「攻击 ≥20 血敌对目标」累积「推理时间」(上限 5,**死亡不清**,骰神赐福结束后 −1 层),
    // 「挚友守护」为同队装备人偶师立牌的玩家减伤 1 点。
    // 奇特品质 = ASTRAL_DICE_BIZARRE(2026-09-25 用户裁决「专属牌 + 怪力侦探和人偶师改奇特」);
    // 配方 = 紫晶骰子 + 黄金星盘档(2026-09-25 用户裁决:骰子由钻石骰子改紫晶骰子)。
    public static final DeferredItem<Item> SHERRY_SIGN = registerItem("sherry_sign",
            () -> new com.merlinkitsune.astral_dice.item.sign.SherrySignItem(new Item.Properties()
                    .stacksTo(1)
                    .rarity(AstralRarities.bizarre())));

    // 人偶师立牌(命名:hanna,奇特):被动「幻想千金」(战斗骰点 = 6 ⇒ 1 星币;路过 3 格内友方玩家 ⇒
    // 该玩家 1 星币 + 自身 1 层「人偶制作」,自身处于「魔女漂浮」时该玩家改为 3 星币;「人偶制作」满 7 层
    // ⇒ 归零转为「人偶完成」,此后路过额外给该玩家 迅捷 II (1:00) + 3 星币;整体每 1:00 仅触发 1 次)
    // + 被动「挚友祝福」(路过装备「怪力侦探」立牌的玩家 ⇒ 该玩家获得 力量 II (1:00) + 抗性提升 (1:00)
    // + 1 层「推理时间」;每 1:00 仅触发 1 次)
    // + 主动「漂浮魔法」(自身 魔女漂浮 1:00:移速 +20%、掉落伤害 -100%、近战攻击被闪避、禁用末影珍珠)。
    // 奇特品质 = ASTRAL_DICE_BIZARRE(2026-09-25 用户裁决;上一批漏改,本批补上);
    // 配方 = 紫晶骰子 + 黄金星盘×2 档(2026-09-25 用户裁决:两个线位改黄金星盘、骰子改紫晶骰子)。
    public static final DeferredItem<Item> HANNA_SIGN = registerItem("hanna_sign",
            () -> new com.merlinkitsune.astral_dice.item.sign.HannaSignItem(new Item.Properties()
                    .stacksTo(1)
                    .rarity(AstralRarities.bizarre())));

    // 符卡-福(专属功能效果牌,风水师立牌专属):出牌数 +1;对玩家(不限队伍)或自身使用 ⇒ 恢复 2 点生命值。
    // 专属绑定:获得即绑定获得者(ModDataComponents.OWNER_UUID),他人无法使用。
    // 品质:**稀有**(ASTRAL_DICE_RARE;用户 2026-09-21 裁决,原为传奇 UNCOMMON)。
    public static final DeferredItem<Item> FU_CARD = registerItem("fu_card",
            () -> new FuCardItem(new Item.Properties()
                    .stacksTo(64)
                    .rarity(AstralRarities.bizarre())));

    // 符卡-祸(专属伤害效果牌,风水师立牌专属):只能对敌对目标(含非同队玩家)使用 ⇒ 1 点伤害;
    // 持有者每 2:00 按当前张数受伤(厄运层数 == 持有张数)。
    // 品质:**稀有**(ASTRAL_DICE_RARE;用户 2026-09-21 裁决,原为传奇 UNCOMMON)。
    public static final DeferredItem<Item> HUO_CARD = registerItem("huo_card",
            () -> new HuoCardItem(new Item.Properties()
                    .stacksTo(64)
                    .rarity(AstralRarities.bizarre())));

    public static <T extends Item> DeferredItem<T> registerItem(String name, Supplier<T> itemSupplier) {
        return ITEMS.register(name, itemSupplier);
    }

    // 判断物品栈是否为任意卡牌(战斗牌 + 效果牌;含专属牌)
    public static boolean isCardItem(ItemStack stack) {
        return stack.is(COMBAT_CARDS_TAG) || stack.is(EFFECT_CARDS_TAG);
    }
}
