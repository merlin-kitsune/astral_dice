package com.merlinkitsune.astral_dice.effect;

import com.merlinkitsune.astral_dice.AstralDiceMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
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

    // 隐匿(秘密侦探立牌「调查阶段」;2026-09-28 用户裁决:取代原版「隐身」):阻止怪物索敌;
    // 玩家攻击怪物 ⇒ 解除,并在骰战中追加目标「标记」层数的伤害,直到调查阶段增益结束。
    // 图标沿用原版隐身图标(assets/astral_dice/textures/mob_effect/concealment.png)。
    public static final RegistryObject<MobEffect> CONCEALMENT =
            EFFECTS.register("concealment", ConcealmentEffect::new);

    // 治愈:显示当前治愈点数(等级=层数,时长=距下次结算);由史莱姆立牌等维护
    public static final RegistryObject<MobEffect> HEALING =
            EFFECTS.register("healing", HealingEffect::new);

    // 美工刀-初级状态:装备且满血时显示(加成生效中)
    public static final RegistryObject<MobEffect> CUTTER_READY =
            EFFECTS.register("cutter_ready", () -> new CutterReadyEffect(0xE0E0E0));

    // 美工刀-锋利状态:装备且满血时显示(加成生效中)
    public static final RegistryObject<MobEffect> CUTTER_BLADE_READY =
            EFFECTS.register("cutter_blade_ready", () -> new CutterReadyEffect(0x4FC3F7));

    // 手电筒-强光状态:佩戴筹码、处于骰神赐福状态且星光 ≥ 4(确有加伤)时显示(加成生效中)
    public static final RegistryObject<MobEffect> FLASHLIGHT_READY =
            EFFECTS.register("flashlight_ready", () -> new FlashlightReadyEffect(0xFFE082));

    // ── 伤害增加型筹码的「就位 / 生效中」指示器（2026-10-01 用户裁决）──
    // 口径：只要加成**可生效**就常驻显示，时长一律无限（不走倒计时）；
    //       显示条件由 PlayerTickEvents#updateChipBonusIndicators 每 tick 维护；
    //       图标 = images/<筹码名>.png 的逐字节副本（与既有状态图标同规格）。
    // 磨刀石：低血（≤50%）时攻击 +4 / 受伤 -2 生效中
    public static final RegistryObject<MobEffect> WHETSTONE_READY =
            EFFECTS.register("whetstone_ready", () -> new ChipReadyEffect(0xCFD8DC));

    // 肾上腺素-一般：低血（≤50%）时攻防 +3 生效中
    public static final RegistryObject<MobEffect> ADRENALINE_READY =
            EFFECTS.register("adrenaline_ready", () -> new ChipReadyEffect(0xA5D6A7));

    // 肾上腺素-高效：低血（≤50%）时攻防 +8 生效中
    public static final RegistryObject<MobEffect> ADRENALINE_HIGH_READY =
            EFFECTS.register("adrenaline_high_ready", () -> new ChipReadyEffect(0x69F0AE));

    // 诅咒之剑：已累计攻击力加成（>0）时显示
    public static final RegistryObject<MobEffect> CURSED_SWORD_READY =
            EFFECTS.register("cursed_sword_ready", () -> new ChipReadyEffect(0xB39DDB));

    // 电流剑：充能 ≥ 4（攻击力 +1 生效）时显示
    public static final RegistryObject<MobEffect> ELECTRIC_SWORD_READY =
            EFFECTS.register("electric_sword_ready", () -> new ChipReadyEffect(0xFFF176));

    // 电磁炮：充能 ≥ 6（攻击力 +5 生效）时显示
    public static final RegistryObject<MobEffect> RAILGUN_READY =
            EFFECTS.register("railgun_ready", () -> new ChipReadyEffect(0x81D4FA));

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

    // 青之诅咒:护甲值 -20%(向下取整),盔甲韧性 -50%;暂未配置触发条件
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
     * <p>常驻效果({@link MobEffectInstance#INFINITE_DURATION}),由立牌 tick 每 tick {@code refresh}、
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

    // 推理时间(怪力侦探立牌 sherry 专属资源):层数真值在附件(死亡保留名单,死亡不清),
    // 本效果只做 HUD 镜像(层数 = amplifier + 1,图标 = 立牌同图)。
    public static final RegistryObject<MobEffect> SHERRY_REASONING =
            EFFECTS.register("sherry_reasoning", SherryReasoningEffect::new);

    // 人偶制作(人偶师立牌 hanna 专属资源):层数真值在附件(不跨死亡),本效果只做 HUD 镜像
    // (层数 = amplifier + 1,上限 HannaSignItem.MAX_CRAFT = 7;图标 images/人偶制作.png)。
    public static final RegistryObject<MobEffect> HANNA_DOLL_CRAFT =
            EFFECTS.register("hanna_doll_craft", HannaDollCraftEffect::new);

    // 人偶完成(人偶师立牌 hanna):「人偶制作」满 7 层后**归零转换**而来的常驻状态(用户 2026-09-21 裁决);
    // 无限时长、不登记 EffectTimerGuard;图标 images/人偶完成.png。
    public static final RegistryObject<MobEffect> HANNA_DOLL_COMPLETE =
            EFFECTS.register("hanna_doll_complete", HannaDollCompleteEffect::new);

    // 魔女漂浮(人偶师立牌 hanna 主动):有限时长 1:00 = 1200 tick,移速 +20%(ADD_MULTIPLIED_TOTAL);
    // 掉落免疫 / 近战闪避 / 禁用末影珍珠三条语义在 HannaSignItem 的事件里;
    // 图标与立牌本体同图(textures/mob_effect/hanna_float.png 逐字节复制)。
    public static final RegistryObject<MobEffect> HANNA_FLOAT =
            EFFECTS.register("hanna_float", HannaFloatEffect::new);

    /**
     * 「精准打击」(机械师立牌 megas 的轨道轰炸层数真值效果):每层使该目标受到的轨道轰炸伤害 +1,
     * 永久持续直到目标死亡(时长 MobEffectInstance.INFINITE_DURATION,由原版实体生命周期保证移除);
     * 层数 = amplifier + 1 = 该目标被轨道轰炸命中的次数。作用对象为 LivingEntity(怪物)。
     * 图标 = {@code images/精准打击.png}(实装路径 {@code textures/mob_effect/precision_strike.png})。
     *
     * <p>⚠️ 1.20.1 是 {@code RegistryObject} ⇒ 全部引用处必须 {@code .get()}。
     */
    public static final RegistryObject<MobEffect> PRECISION_STRIKE =
            EFFECTS.register("precision_strike", PrecisionStrikeEffect::new);

    /**
     * 「书页射程」(调查员立牌 rin 主动追加的状态;2026-10-03 用户裁决):持有期间**活体书页的目标选择
     * 距离 +50%**,有限时长 2:00({@link RinPageRangeEffect#DURATION_TICKS})。无属性修饰符、无粒子;
     * 加成唯一读取点 = {@code target/SelectorRangeModifiers}(与探天卫星筹码的常驻 +50% 相加,上限 64 格)。
     * 图标 = {@code textures/mob_effect/rin_page_range.png} = {@code images/调查员立牌.png}
     * ({@code textures/item/rin_sign.png} 同一张)**逐字节复制**(复用调查员本身图标,不自创)。
     */
    public static final RegistryObject<MobEffect> RIN_PAGE_RANGE =
            EFFECTS.register("rin_page_range", RinPageRangeEffect::new);

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
