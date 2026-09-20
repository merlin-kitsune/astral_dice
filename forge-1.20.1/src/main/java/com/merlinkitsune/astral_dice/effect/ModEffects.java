package com.merlinkitsune.astral_dice.effect;

import com.merlinkitsune.astral_dice.AstralDiceMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.effect.MobEffect;
import net.minecraftforge.registries.RegistryObject;
import net.minecraftforge.registries.DeferredRegister;

import com.merlinkitsune.starenginelib.effect.BerserkEffect;
import com.merlinkitsune.starenginelib.effect.CounterEffect;
import com.merlinkitsune.starenginelib.effect.CutterReadyEffect;
import com.merlinkitsune.starenginelib.effect.DiceBlessingEffect;
import com.merlinkitsune.starenginelib.effect.FateGuidanceEffect;
import com.merlinkitsune.starenginelib.effect.HealingEffect;
import com.merlinkitsune.starenginelib.effect.InvestigationBonusEffect;
import com.merlinkitsune.starenginelib.effect.KingPowerEffect;
import com.merlinkitsune.starenginelib.effect.LivingPageEffect;
import com.merlinkitsune.starenginelib.effect.MarkedEffect;
import com.merlinkitsune.starenginelib.effect.MisakiBurstEffect;
import com.merlinkitsune.starenginelib.effect.MosesBrokenEffect;
import com.merlinkitsune.starenginelib.effect.NancyLuHackEffect;
import com.merlinkitsune.starenginelib.effect.PandamanTauntEffect;
import com.merlinkitsune.starenginelib.effect.PaparaBiteEffect;
import com.merlinkitsune.starenginelib.effect.RevengeHalberdEffect;
import com.merlinkitsune.starenginelib.effect.UndercoverInvestigationEffect;
import com.merlinkitsune.starenginelib.effect.WeakMarkEffect;
import java.util.Collection;

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

    // 汲取(吸血鬼立牌 papara 主动):攻击与受伤时按骰神赐福最终伤害/受到伤害的一半恢复生命
    public static final RegistryObject<MobEffect> PAPARA_BITE =
            EFFECTS.register("papara_bite", PaparaBiteEffect::new);

    // 隐匿调查(秘密侦探立牌主动):永久存在于目标身上直到死亡;击杀后触发调查阶段事件
    public static final RegistryObject<MobEffect> UNDERCOVER_INVESTIGATION =
            EFFECTS.register("undercover_investigation", UndercoverInvestigationEffect::new);

    // 调查阶段增益:由调查阶段事件触发,amplifier 表示阶段(1=I,2=II,3=III,4=真相揭露;阶段 I 仅提示无攻击加成)
    public static final RegistryObject<MobEffect> INVESTIGATION_BONUS =
            EFFECTS.register("investigation_bonus", InvestigationBonusEffect::new);

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

    // 嘲讽(肉弹战车立牌 pandaman 主动):目标只能攻击对其施加嘲讽的玩家
    public static final RegistryObject<MobEffect> PANDAMAN_TAUNT =
            EFFECTS.register("pandaman_taunt", PandamanTauntEffect::new);

    // 鼠鼠护盾(游戏大师立牌 ren):5 黄心(10 点吸收)+ 抗性提升;黄心被打空即清空。
    // 本效果同时是「是否持有护盾」的唯一真值(客户端球形渲染以原生效果同步为条件源)。
    // 平台差异:1.20.1 无 MAX_ABSORPTION 属性、吸收值不被钳制 ⇒ 本类不含修饰器(1.21.1 侧必须带)。
    public static final RegistryObject<MobEffect> REN_SHIELD =
            EFFECTS.register("ren_shield", RenShieldEffect::new);

    /** 「反击」:鼠鼠护盾那 1 层一次性反击的可见载体(层数镜像到 HUD 图标;图标 = images/反击.png) */
    public static final RegistryObject<MobEffect> REN_COUNTER =
            EFFECTS.register("ren_counter", RenCounterEffect::new);

    // 白泽赐福(风水师立牌 zhao 主动):溢出治疗转攻击力的有效期载体;
    // 无限时长施加,由「下次骰神赐福结束」驱动移除(两种分支见 ZhaoSignItem)。
    // 图标按需求复用风水师立牌贴图(images/风水师立牌.png → textures/mob_effect/zhao_blessing.png)。
    public static final RegistryObject<MobEffect> ZHAO_BLESSING =
            EFFECTS.register("zhao_blessing", ZhaoBlessingEffect::new);

    // 厄运:持有「符卡-祸」的层数镜像(层数 = 张数);每 2:00 按当前张数结算一次伤害。
    // 图标 = images/厄运.png(实装路径 textures/mob_effect/misfortune.png)。
    public static final RegistryObject<MobEffect> MISFORTUNE =
            EFFECTS.register("misfortune", MisfortuneEffect::new);

    // 「降神」(教主立牌 teru 主动):施加在被指定目标身上的状态载体,持续到**该目标的下一次骰神赐福结束**
    // 才移除(玩家级 tick 下降沿判定,见 TeruSignItem#tick)。给施法者的 50% 攻防加成与狐光攻击基数另存附件。
    // 图标 = images/教主立牌.png(实装路径 textures/mob_effect/teru_descent.png)。
    public static final RegistryObject<MobEffect> TERU_DESCENT =
            EFFECTS.register("teru_descent", TeruDescentEffect::new);

    // 「狐光」(教主立牌 teru 的资源层数镜像):层数 == 施法者附件 TERU_HUGUANG_LAYERS(上限 20);
    // 归 0 即移除。增层带两条防刷守卫(拾取不计层 + 装备按历史水位去重),见 TeruSignItem。
    // 图标 = images/狐光.png(实装路径 textures/mob_effect/teru_huguang.png)。
    public static final RegistryObject<MobEffect> TERU_HUGUANG =
            EFFECTS.register("teru_huguang", HuguangEffect::new);

    /**
     * 「女王特权」(绿洲女王立牌 nardis 主动):**有限时长 3:00(3600 tick)** 的状态载体。
     * 它同时是「临时牌是否仍在有效期」的**唯一真值**(玩家级 tick 自检:有临时牌但无本效果 ⇒ 清空),
     * 并直接充当 HUD 计时器与图标(`showIcon=true`,图标 = 立牌贴图,见 {@link NardisPrivilegeEffect})。
     *
     * <p>⚠️ 1.20.1 是 {@code RegistryObject} ⇒ 全部引用处必须 {@code .get()}。
     */
    public static final RegistryObject<MobEffect> NARDIS_PRIVILEGE =
            EFFECTS.register("nardis_privilege", NardisPrivilegeEffect::new);

    /**
     * 「真龙形态」(蛟龙立牌 mamushi 的锁存态载体,2026-09-27)。
     *
     * <p>常驻效果({@code Integer.MAX_VALUE}),由立牌 tick 每 tick {@code refresh}、
     * 判据不成立时 {@code remove} —— 与 {@code zhao_blessing} 同一写法。
     * **不登记 {@code EffectTimerGuard}**:它没有自己的倒计时,移除时机完全由层数与佩戴判定驱动
     * (规格 §1 效果表 + §2.2)。图标 = {@code images/蛟龙立牌.png}
     * (实装路径 {@code textures/mob_effect/mamushi_dragon.png},与立牌贴图逐字节相同)。
     *
     * <p>⚠️ 1.20.1 是 {@code RegistryObject} ⇒ 全部引用处必须 {@code .get()}。
     */
    public static final RegistryObject<MobEffect> MAMUSHI_DRAGON =
            EFFECTS.register("mamushi_dragon", MamushiDragonEffect::new);

    /**
     * 「破防」(龙之咆哮命中):{@code HARMFUL},携带 {@code ARMOR -8}(= 减 4 点防御,1 防御 = 2 护甲)。
     * 时长 {@code DragonRoarBreakEffect.DURATION_TICKS} = 1:00,重复命中**刷新时长、不叠层**。
     * 图标 = {@code images/龙之咆哮.png}(实装路径 {@code textures/mob_effect/dragon_roar_break.png},
     * 与战斗牌贴图逐字节相同)。
     */
    public static final RegistryObject<MobEffect> DRAGON_ROAR_BREAK =
            EFFECTS.register("dragon_roar_break", DragonRoarBreakEffect::new);

    /**
     * 本模组已注册的全部效果的**只读**视图(调试命令 {@code /astralparty cleareffect} 用)。
     *
     * <p>直接派生自 {@link #EFFECTS} 的注册条目视图——Forge 的 {@code getEntries()} 返回
     * {@code Collections.unmodifiableSet(entries.keySet())} 的**活视图**,故新增效果会自动纳入,
     * 不存在「忘记往清单里补一个」的漂移风险;同时**不改变任何既有注册语义**
     * (不新增、不重排、不延迟任何注册调用)。
     */
    public static final Collection<RegistryObject<MobEffect>> ALL = EFFECTS.getEntries();
}
