package com.merlinkitsune.astral_dice.effect;

import com.merlinkitsune.astral_dice.AstralDiceMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.effect.MobEffect;
import net.minecraftforge.registries.RegistryObject;
import net.minecraftforge.registries.DeferredRegister;

public class ModEffects {
    public static final DeferredRegister<MobEffect> EFFECTS =
            DeferredRegister.create(Registries.MOB_EFFECT, AstralDiceMod.MODID);

    public static final RegistryObject<MobEffect> KING_POWER =
            EFFECTS.register("king_power", KingPowerEffect::new);

    public static final RegistryObject<MobEffect> BERSERK =
            EFFECTS.register("berserk", BerserkEffect::new);

    public static final RegistryObject<MobEffect> UNWAVERING =
            EFFECTS.register("unwavering", UnwaveringEffect::new);

    public static final RegistryObject<MobEffect> MARKED =
            EFFECTS.register("marked", MarkedEffect::new);

    public static final RegistryObject<MobEffect> DICE_BLESSING =
            EFFECTS.register("dice_blessing", DiceBlessingEffect::new);

    // 爆发(护法立牌 misaki 主动):持续 60 秒
    public static final RegistryObject<MobEffect> MISAKI_BURST =
            EFFECTS.register("misaki_burst", MisakiBurstEffect::new);

    public static final RegistryObject<MobEffect> LIVING_PAGE =
            EFFECTS.register("living_page", LivingPageEffect::new);

    // 清扫(扫地机立牌 jasmine 主动):迅捷+护甲惩罚,持续 1 分钟
    public static final RegistryObject<MobEffect> JASMINE_SWEEP =
            EFFECTS.register("jasmine_sweep", JasmineSweepEffect::new);

    // 虚弱印记:目标受到的任意伤害 +10%(占星师立牌主动施加)
    public static final RegistryObject<MobEffect> WEAK_MARK =
            EFFECTS.register("weak_mark", WeakMarkEffect::new);

    // 命运的指引:对带有虚弱印记的目标额外 +20% 伤害(持续 5:00)
    public static final RegistryObject<MobEffect> FATE_GUIDANCE =
            EFFECTS.register("fate_guidance", FateGuidanceEffect::new);

    // 嘬一口(吸血鬼立牌 papara 主动):攻击与受伤时按骰神赐福最终伤害/受到伤害的一半恢复生命
    public static final RegistryObject<MobEffect> PAPARA_BITE =
            EFFECTS.register("papara_bite", PaparaBiteEffect::new);

    // 隐匿调查(秘密侦探立牌主动):永久存在于目标身上直到死亡;击杀后触发调查阶段事件
    public static final RegistryObject<MobEffect> UNDERCOVER_INVESTIGATION =
            EFFECTS.register("undercover_investigation", UndercoverInvestigationEffect::new);

    // 调查阶段增益:由调查阶段事件触发,amplifier 表示阶段(1=I,2=II,3=III,4=真相揭露;阶段 I 仅提示无攻击加成)
    public static final RegistryObject<MobEffect> INVESTIGATION_BONUS =
            EFFECTS.register("investigation_bonus", InvestigationBonusEffect::new);

    // 占星师立牌主动待命:主动已激活,攻击目标后施加"虚弱印记"
    public static final RegistryObject<MobEffect> HAIQING_READY =
            EFFECTS.register("haiqing_ready", () -> new ReadyEffect(0x4B0082));

    // 秘密侦探立牌主动待命:主动已激活,攻击目标后施加"隐匿调查"
    public static final RegistryObject<MobEffect> BONNIE_READY =
            EFFECTS.register("bonnie_ready", () -> new ReadyEffect(0x8B4513));

    // 治愈:显示当前治愈点数(等级=层数,时长=距下次结算);由史莱姆立牌等维护
    public static final RegistryObject<MobEffect> HEALING =
            EFFECTS.register("healing", HealingEffect::new);

    // 美工刀-初级状态:装备且满血时显示(加成生效中)
    public static final RegistryObject<MobEffect> CUTTER_READY =
            EFFECTS.register("cutter_ready", () -> new CutterReadyEffect(0xE0E0E0));

    // 美工刀-锋利状态:装备且满血时显示(加成生效中)
    public static final RegistryObject<MobEffect> CUTTER_BLADE_READY =
            EFFECTS.register("cutter_blade_ready", () -> new CutterReadyEffect(0x4FC3F7));

    // 对怪激光:远程和魔法伤害 +4
    public static final RegistryObject<MobEffect> MONSTER_LASER =
            EFFECTS.register("monster_laser", () -> new RangedBoostEffect(0xFF3D3D));

    // 对怪板砖:远程和魔法伤害 +6
    public static final RegistryObject<MobEffect> MONSTER_BRICK =
            EFFECTS.register("monster_brick", () -> new RangedBoostEffect(0x8B4513));

    // 轨道炮:远程和魔法伤害 +8
    public static final RegistryObject<MobEffect> ORBITAL_STRIKE =
            EFFECTS.register("orbital_strike", () -> new RangedBoostEffect(0x7B68EE));

    // 定向爆破:远程和魔法伤害 +5,并对目标周围 6 格敌对目标造成同样伤害
    public static final RegistryObject<MobEffect> DIRECTIONAL_BLAST =
            EFFECTS.register("directional_blast", () -> new RangedBoostEffect(0xFF8C00));

    // 忍者立牌(komachi):出牌计数(等级 = 当前第几张效果牌)
    public static final RegistryObject<MobEffect> KOMACHI_COUNT =
            EFFECTS.register("komachi_count", () -> new CounterEffect(0x9C27B0));

    // 魔法秘典:出牌计数(等级 = 当前第几张效果牌)
    public static final RegistryObject<MobEffect> MAGIC_TOME_COUNT =
            EFFECTS.register("magic_tome_count", () -> new CounterEffect(0x7B68EE));

    // 战斗爽(大当家立牌 fen 主动):攻击力 +3,持续 1:00
    public static final RegistryObject<MobEffect> FEN_FRENZY =
            EFFECTS.register("fen_frenzy", () -> new FenFrenzyEffect(0xFF4500));

    // 青之诅咒:护甲值 -20%(向下取整),盔甲韧性 -100%(归 0);暂未配置触发条件
    public static final RegistryObject<MobEffect> BLUE_CURSE =
            EFFECTS.register("blue_curse", BlueCurseEffect::new);

    // 骇客立牌主动"远程侵入":攻击力加成(数值由附件提供)
    public static final RegistryObject<MobEffect> NANCY_LU_HACK =
            EFFECTS.register("nancy_lu_hack", NancyLuHackEffect::new);

    // 复仇之戟:负面/诅咒效果触发攻击/防御加成时显示的标记效果(图标=复仇之戟自身图标)
    public static final RegistryObject<MobEffect> REVENGE_HALBERD =
            EFFECTS.register("revenge_halberd", RevengeHalberdEffect::new);

    // 充能(流派资源):层数 = amplifier+1;拥有至少 1 层时提供固定流派加成(防御+1、冷却-20%)
    public static final RegistryObject<MobEffect> CHARGE =
            EFFECTS.register("charge", ChargeEffect::new);


    // 赋能(原初核心筹码转换而来的资源):层数 = amplifier+1;每层攻击/防御 +1,每 0:30 递减 1 层
    public static final RegistryObject<MobEffect> EMPOWER =
            EFFECTS.register("empower", EmpowerEffect::new);


    // 弱点识破(枪匠立牌 Moses):玩家增益,层数 = amplifier+1;每层攻击/防御+1、骰点最低数+1
    public static final RegistryObject<MobEffect> WEAKNESS_REVEAL =
            EFFECTS.register("weakness_reveal", WeaknessRevealEffect::new);

    // 破绽(枪匠立牌 Moses 主动):目标减益,持续 2:00;该目标与枪匠交战时骰点只能为 0 且会被闪避
    public static final RegistryObject<MobEffect> MOSES_BROKEN =
            EFFECTS.register("moses_broken", MosesBrokenEffect::new);

    // 枪匠立牌主动待命:主动已激活,攻击敌对目标后施加"破绽"
    public static final RegistryObject<MobEffect> MOSES_READY =
            EFFECTS.register("moses_ready", () -> new ReadyEffect(0x8B5A2B));

    // 嘲讽(肉弹战车立牌 pandaman 主动):目标只能攻击对其施加嘲讽的玩家
    public static final RegistryObject<MobEffect> PANDAMAN_TAUNT =
            EFFECTS.register("pandaman_taunt", PandamanTauntEffect::new);
}
