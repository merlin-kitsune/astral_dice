package com.merlinkitsune.astral_dice.item;

import com.merlinkitsune.astral_dice.AstralDiceMod;
import com.merlinkitsune.astral_dice.component.AppliedStone;
import com.merlinkitsune.astral_dice.component.ModDataComponents;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.registries.RegistryObject;
import net.minecraftforge.registries.DeferredRegister;

import java.util.function.Supplier;
import com.merlinkitsune.astral_dice.item.chip.StarCoinHammerChipItem;
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
import com.merlinkitsune.astral_dice.item.card.FuCardItem;
import com.merlinkitsune.astral_dice.item.card.HuoCardItem;
import com.merlinkitsune.astral_dice.item.sign.ZhaoSignItem;
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
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(net.minecraftforge.registries.ForgeRegistries.ITEMS, AstralDiceMod.MODID);

    private static final net.minecraft.tags.TagKey<Item> COMBAT_CARDS_TAG =
            net.minecraft.tags.ItemTags.create(new ResourceLocation(
                    AstralDiceMod.MODID, "combat_cards"));
    private static final net.minecraft.tags.TagKey<Item> EFFECT_CARDS_TAG =
            net.minecraft.tags.ItemTags.create(new ResourceLocation(
                    AstralDiceMod.MODID, "effect_cards"));

    // ═══════════════════════════════════════════════════════════════════════════
    // 本模组稀有度标准(仅代码层表示,映射 MC 标准 Rarity):
    //   白 = 普通   → Rarity.COMMON
    //   蓝 = 稀有   → Rarity.RARE
    //   紫 = 史诗   → Rarity.EPIC
    //   金 = 传奇   → Rarity.UNCOMMON(MC 无金色枚举,以黄色 UNCOMMON 表示传奇)
    // 说明:MC 1.21.1 的 Rarity 为枚举,无法自定义新实例;统一按上表映射。
    // Bountiful 赏金联动数据层(资源文件 data/bountiful/bounty_pools/bountiful/astral_*):
    //   金=传奇 一律写 "rarity": "LEGENDARY"(Bountiful 原生枚举,金色,权重最低/声望最高),
    //   蓝=RARE、紫=EPIC、白=COMMON 与上表一致;传奇品质(金)的筹码与立牌不进入奖励池 astral_rews。
    // 骰子品质按合成配方升级链配色:基础=白(普通)、黄金=蓝(稀有)、钻石=紫(史诗)、合金=金(传奇)。
    // 下界合金骰子(金=传奇)不参与赏金板:已从 astral_objs/astral_rews 池中移除。
    // 新增物品时按此标准选择 rarity,并保持与图标边框颜色一致;若参与赏金,同步维护 astral_objs/astral_rews。
    // ═══════════════════════════════════════════════════════════════════════════

    public static final RegistryObject<Item> DICE = registerItem("dice",
            () -> new DiceCurioItem(new Item.Properties()
                    .stacksTo(1)
                    .rarity(Rarity.COMMON)));

    // 黄金骰子:由基础骰子 + 4 星币 + 4 金锭升级而来,卡牌放置栏固定攻防各 4(共 8)
    public static final RegistryObject<Item> GOLDEN_DICE = registerItem("golden_dice",
            () -> new DiceCurioItem(new Item.Properties()
                    .stacksTo(1)
                    .rarity(Rarity.RARE)));

    // 玻璃骰子:与黄金骰子同阶(tag dice_t1),可由黄金骰子升级;战斗牌点数始终取最大值,但死亡会丢失该骰子及已装备卡牌
    public static final RegistryObject<Item> GLASS_DICE = registerItem("glass_dice",
            () -> new DiceCurioItem(new Item.Properties()
                    .stacksTo(1)
                    .rarity(Rarity.RARE)));

    // 钻石骰子:由黄金骰子 + 4 星盘 + 4 钻石升级而来,卡牌放置栏固定攻防各 5(共 10)
    public static final RegistryObject<Item> DIAMOND_DICE = registerItem("diamond_dice",
            () -> new DiceCurioItem(new Item.Properties()
                    .stacksTo(1)
                    .rarity(Rarity.EPIC)));

    // 合金骰子:由钻石骰子 + 4 黄金星盘 + 4 下界合金锭升级而来,卡牌放置栏为攻防各 6 个(共 12)
    public static final RegistryObject<Item> NETHERITE_DICE = registerItem("netherite_dice",
            () -> new DiceCurioItem(new Item.Properties()
                    .stacksTo(1)
                    .rarity(Rarity.UNCOMMON)));

    // 绿宝石骰子:与钻石骰子同阶(tag dice_t2),可由钻石骰子升级;佩戴后村民交易绿宝石费用改为星币并享 20% 折扣
    public static final RegistryObject<Item> EMERALD_DICE = registerItem("emerald_dice",
            () -> new DiceCurioItem(new Item.Properties()
                    .stacksTo(1)
                    .rarity(Rarity.EPIC)));

    // 黑曜石骰子:与钻石骰子同阶(tag dice_t2),可由钻石骰子升级;基础防御力 +3(折算 +6 护甲),火焰伤害 -70%
    public static final RegistryObject<Item> OBSIDIAN_DICE = registerItem("obsidian_dice",
            () -> new com.merlinkitsune.astral_dice.item.dice.ObsidianDiceItem(new Item.Properties()
                    .stacksTo(1)
                    .rarity(Rarity.EPIC)));

    // 下界岩骰子:与黄金骰子同阶(tag dice_t1),可由黄金骰子升级;下界挖矿概率掉星币/星盘,猪灵保持中立
    public static final RegistryObject<Item> NETHERRACK_DICE = registerItem("netherrack_dice",
            () -> new DiceCurioItem(new Item.Properties()
                    .stacksTo(1)
                    .rarity(Rarity.RARE)));

    // 诡异骰子:与钻石骰子同阶(tag dice_t2),可由钻石骰子升级;立牌主动冷却 -50%,但战斗骰低点数(1-3)概率提升 50%
    public static final RegistryObject<Item> WEIRD_DICE = registerItem("weird_dice",
            () -> new DiceCurioItem(new Item.Properties()
                    .stacksTo(1)
                    .rarity(Rarity.EPIC)));

    // 绯红骰子:与下界合金骰子同阶(tag dice_t3),可由下界合金骰子升级;战斗骰高点数(4-6)概率提升 50%,但骰出 1 时立即受到 6 点伤害
    public static final RegistryObject<Item> CRIMSON_DICE = registerItem("crimson_dice",
            () -> new DiceCurioItem(new Item.Properties()
                    .stacksTo(1)
                    .rarity(Rarity.UNCOMMON)));

    // 紫晶骰子:与钻石骰子同阶(tag dice_t2),可由钻石骰子升级;远程/魔法攻击也触发战斗骰并追加骰点伤害(不触发骰神赐福)
    public static final RegistryObject<Item> AMETHYST_DICE = registerItem("amethyst_dice",
            () -> new DiceCurioItem(new Item.Properties()
                    .stacksTo(1)
                    .rarity(Rarity.EPIC)));

    // 末影骰子:与下界合金骰子同阶(tag dice_t3),可由下界合金骰子升级;致命伤害触发不死图腾效果(冷却 5:00),但雨中/水下受到的伤害 +40%
    public static final RegistryObject<Item> ENDER_DICE = registerItem("ender_dice",
            () -> new DiceCurioItem(new Item.Properties()
                    .stacksTo(1)
                    .rarity(Rarity.UNCOMMON)));

    // 下界之星骰子:T4 奇异品阶(tag dice_t4),可由任意 T3 骰子升级;卡牌槽/费用恒为最高档、筹码栏 +1,星级附加攻防
    public static final RegistryObject<Item> NETHER_STAR_DICE = registerItem("nether_star_dice",
            () -> new com.merlinkitsune.astral_dice.item.dice.NetherStarDiceItem(new Item.Properties()
                    .stacksTo(1)
                    .rarity(Rarity.UNCOMMON)));

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

    public static final RegistryObject<Item> ATTACK_CARD_MEDIUM = registerItem("attack_card_medium",
            () -> new CardItem(new Item.Properties()
                    .stacksTo(64)
                    , "medium"));

    public static final RegistryObject<Item> ATTACK_CARD_LARGE = registerItem("attack_card_large",
            () -> new CardItem(new Item.Properties()
                    .stacksTo(64)
                    .rarity(Rarity.RARE)
                    , "large"));

    public static final RegistryObject<Item> ATTACK_CARD_EPIC = registerItem("attack_card_epic",
            () -> new CardItem(new Item.Properties()
                    .stacksTo(64)
                    .rarity(Rarity.EPIC)
                    , "epic"));

    public static final RegistryObject<Item> ATTACK_CARD_SHADOW_STRIKE = registerItem("attack_card_shadow_strike",
            () -> new CardItem(new Item.Properties()
                    .stacksTo(64)
                    .rarity(Rarity.RARE)
                    , "shadow_strike"));

    public static final RegistryObject<Item> ATTACK_CARD_MEITO = registerItem("attack_card_meito",
            () -> new CardItem(new Item.Properties()
                    .stacksTo(64)
                    .rarity(Rarity.EPIC)
                    , "meito"));

    public static final RegistryObject<Item> ATTACK_CARD_CHARGE = registerItem("attack_card_charge",
            () -> new CardItem(new Item.Properties()
                    .stacksTo(64)
                    .rarity(Rarity.UNCOMMON)
                    , "charge"));

    public static final RegistryObject<Item> ATTACK_CARD_FULL_POWER = registerItem("attack_card_full_power",
            () -> new CardItem(new Item.Properties()
                    .stacksTo(64)
                    .rarity(Rarity.UNCOMMON)
                    , "full_power"));

    public static final RegistryObject<Item> DEFENSE_CARD_MEDIUM = registerItem("defense_card_medium",
            () -> new CardItem(new Item.Properties()
                    .stacksTo(64)
                    , "defense_medium"));

    public static final RegistryObject<Item> DEFENSE_CARD_LARGE = registerItem("defense_card_large",
            () -> new CardItem(new Item.Properties()
                    .stacksTo(64)
                    .rarity(Rarity.RARE)
                    , "defense_large"));

    public static final RegistryObject<Item> DEFENSE_CARD_EPIC = registerItem("defense_card_epic",
            () -> new CardItem(new Item.Properties()
                    .stacksTo(64)
                    .rarity(Rarity.EPIC)
                    , "defense_epic"));

    public static final RegistryObject<Item> EFFECT_CARD_KING_POWER = registerItem("effect_card_king_power",
            () -> new EffectCardItem(new Item.Properties()
                    .stacksTo(64)
                    .rarity(Rarity.UNCOMMON)));

    public static final RegistryObject<Item> EFFECT_CARD_BERSERK = registerItem("effect_card_berserk",
            () -> new BerserkCardItem(new Item.Properties()
                    .stacksTo(64)
                    .rarity(Rarity.EPIC)));

    public static final RegistryObject<Item> EFFECT_CARD_UNWAVERING = registerItem("effect_card_unwavering",
            () -> new UnwaveringCardItem(new Item.Properties()
                    .stacksTo(64)
                    .rarity(Rarity.EPIC)));

    // 以毒攻毒(效果牌):中毒 8 秒后移除负面效果并获得生命恢复 II 15 秒
    public static final RegistryObject<Item> EFFECT_CARD_FIGHT_POISON_WITH_POISON = registerItem("effect_card_fight_poison_with_poison",
            () -> new FightPoisonWithPoisonCardItem(new Item.Properties()
                    .stacksTo(64)
                    .rarity(Rarity.EPIC)));

    // 对怪激光(伤害效果牌):远程和魔法伤害 +4。品质:青(蓝)
    public static final RegistryObject<Item> MONSTER_LASER_CARD = registerItem("effect_card_monster_laser",
            () -> new MonsterLaserCardItem(new Item.Properties()
                    .stacksTo(64)
                    .rarity(Rarity.RARE)));

    // 对怪板砖(伤害效果牌):远程和魔法伤害 +6
    public static final RegistryObject<Item> MONSTER_BRICK_CARD = registerItem("effect_card_monster_brick",
            () -> new MonsterBrickCardItem(new Item.Properties()
                    .stacksTo(64)
                    .rarity(Rarity.EPIC)));

    // 轨道炮(伤害效果牌):远程和魔法伤害 +8。品质:黄(金)
    public static final RegistryObject<Item> ORBITAL_STRIKE_CARD = registerItem("effect_card_orbital_strike",
            () -> new OrbitalStrikeCardItem(new Item.Properties()
                    .stacksTo(64)
                    .rarity(Rarity.UNCOMMON)));

    // 定向爆破(伤害效果牌):远程和魔法伤害 +5,并对目标周围 6 格敌对目标造成同样伤害。品质:黄(金)
    public static final RegistryObject<Item> DIRECTIONAL_BLAST_CARD = registerItem("effect_card_directional_blast",
            () -> new DirectionalBlastCardItem(new Item.Properties()
                    .stacksTo(64)
                    .rarity(Rarity.UNCOMMON)));

    public static final RegistryObject<Item> STAR_COIN = registerItem("star_coin",
            () -> new Item(new Item.Properties()
                    .stacksTo(64)
                    .rarity(Rarity.RARE)));

    // 袋装星币:9 枚星币打包(可逆),便于批量携带
    public static final RegistryObject<Item> STAR_COIN_BAG = registerItem("star_coin_bag",
            () -> new Item(new Item.Properties()
                    .stacksTo(64)
                    .rarity(Rarity.RARE)));

    public static final RegistryObject<Item> BLANK_SIGN = registerItem("blank_sign",
            () -> new Item(new Item.Properties()
                    .stacksTo(64)));

    public static final RegistryObject<Item> PARUNAN_SIGN = registerItem("parunan_sign",
            () -> new ParunanSignItem(new Item.Properties()
                    .stacksTo(1)
                    .rarity(Rarity.RARE)));

    public static final RegistryObject<Item> JASMINE_SIGN = registerItem("jasmine_sign",
            () -> new JasmineSignItem(new Item.Properties()
                    .stacksTo(1)
                    .rarity(Rarity.RARE)));

    public static final RegistryObject<Item> MISAKI_SIGN = registerItem("misaki_sign",
            () -> new MisakiSignItem(new Item.Properties()
                    .stacksTo(1)
                    .rarity(Rarity.UNCOMMON)));

    public static final RegistryObject<Item> MIMI_SIGN = registerItem("mimi_sign",
            () -> new MimiSignItem(new Item.Properties()
                    .stacksTo(1)
                    .rarity(Rarity.RARE)));

    public static final RegistryObject<Item> LULU_SIGN = registerItem("lulu_sign",
            () -> new LuluSignItem(new Item.Properties()
                    .stacksTo(1)
                    .rarity(Rarity.RARE)));

    public static final RegistryObject<Item> KOMACHI_SIGN = registerItem("komachi_sign",
            () -> new KomachiSignItem(new Item.Properties()
                    .stacksTo(1)
                    .rarity(Rarity.EPIC)));

    public static final RegistryObject<Item> FLASHLIGHT_CHIP = registerItem("flashlight_chip",
            () -> new FlashlightChipItem(new Item.Properties()
                    .stacksTo(1)
                    .rarity(Rarity.EPIC)));

    public static final RegistryObject<Item> CUTTER_CHIP = registerItem("cutter_chip",
            () -> new CutterChipItem(new Item.Properties()
                    .stacksTo(1)
                    .rarity(Rarity.EPIC)));

    // 美工刀-锋利:与美工刀-初级功能一致,基础攻击提高至 4 点;可与美工刀-初级同时装备
    public static final RegistryObject<Item> CUTTER_BLADE_CHIP = registerItem("cutter_blade_chip",
            () -> new CutterBladeChipItem(new Item.Properties()
                    .stacksTo(1)
                    .rarity(Rarity.UNCOMMON)));

    public static final RegistryObject<Item> BLANK_CHIP = registerItem("blank_chip",
            () -> new Item(new Item.Properties()
                    .stacksTo(64)));

    public static final RegistryObject<Item> SCOPE_CHIP = registerItem("scope_chip",
            () -> new ScopeChipItem(new Item.Properties()
                    .stacksTo(1)
                    .rarity(Rarity.EPIC)));

    public static final RegistryObject<Item> EAGLE_SCOPE_CHIP = registerItem("eagle_scope_chip",
            () -> new EagleScopeChipItem(new Item.Properties()
                    .stacksTo(1)
                    .rarity(Rarity.UNCOMMON)));

    public static final RegistryObject<Item> MEDKIT_EMERGENCY_CHIP = registerItem("medkit_emergency_chip",
            () -> new MedkitEmergencyChipItem(new Item.Properties()
                    .stacksTo(1)
                    .rarity(Rarity.RARE)));

    public static final RegistryObject<Item> MEDKIT_COMPLETE_CHIP = registerItem("medkit_complete_chip",
            () -> new MedkitCompleteChipItem(new Item.Properties()
                    .stacksTo(1)
                    .rarity(Rarity.UNCOMMON)));

    // 维生素药丸:通过合成或奖励途径获得任意卡牌时,治愈 +1
    public static final RegistryObject<Item> VITAMIN_PILL_CHIP = registerItem("vitamin_pill_chip",
            () -> new VitaminPillChipItem(new Item.Properties()
                    .stacksTo(1)
                    .rarity(Rarity.EPIC)));

    public static final RegistryObject<Item> TARGET_CHIP = registerItem("target_chip",
            () -> new TargetChipItem(new Item.Properties()
                    .stacksTo(1)
                    .rarity(Rarity.RARE)));

    // 标记喷罐:对目标造成远程或魔法伤害后,使目标获得一层"标记"
    public static final RegistryObject<Item> MARKER_SPRAYER_CHIP = registerItem("marker_sprayer_chip",
            () -> new MarkerSprayerChipItem(new Item.Properties()
                    .stacksTo(1)
                    .rarity(Rarity.RARE)));

    // 魔法秘典:每使用 3 张效果牌,复制最后一张使用的效果牌并返回物品栏
    public static final RegistryObject<Item> MAGIC_TOME_CHIP = registerItem("magic_tome_chip",
            () -> new MagicTomeChipItem(new Item.Properties()
                    .stacksTo(1)
                    .rarity(Rarity.EPIC)));

    // 大背包:效果牌出牌数 +1(装备后生效)
    public static final RegistryObject<Item> BIG_BACKPACK_CHIP = registerItem("big_backpack_chip",
            () -> new BigBackpackChipItem(new Item.Properties()
                    .stacksTo(1)
                    .rarity(Rarity.EPIC)));

    // 忍术飞镖:效果牌出牌数+1;伤害效果牌生效期间远程/魔法伤害获得目标标记层数加成
    public static final RegistryObject<Item> NINJA_STAR_CHIP = registerItem("ninja_star_chip",
            () -> new NinjaStarChipItem(new Item.Properties()
                    .stacksTo(1)
                    .rarity(Rarity.UNCOMMON)));

    // 手持风扇-小:使用主动技能后对周围 16 格敌对目标施加标记
    public static final RegistryObject<Item> HAND_FAN_SMALL_CHIP = registerItem("hand_fan_small_chip",
            () -> new FanSmallChipItem(new Item.Properties()
                    .stacksTo(1)
                    .rarity(Rarity.RARE)));

    // 手持风扇-大:使用主动技能后获得一张随机效果牌(不含专属),并对周围范围内敌对目标施加标记
    public static final RegistryObject<Item> HAND_FAN_BIG_CHIP = registerItem("hand_fan_big_chip",
            () -> new FanBigChipItem(new Item.Properties()
                    .stacksTo(1)
                    .rarity(Rarity.EPIC)));

    public static final RegistryObject<Item> STAR_PLATE = registerItem("star_plate",
            () -> new Item(new Item.Properties()
                    .stacksTo(64)
                    .rarity(Rarity.EPIC)));

    public static final RegistryObject<Item> GOLDEN_STAR_PLATE = registerItem("golden_star_plate",
            () -> new Item(new Item.Properties()
                    .stacksTo(64)
                    .rarity(Rarity.UNCOMMON)));

    // === 新材料(1.2.0):合成材料,本身不参与配方 ===
    // 再生试剂:再生相关的试剂(动态贴图,2 帧)
    public static final RegistryObject<Item> REGENERATION_REAGENT = registerItem("regeneration_reagent",
            () -> new Item(new Item.Properties()
                    .stacksTo(64)));

    // 导电线材:可导引电流的线材(动态贴图,10 帧)
    public static final RegistryObject<Item> CONDUCTIVE_WIRE = registerItem("conductive_wire",
            () -> new Item(new Item.Properties()
                    .stacksTo(64)));

    // 星币尘:星币研磨而成的粉末(动态贴图,7 帧)
    public static final RegistryObject<Item> STAR_COIN_DUST = registerItem("star_coin_dust",
            () -> new Item(new Item.Properties()
                    .stacksTo(64)));

    // 标记涂料:用于标记目标的涂料(静态贴图)
    public static final RegistryObject<Item> MARK_PAINT = registerItem("mark_paint",
            () -> new Item(new Item.Properties()
                    .stacksTo(64)));

    public static final RegistryObject<Item> EIGHT_SIDED_DICE = registerItem("eight_sided_dice_chip",
            () -> new EightSidedDiceChipItem(new Item.Properties()
                    .stacksTo(1)
                    .rarity(Rarity.RARE)));

    // === 新筹码(ATM机/银行卡/拳击手套/速度轮滑/摩托头盔/夹心饼干/魔法箭袋/缓冲盾牌/星币锤) ===
    // ATM机:装备时星光 +1;星光兑换星币时额外增加 40% 的星光用于兑换
    public static final RegistryObject<Item> ATM = registerItem("atm_chip",
            () -> new AtmChipItem(new Item.Properties()
                    .stacksTo(1)
                    .rarity(Rarity.RARE)));

    // 银行卡-余额少:装备期间星光基础值 +4(下限)
    public static final RegistryObject<Item> BANK_CARD_LOW = registerItem("bank_card_low_chip",
            () -> new BankCardChipItem(new Item.Properties()
                    .stacksTo(1)
                    .rarity(Rarity.RARE), BankCardChipItem.BASE_LOW));

    // 银行卡-余额多:装备期间星光基础值 +7(下限)
    public static final RegistryObject<Item> BANK_CARD_HIGH = registerItem("bank_card_high_chip",
            () -> new BankCardChipItem(new Item.Properties()
                    .stacksTo(1)
                    .rarity(Rarity.EPIC), BankCardChipItem.BASE_HIGH));

    // 银行卡-用不完:装备时星光 +3;每次骰神赐福结束后,自身及团队所有成员获得 3 星币
    public static final RegistryObject<Item> BANK_CARD_UNLIMITED = registerItem("bank_card_unlimited_chip",
            () -> new BankCardUnlimitedChipItem(new Item.Properties()
                    .stacksTo(1)
                    .rarity(Rarity.UNCOMMON)));

    // 拳击手套-初级:骰神赐福攻击力 +2
    public static final RegistryObject<Item> BOXING_GLOVES_LOW = registerItem("boxing_gloves_low_chip",
            () -> new BoxingGlovesChipItem(new Item.Properties()
                    .stacksTo(1)
                    .rarity(Rarity.RARE)));

    // 拳击手套-中级:骰神赐福攻击力 +4
    public static final RegistryObject<Item> BOXING_GLOVES_MEDIUM = registerItem("boxing_gloves_medium_chip",
            () -> new BoxingGlovesChipItem(new Item.Properties()
                    .stacksTo(1)
                    .rarity(Rarity.EPIC)));

    // 拳击手套-高级:骰神赐福攻击力 +8
    public static final RegistryObject<Item> BOXING_GLOVES_HIGH = registerItem("boxing_gloves_high_chip",
            () -> new BoxingGlovesChipItem(new Item.Properties()
                    .stacksTo(1)
                    .rarity(Rarity.UNCOMMON)));

    // 速度轮滑-初级:移动速度 +5%
    public static final RegistryObject<Item> SPEED_SKATES_LOW = registerItem("speed_skates_low_chip",
            () -> new SpeedSkatesChipItem(new Item.Properties()
                    .stacksTo(1)
                    .rarity(Rarity.RARE), SpeedSkatesChipItem.SPEED_LOW));

    // 速度轮滑-中级:移动速度 +15%
    public static final RegistryObject<Item> SPEED_SKATES_MEDIUM = registerItem("speed_skates_medium_chip",
            () -> new SpeedSkatesChipItem(new Item.Properties()
                    .stacksTo(1)
                    .rarity(Rarity.EPIC), SpeedSkatesChipItem.SPEED_MEDIUM));

    // 速度轮滑-高级:移动速度 +25%
    public static final RegistryObject<Item> SPEED_SKATES_HIGH = registerItem("speed_skates_high_chip",
            () -> new SpeedSkatesChipItem(new Item.Properties()
                    .stacksTo(1)
                    .rarity(Rarity.UNCOMMON), SpeedSkatesChipItem.SPEED_HIGH));

    // 摩托头盔-一般:防御力 +2(无盔甲韧性;代码折算护甲 +4)
    public static final RegistryObject<Item> MOTO_HELMET_LOW = registerItem("moto_helmet_low_chip",
            () -> new MotoHelmetChipItem(new Item.Properties()
                    .stacksTo(1)
                    .rarity(Rarity.RARE), MotoHelmetChipItem.DEFENSE_LOW, 0));

    // 摩托头盔-中级:防御力 +4(无盔甲韧性;代码折算护甲 +8)
    public static final RegistryObject<Item> MOTO_HELMET_MEDIUM = registerItem("moto_helmet_medium_chip",
            () -> new MotoHelmetChipItem(new Item.Properties()
                    .stacksTo(1)
                    .rarity(Rarity.EPIC), MotoHelmetChipItem.DEFENSE_MEDIUM, 0));

    // 摩托头盔-高级:防御力 +6,盔甲韧性 +2(仅高级拥有韧性;代码折算护甲 +12)
    public static final RegistryObject<Item> MOTO_HELMET_HIGH = registerItem("moto_helmet_high_chip",
            () -> new MotoHelmetChipItem(new Item.Properties()
                    .stacksTo(1)
                    .rarity(Rarity.UNCOMMON), MotoHelmetChipItem.DEFENSE_HIGH, MotoHelmetChipItem.TOUGHNESS_BONUS));

    // 夹心饼干-一般:最大生命值 +4
    public static final RegistryObject<Item> SANDWICH_LOW = registerItem("sandwich_low_chip",
            () -> new SandwichChipItem(new Item.Properties()
                    .stacksTo(1)
                    .rarity(Rarity.RARE), SandwichChipItem.HEALTH_LOW));

    // 夹心饼干-可口:最大生命值 +8
    public static final RegistryObject<Item> SANDWICH_MEDIUM = registerItem("sandwich_medium_chip",
            () -> new SandwichChipItem(new Item.Properties()
                    .stacksTo(1)
                    .rarity(Rarity.EPIC), SandwichChipItem.HEALTH_MEDIUM));

    // 夹心饼干-美味:最大生命值 +8;最大生命值超过 20 点时,超出部分每 4 点 +1 攻击力
    public static final RegistryObject<Item> SANDWICH_HIGH = registerItem("sandwich_high_chip",
            () -> new SandwichChipItem(new Item.Properties()
                    .stacksTo(1)
                    .rarity(Rarity.UNCOMMON), SandwichChipItem.HEALTH_HIGH));

    // 肾上腺素-一般:生命值为 50% 或更低时,攻击力/防御力 +3(史诗)
    public static final RegistryObject<Item> ADRENALINE_LOW = registerItem("adrenaline_low_chip",
            () -> new AdrenalineChipItem(new Item.Properties()
                    .stacksTo(1)
                    .rarity(Rarity.EPIC), AdrenalineChipItem.BONUS_LOW));

    // 肾上腺素-高效:生命值为 50% 或更低时,攻击力/防御力 +8;触发加成时被敌方攻击,
    // 骰点 4-5 → 50% 闪避、6 → 100% 闪避本次伤害(传奇)
    public static final RegistryObject<Item> ADRENALINE_HIGH = registerItem("adrenaline_high_chip",
            () -> new AdrenalineChipItem(new Item.Properties()
                    .stacksTo(1)
                    .rarity(Rarity.UNCOMMON), AdrenalineChipItem.BONUS_HIGH));

    // 魔法箭袋:使用过效果牌并对带标记目标造成法伤 → 施加标记并返还第一张使用的效果牌(每分钟一次)
    public static final RegistryObject<Item> MAGIC_QUIVER = registerItem("magic_quiver_chip",
            () -> new MagicQuiverChipItem(new Item.Properties()
                    .stacksTo(1)
                    .rarity(Rarity.EPIC)));

    // 缓冲盾牌:受到攻击时增加 2 点治愈与 3 星币(每 15 秒一次)
    public static final RegistryObject<Item> BUFFER_SHIELD = registerItem("buffer_shield_chip",
            () -> new BufferShieldChipItem(new Item.Properties()
                    .stacksTo(1)
                    .rarity(Rarity.RARE)));

    // 星币锤:装备时星光 +5;持有星币超过 20 枚时,每次进入骰神赐福消耗 3 星币并按持有总数 30% 提升攻击力
    public static final RegistryObject<Item> STAR_COIN_HAMMER = registerItem("star_coin_hammer_chip",
            () -> new StarCoinHammerChipItem(new Item.Properties()
                    .stacksTo(1)
                    .rarity(Rarity.UNCOMMON)));

    // 诅咒之剑:装备时始终受到青之诅咒;骰神赐福期间每击杀 1 个不少于 20 血的敌对目标攻击力 +1(上限默认 16,最大 32)
    public static final RegistryObject<Item> CURSED_SWORD = registerItem("cursed_sword_chip",
            () -> new CursedSwordChipItem(new Item.Properties()
                    .stacksTo(1)
                    .rarity(Rarity.RARE)));

    // 复仇之戟:拥有指定负面/诅咒效果时,攻击力/防御力 +6(每类只触发一次,不叠加)
    public static final RegistryObject<Item> REVENGE_HALBERD = registerItem("revenge_halberd_chip",
            () -> new RevengeHalberdChipItem(new Item.Properties()
                    .stacksTo(1)
                    .rarity(Rarity.EPIC)));

    // 贯穿之铳:伤害效果牌生效时,对敌对目标远程/魔法伤害额外增加目标防御力点数
    public static final RegistryObject<Item> PIERCING_GUN = registerItem("piercing_gun_chip",
            () -> new PiercingGunChipItem(new Item.Properties()
                    .stacksTo(1)
                    .rarity(Rarity.UNCOMMON)));

    // 可口糖果:每使用一张效果牌,治愈+1并恢复1点生命;满血使用时本轮出牌数+1(每轮一次)
    public static final RegistryObject<Item> CANDY_CHIP = registerItem("candy_chip",
            () -> new CandyChipItem(new Item.Properties()
                    .stacksTo(1)
                    .rarity(Rarity.EPIC)));

    // 友情徽章:对友方玩家施加治疗效果时,双方各获得 2 点治愈
    public static final RegistryObject<Item> FRIENDSHIP_BADGE = registerItem("friendship_badge_chip",
            () -> new FriendshipBadgeChipItem(new Item.Properties()
                    .stacksTo(1)
                    .rarity(Rarity.EPIC)));

    // 探天卫星:自动补充轨道炮;使用轨道炮后出牌数+1;轨道炮生效期间远程/魔法击杀给随机效果牌
    public static final RegistryObject<Item> SATELLITE_CHIP = registerItem("satellite_chip",
            () -> new SatelliteChipItem(new Item.Properties()
                    .stacksTo(1)
                    .rarity(Rarity.UNCOMMON)));

    // 跃迁引擎:触发指定传送后,获得 2 层充能并获得迅捷 0:10
    public static final RegistryObject<Item> WARP_ENGINE_CHIP = registerItem("warp_engine_chip",
            () -> new WarpEngineChipItem(new Item.Properties()
                    .stacksTo(1)
                    .rarity(Rarity.RARE)));

    // 能量回收器:每移动 50 米获得 1 点充能
    public static final RegistryObject<Item> ENERGY_RECYCLER = registerItem("energy_recycler_chip",
            () -> new EnergyRecyclerChipItem(new Item.Properties()
                    .stacksTo(1)
                    .rarity(Rarity.RARE)));

    // 电流剑:每 4 点充能攻击力 +1;击杀 10 个敌对目标后获得 2 点充能
    public static final RegistryObject<Item> ELECTRIC_SWORD = registerItem("electric_sword_chip",
            () -> new ElectricSwordChipItem(new Item.Properties()
                    .stacksTo(1)
                    .rarity(Rarity.RARE)));

    // 高级外设:充能 ≥ 4 时攻击力 +4;每次触发骰神赐福移除 1 层充能
    public static final RegistryObject<Item> ADVANCED_PERIPHERALS = registerItem("advanced_peripherals_chip",
            () -> new AdvancedPeripheralsChipItem(new Item.Properties()
                    .stacksTo(1)
                    .rarity(Rarity.EPIC)));

    // 永动机:触发骰神赐福时,充能 +6
    public static final RegistryObject<Item> PERPETUAL_MOTION = registerItem("perpetual_motion_chip",
            () -> new PerpetualMotionChipItem(new Item.Properties()
                    .stacksTo(1)
                    .rarity(Rarity.UNCOMMON)));

    // 电流核心:使用主动技能时充能 +1;冷却中按下主动技能键时按剩余冷却占比消耗充能并立即使冷却完成
    public static final RegistryObject<Item> CURRENT_CORE_CHIP = registerItem("current_core_chip",
            () -> new CurrentCoreChipItem(new Item.Properties()
                    .stacksTo(1)
                    .rarity(Rarity.EPIC)));

    // 大碗炖肉:骰神赐福结束后,16 格范围内所有友方目标 +1 治愈并恢复 2 点生命值
    public static final RegistryObject<Item> BIG_BOWL_STEW_CHIP = registerItem("big_bowl_stew_chip",
            () -> new BigBowlStewChipItem(new Item.Properties()
                    .stacksTo(1)
                    .rarity(Rarity.UNCOMMON)));

    // 会员推荐信:每次触发骰神赐福时,获得一张随机卡牌
    public static final RegistryObject<Item> MEMBER_RECOMMENDATION_CHIP = registerItem("member_recommendation_chip",
            () -> new MemberRecommendationChipItem(new Item.Properties()
                    .stacksTo(1)
                    .rarity(Rarity.RARE)));

    // 书签:使伤害效果牌伤害加成 +1
    public static final RegistryObject<Item> BOOKMARK_CHIP = registerItem("bookmark_chip",
            () -> new BookmarkChipItem(new Item.Properties()
                    .stacksTo(1)
                    .rarity(Rarity.RARE)));

    // 小猪存钱罐:每使用 2 张效果牌后,获得 3 星币
    public static final RegistryObject<Item> PIGGY_BANK_CHIP = registerItem("piggy_bank_chip",
            () -> new PiggyBankChipItem(new Item.Properties()
                    .stacksTo(1)
                    .rarity(Rarity.RARE)));

    // 智能手表:物品栏卡牌不足 10 张时,每击杀 1 个敌对目标获得一张随机卡牌
    public static final RegistryObject<Item> SMART_WATCH_CHIP = registerItem("smart_watch_chip",
            () -> new SmartWatchChipItem(new Item.Properties()
                    .stacksTo(1)
                    .rarity(Rarity.EPIC)));

    // 电击手套:使用效果牌时充能 +1;充能 ≥4 时使用伤害效果牌会消耗 4 层充能,使本周期内远程/魔法伤害
    // 同时命中目标 3 格范围内的其他敌对目标(每周期一次,史诗)
    public static final RegistryObject<Item> ELECTRIC_GLOVE_CHIP = registerItem("electric_glove_chip",
            () -> new ElectricGloveChipItem(new Item.Properties()
                    .stacksTo(1)
                    .rarity(Rarity.EPIC)));

    // 安全气囊:受到致命伤害时消耗 6 点充能无效化本次伤害(冷却 1:00,史诗)
    public static final RegistryObject<Item> AIRBAG_CHIP = registerItem("airbag_chip",
            () -> new AirbagChipItem(new Item.Properties()
                    .stacksTo(1)
                    .rarity(Rarity.EPIC)));

    // 电磁炮:充能 ≥6 时攻击力 +5;攻击敌对目标消耗 6 层充能,延迟 1 秒对其 3 格内敌对目标降下雷击(传奇)
    public static final RegistryObject<Item> RAILGUN_CHIP = registerItem("railgun_chip",
            () -> new RailgunChipItem(new Item.Properties()
                    .stacksTo(1)
                    .rarity(Rarity.UNCOMMON)));

    // 原初核心:每消耗 1 层充能获得 1 层赋能(每层 +1 攻击/防御,每 0:30 递减 1 层,传奇)
    public static final RegistryObject<Item> PRIMORDIAL_CORE_CHIP = registerItem("primordial_core_chip",
            () -> new PrimordialCoreChipItem(new Item.Properties()
                    .stacksTo(1)
                    .rarity(Rarity.UNCOMMON)));

    // 磨刀石:生命值为 50% 或更低时攻击力 +4、受到伤害 -2;生命值 >1 时受到的伤害不超过剩余生命值(史诗)
    public static final RegistryObject<Item> WHETSTONE_CHIP = registerItem("whetstone_chip",
            () -> new WhetstoneChipItem(new Item.Properties()
                    .stacksTo(1)
                    .rarity(Rarity.EPIC)));

    public static final RegistryObject<Item> PADMAN_SIGN = registerItem("padman_sign",
            () -> new PadmanSignItem(new Item.Properties()
                    .stacksTo(1)
                    .rarity(Rarity.RARE)));

    public static final RegistryObject<Item> FANNY_SIGN = registerItem("fanny_sign",
            () -> new FannySignItem(new Item.Properties()
                    .stacksTo(1)
                    .rarity(Rarity.EPIC)));

    public static final RegistryObject<Item> RIN_SIGN = registerItem("rin_sign",
            () -> new RinSignItem(new Item.Properties()
                    .stacksTo(1)
                    .rarity(Rarity.UNCOMMON)));

    public static final RegistryObject<Item> LIVING_PAGE = registerItem("effect_card_living_page",
            () -> new LivingPageItem(new Item.Properties()
                    .stacksTo(64)
                    .rarity(Rarity.EPIC)));

    // 占星师立牌(命名:haiqing)
    public static final RegistryObject<Item> HAIQING_SIGN = registerItem("haiqing_sign",
            () -> new HaiqingSignItem(new Item.Properties()
                    .stacksTo(1)
                    .rarity(Rarity.EPIC)));

    // 命运的指引(专属功能效果牌,击杀带虚弱印记的目标获取)
    public static final RegistryObject<Item> FATE_GUIDANCE_CARD = registerItem("effect_card_fate_guidance",
            () -> new FateGuidanceCardItem(new Item.Properties()
                    .stacksTo(64)
                    .rarity(Rarity.EPIC)));

    // 吸血鬼立牌(命名:papara):配方=黄金骰子+星盘 → 史诗
    public static final RegistryObject<Item> PAPARA_SIGN = registerItem("papara_sign",
            () -> new PaparaSignItem(new Item.Properties()
                    .stacksTo(1)
                    .rarity(Rarity.EPIC)));

    // 秘密侦探立牌(命名:bonnie):配方=下界合金骰子 → 传奇
    public static final RegistryObject<Item> BONNIE_SIGN = registerItem("bonnie_sign",
            () -> new BonnieSignItem(new Item.Properties()
                    .stacksTo(1)
                    .rarity(Rarity.UNCOMMON)));

    // === 新效果牌(治疗/互动) ===
    // 巧克力蛋糕:使用后恢复 4 点生命值。品质:青(蓝)
    public static final RegistryObject<Item> CHOCOLATE_CAKE = registerItem("effect_card_chocolate_cake",
            () -> new ChocolateCakeCardItem(new Item.Properties()
                    .stacksTo(64)
                    .rarity(Rarity.RARE)));

    // 汉堡:使用后恢复 8 点生命值
    public static final RegistryObject<Item> HAMBURGER = registerItem("effect_card_hamburger",
            () -> new HamburgerCardItem(new Item.Properties()
                    .stacksTo(64)
                    .rarity(Rarity.EPIC)));

    // 奢华大餐:治疗目标及周围 6 格内所有玩家 6 点生命值(可对自己/他人使用)
    public static final RegistryObject<Item> LUXURY_FEAST = registerItem("effect_card_luxury_feast",
            () -> new LuxuryFeastCardItem(new Item.Properties()
                    .stacksTo(64)
                    .rarity(Rarity.EPIC)));

    // 你有我有:仅能对其他玩家使用,自身与目标玩家各获得一张随机卡牌
    public static final RegistryObject<Item> YOU_HAVE_I_HAVE = registerItem("effect_card_you_have_i_have",
            () -> new YouHaveIHaveCardItem(new Item.Properties()
                    .stacksTo(64)
                    .rarity(Rarity.EPIC)));

    // 加急加快:使目标获得 迅捷 II 1:00(可对自己/他人使用)
    public static final RegistryObject<Item> EXPRESS_DELIVERY = registerItem("effect_card_express_delivery",
            () -> new ExpressDeliveryCardItem(new Item.Properties()
                    .stacksTo(64)
                    .rarity(Rarity.EPIC)));

    // 大当家立牌(命名:fen):养精蓄锐计数器 + 战斗爽主动;配方=钻石骰子+星盘 → 传奇
    public static final RegistryObject<Item> FEN_SIGN = registerItem("fen_sign",
            () -> new FenSignItem(new Item.Properties()
                    .stacksTo(1)
                    .rarity(Rarity.UNCOMMON)));

    // 骇客立牌(命名:nancy_lu):网络防火墙被动 + 远程侵入主动
    public static final RegistryObject<Item> NANCY_LU_SIGN = registerItem("nancy_lu_sign",
            () -> new NancyLuSignItem(new Item.Properties()
                    .stacksTo(1)
                    .rarity(Rarity.EPIC)));

    // 枪匠立牌(命名:moses):弱点识破 + 破绽;主动弱点反击,主动冷却 120 秒(史诗)
    public static final RegistryObject<Item> MOSES_SIGN = registerItem("moses_sign",
            () -> new MosesSignItem(new Item.Properties()
                    .stacksTo(1)
                    .rarity(Rarity.EPIC)));

    // 肉弹战车立牌(命名:pandaman,稀有):治疗/生命上限被动 + 大吃特吃主动
    public static final RegistryObject<Item> PANDAMAN_SIGN = registerItem("pandaman_sign",
            () -> new PandamanSignItem(new Item.Properties()
                    .stacksTo(1)
                    .rarity(Rarity.RARE)));

    // 游戏大师立牌(命名:ren,史诗):鼠鼠救我被动(5:00 无盾自动补「1 张随机卡牌 + 护盾」)
    // + 熊孩子特权主动(选任意玩家或自身);盾 = 5 黄心 + 抗性提升 + 1 层反击;配方=基础骰子(纸×4 + 星币×3) → 史诗
    public static final RegistryObject<Item> REN_SIGN = registerItem("ren_sign",
            () -> new RenSignItem(new Item.Properties()
                    .stacksTo(1)
                    .rarity(Rarity.EPIC)));

    // 风水师立牌(全局命名:zhao,金=传奇):被动「福祸相倚」(骰点 1 → 符卡-祸 / 6 → 符卡-福)
    // + 被动「完美帮手」(对装备大当家立牌者施白泽赐福 ⇒ 其养精蓄锐 +1 层)
    // + 主动「白泽赐福」(目标选择器,可选玩家/自身;溢出治疗转攻击力;持续到下次骰神赐福结束)
    public static final RegistryObject<Item> ZHAO_SIGN = registerItem("zhao_sign",
            () -> new ZhaoSignItem(new Item.Properties()
                    .stacksTo(1)
                    .rarity(Rarity.UNCOMMON)));

    // 符卡-福(风水师立牌专属效果牌,传奇=UNCOMMON):目标选择器(任意玩家或自身)⇒ 目标治疗 2 点 + 本出牌轮出牌数 +1;
    // 专属绑定(仅获得者可用)。命名按用户裁决保持卡牌自身命名(fu_card)。
    public static final RegistryObject<Item> FU_CARD = registerItem("fu_card",
            () -> new FuCardItem(new Item.Properties()
                    .stacksTo(64)
                    .rarity(Rarity.UNCOMMON)));

    // 符卡-祸(风水师立牌专属效果牌,传奇=UNCOMMON):目标选择器(敌对目标,含非同队玩家)⇒ 1 点真实伤害;
    // 持有 N 张时「厄运」层数 == N,每 2:00 按当前张数结算伤害。
    public static final RegistryObject<Item> HUO_CARD = registerItem("huo_card",
            () -> new HuoCardItem(new Item.Properties()
                    .stacksTo(64)
                    .rarity(Rarity.UNCOMMON)));

    public static <T extends Item> RegistryObject<T> registerItem(String name, Supplier<T> itemSupplier) {
        return ITEMS.register(name, itemSupplier);
    }

    // 判断物品栈是否为任意卡牌(战斗牌 + 效果牌;含专属牌)
    public static boolean isCardItem(ItemStack stack) {
        return stack.is(COMBAT_CARDS_TAG) || stack.is(EFFECT_CARDS_TAG);
    }
}
