package com.merlinkitsune.astral_dice.event;

import com.merlinkitsune.astral_dice.AstralDiceMod;
import com.merlinkitsune.astral_dice.audio.ModSounds;
import com.merlinkitsune.astral_dice.audio.SoundPlayback;
import com.merlinkitsune.astral_dice.combat.SpellDamageContext;
import com.merlinkitsune.astral_dice.item.RenShieldManager;
import com.merlinkitsune.astral_dice.combat.SpellDamageModifier;
import com.merlinkitsune.astral_dice.combat.SpellDamageRegistry;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.CombatRules;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import com.merlinkitsune.astral_dice.platform.event.SubscribeEvent;
import com.merlinkitsune.astral_dice.platform.event.EventPriority;
import com.merlinkitsune.astral_dice.platform.event.entity.living.LivingDamageEvent;
import com.merlinkitsune.astral_dice.combat.DamageNumberAggregator;

/**
 * 法伤(远程/魔法伤害)结算主链路:判定作用域后,由注册表修饰器聚合加成并应用。
 * 作用域白名单与加成修饰器见 {@link SpellDamageRegistry};
 * 新增卡牌/筹码/立牌对法伤的作用只需注册修饰器,无需修改本类。
 *
 * <p>2026-11-07 与 neoforge-1.21.1 对齐(用户裁决「方案A 折叠」):法伤加成**并进同一次伤害
 * 事件**({@code event.setAmount(...)}),不再发嵌套真伤 hurt。折叠点 = 本线
 * {@link com.merlinkitsune.astral_dice.platform.event.entity.living.LivingDamageEvent},
 * 由 Puzzles Lib 8.1.33 的 {@code LivingEntityFabricMixin#actuallyHurt}
 * ({@code method_6074}) HEAD 注入 + {@code @ModifyVariable} 写回
 * (桥接见 platform/PuzzlesBridges.java:99-110);{@code actuallyHurt} 方法体首行才是
 * {@code getDamageAfterArmorAbsorb} / 魔抗 / 吸收 ⇒ **载荷口径 = 护甲之前**,与 neo 的
 * {@code LivingIncomingDamageEvent} 同口径,故折叠函数可逐行等价移植。
 *
 * <p>与 neo 的时机差异仍是 B27 已登记残余:本线的折叠发生在 {@code hurt()} 的无敌帧差额
 * 分摊**之后**,也晚于盾牌格挡 / 冰冻 / 破甲头盔,故被无敌帧吞掉的那一击不产生加成数值。
 * 2026-11-07 二次复核又补了两处与时序相关的对齐:受击侧倍率已在更早的 {@code LivingHurtEvent@HIGH}
 * 乘进实参 ⇒ 折叠改为「倍率前空间求解、再乘回同一倍率」({@link #foldBonusPreFactor});鼠鼠护盾的钳制
 * 在更早的 {@code LivingHurtEvent@LOWEST} ⇒ 折叠后按剩余黄心重钳一次,不再漏红心。
 */
public class DamageEffectCardHandler {

    /** 活体书页命中的「重击」音效门槛:本次**完整伤害**(与 HUD 跳字同值) >= 该值即播 bighit。 */
    private static final int LIVING_PAGE_BIG_HIT_THRESHOLD = 8;

    /**
     * 折叠时为「加成绕过护甲」做预补偿(neo 2026-10-27 用户裁决;2026-11-07 逐行等价移植)。
     *
     * <p>折叠把加成并进本体,而护甲对整条伤害实例统一结算;若本体源要吃护甲,加成也会被削。
     * 这里在护甲**之前**求解 x,使平台的护甲公式在 x 上得到「本体护甲后值 + bonus」 ⇒ 护甲后的
     * 总量 = 本体护甲后值 + bonus,加成等效绕过护甲;抗性/保护/吸收/受击倍率仍对整段作用**一次**。
     *
     * <p>⚠️ 本线载荷口径:事件由 {@code LivingEntityFabricMixin#actuallyHurt} HEAD 派发
     * (platform/PuzzlesBridges.java:99-110),入参与写回都是 {@code actuallyHurt} 的**护甲前**
     * 参数(该方法体首行才调 {@code getDamageAfterArmorAbsorb}) ⇒ 与 neo 的
     * {@code LivingIncomingDamageEvent} 同口径,故本函数「base = 护甲前值」的假设成立。
     *
     * <p>源自身带 {@code bypasses_armor}(本仓全部 {@code astral_dice:card_spell} 法伤都在内)或
     * 目标没有护甲时直接相加、不做补偿。求解用二分:护甲公式对伤害单调不减(f(x) >= 0.2x,
     * 平台实测 {@code CombatRules#getDamageAfterAbsorb} = damage * (1 - clamp(armor - damage/f,
     * 0.2*armor, 20) / 25),f = 2 + toughness/4),故以 [0, 8*want] 为区间迭代 40 次足够
     * 收敛到 float 精度。
     */
    private static float foldBonusBypassingArmor(LivingEntity target, DamageSource source, float base, double bonus) {
        return foldBonusBypassingArmor(target, source, base, bonus, 1.0);
    }

    /**
     * 折叠加成,并把结果再乘一次受击侧倍率(2026-11-07 二次复核 M1 订正)。
     *
     * <p>目标值 = {@code (armor(base) + bonus) × multiplier}:加成等效绕过护甲,随后与本体一起
     * 吃同一份倍率 —— 这正是 neo 的顺序({@code LivingDamageEvent.Pre} 在护甲/魔抗之后、吸收之前,
     * 倍率乘的是折叠后的整份金额)。本线倍率在护甲之前生效,无法把乘法的「位置」搬过去,只能把
     * **同样的数值结果**解出来:求 y 使 {@code armor(y) = 目标值},平台随后对 y 做护甲即成。
     *
     * <p>源自带 {@code bypasses_armor} 或目标无护甲时护甲是恒等映射,直接 {@code (base+bonus)×倍率}。
     */
    private static float foldBonusBypassingArmor(LivingEntity target, DamageSource source, float base,
            double bonus, double multiplier) {
        if (bonus <= 0.0) return (float) (base * multiplier);
        if (source.is(DamageTypeTags.BYPASSES_ARMOR)) return (float) ((base + bonus) * multiplier);
        float armor = (float) target.getArmorValue();
        if (armor <= 0.0F) return (float) ((base + bonus) * multiplier);
        float toughness = (float) target.getAttributeValue(Attributes.ARMOR_TOUGHNESS);
        float want = (float) ((CombatRules.getDamageAfterAbsorb(target, base, source, armor, toughness)
                + (float) bonus) * multiplier);
        if (want <= 0.0F) return 0.0F;
        float lo = 0.0F;
        float hi = want * 8.0F;
        for (int i = 0; i < 40; i++) {
            float mid = (lo + hi) * 0.5F;
            if (CombatRules.getDamageAfterAbsorb(target, mid, source, armor, toughness) < want) {
                lo = mid;
            } else {
                hi = mid;
            }
        }
        return hi;
    }

    // ⚠️ 事件时机(B27 订正本线事实;2026-09-15 用户裁决修补 KI-5「电击手套 AOE 基准跨版本」):
    // 本处挂在 LivingDamageEvent,与 1.21.1 的 LivingDamageEvent.Pre 同名对应。但本次改挂
    // **不会**让本线拿到「护甲之后」的值:该事件由 Puzzles Lib 8.1.33 的
    // fuzs.puzzleslib.mixin.LivingEntityFabricMixin#actuallyHurt(@Inject(at=@At("HEAD"))) 在
    // actuallyHurt 偏移 0 派发,而护甲(偏移 12 getDamageAfterArmorAbsorb)、魔抗(19
    // getDamageAfterMagicAbsorb)、吸收(25-45)全在方法体内、注入点之后 ⇒ 载荷仍是护甲与吸收
    // **之前**的原始入参。桥接处见 platform/PuzzlesBridges.java:99-110。
    // 残余(平台固有,已登记 B27 报告):本线基准比 1.21.1 Pre 还早一层(护甲前),
    // 带甲或带吸收(黄心)的目标都会有差;本处只改挂点,不引入换算。
    @SubscribeEvent(priority = EventPriority.NORMAL)
    public static void onLivingDamagePre(LivingDamageEvent event) {
        LivingEntity target = event.getEntity();
        if (target.level().isClientSide()) return;
        DamageSource source = event.getSource();
        // 施法者必须为玩家
        if (!(source.getEntity() instanceof Player player)) return;
        // 目标判定与骰神赐福一致
        if (!com.merlinkitsune.astral_dice.combat.DiceCombatEvents.isBlessingTarget(target, player)) return;
        // 作用域判定(白名单 + 军火排除)
        Entity direct = source.getDirectEntity();
        if (!SpellDamageRegistry.isSpellDamage(source, direct)) return;

        SpellDamageContext ctx = new SpellDamageContext(player, target, event, source, direct);

        // 聚合加成(按注册顺序):单次遍历收集生效修饰器,避免 isActive 重复求值
        java.util.List<SpellDamageModifier> active = new java.util.ArrayList<>();
        double bonus = 0;
        for (SpellDamageModifier modifier : SpellDamageRegistry.modifiers()) {
            if (modifier.isActive(ctx)) {
                active.add(modifier);
                bonus = modifier.apply(ctx, bonus);
            }
        }

        // 应用加成并跳数字。
        // **2026-11-07 对齐 neo(方案A 折叠)**:法伤加成**并进同一次伤害事件**,不再发嵌套
        // 真伤 hurt(旧实现 target.hurt(bonusSource, bonus) 让每条命中产生两条伤害实例 ⇒
        // 两次死亡/击杀派发、两套音效分档,且抗性/保护/受击侧倍率/筹码固定点减伤对加成各算一次)。
        // 本线载荷口径 = 护甲**之前**(见类头与上方挂点说明),与 neo 的 LivingIncomingDamageEvent
        // 同口径 ⇒ 加成仍按「绕过护甲」结算:先做护甲预补偿再让平台的护甲公式统一削一次
        // (见 foldBonusBypassingArmor)。
        // ⚠️ 受击侧倍率**不**在本折叠点之后:event/EnderDiceHandler 已在更早的
        // LivingHurtEvent@HIGH 乘进了本次实参 ⇒ 必须在倍率前空间里折叠、再乘回同一倍率
        // (见 foldBonusPreFactor),否则加成永远不吃倍率。抗性/保护/吸收则对合并值**只作用一次**。
        // 重入闸门 APPLYING_TRUE_BONUS 随嵌套 hurt 一并移除(全仓无其它消费者,已 grep 核实)。
        if (bonus > 0) {
            // 本次命中的**基础伤害**也归入法伤组。必须显式登记:这条命中绝大多数是**原版**伤害源
            // (箭矢 arrow / 投掷物 / 其它模组法术),聚合器的类型回落认不出来。
            // 折叠后本体与加成是同一个 DamageSource / 同一个事件 ⇒ 只此一处登记,单 SPELL 槽。
            DamageNumberAggregator.tag(target, source, DamageNumberAggregator.Group.SPELL);
            float folded = foldBonusPreFactor(target, source, event.getAmount(), bonus);
            // 鼠鼠护盾的金额钳制挂在更早的 LivingHurtEvent@LOWEST(折叠之前),折叠抬高金额后必须
            // 重新钳一次,否则「剩余黄心 3 点、折叠后 20 点」会直接漏红心(neo 的钳制在折叠之后,无此缺口)。
            if (target instanceof Player shielded) {
                float remaining = RenShieldManager.remainingShieldAbsorption(shielded);
                if (remaining > 0.0F && folded > remaining) folded = remaining;
            }
            event.setAmount(folded);
        }

        // 活体书页命中音效:**按本次命中的完整伤害分档**,取值与上面的 HUD 跳字完全同源。
        // ⚠️ 折叠后 event.getAmount() **已含**法伤加成,不得再 + bonus(再加就是双算)。
        // 本线读数仍是护甲**之前**的合并值(B27 已登记残余),与 neo 的护甲后终值口径不同。
        // 判据 = 伤害类型是 astral_dice:card_spell，**且**不是四张伤害效果牌自己的 FX 伤害
        // （2026-10-07 修复 KI-G4：card_spell 不再只有活体书页一条产出路径 —— 四张牌重做后它们也走
        //   ModDamageTypes.cardSpell，且各自已播命中音，见 combat/DamageCardFxScheduler）。
        if (source.is(com.merlinkitsune.astral_dice.damage.ModDamageTypes.CARD_SPELL)
                && !com.merlinkitsune.astral_dice.combat.DamageCardFxScheduler.isApplyingCardFxDamage()) {
            int dealt = (int) Math.round(event.getAmount());
            SoundPlayback.playAt(target.level(), target.getX(), target.getY(), target.getZ(),
                    dealt >= LIVING_PAGE_BIG_HIT_THRESHOLD
                            ? ModSounds.DAMAGE_EFFECT_CARD_BIGHIT.get()
                            : ModSounds.DAMAGE_EFFECT_CARD_HIT.get());
        }

        // 命中副作用(施加标记/定向爆破 AOE 等)
        for (SpellDamageModifier modifier : active) {
            modifier.onHit(ctx, bonus);
        }
    }

    /**
     * 折叠 + 受击侧倍率对齐(2026-11-07 二次复核 M1)。
     *
     * <p>本线载荷口径是「护甲前、**受击侧倍率之后**」:受击侧倍率由 {@code event/EnderDiceHandler}
     * 在 {@code LivingHurtEvent@HIGH} 乘进实参(早于本折叠点);neo 的折叠挂在
     * {@code LivingIncomingDamageEvent}(倍率之前)。若直接按当前 {@code event.getAmount()} 折叠,
     * 本体取的是已乘倍率的值、加成却漏乘 ⇒ 加成永远不吃受击侧倍率(雨中/水下 +40%、赐福减伤等),
     * 与 neo 不等价。
     *
     * <p>⚠️ neo 的真实顺序不是「折叠 → 倍率 → 护甲」:它的折叠挂在
     * {@code LivingIncomingDamageEvent}(护甲前),但倍率挂在 {@code LivingDamageEvent.Pre}
     * (护甲、魔抗之后,吸收之前)⇒ 实际是「折叠 → 护甲 → 魔抗 → ×倍率」。本线倍率固定在护甲前,
     * 位置搬不过去,只能把**数值结果**对齐:先退回倍率前本金(当前值 ÷ 倍率),按
     * {@code want = (armor(本金) + bonus) × 倍率} 求目标,再反解 x 使 {@code armor(x) = want}。
     * 平台对 x 做护甲/魔抗/吸收后的落地值与 neo 一致(魔抗对本体与加成各线性一次、可交换;吸收两线都在最后)。
     *
     * <p>倍率取自 {@link com.merlinkitsune.astral_dice.combat.DiceCombatModifiers#instanceVictimFactor}
     * ——只搬运本实例已登记的倍数,不重新求值(重算即双算,见该方法的 A3 口径);未登记时返回 1.0,
     * 走与折叠前完全相同的路径。
     */
    private static float foldBonusPreFactor(LivingEntity target, DamageSource source,
            float postFactorAmount, double bonus) {
        double factor = com.merlinkitsune.astral_dice.combat.DiceCombatModifiers.instanceVictimFactor(target, source);
        if (!Double.isFinite(factor) || factor == 1.0) {
            return foldBonusBypassingArmor(target, source, postFactorAmount, bonus);
        }
        if (factor <= 0.0) {
            // 受击倍率为 0:本次伤害已被完全归零,加成同样归零;且不能做除法(0 会得到 NaN/Inf)。
            return 0.0F;
        }
        float preFactor = (float) (postFactorAmount / factor);
        return foldBonusBypassingArmor(target, source, preFactor, bonus, factor);
    }

    // ⚠️ 本类原有的 sendBonusDamageNumber 私有帮助方法已于 2026-09-26 移除:跳数字的唯一出口是
    // combat/DamageNumberAggregator(取伤害管线终值 + 按组别聚合),各调用点自行发包会被它在
    // 客户端单槽覆盖(见该类的类头)。
}
