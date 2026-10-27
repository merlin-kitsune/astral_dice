package com.merlinkitsune.astral_dice.event;

import com.merlinkitsune.astral_dice.AstralDiceMod;
import com.merlinkitsune.astral_dice.audio.ModSounds;
import com.merlinkitsune.astral_dice.audio.SoundPlayback;
import com.merlinkitsune.astral_dice.combat.DamageNumberAggregator;
import com.merlinkitsune.astral_dice.combat.SpellDamageContext;
import com.merlinkitsune.astral_dice.combat.SpellDamageModifier;
import com.merlinkitsune.astral_dice.combat.DiceCombatEvents;
import com.merlinkitsune.astral_dice.combat.SpellDamageRegistry;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * 法伤(远程/魔法伤害)结算主链路:判定作用域后,由注册表修饰器聚合加成并应用。
 * 作用域白名单与加成修饰器见 {@link SpellDamageRegistry};
 * 新增卡牌/筹码/立牌对法伤的作用只需注册修饰器,无需修改本类。
 *
 * <p>2026-10-27 用户裁决(方案A 折叠):加成**并进同一次伤害事件**
 * ({@code event.setAmount(event.getAmount() + (float) bonus)}),不再发嵌套真伤 hurt。
 * 折叠点 = {@link LivingIncomingDamageEvent}(平台 {@code LivingEntity#hurt} 里的
 * {@code CommonHooks.onEntityIncomingDamage},1.21.1 :1153),在护甲/抗性/保护附魔
 * ({@code actuallyHurt} :1787-1789)、魔法吸收(:1790-1792)与 {@code LivingDamageEvent.Pre}
 * (:1789)之**前** ⇒ 加成与本体**完全同源同管道**:
 * 同一条伤害实例一起过护甲(该伤害源 bypasses_armor 时不参与)、抗性、保护附魔、吸收、
 * 受击侧倍率、弱印记与筹码减伤,**只结算一次**。
 * 目的:消除抗性/保护、受击侧倍率、弱印记、筹码固定点减、黄心钳制对加成的**双重结算**,以及
 * 第三方把加成当成第二次命中的问题(N15)。1.21.1 专有的位置关系见
 * {@link #onLivingIncomingDamagePre(LivingIncomingDamageEvent)} 方法头的注释。
 */
@EventBusSubscriber(modid = AstralDiceMod.MODID)
public class DamageEffectCardHandler {

    // 2026-10-27 法伤折叠(用户裁决,见 onLivingIncomingDamagePre 的新语义):法伤加成不再发嵌套真伤 hurt,
    // 重入闸门 APPLYING_TRUE_BONUS / isApplyingTrueBonus() 已无意义,随批移除。
    // 原两个消费者(event/LinglingSignEvents 的 +5、combat/DiceCombatEvents 的狂暴反噬)同步删掉该闸门。
    // 残余清理记录:本类不再产出 true_damage 伤害源,故也不再需要"防止自己的真伤段被判成法伤"的递归保护。


    /** 活体书页命中的「重击」音效门槛:本次**完整伤害**(与 HUD 跳字同值) >= 该值即播 bighit。 */
    private static final int LIVING_PAGE_BIG_HIT_THRESHOLD = 8;

    // ⚠️ 折叠点必须在**护甲之前**那次派发上(2026-10-27 第二批裁决,取代本处更早的
    // 「对齐 1.20.1 LivingDamageEvent」口径):用户要求「加成与本体完全同管道」—— 同一条
    // 伤害实例一起过护甲/抗性/保护/吸收/受击侧倍率/筹码减伤,故折叠点只能是 LivingIncomingDamageEvent
    // (CommonHooks.onEntityIncomingDamage 派发,1.21.1 LivingEntity.java:1153,早于同一 hurt 里的
    // :1190 无敌帧差额判定、:1787-1789 护甲/抗性/保护附魔与 :1790-1792 吸收)。
    // 它与 1.20.1 侧折叠到 LivingHurtEvent(ForgeHooks.onLivingHurt,:1665,同为护甲之前)
    // 是**同一语义**,两线逐字对称(仅事件类型与 setter 名不同)。
    // KI-5「电击手套 AOE 基准跨版本」的老顾虑已不适用:电击手套改为独立延迟结算、固定 2 点伤害
    // (item/chip/ElectricGloveChipItem),全仓已无修饰器消费事件金额,分档差异不再存在。
    // `SpellDamageContext.event` 的类型随之由 LivingDamageEvent.Pre 改为 LivingIncomingDamageEvent
    // (全仓无消费者读该字段,只影响 javadoc)。
    // 音效分档拆到 {@link #onLivingDamageSound(LivingDamageEvent.Pre)}:它仍取管线终值
    // (护甲/吸收之后、LOWEST 改动之前),与本批折叠点搬家无关。
    //
    // 优先级取 HIGHEST 的理由:**必须早于**受击侧倍率与鼠鼠护盾钳制,这样加成与本体一起被乘、
    // 一起被钳 —— 这就是「同管道」的全部含义。本事件上早于我们的只有 ObsidianDiceHandler(@HIGH,
    // 黑曜石爆炸折算,改的是"已有金额×系数"、不读 bonus)⇒ 加成按最大值口径与本体一致。
    // 取消类处理器(HannaSignItem / NancyLuSignItem / NewSignEvents)都在 NORMAL 或更晚 ⇒ 它们取消时
    // 本方法已跑完;取消语义是 hurt 直接 return false(LivingEntity.java:1153),该击不落地任何伤害,
    // 折叠金额与 onHit 副作用随之作废,数值无影响。
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onLivingIncomingDamagePre(LivingIncomingDamageEvent event) {
        LivingEntity target = event.getEntity();
        if (target.level().isClientSide()) return;
        DamageSource source = event.getSource();
        // 施法者必须为玩家
        if (!(source.getEntity() instanceof Player player)) return;
        // 目标判定与骰神赐福一致
        if (!DiceCombatEvents.isBlessingTarget(target, player)) return;
        // 作用域判定(白名单 + 军火排除)
        Entity direct = source.getDirectEntity();
        if (!SpellDamageRegistry.isSpellDamage(source, direct)) return;

        SpellDamageContext ctx = new SpellDamageContext(player, target, event, source, direct);

        // 聚合加成(按注册顺序):单次遍历收集生效修饰器,避免 isActive 重复求值
        List<SpellDamageModifier> active = new ArrayList<>();
        double bonus = 0;
        for (SpellDamageModifier modifier : SpellDamageRegistry.modifiers()) {
            if (modifier.isActive(ctx)) {
                active.add(modifier);
                bonus = modifier.apply(ctx, bonus);
            }
        }

        // 应用加成并跳数字。
        // **2026-10-27 用户裁决(方案A 折叠)**:法伤加成**并进同一次伤害事件**(本处 = 护甲之前那次
        // 派发的 LivingIncomingDamageEvent),不再发嵌套 true_damage hurt。语义 = 加成与本体**完全同管道**:
        // 同一条伤害实例一起过护甲(该伤害源 bypasses_armor 时不参与)、抗性、保护附魔、吸收、
        // 受击侧倍率(EnderDiceHandler @HIGH on LivingDamageEvent.Pre)、弱印记乘算、筹码固定点数减伤
        // —— 每项**只算一次**。
        // 这正是「true_damage 与法伤链口径一致:都只绕过护甲、都吃抗性/保护」的落地方式。
        // 收益(本方案目的):一次命中只产生**一条**伤害实例 ⇒ 抗性/保护、受击侧倍率(1.10²/1.4²)、
        // 固定点减伤、黄心钳制不再对加成各算两次;第三方也不再把它当成第二次命中(N15)。
        // 历史:2026-09-14 裁决曾把加成拆成独立真伤段绕开护甲/保护;2026-10-27 第一批折叠到
        // LivingDamageEvent(护甲**之后**)⇒ 加成反而不吃抗性/保护,在高抗高保护目标上相当于被
        // 放大 4.6~13.9 倍,与用户意图不符,故按第二批裁决上移到本处(护甲**之前**)。
        // 连带清理:ThreadLocal 重入闸门 APPLYING_TRUE_BONUS / isApplyingTrueBonus() 及其两个消费者
        // (LinglingSignEvents +5、DiceCombatEvents 狂暴反噬)已无用,一并移除。
        // 2026-09-26:本处原有的「HUD 数显 = getAmount() + bonus」直发仍保持移除状态 —— 显示值**同源**,
        // 改由 combat/DamageNumberAggregator 在伤害管线终值上取,并与基础伤害累加成一个法伤总值;
        // 折叠后该终值天然含加成。
        //
        // ⚠️ 一次 hurt 只加一次(平台源码取证,勿凭印象):LivingIncomingDamageEvent 由 hurt 里的
        //    CommonHooks.onEntityIncomingDamage **唯一**派发点发出(LivingEntity.java:1153),每次 hurt
        //    只调一次;后续阶段(:1190 无敌帧差额走 actuallyHurt(amount - lastHurt)、:1203 全额)
        //    都在 already-派发之后,不会再派发一次本事件 ⇒ bonus 不会被重复叠加。
        //    残余(平台固有,如实登记):本事件在**无敌帧判定之前**派发(neo :1190 amount <= lastHurt
        //    时直接 return false),即被无敌帧吞掉的那一击也会先加上加成 —— 但该击随即被丢弃,
        //    不落地任何伤害,数值无影响(LivingDamageEvent 侧本类只剩音效读取,不再写值)。
        //
        // ⚠️ 位置关系(1.21.1 专有):骰战终值写回在 LivingDamageEvent.Pre(@NORMAL,批次A),晚于本处,
        //    且是**覆盖式**写入 ⇒ 若将来出现「骰战 + 法伤」重叠的伤害,settle 会覆盖本次折叠值
        //    (加成丢失)而不是再叠一次 —— 不会出现"加成被骰战再乘算"的风险。
        //    七咒减半(FateGuidanceCardItem @LOWEST on LivingIncomingDamageEvent)与鼠鼠护盾钳制
        //    (RenShieldManager @LOWEST on LivingDamageEvent.Pre)都在本处之后 ⇒ 口径 = 加成也算进基础值。
        if (bonus > 0) {
            // 本次命中的**基础伤害**也归入法伤组。必须显式登记:这条命中绝大多数是**原版**伤害源
            // (箭矢 arrow / 投掷物 / 其它模组法术),聚合器的类型回落认不出来。
            // 折叠后本体与加成是同一个 DamageSource / 同一个事件 ⇒ 只此一处登记,单 SPELL 槽。
            DamageNumberAggregator.tag(target, source, DamageNumberAggregator.Group.SPELL);
            event.setAmount(event.getAmount() + (float) bonus);
        }

        // 命中副作用(施加标记/定向爆破 AOE 等)
        for (SpellDamageModifier modifier : active) {
            modifier.onHit(ctx, bonus);
        }
    }

    /**
     * 活体书页命中音效(2026-10-27 折叠点搬家的配套拆分,原样搬自折叠处理器)。
     *
     * <p>为什么必须独立:折叠点搬到 {@link #onLivingIncomingDamagePre(LivingIncomingDamageEvent)}(护甲**之前**)后,
     * 若还在同一方法里顺读 {@code event.getAmount()} 分档,基准就变成「护甲前本金」,
     * {@link #LIVING_PAGE_BIG_HIT_THRESHOLD} 的重击门槛(8)会明显更容易触发。
     * 本处理器挂在 {@link LivingDamageEvent.Pre} 的 {@code NORMAL} —— 正是旧实现所在的那个桶:
     * 护甲/韧性与抗性/保护附魔已结算(平台 :1787-1789,派发点 :1789),又早于 {@code LOWEST} 的
     * 筹码减伤链 ⇒ 取值与旧实现**完全一致**,也与 HUD 跳字(聚合器取管线终值)同源。
     *
     * <p>三个前置闸门逐条复刻折叠处理器(施法者是玩家 / 赐福目标 / 法伤作用域),不得省略:
     * 折叠处理器在护甲之前、@HIGHEST,取消类处理器(event/NewSignEvents 的免疫 cancel)在它之后,
     * 所以「事件还能走到这里」并不等于「旧实现会放音效」—— 只有闸门复刻才能保证触发面不变。
     */
    @SubscribeEvent(priority = EventPriority.NORMAL)
    public static void onLivingDamageSound(LivingDamageEvent.Pre event) {
        LivingEntity target = event.getEntity();
        if (target.level().isClientSide()) return;
        DamageSource source = event.getSource();
        if (!(source.getEntity() instanceof Player player)) return;
        if (!DiceCombatEvents.isBlessingTarget(target, player)) return;
        if (!SpellDamageRegistry.isSpellDamage(source, source.getDirectEntity())) return;

        // 活体书页命中音效:**按本次命中的完整伤害分档**,取值与 HUD 跳字同源。
        // ⚠️ 本处读到的 {@code getNewDamage()} 已含折叠进来的法伤加成(加成在护甲之前就并进同一条
        //    伤害实例),故**不得**再 + bonus —— 再加一次就是双算,会把普通命中误判成重击。
        // 判据 = 伤害类型是 astral_dice:card_spell,**且**不是四张伤害效果牌自己的 FX 伤害
        // (2026-10-07 修复 KI-G4:card_spell 不再只有活体书页一条产出路径 —— 四张牌重做后它们也走
        //  ModDamageTypes.cardSpell,且各自已播命中音,见 combat/DamageCardFxScheduler)。
        if (source.is(com.merlinkitsune.astral_dice.damage.ModDamageTypes.CARD_SPELL)
                && !com.merlinkitsune.astral_dice.combat.DamageCardFxScheduler.isApplyingCardFxDamage()) {
            // neo 的 {@code LivingDamageEvent.Pre} 没有 getAmount():读数与旧实现同一口径,取
            // {@code getNewDamage()}(护甲/抗性/保护之后、吸收之前的值;旧实现即如此)。
            int dealt = (int) Math.round(event.getNewDamage());
            SoundPlayback.playAt(target.level(), target.getX(), target.getY(), target.getZ(),
                    dealt >= LIVING_PAGE_BIG_HIT_THRESHOLD
                            ? ModSounds.DAMAGE_EFFECT_CARD_BIGHIT.get()
                            : ModSounds.DAMAGE_EFFECT_CARD_HIT.get());
        }
    }

    // ⚠️ 本类原有的 sendBonusDamageNumber 私有帮助方法已于 2026-09-26 移除:跳数字的唯一出口是
    // combat/DamageNumberAggregator(取伤害管线终值 + 按组别聚合),各调用点自行发包会被它在
    // 客户端单槽覆盖(见该类的类头)。
}