package com.merlinkitsune.astral_dice.combat;
import com.merlinkitsune.starengine.item.CuriosCompat;
import com.merlinkitsune.astral_dice.network.ModNetwork;

import com.merlinkitsune.astral_dice.component.AppliedStone;
import com.merlinkitsune.starengine.component.GameplayConstants;
import com.merlinkitsune.astral_dice.component.ModAttachments;
import com.merlinkitsune.astral_dice.component.ModDataComponents;
import com.merlinkitsune.astral_dice.component.WeaponEnhancement;
import com.merlinkitsune.astral_dice.effect.ModEffects;
import com.merlinkitsune.astral_dice.item.RenShieldManager;
import com.merlinkitsune.astral_dice.item.sign.BaseSignItem;
import com.merlinkitsune.astral_dice.item.sign.MosesSignItem;
import com.merlinkitsune.astral_dice.item.sign.LinglingSignManager;
import com.merlinkitsune.astral_dice.item.sign.PandamanSignItem;
import com.merlinkitsune.astral_dice.item.sign.SignHookDispatcher;
import com.merlinkitsune.astral_dice.effect.WeaknessRevealEffect;
import com.merlinkitsune.astral_dice.item.dice.DiceCurioItem;
import com.merlinkitsune.astral_dice.item.MarkManager;
import com.merlinkitsune.astral_dice.item.StarLightManager;
import com.merlinkitsune.astral_dice.item.sign.MisakiSignItem;
import com.merlinkitsune.astral_dice.item.ModItems;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraft.tags.DamageTypeTags;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingChangeTargetEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.living.MobEffectEvent;
import net.minecraftforge.event.TickEvent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import com.merlinkitsune.astral_dice.item.chip.PerpetualMotionChipItem;
import com.merlinkitsune.astral_dice.item.chip.AdvancedPeripheralsChipItem;
import com.merlinkitsune.astral_dice.item.chip.BufferShieldChipItem;
import com.merlinkitsune.astral_dice.combat.DiceCombatContext;
import com.merlinkitsune.astral_dice.item.chip.VitaminPillChipItem;
import com.merlinkitsune.astral_dice.item.sign.NancyLuSignItem;
import com.merlinkitsune.astral_dice.combat.DiceCombatModifiers;
import com.merlinkitsune.astral_dice.item.card.FateGuidanceCardItem;
import com.merlinkitsune.starengine.combat.HostileTargets;
import com.merlinkitsune.starengine.combat.PartyRelations;
import com.merlinkitsune.astral_dice.event.PlayerEffectProtection;

@Mod.EventBusSubscriber(modid = com.merlinkitsune.astral_dice.AstralDiceMod.MODID)
public class DiceCombatEvents {
    // === 神秘遗物 (Enigmatic Legacy 2.30.1, modId `enigmaticlegacy`) 联动 ===
    // ⚠️ **物品 path 与 1.21.1 移植版完全相同,只有命名空间不同**(2026-09-30 逐项核对两版 jar 的 lang 物品表)。
    //    ⚠️ 本线 2.30.1 **没有「恩惠之典」**(the_bless 属移植版) ⇒ `ENIGMATIC_BLESS` 恒取到 AIR、
    //    该免疫分支在 1.20.1 上**不生效**(保留常量是为了命名空间一致与未来物品补齐)。
    private static final String ENIGMATIC_CURSED_RING = "enigmaticlegacy:cursed_ring";
    private static final String ENIGMATIC_ACKNOWLEDGMENT = "enigmaticlegacy:the_acknowledgment";
    private static final String ENIGMATIC_TWIST = "enigmaticlegacy:the_twist";
    private static final String ENIGMATIC_BLESS = "enigmaticlegacy:the_bless";

    /**
     * 玩家侧闪避判定开关:当前 false(玩家侧闪避已移除,目标未佩戴骰子时直接进入常规防御结算)。
     * 未来如需恢复闪避,改为 true 即可——闪避对骰与闪避失败结算代码保留在
     * {@code onLivingDamagePre} 的 targetDiceResult.isEmpty() 分支内。
     */
    private static final boolean PLAYER_DODGE_ENABLED = false;
    // AOE(顺劈/溅射/法伤波及)处理中:被波及目标不再进入骰战结算。
    // 用**深度计数**(与下方 counterDepth 同构)而非布尔:实测存在**嵌套**窗口 ——
    // 「活体书页命中(LivingPageImpact 开窗)→ 该伤害是 astral_dice:card_spell、命中法伤白名单
    //   ⇒ 进入 DamageEffectCardHandler → onHit 再触发定向爆破/电击手套的波及(内层再开窗)」。
    // 布尔实现下内层 endAoe() 会把守卫整体清零 ⇒ 外层剩余过程失去保护;
    // 深度计数只减回 1 ⇒ 外层始终受保护(与 counterDepth 的①条同理)。
    // ⚠️ 一个「以玩家为 directEntity」的注入伤害就会让本窗口变成**递归入口** ⇒ 扩散/追加伤害源
    //    一律用 directEntity=null 的类型(true/skill/extra/unreducible_damage)。
    private static int aoeDepth = 0;
    // 反击链深度(替代原单层布尔 counterProcessing,结构性阻止"反击→闪避→反击"递归):
    //   >0 表示当前正处于「injectCounterDamage → attacker.hurt(...)」的同步调用链中。
    //   ① 用"深度"而不是布尔:嵌套注入时内层 finally 只把深度减回 1,而不会把守卫整体清零,
    //      外层剩余的注入过程始终受保护(原 boolean 会被内层 finally 提前复位 = 守卫失效);
    //   ② 增减包在 try/finally 内:异常/提前返回都会复位,守卫不会卡死;
    //   ③ 所有能绕回 injectCounterDamage 的入口(骰战结算 / 破绽闪避 / 嘲讽反击 / 肾上腺素闪避)
    //      都先判定 isInCounterChain(),injectCounterDamage 自身也拒绝再入 →
    //      反击链内不可能再发起一次反击,递归在结构上不成立(不是"限制递归层数")。
    private static int counterDepth = 0;

    // 把两条内部窗口的开关讲给库听(库的 PlayerHostilityTracker 需要它来区分"主动攻击"与"内部波及",
    // 但窗口状态属本类的玩法实现,不下沉)。
    static {
        com.merlinkitsune.starengine.combat.InternalDamageWindows.install(
                DiceCombatEvents::isInternalAoe,
                DiceCombatEvents::isInCounterChain);
        // 再把「试验假人应当算敌对目标」讲给库听:库的 combat/HostileTargets 是「敌对目标」的唯一入口,
        // 而它的判定点有一部分落在库内 —— target/SelectorTargets 的可选中判定(客户端射线/半径高亮/
        // 服务端确认)直接调它 ⇒ 消费方无法插手,故由库留 seam、本模组在此注入。
        // 未注入时库退回原口径(假人不算敌对),即本注入是**纯增益**、不影响其它实体。
        com.merlinkitsune.starengine.combat.HostileTargets.installExtraHostileProbe(
                DiceCombatEvents::isTrainingDummy);
    }

    // 当前是否处于反击链中(供骰战结算 / 闪避 / 反击入口判定)
    public static boolean isInCounterChain() {
        return counterDepth > 0;
    }

    // 当前是否处于本模组内部 AOE(顺劈/溅射/法伤波及)结算窗口。
    // 语义化只读入口:供受击记录等外部判定区分"主动攻击"与"内部波及"(禁止复制该标志)。
    public static boolean isInternalAoe() {
        return aoeDepth > 0;
    }

    /** 进入「本模组内部波及」窗口(**必须**与 {@link #endAoe()} 严格 try/finally 配对)。
     *  <p>用深度计数而非布尔:嵌套窗口的内层 {@code endAoe()} 只把深度减回 1,
     *  不会把守卫整体清零 ⇒ 外层剩余过程仍受保护。 */
    public static void beginAoe() {
        aoeDepth++;
    }

    /** 退出「本模组内部波及」窗口(与 {@link #beginAoe()} 严格配对;深度不会降到 0 以下)。 */
    public static void endAoe() {
        if (aoeDepth > 0) aoeDepth--;
    }

    // ── 电击手套「本次可扩散」窗口(2026-10-05 重写) ─────────────────────────────
    // 语义 = 「**这一次**主目标命中,是那张已付费的伤害效果牌自己的伤害」。
    // 由承载该次使用的那张牌的结算点置位(combat/DamageCardFxScheduler 的主目标命中、
    // combat/LivingPageImpact),只有在此期间法伤链里的电击手套修饰器才会扩散 ⇒
    // 原版箭矢 / 别家法术 / 同一张牌的 AOE 波及命中都拿不到扩散。
    // 旧实现是「玩家级武装、跨伤害实例存活到效果牌周期结束」= 周期溅伤,已废弃(用户 2026-10-05 裁决)。
    static boolean gloveSplashInstance = false;
    // 扩散结算重入闸门:扩散自身用的 true_damage 目前不在法伤白名单里,但白名单**可变**
    // (docs/audit/aoe-recursion-and-self-hit-audit-2026-09-24.md §3 已登记该脆弱点) ⇒
    // 用独立闸门把「扩散结算期间的重入」结构性挡掉,不再依赖「恰好不被判为法伤」这一事实。
    static boolean gloveSplashSettling = false;

    // ⚠️ 2026-10-21 语义定稿：电击手套已改为**独立延迟结算**
    //   （item/chip/ElectricGloveChipItem#executeShock，走 event/RailgunStrikeScheduler 的 20 tick
    //   延迟队列，用 card_spell 独立结算）⇒ **不再经过本窗口**。
    //   下列闸门 / 计数 / 登记（gloveSplashInstance、gloveSplashSettling、runAsGloveSplashInstance、
    //   runAsGloveSplashSettlement、aoeDepth、markCardSplashHit）**全部保留**：
    //   ① 其它链路（活体书页、定向爆破 AOE 等）仍在用；② 即便暂时无人调用，它们仍是法伤链重入的
    //   结构性防护 —— **不要**因为"看起来没人用"而删除或简化成布尔。

    /** 当前这一次命中是否属于「已付费的伤害效果牌自己的主目标伤害」(供电击手套修饰器判定)。 */
    public static boolean isGloveSplashInstance() {
        return gloveSplashInstance;
    }

    /** 扩散结算进行中 ⇒ 电击手套修饰器必须直接失效(防指数递归)。 */
    public static boolean isGloveSplashSettling() {
        return gloveSplashSettling;
    }

    /**
     * 以「电击手套 · 本次可扩散命中」窗口包裹**那一张牌自己的主目标伤害**。
     * 保存并恢复原值 ⇒ 嵌套安全(与 {@link #beginAoe()} / {@link #endAoe()} 同属「成对开窗」纪律)。
     */
    public static void runAsGloveSplashInstance(Runnable action) {
        if (action == null) return;
        boolean previous = gloveSplashInstance;
        gloveSplashInstance = true;
        try {
            action.run();
        } finally {
            gloveSplashInstance = previous;
        }
    }

    /** 以「电击手套 · 扩散结算中」窗口包裹扩散循环(重入闸门;保存并恢复原值)。 */
    public static void runAsGloveSplashSettlement(Runnable action) {
        if (action == null) return;
        boolean previous = gloveSplashSettling;
        gloveSplashSettling = true;
        try {
            action.run();
        } finally {
            gloveSplashSettling = previous;
        }
    }

    // ── 「一次用量」内的扩散/波及**已结算目标**登记(2026-10-05 用户裁决) ───────────────────
    // 同一次牌的结算里可能存在**两条独立枚举**:电击手套的 3 格扩散(在主目标命中内触发)与
    // 定向爆破自身的 6 格 AOE(combat/DamageCardFxScheduler#resolveBlast)。两者锚点相同
    // (都是那张牌的主目标)、且 3 ⊂ 6 ⇒ 交集必非空 ⇒ 交集目标会被交给 hurt() 两次。
    // ⚠️ 这**不是**理论问题:改造前只是「恰好」没翻倍 —— 手套扩散用 astral_dice:true_damage
    //   (在 minecraft:bypasses_cooldown 里)先落地、并写无敌帧;爆破 AOE 用 astral_dice:card_spell
    //   (当时不在该标签里)⇒ 被原版「无敌帧内不更高伤害予以差减」的规则削掉。这条保证依赖
    //   **标签表内容 + 结算顺序**,任一处变化就会变成真·重复吃 ⇒ 改为**显式登记、先到先得**。
    //   ⚠️ 2026-10-08:card_spell **已**追加登记进 bypasses_cooldown(为修「伤害效果牌实测 ±1 偏差」,
    //   见 damage/ModDamageTypes 的 CARD_SPELL javadoc)⇒ 上述「恰好」前提**已不存在**,
    //   去重从此**完全**由本段显式登记承担(标签层不再提供任何兜底)。
    // 只有「一次用量」期间为集合态(由承载该次用量的牌开启);其余时刻为 null ⇒ 登记恒放行,
    // 行为与改造前完全一致(单枚举的激光/板砖/轨道炮/活体书页不受影响)。
    private static java.util.Set<Entity> cardSplashResolved = null;

    /** 开启「本次用量」的扩散登记窗口。保存并恢复原值 ⇒ 与外层嵌套安全(同 {@link #beginAoe()} / {@link #endAoe()})。 */
    public static void runAsCardSplashResolution(Runnable action) {
        if (action == null) return;
        final java.util.Set<Entity> previous = cardSplashResolved;
        cardSplashResolved = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
        try {
            action.run();
        } finally {
            cardSplashResolved = previous;
        }
    }

    /**
     * 登记一次扩散/波及命中(先到先得)。
     *
     * @return {@code false} = 该目标在**本次用量**内已经吃过一次扩散/波及 ⇒ 调用方**必须跳过它**。
     *         未开启登记窗口时恒返回 {@code true}(= 不干预)。
     */
    public static boolean markCardSplashHit(Entity entity) {
        final java.util.Set<Entity> set = cardSplashResolved;
        if (set == null || entity == null) return true;
        return set.add(entity);
    }

    // 检测玩家是否佩戴了七咒之戒(按物品 ID 识别,未安装该模组时返回 false)
    public static boolean hasEnigmaticCurse(Player player) {
        Item ring = BuiltInRegistries.ITEM.get(new ResourceLocation(ENIGMATIC_CURSED_RING));
        if (ring == Items.AIR) return false;
        var curios = CuriosCompat.getCuriosInventory(player);
        return curios.isPresent() && curios.get().findFirstCurio(s -> s.is(ring)).isPresent();
    }

    // 检测玩家是否手持指定神秘遗物+ 物品(如启示之证)
    public static boolean isHoldingEnigmaticItem(Player player, String itemId) {
        Item item = BuiltInRegistries.ITEM.get(new ResourceLocation(itemId));
        if (item == Items.AIR) return false;
        return player.getMainHandItem().is(item) || player.getOffhandItem().is(item);
    }

    // 七咒减益:对骰子/卡牌点数施加 -40%(手持启示之证再 -20%;护法爆发/倒转之启/恩惠之典完全免疫)。
    // 用于攻击点数(骰点+卡牌)与闪避失败的"攻击点数最大值"结算。
    private static double applyCurseToDicePoints(Player player, double points) {
        if (points <= 0 || !hasEnigmaticCurse(player)) return points;
        if (player.hasEffect(ModEffects.MISAKI_BURST.get())
                || isHoldingEnigmaticItem(player, ENIGMATIC_TWIST)
                || isHoldingEnigmaticItem(player, ENIGMATIC_BLESS)) {
            // 爆发期间/持有免疫物品:不施加减益,造成全额点数
            return points;
        }
        double cursePenalty = 0.4;
        if (isHoldingEnigmaticItem(player, ENIGMATIC_ACKNOWLEDGMENT)) {
            cursePenalty = Math.max(0, cursePenalty - 0.2);
        }
        return points * (1 - cursePenalty);
    }

    // ============================================================================================
    // N1 + N2(审计 docs/features/attack-damage-chain-audit.md §2 不变量②③):受击侧吸收按**骰战终值**
    // 结算且**只扣一次**;鼠鼠护盾按剩余护盾钳制终值。两条写终值路径(玩家攻击 / 生物近战)共用
    // settleDiceDamage —— 将来任何新的写终值路径也必须走它,否则吸收会被平台双扣。
    // --------------------------------------------------------------------------------------------
    // 平台(1.20.1)在 actuallyHurt 里对吸收做了**两段**扣减(forge-1.20.1-47.4.10-sources.jar,
    // net/minecraft/world/entity/LivingEntity.java):
    //   :1669-1670  P = 护甲/抗性/保护后的平台数值;c1 = min(P, A0);setAbsorptionAmount(A0 - c1)
    //   :1680       f1 = ForgeHooks.onLivingDamage(...)   <- 骰战在本事件里用终值 F 覆盖式 setAmount
    //   :1684       setAbsorptionAmount(getAbsorptionAmount() - f1)  <- 又按(可能被改过的)f1 扣一次
    // 原版路径之所以不出错:平台 f1 = max(P - A0, 0),f1 > 0 时吸收在 :1670 已扣到 0,第二段被
    // setAbsorptionAmount 对负值的钳位吞掉(吸收不会变负)。骰战覆盖 f1 = F 后,第二段就会**再吃一遍
    // 吸收** => 吸收双扣、红心反而少扣。故本类显式记账:
    //   A0 = 护甲前(:1665 LivingHurtEvent,平台尚未扣吸收)的目标吸收余额
    //   F  = 骰战终值(已含 N1 护盾钳制 / N8 弱印记)
    //   absorbedShould = min(F, A0)   <- 本次**应当**被吸收吃掉的量
    //   H = F - absorbedShould        <- 应当落红心的量
    //   写回 setAbsorptionAmount(A0 - absorbedShould + H) 且 setAmount(H)
    //   => 平台 :1684 再扣 H => 终态吸收 = A0 - absorbedShould (正确);红心 = H (正确)
    //   => H == 0 时平台 :1681 整个分支跳过(不扣血也不再扣吸收),终态同样正确
    // 已知限制(原始记录;2026-10-27 批次F 已在下游出口消除,机制见 {@link #PHANTOM_ABSORPTION}):
    //    本模组 LOWEST 处理器(安全气囊归零 / 磨刀石 -N / 立牌 -N)可能在本处之后**再改金额**
    //    => 平台 :1684 的扣减量 != H => 吸收终值偏移 |H - f1最终|。
    //    1.20.1 没有 LivingDamageEvent.Post,同优先级内的监听顺序也不可依赖,故不在此补偿;
    //    偏移方向是"少消耗吸收 / 多留吸收",不会让玩家多吃红心,记入审计待后续批次。
    // 2026-10-27 批次F:settle 写回处把 H 登记进 {@link #PHANTOM_ABSORPTION};窗口内改金额的处理器
    // 统一调 {@link #compensateAbsorption}(改金额后),窗口内的嵌套 hurt 统一用
    // {@link #withPhantomPeeled} 包裹 ⇒ 「嵌套伤害被幻影吞掉」与「改金额后黄心白涨」两条都消除。
    private static final Map<LivingEntity, PreHurtSnapshot> PRE_HURT_ABSORPTION =
            Collections.synchronizedMap(new WeakHashMap<>());

    /** N2 受击前快照:护甲前(平台 L1665 LivingHurtEvent,尚未扣吸收)的**吸收余额**与**鼠鼠护盾剩余**。
     *  <p>为什么护盾也必须在这里取:平台在 :1669-1670 就按平台数值扣掉了一段吸收;等 :1680
     *  (LivingDamageEvent)再读 {@code remainingShieldAbsorption} 时,只要平台数值 >= 护盾,读到的就是 0。
     *  那样 N1 的钳制会静默失效 —— 骰战覆盖金额后超出的部分会漏到红心,破坏 2026-09-24 的裁决
     *  「护盾免疫单次超过黄心的伤害(不会影响红心)」。受击前取才与 1.21.1(LivingDamageEvent.Pre,
     *  吸收之前)同口径。 */
    private static final class PreHurtSnapshot {
        final float absorption;
        final float shield;

        PreHurtSnapshot(float absorption, float shield) {
            this.absorption = absorption;
            this.shield = shield;
        }
    }

    /** N8:虚弱印记受击侧倍率(审计 §2 不变量③:受击方乘算必须在**吸收之前**,等价 1.21.1 的
     *  {@code LivingDamageEvent.Pre})。原先散落两处 1.10f 字面量(骰战并入 + LOW 处理器),抽成
     *  同一常量后两条路径同源,避免"并入一份、LOW 再乘一份"的漂移。 */
    private static final float WEAK_MARK_DAMAGE_MULTIPLIER = 1.10f;

    /** N2 记录点:护甲前、平台扣吸收(L1669)之前把吸收余额记进 {@link #PRE_HURT_ABSORPTION}。
     *  <p>用 HIGHEST:本事件里 RenShieldManager#onRenShieldAbsorb(LOWEST)只钳**金额**、不动吸收
     *  余额,故记在它前后数值相同;取 HIGHEST 是为了贴近 L1665 的派发起点、尽量避开其它监听器副作用。
     *  <p>容器选型:弱键 => 实体卸载后条目可回收(不随实体数无限增长);同键覆盖写 => 一次受击只有一条
     *  生效的 Hurt 实例;若该实例最终没有派发 LivingDamageEvent(金额被别的监听器清成 <= 0),条目会被
     *  下一次受击覆盖,且取用点绝不会把它算到别的实体头上。 */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onLivingHurtRecordAbsorption(LivingHurtEvent event) {
        LivingEntity target = event.getEntity();
        if (target.level().isClientSide()) return;
        // N1:护盾剩余与吸收余额同批快照(见 PreHurtSnapshot);非玩家目标没有鼠鼠护盾,记 0。
        float shield = target instanceof Player defender
                ? RenShieldManager.remainingShieldAbsorption(defender) : 0.0F;
        PRE_HURT_ABSORPTION.put(target, new PreHurtSnapshot(target.getAbsorptionAmount(), shield));
        // 2026-10-27 批次F:每次**新受击**都会开启一个新的「settle ⇒ 平台 :1684」窗口,上一窗口留下的
        // 幻影登记必然已被 :1684 消费掉(或该实例没派发 LivingDamageEvent)⇒ 这里先清空,避免上一段的 H
        // 被本段的收尾处理器误当成「仍待扣的幻影」再剥一次(同 tick 连续两击 / A0<=0 短路后无新登记时)。
        PHANTOM_ABSORPTION.remove(target);
    }

    /** N2 取用点:**立刻**摘走并移除登记 —— 同一 tick 内同一实体可能有嵌套/多段伤害实例
     *  (额外加伤 / 狂暴反噬 / 反击 / 溅射),先摘后算可保证各实例互不串味。 */
    private static PreHurtSnapshot consumePreHurtAbsorption(LivingEntity target) {
        PreHurtSnapshot recorded = PRE_HURT_ABSORPTION.remove(target);
        if (recorded != null) return recorded;
        // 兜底:理论上不会走到(能派发 LivingDamageEvent 说明走过 ForgeHooks.onLivingHurt)。
        // 只有绕过 hurt() 的同 tick 路径(其它模组直接改血量/重复派发)才可能取不到登记;那时平台已扣过
        // 一段吸收,取当前值 => A0 偏小、护盾可能已被打空 => 结果偏「少消耗吸收」,绝不双扣、绝不多掉红心。
        float shield = target instanceof Player defender
                ? RenShieldManager.remainingShieldAbsorption(defender) : 0.0F;
        return new PreHurtSnapshot(target.getAbsorptionAmount(), shield);
    }

    /** N1 + N2 唯一出口:把骰战终值落到平台上(护盾钳制 => 吸收按终值只扣一次)。
     *
     *  <p><b>N1 鼠鼠护盾</b>:按「剩余护盾」钳制终值,等价 1.21.1 侧
     *  {@code RenShieldManager#onRenShieldAbsorb}(LivingDamageEvent.Pre)的
     *  {@code if (damage <= remaining) return; setNewDamage(remaining);}。
     *  为什么必须在这里补:本线 RenShieldManager 只能挂 LivingHurtEvent(LOWEST,护甲前),
     *  它钳的是**平台数值**;骰战随后用自算终值覆盖式 setAmount => 那次钳制被整段丢弃(审计 N1)。
     *  shield 取**受击前**值(见 PreHurtSnapshot),此时 shield = A0 - baseline <= A0;钳制后 F <= shield
     *  => absorbedShould = F、H = 0 => 护盾完整吃掉这一击(与 neo 同口径)。
     *
     *  <p><b>N2 吸收结算</b>:推导见 {@link #PRE_HURT_ABSORPTION} 上方注释。 */
    private static float settleDiceDamage(LivingEntity target, LivingDamageEvent event, float finalDamage) {
        float damage = finalDamage;
        // N1+N2:先摘快照(受击前吸收余额 + 受击前护盾剩余)再算 —— 后续额外加伤/狂暴反噬会再 hurt 一轮,
        // 先摘后算保证本轮与嵌套轮各用各的快照,不串味。
        PreHurtSnapshot snapshot = consumePreHurtAbsorption(target);
        // N1:剩余护盾钳制(护盾余额与基线的口径归 RenShieldManager,此处只消费受击前快照)
        if (snapshot.shield > 0.0F && damage > snapshot.shield) {
            damage = snapshot.shield;
        }
        // N2:A0 = 平台扣吸收**前**的吸收余额
        float absorptionBefore = snapshot.absorption;
        if (absorptionBefore <= 0.0F) {
            // A0 == 0:本击没有吸收可吃 => **完全跳过**吸收补偿,只写终值。
            // 不能在这里顺手 setAbsorptionAmount(damage):本事件同优先级以及更低优先级的处理器会读
            //    getAbsorptionAmount()(审计 §2 不变量④要求它们读到"扣除本次吸收后的量"),中途把吸收
            //    改成 damage 会直接污染这些判据。A0 == 0 时本就不需要补偿(写回值与平台扣减量天然一致)。
            event.setAmount(damage);
            // F7:修女被动一律在公共出口判(见 applyAlanaBigHit)
            applyAlanaBigHit(target, damage);
            return damage;
        }
        float absorbedShould = Math.min(damage, absorptionBefore);
        float toHealth = damage - absorbedShould;
        // 写回 A0 - absorbedShould + H:平台 L1684 会再扣一次 H => 终态吸收 = A0 - absorbedShould
        float expectedAfter = Math.max(0.0F, absorptionBefore - absorbedShould);
        // F1(2026-10-27 近战审计,forge 专属):**玩家受害者**路径上平台不会再扣一次吸收。
        //   非玩家走 LivingEntity#actuallyHurt(:1669-1670 扣吸收 → :1680 派发 → :1683 扣血 → :1684 再扣吸收);
        //   玩家走 Player#actuallyHurt(:940-965 覆写、不调 super)::946-947 扣吸收 → :948 派发 → :954 扣血,
        //   **没有** :1684 那一步。故写回「A0 - absorbedShould + H」会把幻影 H 永久留在黄心上
        //   (A0=8、F=20 ⇒ 终态 12;且 compensateAbsorption 对玩家恰好是恒等式,永远清不掉),
        //   并连带抬高鼠鼠护盾钳制上限、让 ChipDamageHandler 的「护盾>0 就直接 return」旁路筹码减伤。
        //   ⇒ 玩家路径直接写真实终态、不登记幻影。
        if (target instanceof Player) {
            target.setAbsorptionAmount(expectedAfter);
            PHANTOM_ABSORPTION.remove(target);
            event.setAmount(toHealth);
            applyAlanaBigHit(target, toHealth);
            return toHealth;
        }
        target.setAbsorptionAmount(absorptionBefore - absorbedShould + toHealth);
        // 2026-10-27 批次F:把这份「幻影 H」显式登记(见 {@link #PHANTOM_ABSORPTION})。
        // H == 0 时平台 :1681 整段跳过、不会有后续扣减 ⇒ 不登记(顺带清掉可能残留的旧登记)。
        registerPhantom(target, toHealth, expectedAfter);
        event.setAmount(toHealth);
        applyAlanaBigHit(target, toHealth);
        return toHealth;
    }

    /**
     * F7(2026-10-27 近战审计):修女(alana)被动「单次受击 >10 清空全部加成」的判定。
     *
     * <p>原判据挂在 {@code event/NewSignEvents}(非骰战)与<b>玩家攻击路径</b>上,读的是护甲前原版值;
     * 骰战以自算终值覆盖式写入后该值已无意义(近战骰战下几乎永不触发)。故改为按<b>落地红心量</b>判定,
     * 并放在两条骰战路径的<b>共同出口</b>:{@link #settleDiceDamage}(玩家攻击 / 生物近战都走它)。
     * 只挂玩家路径会漏判生物→玩家骰战(2026-10-27 实机见证:怪 22 点打到玩家、加成未清)。
     *
     * @param applied 落地红心量(= settle 之后 event.getAmount())
     */
    private static void applyAlanaBigHit(LivingEntity target, float applied) {
        if (target instanceof Player victim && applied > 10.0F
                && ModAttachments.getAlanaPassiveBonus(victim) > 0) {
            com.merlinkitsune.astral_dice.item.sign.AlanaSignItem.clearBonus(victim);
        }
    }

    // ============================================================================================
    // 2026-10-27 批次F:幻影吸收余额 H 的显式管理(P2-A / P2-B)
    // --------------------------------------------------------------------------------------------
    // settle 写回的「A0 - absorbedShould + H」中,H 是一个**幻影余额**:它只活在「settle 之后 ⇒ 平台
    // :1684 之前」这段窗口里,靠平台再扣一次 H 才落回真实余额。原实现把这个约定**隐式散落在时间轴上**,
    // 于是窗口内任何再次触碰吸收 / 金额的代码都会破坏它:
    //   P2-A:窗口内对**同一目标**补发的嵌套 hurt(额外加伤 / 大当家溅射 / 狂暴反噬)在平台 :1669-1670
    //        按 c1 = min(P, 含 H 的余额) 结算 ⇒ 这段伤害被幻影整段吞掉。
    //   P2-B:窗口内改动**金额**的 LOWEST 处理器(筹码减伤 / 立牌 +5 / 弱印记)让平台 :1684 的扣减量
    //        != H;气囊归零时 :1681 整段跳过 ⇒ 吸收停在 A0-absorbedShould+H(例 A0=4、F=20 白涨 12 点黄心)。
    // 故把 H 登记成显式数据(弱键 + gameTime),对外提供三个操作:
    //   phantomOf(target)                     读登记(跨 tick 自动失效)
    //   withPhantomPeeled(target, action)     临时剥掉 H ⇒ 嵌套 hurt 在**真实余额**上结算 ⇒ 结束后挂回 H
    //   compensateAbsorption(target, amount)  剥掉 H ⇒ 写成 真实余额 + amount ⇒ 交平台 :1684 扣 amount
    private static final Map<LivingEntity, PhantomBalance> PHANTOM_ABSORPTION =
            Collections.synchronizedMap(new WeakHashMap<>());

    /** 单条幻影登记:{@code amount} = settle 写回后**仍待平台 :1684 扣掉**的量。 */
    private static final class PhantomBalance {
        final float amount;
        final long gameTime;
        /** 这一击**应该**留下的真实吸收余额(= A0 - absorbedShould);供 tick 末对账收回上偏(A5)。 */
        final float expectedAfter;

        PhantomBalance(float amount, long gameTime, float expectedAfter) {
            this.amount = amount;
            this.gameTime = gameTime;
            this.expectedAfter = expectedAfter;
        }
    }

    /** 登记本次写回留下的幻影;{@code amount <= 0} 时改为一律清除(H == 0 ⇒ 平台 :1681 整段跳过,无需幻影)。 */
    private static void registerPhantom(LivingEntity target, float amount, float expectedAfter) {
        if (amount <= 0.0F) {
            PHANTOM_ABSORPTION.remove(target);
            return;
        }
        PHANTOM_ABSORPTION.put(target, new PhantomBalance(amount, target.level().getGameTime(), expectedAfter));
    }

    /** 当前目标身上登记的幻影余额(未登记 / 跨 tick ⇒ 0)。
     *  <p>跨 tick 失效的理由:平台 :1684 只会在**同一 tick** 的那次 {@code hurt()} 里扣掉幻影;任何活过
     *  一个 tick 的登记都说明平台没走 :1684(金额被改回 0 的路径已由 {@link #compensateAbsorption} 清除),
     *  再拿它去剥余额只会把真实余额剥坏。 */
    public static float phantomOf(LivingEntity target) {
        if (target == null || target.level().isClientSide()) return 0.0F;
        PhantomBalance recorded = PHANTOM_ABSORPTION.get(target);
        if (recorded == null) return 0.0F;
        if (recorded.gameTime != target.level().getGameTime()) {
            PHANTOM_ABSORPTION.remove(target);
            return 0.0F;
        }
        return recorded.amount;
    }

    /** 在**真实余额**(剥掉幻影 H)上执行一段会再次派发伤害事件的代码,结束后把 H 挂回去。
     *  <p>嵌套 hurt 的整条平台流程(:1669-1670 扣吸收 → :1680 派发事件 → :1684 再扣)都在 {@code action}
     *  内同步跑完,故「挂回 H」= 让**外层**平台 :1684 扣掉它,终态仍是 settle 承诺的 A0 - absorbedShould。
     *  <p>剥掉期间**同时摘掉登记**:嵌套伤害自己的收尾处理器(补偿点)不能拿外层的 H 去剥余额。 */
    public static void withPhantomPeeled(LivingEntity target, Runnable action) {
        PhantomBalance recorded = PHANTOM_ABSORPTION.get(target);
        float phantom = recorded == null || recorded.gameTime != target.level().getGameTime()
                ? 0.0F : recorded.amount;
        if (phantom <= 0.0F) {
            action.run();
            return;
        }
        float expectedAfter = recorded.expectedAfter;
        PHANTOM_ABSORPTION.remove(target);
        target.setAbsorptionAmount(Math.max(0.0F, target.getAbsorptionAmount() - phantom));
        try {
            action.run();
        } finally {
            target.setAbsorptionAmount(target.getAbsorptionAmount() + phantom);
            PHANTOM_ABSORPTION.put(target, new PhantomBalance(phantom, target.level().getGameTime(), expectedAfter));
        }
    }

    /** 把「settle 之后又被改过金额」的这次伤害重新对齐:剥掉幻影 ⇒ 写成 真实余额 + {@code finalAmount}
     *  ⇒ 平台 :1684 扣 {@code finalAmount} ⇒ 落回真实余额。
     *
     *  <p>登记的处理(本批关键):写入后把登记**刷新成 {@code finalAmount}** 而不是一律清除 ——
     *  LOWEST 阶段可能有多个处理器依次改金额(弱印记 @LOW ⇒ 立牌 +5 ⇒ 筹码减伤 @LOWEST,先后不可依赖),
     *  后一个处理器必须能看到「平台接下来会扣多少」才能继续对齐。{@code finalAmount <= 0} 时平台 :1681
     *  整段跳过、不会再有扣减,登记随之清除。
     *  <p>没有登记 ⇒ 直接返回(这一击不是骰战写终值的,或已被别人结清),绝不改吸收余额。 */
    public static void compensateAbsorption(LivingEntity target, float finalAmount) {
        if (target == null || target.level().isClientSide()) return;
        PhantomBalance recorded = PHANTOM_ABSORPTION.get(target);
        if (recorded == null) return;
        if (recorded.gameTime != target.level().getGameTime()) {
            PHANTOM_ABSORPTION.remove(target);
            return;
        }
        float phantom = recorded.amount;
        if (phantom <= 0.0F) return;
        float real = Math.max(0.0F, target.getAbsorptionAmount() - phantom);
        target.setAbsorptionAmount(real + finalAmount);
        // expectedAfter 不随「后来有人改了金额」变化 —— 它描述的是这一击的真实终态。
        registerPhantom(target, finalAmount, recorded.expectedAfter);
    }

    /**
     * A5(2026-10-27 近战审计):tick 末对账幻影吸收。
     *
     * <p>settle 写回的 H 靠平台 :1684 再扣一次才落回真实余额;但**事件被取消**(第三方/本模组未覆盖的
     * 取消点)或**金额被第三方改成与 H 不一致**时,平台那次扣减不发生或量不对 ⇒ 黄心永久偏高。
     * 本仓自家改动点已由 {@link #compensateAbsorption} 覆盖,这里补的是"没人通知我们"的那类。
     *
     * <p>只纠**上偏**:当前余额高于 {@code expectedAfter} 时才收回,绝不动已经合法扣掉的量。
     */
    @SubscribeEvent
    public static void onServerTickReconcilePhantoms(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (PHANTOM_ABSORPTION.isEmpty()) return;
        synchronized (PHANTOM_ABSORPTION) {
            var it = PHANTOM_ABSORPTION.entrySet().iterator();
            while (it.hasNext()) {
                var entry = it.next();
                LivingEntity target = entry.getKey();
                PhantomBalance recorded = entry.getValue();
                if (target == null || target.isRemoved() || target.level().isClientSide()) {
                    it.remove();
                    continue;
                }
                long now = target.level().getGameTime();
                if (recorded.gameTime != now) {
                    it.remove();
                    continue;
                }
                float actual = target.getAbsorptionAmount();
                // N6(2026-10-27 复核):只纠「幻影量原封未动」那一档 —— 若同 tick 内有别的来源
                //  (第三方 / 我们自己的吸收类治疗)合法加了吸收,actual 会高于 expectedAfter+amount,
                //  此时一刀切收回会白白吃掉合法增益。
                float phantomUntouched = recorded.expectedAfter + recorded.amount;
                if (actual > recorded.expectedAfter + 0.01F
                        && Math.abs(actual - phantomUntouched) <= 0.01F) {
                    target.setAbsorptionAmount(recorded.expectedAfter);
                    it.remove();
                }
            }
        }
    }

    @SubscribeEvent(priority = EventPriority.NORMAL)
    public static void onLivingDamagePre(LivingDamageEvent event) {
        DamageSource source = event.getSource();
        Entity directEntity = source.getDirectEntity();

        LivingEntity target = event.getEntity();
        // A3:先把本次伤害实例的受击侧倍率固化到局部变量(前置 HIGH 监听器已登记)——
        // 后续若发生**嵌套**伤害实例(溅射/AOE/反击注入等),登记槽会被那些实例刷新,
        // 此处先取值可保证骰战路径搬运的仍是"本实例"的倍率(仅同一受害者的登记才会被读取)。
        double victimFactor = DiceCombatModifiers.instanceVictimFactor(target);
        // P2-C8(仅 1.20.1):七咒 ratio 的捕获点是 FateGuidanceCardItem 的 LivingHurtEvent@LOWEST,
        // **晚于**本模组受击侧修饰器的应用点(HIGH)⇒ 捕获到的 ratio 已把本次 ×1.4 计入;
        // 骰战路径随后用该 ratio 应用一次(见 EXTERNAL_DAMAGE_FACTORS 内置因子),
        // 若此处再搬运一次受击侧倍率就会变成 ×1.96。故 ratio > 1(说明本次增伤已进入 ratio 链路)时不再搬运。
        // 1.21.1 的捕获点在 LivingIncomingDamageEvent(早于本模组的应用点),ratio 不含该倍率,无需此规则。
        if (victimFactor != 1.0 && target instanceof Player cursed
                && ModAttachments.getDiceCurseRatio(cursed) > 1.0f) {
            victimFactor = 1.0;
        }

        // 立牌受击钩子分发(史莱姆立牌等受击类被动由各立牌 onHurt 实现,不再在此硬编码)
        if (!target.level().isClientSide() && target instanceof Player targetPlayer) {
            BaseSignItem.invokeHurtHooks(targetPlayer, event.getSource(), event.getAmount());
            // 缓冲盾牌筹码:受到**敌对目标**攻击时 +2 治愈 +3 星币(每 15 秒一次;
            // 2026-09-29 收紧 —— 环境伤害与自伤不再触发,判定见 DiceCombatEvents#isHostileAttack)
            com.merlinkitsune.astral_dice.item.chip.BufferShieldChipItem.onHurt(targetPlayer, event.getSource(), event.getAmount());
        }

        // 反击链中的伤害不进入骰战结算(已按反击公式自算),同时结构性阻止反击递归
        if (counterDepth > 0) return;

        // 白泽赐福 / 降神:倒计时由「被施加者实施**一次有效攻击**」启动(2026-09-28 用户裁决)。
        // 判据 = **任意攻击行为(近战或远程)且必须命中目标** —— 本事件到达本身即"命中"(伤害正在落地),
        // 故不再要求近战武器;只要求伤害的**来源实体**是该玩家、且目标属于骰神赐福合法目标。
        // ⚠️ 位置必须在下方 isMeleeWeaponAttack 闸门**之前**:远程(箭/投掷物)的 directEntity 是弹射物,
        //    走不进下方玩家分支,挂在那之后永远起不了表。
        // 只启动一次(内部判定 timer_started),之后重复攻击不重置;被施加者身上**不需要**有骰子。
        if (!target.level().isClientSide()
                && source.getEntity() instanceof Player timerAttacker
                && timerAttacker != target
                && isBlessingTarget(target, timerAttacker)) {
            com.merlinkitsune.astral_dice.item.sign.ZhaoSignItem.onBlessingTimerAttack(timerAttacker);
            com.merlinkitsune.astral_dice.item.sign.TeruSignItem.onDescentTimerAttack(timerAttacker);
        }

        // 秘密侦探「隐匿」(2026-09-28 用户裁决):玩家对**非玩家实体**造成有效伤害(任意攻击方式均算)
        // ⇒ 立即解除隐匿;解除后由 DiceCombatModifiers 的额外加伤修饰器追加「目标标记层数」伤害,
        // 直到调查阶段增益结束。幂等,内部自判是否处于隐匿。
        if (!target.level().isClientSide()
                && source.getEntity() instanceof Player concealBreaker
                && !(target instanceof Player)) {
            com.merlinkitsune.astral_dice.effect.ConcealmentEffect.breakOnAttack(concealBreaker);
        }
        // AOE(顺劈/溅射/法伤波及)波及的目标**只**拦骰战结算,不再抵销「攻击行为」本身
        // (2026-10-27 用户裁决 N14:AOE 命中也算) ⇒ 上面的赐福/降神倒计时启动与隐匿解除
        // 已提到本闸门**之前**执行;此处仅保证被波及目标不二次吃到完整骰战。
        if (isInternalAoe()) return;

        if (!(directEntity instanceof Player player)) {
            // 生物近战攻击玩家:走**独立**骰战路径(2026-09-26 新增)。
            // ⚠️ 该路径不进入下方玩家攻击逻辑(DiceCombatContext 要求 Player 攻击者),
            //    也**不触发骰神赐福** —— 赐福只能由玩家主动近战攻击触发。
            if (directEntity instanceof Mob mob && target instanceof Player mobTarget) {
                resolveMobMeleeAttack(mob, mobTarget, event, source, victimFactor);
            }
            return;
        }
        if (target == player) return;

        // 手电筒-爆闪筹码:攻击「该玩家未攻击过的目标（玩家×目标判重）」⇒ +1 星光
        //(去重载体 = **该玩家自己的**玩家级附件 flashlight_burst_targets,记录满 256 后不再发放;目标须为敌对)。
        // 位置:在「骰神赐福 / 近战武器」闸门**之前** ⇒ 不要求触发赐福、不要求主手持近战武器;
        // 但上方 `directEntity instanceof Player` 闸门已保证"这一击的直接来源就是该玩家本人"
        //(箭矢等弹射物的 directEntity 是弹射物 ⇒ 不计)。
        com.merlinkitsune.astral_dice.item.chip.FlashlightBurstChipItem.onAttack(player, target);

        // 电磁炮筹码:对敌对目标发起攻击时消耗 6 层充能,延迟 1 秒对目标 3 格内敌对目标降下雷击。
        // ⚠️ **触发限「该玩家本人的近战武器攻击」**（2026-10-01 用户裁决）：判据见
        //    {@link #isPlayerMeleeAttack} —— 排除任意非近战 / 非玩家自身的攻击、荆棘与其它反伤
        //    （以玩家为归属但非其挥击）、宠物（狼与车万女仆均走 mob_attack）。
        // 雷击伤害 = 本次攻击伤害的 50%:此处骰战尚未结算,先按即时伤害兜底登记,
        // 骰战最终伤害确定后(下方 setAmount 之后)回填。
        var railgunStrike = isPlayerMeleeAttack(player, source)
                ? com.merlinkitsune.astral_dice.item.chip.RailgunChipItem.onAttack(
                        player, target, event.getAmount())
                : null;

        // 法伤链不参与骰战结算(2026-10-27 折叠批复核 R5 补口):
        // 法伤加成已折叠进**本次**伤害事件(DamageEffectCardHandler),而骰战 settle 是覆盖式写值
        // (自算终值 setNewDamage/setAmount、不读事件现值)⇒ 法伤一旦走进 settle,折叠加成会被整段覆盖。
        // 本仓自产法伤源 getDirectEntity() 恒 null(必然在上面早退),但**第三方法术源**的直接实体可能就是
        // 玩家本人;此处按伤害类型白名单显式排除,不再依赖"direct==null"这一巧合。
        // 只拦骰战结算:此前的赐福倒计时启动 / 隐匿解除 / 手电筒爆闪 / 电磁炮保持原有触发面(技能命中算攻击)。
        if (SpellDamageRegistry.isSpellDamage(source, directEntity)) return;

        // 骰神赐福仅能由近战武器攻击触发与生效:直接伤害来源必须为玩家(已排除弓/弩/三叉戟投掷等远程),
        // 主手必须持有近战武器(排除空手/盾牌/非近战类武器)
        if (!isMeleeWeaponAttack(player)) return;

        // === ATTACKER DICE (unique, via curios dice slot) ===
        ItemStack diceStack = null;
        WeaponEnhancement enhancement = null;
        var attackerCurios = CuriosCompat.getCuriosInventory(player);
        if (attackerCurios.isPresent()) {
            var diceResult = attackerCurios.get().findFirstCurio(DiceCurioItem::isDiceItem);
            if (diceResult.isPresent()) {
                diceStack = diceResult.get().stack();
                enhancement = ModDataComponents.WEAPON_ENHANCEMENT.getOrDefault(diceStack, null);
            }
        }

        // 占星师/秘密侦探立牌主动已迁移至目标选择器(TargetSelectionManager + HaiqingSignItem/BonnieSignItem 的
        // TargetSelectionAction.apply),不再于攻击时自动释放,此处无攻击释放逻辑。
        // 枪匠立牌主动同样已迁移至目标选择器(见 MosesSignItem 注册的 TargetSelectionAction),此处仅保留其被动:
        // 攻击已带"破绽"的目标,每次攻击获得 1 层「弱点识破」(2026-10-02 起:每次都给,不再限每段一次)。
        // 触发条件与骰神赐福完全一致:近战武器(外层已判定)+ isBlessingTarget(外层已判定),
        // 因此不再额外限制"普通敌对生物"(2026-09-24 与 26.1.2 线回填对齐;
        // 旧判据 `target instanceof Enemy` 比外层闸门更窄,使中立生物(狼/铁傀儡/北极熊/蜜蜂、山羊等)
        // 身上带破绽时拿不到弱点识破)。
        if (!player.level().isClientSide() && attackerCurios.isPresent() && isBlessingTarget(target, player)) {
            // 枪匠立牌:攻击已带"破绽"的目标 ⇒ 立牌钩子分发(佩戴与破绽闸门由 MosesSignItem 实现内部判定)
            SignHookDispatcher.fireAttackBrokenTarget(player, target);
        }

        // 本次攻击是否触发了骰神赐福(与赐福触发逻辑一致:仅在未拥有赐福时触发;同一挥击命中多目标也仅触发一次)
        boolean triggeredBlessing = false;
        // 大当家立牌被动"战斗爽·溅射":满层赐福触发时置位,本次攻击伤害定稿后立即引爆一次
        boolean fenSplashArmed = false;
        if (!player.level().isClientSide() && diceStack != null && !player.hasEffect(ModEffects.DICE_BLESSING.get())
                && isBlessingTarget(target, player)) {
            // 六参构造:visible=false 禁用粒子,showIcon=true 让赐福图标(含剩余时间)在 HUD 效果栏正常显示。
            // ⚠️ 五参构造 (…, ambient, visible) 内部等价于 showIcon=visible ⇒ 传 false 会把 HUD 图标一并隐藏
            // (物品栏效果面板不读 showIcon,故只在物品栏可见)——与本模组其它状态效果(充能/赋能/弱点识破/治愈)
            // 一律用 (…, false, false, true) 的口径保持一致,不得改回五参。
            player.addEffect(new MobEffectInstance(ModEffects.DICE_BLESSING.get(),
                    GameplayConstants.DICE_BLESSING_DURATION_TICKS, 0, false, false, true));
            triggeredBlessing = true;
            // 新赐福周期:重置“防御牌已消耗”标记,确保本次赐福期间最多消耗一次防御牌耐久
            ModAttachments.setDefenseCardConsumedThisBlessing(player, false);
            // 新赐福周期:重置诅咒之剑“本次赐福已触发”标记
            ModAttachments.setCursedSwordBlessingTriggered(player, false);
            // 新赐福周期:重置财运签“本次赐福已触发”标记(与诅咒之剑同批同处复位,每赐福周期一次)
            ModAttachments.setFortuneStickBlessingClaimed(player, false);
            // 玩家对玩家:若被攻击方也佩戴骰子,则同时触发其骰神赐福(双方都拥有骰子时)
            if (target instanceof Player targetPlayer) {
                var targetCurios = CuriosCompat.getCuriosInventory(targetPlayer);
                if (targetCurios.isPresent()) {
                    var targetDiceResult = targetCurios.get().findFirstCurio(DiceCurioItem::isDiceItem);
                    if (targetDiceResult.isPresent() && !targetPlayer.hasEffect(ModEffects.DICE_BLESSING.get())) {
                        // 同主分支:六参构造保持 HUD 图标可见(粒子仍由 visible=false 关闭)
                        targetPlayer.addEffect(new MobEffectInstance(ModEffects.DICE_BLESSING.get(),
                                GameplayConstants.DICE_BLESSING_DURATION_TICKS, 0, false, false, true));
                        ModAttachments.setDefenseCardConsumedThisBlessing(targetPlayer, false);
                        ModAttachments.setCursedSwordBlessingTriggered(targetPlayer, false);
                        ModAttachments.setFortuneStickBlessingClaimed(targetPlayer, false);
                    }
                }
            }
            // 标靶筹码:触发骰神赐福后,对距离最近的**其他**目标施加一层标记(无固定范围常量)。
            // 2026-09-24 用户裁决:不再判定「本次正在攻击的那个目标」—— 标记要落到**别的**目标身上。
            if (attackerCurios.isPresent()) {
                var targetChipResult = attackerCurios.get().findFirstCurio(s -> s.is(ModItems.TARGET_CHIP.get()));
                if (targetChipResult.isPresent()) {
                    LivingEntity nearest = null;
                    double nearestDistSqr = Double.MAX_VALUE;
                    net.minecraft.server.level.ServerLevel serverLevel =
                            (net.minecraft.server.level.ServerLevel) player.level();
                    for (net.minecraft.world.entity.Entity entity : serverLevel.getEntities().getAll()) {
                        if (entity instanceof LivingEntity living
                                // 上下文重载:攻击者"视谁为敌"(全局规则,含曾主动攻击过攻击者的非同队玩家)
                                // ⚠️ 排除本次攻击的目标(2026-09-24 用户裁决)与玩家自身
                                && living != target && living != player
                                && PartyRelations.isHostileTo(player, living) && living.isAlive()) {
                            double distSqr = living.distanceToSqr(player);
                            if (distSqr < nearestDistSqr) {
                                nearestDistSqr = distSqr;
                                nearest = living;
                            }
                        }
                    }
                    if (nearest != null) {
                        MarkManager.apply(nearest, 1200);
                    }
                }
            }
            // 星币锤筹码:每次进入骰神赐福时,若持有星币超过 20 枚,则消耗 6 星币并按持有总数 30% 提升攻击力
            if (attackerCurios.isPresent()) {
                var hammerResult = attackerCurios.get().findFirstCurio(s -> s.is(ModItems.STAR_COIN_HAMMER.get()));
                if (hammerResult.isPresent()) {
                    com.merlinkitsune.astral_dice.item.chip.StarCoinHammerChipItem.onBlessingStart(player);
                }
            }
            // 永动机筹码:触发骰神赐福时,充能 +6
            PerpetualMotionChipItem.onBlessingStart(player);
            // 高级外设筹码:触发骰神赐福时,移除 1 层充能
            AdvancedPeripheralsChipItem.onBlessingStart(player);
            // 会员推荐信筹码:触发骰神赐福时,获得一张随机卡牌
            com.merlinkitsune.astral_dice.item.chip.MemberRecommendationChipItem.onBlessingStart(player);
            // 大当家立牌:触发骰神赐福 → 记录触发时刻;养精蓄锐满层则消耗 2 层并置位本次攻击的溅射
            fenSplashArmed = SignHookDispatcher.fireBlessingTriggered(player);
            // 蛟龙立牌(mamushi)专属战斗牌「撕咬」:触发骰神赐福时,每张**装备中**的撕咬 +1 层觉醒并锁存加成
            SignHookDispatcher.fireDiceBlessingTriggered(player);
            // 修女立牌(alana)「鼓起勇气」已并入上面的 fireBlessingTriggered(同为赐福触发钩子)。
            // 治愈体系:触发骰神赐福 → 医疗箱加点(先)+ 按当前治愈点×2 回血(后)。
            // 置于触发块末尾,确保晚于本事件内所有影响治愈点数量的效果(立牌受击钩子/缓冲盾牌在前部已执行)
            com.merlinkitsune.astral_dice.item.HealingManager.onBlessingTriggered(player);
        }

        // === 骰战模型适用闸门(2026-09-26 用户裁决:骰战对玩家常驻,"是否装备骰子"不再是门槛) ===
        // · 装备骰子 —— 沿用旧口径:必须处于骰神赐福生效时(赐福由本次攻击或既有 buff 提供);
        // · 未装备骰子 —— **不再退出骰战**:只要目标属于骰战适用目标(isBlessingTarget)即以
        //   "双方骰点恒为 0"参与结算 —— 与"生物→玩家"路径的 opposed = hasDice(target) 对称。
        //   一方没有骰点,另一方也不得掷:攻击方骰点见下方 baseDice 归零;防御方(生物/玩家)
        //   骰点见 DEFENSE 段的 opposed 判定。
        if (diceStack == null) {
            if (!isBlessingTarget(target, player)) return;
        } else if (!player.hasEffect(ModEffects.DICE_BLESSING.get())) {
            return;
        }
        if (enhancement == null) {
            enhancement = WeaponEnhancement.EMPTY;

            // 未装备骰子时 diceStack 为 null:无处写回默认组件,且本就无卡牌可耗,故加 null 守卫。
            if (diceStack != null && !ModDataComponents.WEAPON_ENHANCEMENT.has(diceStack)) {
                ModDataComponents.WEAPON_ENHANCEMENT.set(diceStack, WeaponEnhancement.EMPTY);
            }
        }

        // 未装备骰子 ⇒ 骰点恒为 0(2026-09-26 用户裁决:一方没有骰点,另一方也不得掷)。
        int baseDice = (diceStack != null) ? rollCombatDie(player) : 0; // 特殊骰子掷骰(诡异骰子低点数偏置/绯红骰子高点数偏置)

        // === MISAKI SIGN (护法立牌, via curios stand slot) ===
        boolean misakiFound = false;
        // 护法立牌星级 = 玩家装备骰子的星级(立牌无独立升星;此处 enhancement 已兜底非 null)
        int misakiStar = enhancement.starLevel();
        int misakiStacks = 0;
        ItemStack misakiStack = null;
        if (attackerCurios.isPresent()) {
            var result = attackerCurios.get().findFirstCurio(s -> s.is(ModItems.MISAKI_SIGN.get()));
            if (result.isPresent()) {
                misakiFound = true;
                misakiStack = result.get().stack();
                misakiStacks = ModDataComponents.MISAKI_SIGN_STACKS.getOrDefault(misakiStack, 0);
            }
        }
        // 护法立牌(misaki):爆发状态(主动技能触发,持续 60 秒)
        boolean misakiBurst = misakiFound && player.hasEffect(ModEffects.MISAKI_BURST.get());

        // 护法立牌(misaki)被动:触发骰神赐福时累积层数(最大 3 层)
        if (triggeredBlessing && misakiFound && !player.level().isClientSide()) {
            int newStacks = Math.min(misakiStacks + 1, 3);
            ModDataComponents.MISAKI_SIGN_STACKS.set(misakiStack, newStacks);
            misakiStacks = newStacks;
        }

        // 护法立牌(misaki):爆发期间战斗骰点按星级追加基础数字(1⭐+1,2⭐+2,3⭐+3)
        if (misakiBurst) {
            int starBonus = switch (misakiStar) {
                case 1 -> 1;
                case 2 -> 2;
                case 3 -> 3;
                default -> 0;
            };
            baseDice += starBonus;
        }

        // 上班族立牌:赐福期间骰点为1时,下次攻击骰点必为6
        if (attackerCurios.isPresent()) {
            var padmanResult = attackerCurios.get().findFirstCurio(s -> s.is(ModItems.PADMAN_SIGN.get()));
            if (padmanResult.isPresent()) {
                ItemStack padmanStack = padmanResult.get().stack();
                if (ModDataComponents.PADMAN_FORCE_SIX.getOrDefault(padmanStack, false)) {
                    baseDice = 6;
                    ModDataComponents.PADMAN_FORCE_SIX.set(padmanStack, false);
                } else if (baseDice == 1) {
                    ModDataComponents.PADMAN_FORCE_SIX.set(padmanStack, true);
                }
            }
        }

        // ⚠️ 未装备骰子:上方全部骰点加成链(护法爆发追加 / 上班族"下次必 6")一律作废 ——
        // 骰点恒为 0(2026-09-26 用户裁决:一方没有骰点,另一方也不得掷)。
        // 命运的指引(2026-09-28 用户裁决):激活期间玩家战斗骰点**始终为 6**。
        // ⚠️ 位置在「未装备骰子 ⇒ 骰点恒 0」之前:没有骰子仍然恒 0(与上班族"下次必 6"同口径)。
        if (!player.level().isClientSide() && FateGuidanceCardItem.isFateGuidanceActive(player)) {
            baseDice = 6;
        }

        if (diceStack == null) baseDice = 0;

        // 风水师立牌(zhao)被动「福祸相倚」:骰点定稿后判定 —— 结果为 1 ⇒ 获得 1 张符卡-祸;
        // 为 6 ⇒ 获得 1 张符卡-福(卡牌在发放那一刻绑定获得者)。
        // ⚠️ 挂点必须在本处(骰点**已被全部修正方改写之后**):上班族立牌的"骰点为 1 则下次必为 6"
        // 会把 baseDice 直接改写成 6,挂在它之前会读到被覆盖掉的旧值。
        // ⚠️ 「一次结算一次判定」:同一挥击命中多目标会多次进入本事件 ⇒ 由
        // ZhaoSignItem#tryClaimDiceJudgment 的**实例内**标记(纯静态槽位,不写附件、不跨实例持久化,
        // 同 DiceCombatModifiers#instanceVictim 口径)防重复发牌。
        if (!player.level().isClientSide()
                && com.merlinkitsune.astral_dice.item.sign.ZhaoSignItem.tryClaimDiceJudgment(player)) {
            SignHookDispatcher.fireDiceJudgmentRoll(player, baseDice);
        }

        // 人偶师立牌(hanna)「幻想千金」:战斗骰点 = 6 ⇒ 佩戴者获得 1 星币。
        // 「每 1:00 仅触发 1 次」由立牌自己的 hanna_fantasy_cooldown_end 附件保证 ——
        // 同一挥击命中多个目标会多次进入本处,第二次起被冷却挡下。
        if (!player.level().isClientSide()) {
            SignHookDispatcher.fireDiceRollResult(player, baseDice);
        }

        // ⚠️ 2026-10-08 用户裁决「重写经商立牌被动」:原「触发骰神赐福后立即获得 骰点×2 星光」已移除
        // (被动「传奇商人」改为商店/兑换向的五条内容,见 shop/CardShopMenu 与 AGENTS 立牌技能表)。

        // 占星师立牌被动:骰神赐福期间骰点=6 时立即获得 6 星币
        if (!player.level().isClientSide() && baseDice == 6 && attackerCurios.isPresent()) {
            var haiqingResult = attackerCurios.get().findFirstCurio(s -> s.is(ModItems.HAIQING_SIGN.get()));
            if (haiqingResult.isPresent()) {
                ItemStack coinStack = new ItemStack(ModItems.STAR_COIN.get(), 6);
                com.merlinkitsune.astral_dice.resource.ResourceConversion.giveItem(player, coinStack);
            }
        }

        // 八面骰筹码:触发骰神赐福后,使用通用掷骰方法掷 1d10 并提示玩家,累计点数;
        // 每满 8 点 +1 星光(上限 MAX_STARLIGHT),本次骰点恰为 8 时立即获得 8 个星币
        if (triggeredBlessing && attackerCurios.isPresent()) {
            var eightResult = attackerCurios.get().findFirstCurio(s -> s.is(ModItems.EIGHT_SIDED_DICE.get()));
            if (eightResult.isPresent()) {
                int roll = rollDice(10);
                notifyRoll(player, roll);
                if (roll == 8) {
                    ItemStack coinStack = new ItemStack(ModItems.STAR_COIN.get(), 8);
                    com.merlinkitsune.astral_dice.resource.ResourceConversion.giveItem(player, coinStack);
                }
                int starlight = StarLightManager.get(player);
                int accum = ModAttachments.getEightSidedAccum(player) + roll;
                while (accum >= 8 && starlight < StarLightManager.getCap()) {
                    accum -= 8;
                    starlight++;
                }
                // 星光已满时**保留**累计点数(不清零),待星光回落后继续换算
                ModAttachments.setEightSidedAccum(player, accum);
                StarLightManager.set(player, Math.min(starlight, StarLightManager.getCap()));
            }
        }

        // === ATTACK POWER (基础值 + 修饰器注册表) ===
        com.merlinkitsune.astral_dice.combat.DiceCombatContext ctx =
                new com.merlinkitsune.astral_dice.combat.DiceCombatContext(
                        player, target, event, baseDice, diceStack, enhancement,
                        triggeredBlessing, misakiBurst, misakiStar, misakiStacks);

        // === 基础段:属性攻击力 × 攻击冷却缩放(2026-09-26 裁决③:冷却一并处理) ===
        // 原版 Player#attack 的分段(f = 属性值、f1 = 附魔加伤、f2 = 攻击冷却缩放):
        //   f1 = EnchantmentHelper.getDamageBonus(主手, 目标类型)    ← 1.20.1 是**绝对量**(非增量)
        //   f *= 0.2 + f2²·0.8                                      ← 基础段按冷却缩放
        //   f1 *= f2                                                 ← 附魔加伤只乘一次 f2
        //   if (crit) f *= 1.5                                       ← ★ 只乘基础段
        //   f += f1                                                  ← 附魔加伤加在暴击之后
        // 骰战逐段照搬:基础段吃冷却与暴击;附魔加伤两者都不吃;骰点与卡牌作为**其后**的加项,同样不吃暴击。
        // ⚠️ 2026-10-15 修复(永动机攻速副作用):上面这个 f2 取自 diceBattleAttackStrengthScale(player),
        //    已**扣除「永动机」筹码的攻击速度贡献** —— 该筹码的攻速 ×2 只应让挥击更快,不得抬高
        //    「同一真实蓄力节奏」下的骰战攻击力。本段的 f2 同时供暴击判定与附魔增量缩放使用
        //    (与原版「同一个 f2 驱动三处」同构)。
        double rawAttackDamage = player.getAttributeValue(Attributes.ATTACK_DAMAGE);
        float attackCooldown = diceBattleAttackStrengthScale(player);
        double attackPower = rawAttackDamage * (0.2 + attackCooldown * attackCooldown * 0.8);
        for (var modifier : com.merlinkitsune.astral_dice.combat.DiceCombatModifiers.attackModifiers()) {
            attackPower = modifier.apply(ctx, attackPower);
        }
        int attackCardSum = ctx.attackCardSum;
        boolean hasShadowStrike = ctx.hasShadowStrike;
        boolean hasFullPower = ctx.hasFullPower;
        // 防御性兜底:即使未来调整掷骰注册顺序/逻辑,只要骰子已装载全力攻击,就必须应用最终攻击力 +50%
        if (!hasFullPower && enhancement != null) {
            for (AppliedStone stone : enhancement.appliedStones()) {
                if ("full_power".equals(stone.type())) {
                    hasFullPower = true;
                    break;
                }
            }
        }

        // === 神秘遗物+ 联动:七咒之戒 ===
        // 佩戴七咒之戒时,骰子伤害加成(骰点 + 卡牌点数)降低 40%;手持"启示之证"攻击时,减益再降低 20%;
        // 装备"倒转之启"或"恩惠之典"时修正第四诅咒,骰子总能造成全额伤害(完全免疫七咒减益);
        // 护法立牌"爆发"效果期间同样修正第四诅咒:总能造成全额伤害
        // === 暴击(2026-09-26 裁决②:分拆口径) ===
        // ×1.5 **只作用于原版基础段**(属性值 × 冷却缩放 + 攻击修饰器):附魔加伤、骰点、卡牌均不吃 ——
        // 与原版 `f *= 1.5; f += f1` 同域 ⇒ 暴击的实测倍率恰为 1.5(与原版一致)。
        if (isVanillaCriticalHit(player, target, attackCooldown)) {
            attackPower *= 1.5;
        }

        // === 附魔加伤(2026-09-26 裁决④:附魔项加入) ===
        // 1.20.1 的附魔加伤是**绝对量**(EnchantmentHelper.getDamageBonus(主手, 目标 MobType)),
        // 故直接加上并按源码 `f1 *= f2` 乘一次冷却系数。
        attackPower += enchantDamageBonus(player, target) * attackCooldown;

        double diceAttackBonus = applyCurseToDicePoints(player, baseDice + attackCardSum);
        // 基础伤害值(属性 + 附魔 + 冷却/暴击 + 立牌/筹码/效果攻击修饰器,不含骰点/卡牌加成):
        // 供闪避失败结算使用(该路径当前恒不启用,见 PLAYER_DODGE_ENABLED)。
        double baseDamage = attackPower;
        attackPower += diceAttackBonus;

        // === 立牌/筹码攻击加成已全部迁移至 DiceCombatModifiers 攻击修饰器注册表 ===

        if (hasFullPower) {
            attackPower = Math.ceil(attackPower * 1.5);
        }

        // === DODGE / DEFENSE POWER ===
        // 对骰统一口径(2026-09-26 用户裁决):**一方能掷则另一方也必须掷**,反之亦然。
        // 防御方是否掷防御骰,只看「是否装备骰子」——
        // 玩家装备骰子即掷(不再要求骰神赐福生效);未装备则不掷(对骰不成立)。
        // 玩家侧闪避判定仍停用(PLAYER_DODGE_ENABLED=false),闪避代码保留供未来使用。
        // 怪物(含无护甲):不闪避,始终防御。⚠️ **防御骰只在攻击方装备了骰子时掷 1d6** ——
        // 攻击方没有骰子 ⇒ 一方没有骰点,另一方也不得掷 ⇒ 双方骰点恒 0(2026-09-26 用户裁决);
        // 最终伤害仍按骰战公式(= 攻击力 − 防御力)结算,**不退回原版护甲减免**。
        boolean skipDefense = false;
        boolean dodgeFailed = false;
        double dodgeFailDamage = 0;
        int defenseBaseDice = 0;
        if (!player.level().isClientSide() && target instanceof Player targetPlayer) {
            var targetCurios = CuriosCompat.getCuriosInventory(targetPlayer);
            if (targetCurios.isPresent()) {
                var targetDiceResult = targetCurios.get().findFirstCurio(DiceCurioItem::isDiceItem);
                if (PLAYER_DODGE_ENABLED && targetDiceResult.isEmpty()) {
                    // === 玩家侧闪避对骰(保留供未来使用) ===
                    // 攻击方与目标各掷 1d6;目标骰点更高或为 6 → 闪避成功,忽略本次伤害(return);
                    // 否则闪避失败 → 伤害 = 基础伤害值 + 攻击方骰点 + 卡牌加成
                    // (基础伤害值 = 属性攻击 + 立牌/筹码/效果攻击修饰器;骰点/卡牌均取本次实际掷出的值),
                    // 跳过防御;全力攻击倍率在最终伤害处适用。
                    int attackRoll = ThreadLocalRandom.current().nextInt(1, 7);
                    int dodgeRoll = ThreadLocalRandom.current().nextInt(1, 7);
                    if (dodgeRoll > attackRoll || dodgeRoll == 6) {
                        return;
                    }
                    dodgeFailed = true;
                    dodgeFailDamage = baseDamage + baseDice + attackCardSum;
                } else if (diceStack != null && targetDiceResult.isPresent()) {
                    // 防御方装备骰子即掷(2026-09-26:不再要求 DICE_BLESSING 生效)——
                    // 对骰成立后双方各掷 1d6,但**不**因此触发骰神赐福。
                    // 特殊骰子掷骰(防御方:诡异骰子低点数偏置,绯红骰子高点数偏置)
                    defenseBaseDice = rollCombatDie(targetPlayer);
                    // 感测装置筹码:目标每 3 层标记 ⇒ **其**战斗骰点 -1(最多 -4,下限 0)。
                    // 只扣目标方骰点,不影响攻击方(见 SensorChipItem#reduceTargetDice)。
                    defenseBaseDice = com.merlinkitsune.astral_dice.item.chip.SensorChipItem
                            .reduceTargetDice(player, targetPlayer, defenseBaseDice);
                    // 防御卡掷骰由注册表防御修饰器执行(读 ctx.targetEnhancement,写 ctx.defenseCardSum)
                    ItemStack targetDice = targetDiceResult.get().stack();
                    WeaponEnhancement targetEnh = ModDataComponents.WEAPON_ENHANCEMENT.get(targetDice);
                    ctx.targetEnhancement = targetEnh;
                    // 玻璃骰子:防御方佩戴时,防御牌点数始终取最大值
                    ctx.targetCardsMax = targetDice.is(ModItems.GLASS_DICE.get());
                }
            }
        } else if (!target.level().isClientSide() && !(target instanceof Player)) {
            // 怪物:不闪避,始终防御。⚠️ 防御骰**仅在攻击方装备了骰子时**掷 1d6;
            // 攻击方没有骰子 ⇒ 双方骰点恒 0(2026-09-26 用户裁决:一方没有骰点,另一方也不得掷),
            // 但骰战公式照常生效、不退回原版。
            // 枪匠"破绽":目标骰点只能为 0
            // ⚠️ 2026-09-26 用户裁决:攻击方未装备骰子 ⇒ 一方没有骰点,另一方也不得掷 ⇒ 防御骰恒 0。
            if (diceStack == null || target.hasEffect(ModEffects.MOSES_BROKEN.get())) {
                defenseBaseDice = 0;
            } else {
                defenseBaseDice = ThreadLocalRandom.current().nextInt(1, 7);
            }
            // 感测装置筹码:目标每 3 层标记 ⇒ **其**战斗骰点 -1(最多 -4,下限 0;攻击方骰点不受影响)
            defenseBaseDice = com.merlinkitsune.astral_dice.item.chip.SensorChipItem
                    .reduceTargetDice(player, target, defenseBaseDice);
        }

        double finalDmg;
        if (dodgeFailed) {
            // 闪避失败结算(与上方闪避对骰一起保留供未来使用;当前 dodgeFailed 恒为 false)
            finalDmg = hasFullPower ? Math.ceil(dodgeFailDamage * 1.5) : dodgeFailDamage;
        } else {
            double defensePower;
            if (skipDefense) {
                defensePower = 0;
            } else {
                // 先累计所有防御修饰器加成(防御卡掷骰会写入 ctx.defenseCardSum,不直接改变 modifierDefense)
                double modifierDefense = 0;
                for (var modifier : com.merlinkitsune.astral_dice.combat.DiceCombatModifiers.defenseModifiers()) {
                    modifierDefense = modifier.apply(ctx, modifierDefense);
                }

                // 效果牌/立牌/筹码的防御力已折算为真实护甲(1 防御力 = 1/护甲系数 护甲值,
                // 见 DiceCombatModifiers.setDefenseArmorBonus),effectiveArmorPoints() 已包含;
                // modifierDefense 恒为 0(仅防御卡掷骰写入 ctx.defenseCardSum 作为防御点直接加入)。
                // === 玩家与生物公式分离(2026-09-26 用户裁决) ===
                // · 护甲 20 硬上限**两侧一律移除**;
                // · 护甲取**属性终值**(ARMOR 属性 double 值,见 effectiveArmorPoints) ⇒ 状态效果与第三方模组(如七咒之戒)修饰
                //   完毕后的真实值先参与换算,本模组**不再二次换算**(最大模组兼容性);
                // · 玩家:2 + 护甲×0.30 + 0.85×韧性;
                // · 生物:初始值(敌对 0 / 中立 0 / 被动 0 / 友好 0) + 护甲×0.40 + 1.0×韧性,最终取整。
                //   ↑ 两式均取自库 CombatFormula,本模组不再内联系数(2.0.0-SNAPSHOT.2 起)。
                double effectiveArmor = Math.max(0, com.merlinkitsune.astral_dice.combat.DiceCombatModifiers.effectiveArmorPoints(target)
                        + com.merlinkitsune.astral_dice.combat.DiceCombatModifiers.armorValueForDefensePoints(modifierDefense, target instanceof Player));
                double toughness = target.getAttributeValue(Attributes.ARMOR_TOUGHNESS);
                if (target instanceof Player) {
                    defensePower = com.merlinkitsune.starengine.combat.CombatFormula
                            .playerDefense(effectiveArmor, toughness);
                } else {
                    defensePower = com.merlinkitsune.starengine.combat.CombatFormula
                            .mobDefenseInt(
                                    com.merlinkitsune.starengine.combat.TargetCategory.classify(target),
                                    effectiveArmor, toughness);
                }
                defensePower += defenseBaseDice + ctx.defenseCardSum;
            }

            // 上班族立牌:攻击骰为 6 时无视目标防御力——按本模组「目标防御力」口径
            // (与贯穿之铳同一公式:2 + 护甲×0.30 + 0.85×韧性;护甲已包含由防御力折算而来的部分)
            // 整项不计入防御,但保留目标的防御骰与防御牌加成。
            if (ctx.padmanDefBypass && !skipDefense) {
                defensePower = defenseBaseDice + ctx.defenseCardSum;
            }

            // 骰战层:库统一口径「max(绝对下限 1 点, 攻击点 ×15%, 攻击点 − 防御点)」
            // (2026-09-26 裁决⑥:相对下限入库 —— 修「高防+低攻档位恒定触底 ⇒ 任何修饰器读数都退化为 0」)
            finalDmg = com.merlinkitsune.starengine.combat.DiceBattleResolver
                    .resolve(attackPower, defensePower);

        }

        // === 受击方「抗性提升 + 保护附魔」乘算因子(2026-09-26 裁决①:方案 A) ===
        // 原版这两条通道在 LivingDamageEvent **之前**已算完,而骰战在此处覆盖式写入自算终值 ⇒
        // 整段减免被丢弃。此处按库 VanillaMitigation 补乘回来,使两条通道在骰战下与原版等价。
        // ⚠️ 「护甲」不在此列:它已折算进防御力(CombatFormula),再乘一次就是重复计算。
        finalDmg *= com.merlinkitsune.starengine.combat.VanillaMitigation.magicAbsorbFactor(
                resistanceAmplifierOf(target), protectionPointsOf(target, source));

        if (MarkManager.getLevel(target) > 0) {
            finalDmg += 1;
        }

        // 虚弱印记:目标受到任意伤害 +10%;命运指引激活时额外 +20%(骰战自包含结算,
        // 不依赖外部处理器顺序;onWeakMarkDamage 仅对非骰战攻击生效)
        if (target.hasEffect(ModEffects.WEAK_MARK.get())) {
            float weakMultiplier = WEAK_MARK_DAMAGE_MULTIPLIER;
            if (FateGuidanceCardItem.isFateGuidanceActive(player)) {
                weakMultiplier += 0.20f;
            }
            finalDmg *= weakMultiplier;
        }

        // 接管外部伤害影响:按原始设计应用最终伤害数值(各因子相乘)。
        // 附属内容可通过 registerDiceCombatFactor 注册自定义因子,影响骰战最终伤害。
        for (DiceCombatFactor factor : EXTERNAL_DAMAGE_FACTORS) {
            finalDmg = factor.modify(player, target, finalDmg);
        }

        // A3:受击侧伤害修饰器(末影骰子雨中/水下 +40% 等)**只搬运、不二次消费** ——
        // 骰战以 setAmount 覆盖式写入自算的最终伤害,前置 HIGH 监听器(修饰器唯一应用点)乘出的那份值
        // 会被整段替换(见 docs/interaction-audit-1.2.1.md 的 A3);若此处再消费一次修饰器,
        // 同一次伤害就会经过两套独立计算(把"丢弃"变成"重复"×1.96)。
        // instanceVictimFactor(target) 取回**同一伤害实例**由前置监听器登记的倍率(仅同一受害者有效),
        // 故整条链上修饰器只求值一次、倍率只落在最终落地的这个值上(恰好 ×1.4 一次)。
        // 注:victimFactor 可能已被上面的七咒 ratio 规则(P2-C8)置为 1.0,避免与 ratio 链路重复计。
        finalDmg *= victimFactor;

        // A4(2026-10-27 近战审计):怪力乱神立牌「攻击被吸附目标 +5」并入骰战终值。
        //   该 +5 原本挂在 LivingDamageEvent(LOWEST),与筹码减伤/安全气囊**同档**,先后取决于注册顺序:
        //   气囊把金额归零之后仍可能被 +5 破防,或 +5 先加 ⇒ 气囊白烧充能(用户裁决:保命必须优先)。
        // MELEE-06(2026-10-27 用户裁决:限定必须为骰战)⇒骰战路径在此一次并入,且这是**唯一**加点;
        //   LinglingSignEvents 已不再挂任何伤害事件（非骰战伤害不再叠加）。
        if (LinglingSignManager.isEquipped(player) && LinglingSignManager.isTrackedByAny(target)) {
            finalDmg += LinglingSignManager.TRACKED_TARGET_BONUS_DAMAGE;
        }

        // N1 + N2:护盾钳制 + 吸收按终值只扣一次(两条写终值路径的唯一出口,见 settleDiceDamage)
        float applied = settleDiceDamage(target, event, (float) finalDmg);

        // F7(2026-10-27 近战审计):修女被动「单次受击 >10 清空加成」的判定已挪进 settleDiceDamage
        //   (公共出口) —— 生物→玩家骰战(resolveMobMeleeAttack)也走 settle,只挂在这条路径会漏判。
        //   applied 变量保留:供下面跳字/耐久等后续逻辑使用语义清晰。
        // 跳数字不再在此直发(2026-09-26):登记组别,由 DamageNumberAggregator 在**伤害管线终值**
        // (LivingDamageEvent#getAmount() = 实际扣血量)上取值,并与本 tick 同目标的其它
        // 攻击力类伤害累加成一个**总值**后一次性下发。
        // ⚠️ 必须显式登记:骰战主伤害用的是**原版** player_attack 伤害源(本模组只 setAmount 改数值、
        //    改类型),类型回落认不出来。
        DamageNumberAggregator.tag(target, source, DamageNumberAggregator.Group.ATTACK);

        // === 额外加伤(astral_dice:extra_damage):星光 / 星币 / 治愈点三项加伤 ===
        // 2026-09-25 用户裁决:这三项**不得并进「攻击力」**(它们会污染教主立牌降神的「狐光攻击基数」
        // 攻击力快照 —— 即「严格排除」),改为命中落地后按**独立伤害类型**单独结算(额外加伤)。
        // 复用大当家溅射的既有范式:主目标此刻正处在自己这次伤害事件内部(原版 hurt 已先写
        // invulnerableTime/lastHurt),不临时清零无敌帧就会被"无敌帧内不更低伤害被丢弃"整段吞掉。
        if (!player.level().isClientSide()) {
            int extraDamage = com.merlinkitsune.astral_dice.combat.DiceCombatModifiers.extraDamageOf(ctx);
            if (extraDamage > 0) {
                int savedInvulnerable = target.invulnerableTime;
                beginAoe();
                try {
                    // 2026-10-27 批次F(P2-A):嵌套 hurt 必须在**真实余额**上结算 —— settle(:941)刚写回的
                    // 吸收里含幻影 H,不剥掉就会被平台 :1669-1670 的 c1 = min(P, 余额) 整段吞掉。
                    withPhantomPeeled(target, () -> {
                        target.invulnerableTime = 0;
                        target.hurt(com.merlinkitsune.astral_dice.damage.ModDamageTypes.extraDamage(
                                target.level(), player), extraDamage);
                        // 跳数字:不再直发,由 DamageNumberAggregator 在受伤事件上取终值并累加。
                        // 组别走**类型回落**即可 —— astral_dice:extra_damage ⇒ 攻击力/伤害加成类(红)。
                    });
                } finally {
                    target.invulnerableTime = savedInvulnerable;
                    endAoe();
                }
            }
        }
        // 蛟龙立牌(mamushi)专属战斗牌「龙之咆哮」:装备者在骰战中命中目标时施加破防(ARMOR -8 = -4 防御)
        // + 缓慢 III,均 1:00;重复命中只刷新时长不叠层(施加口径与时长常量归 MamushiSignItem)。
        if (!player.level().isClientSide()) {
            // 蛟龙立牌:原「骰子卡牌栏装备了 dragon_roar」闸门已下沉到 MamushiSignItem#handleDragonRoar
            SignHookDispatcher.fireDragonRoar(player, target);
        }
        // 电磁炮:以本次骰战最终伤害回填雷击伤害(50%)
        com.merlinkitsune.astral_dice.item.chip.RailgunChipItem.applyFinalDamage(railgunStrike, (float) finalDmg);

        // 防御牌耐久(2026-09-26 用户裁决·更正版):**每次受到骰战攻击**时,
        // 防御方装备的**所有**防御牌各扣 1 点。
        // 未装备骰子 / 无防御牌的防御方无事发生(由方法内部早退保证)。
        if (!player.level().isClientSide() && target instanceof Player targetDefender) {
            consumeAllDefenseCardDurability(targetDefender);
        }

        // 大当家立牌被动(战斗爽·溅射):本次攻击触发骰神赐福且养精蓄锐满层时,触发块已置位;
        // 这里在本次攻击伤害定稿后**立即引爆一次**(单次效果:不再等待下一次赐福,也没有持续期),
        // 并在此刻才扣除养精蓄锐代价(攻击被取消时不会白扣)。
        // 伤害 = 本次攻击伤害的 88%(**下限 5 点**),范围为**目标及其 6 格范围内**(含主目标)的敌对目标,
        // 伤害类型为**真伤**(astral_dice:true_damage,登记于 minecraft:bypasses_armor →
        // 无视护甲值与盔甲韧性;保护附魔与抗性提升不在此口径内,仍会减免);
        // 只打敌对目标(**无友伤**)、不破坏方块,命中仍附带爆炸粒子与音效(视觉表现与伤害类型无关)。
        // 递归保护:溅射伤害不进入骰战结算(aoeDepth 统一闸门),避免二次触发赐福/互相引爆。
        if (!player.level().isClientSide() && fenSplashArmed) {
            SignHookDispatcher.fireSplashCost(player);
            beginAoe();
            try {
                // 下限取立牌常量(5 点),高于全局"按比例不足 1 时按 1 计"的兜底
                float splashDmg = (float) Math.max(
                        com.merlinkitsune.astral_dice.item.sign.FenSignItem.SPLASH_DAMAGE_MIN,
                        finalDmg * com.merlinkitsune.astral_dice.item.sign.FenSignItem.SPLASH_RATIO);
                net.minecraft.world.phys.AABB splashBox = target.getBoundingBox()
                        .inflate(com.merlinkitsune.astral_dice.item.sign.FenSignItem.SPLASH_RANGE);
                var splashVictims = target.level().getEntitiesOfClass(
                        net.minecraft.world.entity.LivingEntity.class, splashBox,
                        // 2026-09-24 口径统一:波及选目标改用**法伤链闸门** isBlessingTarget(比
                        // HostileTargets 更宽)⇒ 与非同队玩家 / Boss / 会反击的中立怪(山羊等)的主目标口径一致
                        e -> isBlessingTarget(e, player) && e.isAlive());
                if (!splashVictims.isEmpty()) {
                    // 真伤伤害源:直接伤害实体为空、击杀归属玩家(与旧 explosion(null, player) 同形状,
                    // 不会被本模组或其它模组再当成一次"玩家的直接攻击"重走命中判定,同时保留击杀归属);
                    // 类型 astral_dice:true_damage 登记于 minecraft:bypasses_armor → 无视护甲值与盔甲韧性。
                    var splashSource = com.merlinkitsune.astral_dice.damage.ModDamageTypes.trueDamage(
                            target.level(), player);
                    for (var victim : splashVictims) {
                        // 主目标此刻正处在**自己这次攻击的伤害事件内部**:原版 LivingEntity#hurt 先写
                        // lastHurt/invulnerableTime(用的是**骰战结算前**的武器伤害),再进 actuallyHurt →
                        // 本事件;于是"无敌帧内不更低的伤害被丢弃"规则会把溅射整段吞掉(实测主靶只掉近战
                        // 那 3 点、5 点溅射凭空消失)。这里只对主目标临时清零无敌帧,让溅射照常结算;
                        // 其余目标没有在飞的伤害,不动。
                        boolean isMainTarget = victim == target;
                        int savedInvulnerable = isMainTarget ? victim.invulnerableTime : 0;
                        try {
                            // 2026-10-27 批次F(P2-A):主目标此刻的吸收里含幻影 H(settle :941 写回),
                            // 这段溅射同样必须在真实余额上结算;其余目标没有登记 ⇒ 本包装是 no-op。
                            withPhantomPeeled(victim, () -> {
                                if (isMainTarget) victim.invulnerableTime = 0;
                                // 跳数字:登记组别后由 DamageNumberAggregator 取终值(不再直发)。
                                // ⚠️ 必须显式登记:astral_dice:true_damage 被多路复用
                                //    (大当家溅射=攻击力类红 / 效果牌范围波及与法伤加成=法伤类绿),
                                //    只看类型区分不出来。
                                DamageNumberAggregator.tag(victim, splashSource,
                                        DamageNumberAggregator.Group.ATTACK);
                                victim.hurt(splashSource, splashDmg);
                            });
                        } finally {
                            if (isMainTarget) victim.invulnerableTime = savedInvulnerable;
                        }
                    }
                    // 爆炸视觉效果:只发粒子与音效,不改动世界(不破坏方块)
                    if (target.level() instanceof net.minecraft.server.level.ServerLevel serverLevel) {
                        double ex = target.getX();
                        double ey = target.getY(0.5);
                        double ez = target.getZ();
                        serverLevel.sendParticles(net.minecraft.core.particles.ParticleTypes.EXPLOSION_EMITTER,
                                ex, ey, ez, 1, 0.0, 0.0, 0.0, 0.0);
                        serverLevel.playSound(null, ex, ey, ez, net.minecraft.sounds.SoundEvents.GENERIC_EXPLODE,
                                net.minecraft.sounds.SoundSource.BLOCKS, 4.0F,
                                (1.0F + (serverLevel.random.nextFloat() - serverLevel.random.nextFloat()) * 0.2F) * 0.7F);
                    }
                }
            } finally {
                endAoe();
            }
        }

        // 吸血鬼立牌(papara)主动"汲取":攻击时恢复骰神赐福**落地**伤害的一半生命(取整,至少 1 点)
        // MELEE-04(2026-10-27 近战审计):基数必须是 settle 之后的 applied(护盾钳制 + 吸收都已扣掉)。
        //   原实现用 finalDmg(settle 之前的自算终值)⇒ 一记被鼠鼠护盾 / 黄心整段吃掉的攻击仍会白送回血,
        //   与受击侧 settleBiteHeal 的「金额归零不回血」自相矛盾。
        if (!player.level().isClientSide() && player.hasEffect(ModEffects.PAPARA_BITE.get())) {
            player.heal(Math.max(1, (int) applied / 2));
        }

        if (hasShadowStrike && !player.level().isClientSide()) {
            PlayerEffectProtection.apply(target, new MobEffectInstance(MobEffects.DARKNESS, 60, 0, false, false));
        }

        // === DICE STONE CONSUMPTION (与赐福触发逻辑一致:仅在触发骰神赐福的那次攻击消耗一次耐久;
        //      赐福效果期间卡牌攻击/防御加成持续生效、每次攻击独立随机判定,不再消耗耐久) ===
        if (!player.level().isClientSide() && triggeredBlessing) {
            consumeAttackCardDurabilityOnce(player, diceStack, enhancement);
        }
    }

    // 攻击牌耐久消耗(仅在触发骰神赐福的那次攻击执行一次;防御牌/蓄力不消耗)。
    // 普通近战触发与反击伤害注入共用(反击未赐福时作为触发攻击消耗一次耐久)。
    private static void consumeAttackCardDurabilityOnce(Player player, ItemStack diceStack, WeaponEnhancement enhancement) {
        if (diceStack == null || diceStack.isEmpty() || enhancement == null
                || enhancement.appliedStones().isEmpty()) return;
        List<AppliedStone> newStones = new ArrayList<>();
        int attackCostFreed = 0;
        boolean dirty = false;
        for (AppliedStone stone : enhancement.appliedStones()) {
            // 防御牌:不在此消耗(消耗见 consumeAllDefenseCardDurability)
            if (stone.type().startsWith("defense_")) {
                newStones.add(stone);
                continue;
            }
            // 蓄力:赐福期间持续生效不消耗耐久,在骰神赐福结束时返还"全力攻击"
            if ("charge".equals(stone.type())) {
                newStones.add(stone);
                continue;
            }
            int newUses = stone.uses() - 1;
            if (newUses <= 0) {
                attackCostFreed += MisakiSignItem.effectiveCost(player, stone.type());
                dirty = true;
            } else {
                // ⚠️ 必须透传 temporary:临时牌(绿洲女王 nardis)扣 1 点耐久后仍须保持临时性,
                //    否则本次消耗就会把它"洗"成永久牌(到期清理便找不到它)—— 见 AppliedStone 类注释。
                newStones.add(new AppliedStone(stone.type(), newUses, stone.temporary(), stone.owner()));
                dirty = true;
            }
        }
        if (dirty) {
            ModDataComponents.WEAPON_ENHANCEMENT.set(diceStack, new WeaponEnhancement(
                            enhancement.usedCost() - attackCostFreed,
                            enhancement.maxCost(),
                            enhancement.usedDefenseCost(),
                            enhancement.maxDefenseCost(),
                            enhancement.starLevel(),
                            newStones
                    ));
        }
    }

    // 防御牌耐久扣除(2026-09-26 用户裁决·更正版):**每次受到骰战攻击**时,防御方卡牌栏中
    // 装备的**所有防御牌各扣 1 点**(不是只扣装备顺序里的第一张)。
    // 任一牌归零即从卡牌栏移除并返还其费用(沿用 MisakiSignItem.effectiveCost 口径)。
    // 防御方未装备骰子 / 无防御牌 ⇒ 无事发生。
    // ⚠️ 触发口径 = **每一次**受骰战攻击(不按赐福周期计数) —— 本方法取代原先
    //    「按装备顺序只扣第一张」的实现,以及更早的「每赐福周期一次、所有牌各扣 1」实现。
    private static void consumeAllDefenseCardDurability(Player defender) {
        if (defender.level().isClientSide()) return;
        var curios = CuriosCompat.getCuriosInventory(defender);
        if (curios.isEmpty()) return;
        var diceResult = curios.get().findFirstCurio(DiceCurioItem::isDiceItem);
        if (diceResult.isEmpty()) return;
        ItemStack dice = diceResult.get().stack();
        WeaponEnhancement enh = ModDataComponents.WEAPON_ENHANCEMENT.getOrDefault(dice, null);
        if (enh == null) return;

        List<AppliedStone> stones = enh.appliedStones();
        List<AppliedStone> newStones = new ArrayList<>(stones.size());
        int defenseCostFreed = 0;
        boolean dirty = false;
        for (AppliedStone s : stones) {
            // 非防御牌原样保留(已耗尽的防御牌理论上不存在,一并跳过)
            if (!s.type().startsWith("defense_") || s.uses() <= 0) {
                newStones.add(s);
                continue;
            }
            dirty = true;
            int newUses = s.uses() - 1;
            if (newUses <= 0) {
                // 归零 ⇒ 从卡牌栏移除并返还其费用
                defenseCostFreed += MisakiSignItem.effectiveCost(defender, s.type());
            } else {
                // temporary 必须透传(临时牌不会因扣耐久变回永久牌),同 consumeAttackCardDurabilityOnce 口径
                newStones.add(new AppliedStone(s.type(), newUses, s.temporary(), s.owner()));
            }
        }
        if (!dirty) return;
        ModDataComponents.WEAPON_ENHANCEMENT.set(dice, new WeaponEnhancement(
                enh.usedCost(),
                enh.maxCost(),
                enh.usedDefenseCost() - defenseCostFreed,
                enh.maxDefenseCost(),
                enh.starLevel(),
                newStones));
    }

    // === 生物近战攻击玩家:独立骰战结算(2026-09-26 新增) ===
    // 与玩家攻击路径的差异:
    //   · 攻击方是生物 ⇒ 无玩家上下文(DiceCombatContext 要求 Player 攻击者),故不走攻击修饰器链;
    //   · **不触发骰神赐福**(赐福只能由玩家主动近战攻击触发);
    //   · 生物无骰子槽位 ⇒ 只要「玩家装备骰子」即视为对骰成立,生物恒掷 1d6,玩家掷防御骰。
    // 攻击力 = 生物初始攻击力(敌对 4 / 中立 3 / 被动 0 / 友好 0)
    //        + 生物自身 ATTACK_DAMAGE 属性值(经附魔加伤链) + 生物骰点
    // 防御力 = 玩家防御力公式 + 玩家防御骰点 + 战斗牌点数
    // 最终伤害 = max(1, 攻击点 ×15%, 攻击点 − 防御力);随后由 ChipDamageHandler(@LOWEST)做固定值减伤(可扣到 0)。
    // ⚠️ 2026-09-26 裁决①(方案 A):**移除**原先作为加项的 event.getAmount()。
    //    1.20.1 的 LivingDamageEvent 在 actuallyHurt 内、护甲 → 抗性 → 保护三步减免**之后**派发 ⇒
    //    该值当加项用等于①护甲被计入两次、②抗性/保护经它泄漏进攻击项 —— 属结构性缺陷,非取舍。
    //    移除后生物攻击点回归「初始 + 属性值(含武器附魔)」的干净口径,抗性/保护改由受击方乘算因子表达。
    private static void resolveMobMeleeAttack(Mob mob, Player target, LivingDamageEvent event,
                                              DamageSource source, double victimFactor) {
        if (mob.level().isClientSide() || target.level().isClientSide()) return;
        // 仅近战:直接伤害实体必须就是生物本身(弓箭/三叉戟/药水等投掷物的 directEntity 不是 Mob,
        // 已在入口分流处被排除;此处再排除「生物发射但 directEntity 仍指向本身」的少数特殊攻击)。
        if (source.getDirectEntity() != mob) return;

        // 对骰成立条件:玩家装备骰子。未装备 ⇒ 双方均不掷,退化为按基础攻防值直接结算。
        boolean opposed = hasDice(target);
        int mobRoll = 0;
        int playerRoll = 0;
        int defenseCardSum = 0;
        if (opposed) {
            mobRoll = rollDice(6);
            playerRoll = rollCombatDie(target);
            var targetCurios = CuriosCompat.getCuriosInventory(target);
            if (targetCurios.isPresent()) {
                var diceResult = targetCurios.get().findFirstCurio(DiceCurioItem::isDiceItem);
                if (diceResult.isPresent()) {
                    ItemStack dice = diceResult.get().stack();
                    WeaponEnhancement enh = ModDataComponents.WEAPON_ENHANCEMENT.getOrDefault(dice, null);
                    if (enh != null) {
                        defenseCardSum = sumEquippedDefenseCardPoints(target, dice, enh);
                    }
                }
            }
        }

        // 生物武器附魔:按原版 Mob#doHurtTarget —— f += getDamageBonus(getMainHandItem(), 目标 MobType)
        // (1.20.1 是**绝对量**加法,与本侧玩家路径同款)。
        double mobAttack = com.merlinkitsune.starengine.combat.TargetBattleStats.baseAttack(mob)
                + mob.getAttributeValue(Attributes.ATTACK_DAMAGE)
                + enchantDamageBonus(mob, target)
                + mobRoll;
        double playerDefense = com.merlinkitsune.starengine.combat.CombatFormula.playerDefense(
                com.merlinkitsune.astral_dice.combat.DiceCombatModifiers.effectiveArmorPoints(target), target.getAttributeValue(Attributes.ARMOR_TOUGHNESS))
                + playerRoll + defenseCardSum;

        float finalDmg = com.merlinkitsune.starengine.combat.DiceBattleResolver
                .resolve(mobAttack, playerDefense);
        // 受击方(玩家)「抗性提升 + 保护附魔」乘算因子 —— 与「玩家→生物」路径**对称**应用
        // (2026-09-26 裁决①:两条路径必须用同一口径,不得一边丢弃、一边泄漏)。
        finalDmg *= com.merlinkitsune.starengine.combat.VanillaMitigation.magicAbsorbFactor(
                resistanceAmplifierOf(target), protectionPointsOf(target, source));
        // A6(2026-10-27 近战审计):生物近战路径漏了「目标身上有标记 ⇒ +1」 —— 判定与玩家路径同口径
        //   (本文件玩家分支的 if (MarkManager.getLevel(target) > 0) finalDmg += 1)。
        //   位置也与玩家路径对齐:**victimFactor 之前**,故这个 +1 同样会被「同一伤害实例」的倍率放大
        //   (玩家路径的 +1 还会再过 EXTERNAL_DAMAGE_FACTORS;生物攻击者没有外部因子来源)。
        if (MarkManager.getLevel(target) > 0) {
            finalDmg += 1;
        }
        finalDmg *= (float) victimFactor;
        // N8:虚弱印记 x1.10 必须作用在**吸收之前**的骰战终值上(审计 §2 不变量③)。本线原先只有
        // onWeakMarkDamage(@LOW,吸收之后)在乘 => 与 1.21.1(LivingDamageEvent.Pre)口径不同。现并入
        // 终值,onWeakMarkDamage 对生物近战骰战路径改为跳过(判据 isDiceBattleSettledDamage 同源)。
        // 生物攻击者不是玩家 => 没有「命运的指引」+0.20 那条来源,只需基准倍率。
        if (target.hasEffect(ModEffects.WEAK_MARK.get())) {
            finalDmg *= WEAK_MARK_DAMAGE_MULTIPLIER;
        }
        // F3(2026-10-27 近战审计):七咒(神秘遗物+ 第一诅咒)倍率在生物近战路径被整段丢弃 ——
        //   该倍率由 FateGuidanceCardItem 在 LivingHurtEvent 捕获进**目标侧**附件,只有「玩家攻击」路径的
        //   内置外部因子会乘;生物攻击者永远没有「命运的指引」⇒ 按全额 ratio 应用,用后清零(与内置因子同语义,
        //   避免残留到下一击)。
        //   本方法签名里 target 已是 Player(生物近战只打玩家)⇒ 不做 instanceof 模式匹配。
        float curseRatio = ModAttachments.getDiceCurseRatio(target);
        if (curseRatio > 1.0f) {
            finalDmg *= curseRatio;
        }
        ModAttachments.setDiceCurseRatio(target, 1.0f);
        // N1 + N2:护盾钳制 + 吸收按终值只扣一次(与玩家攻击路径同一出口)
        settleDiceDamage(target, event, finalDmg);
        // 跳数字:与玩家攻击同一出口(登记组别,由 DamageNumberAggregator 在管线终值上取值)
        DamageNumberAggregator.tag(target, source, DamageNumberAggregator.Group.ATTACK);
        // 防御牌耐久:每次受到骰战攻击,防御方装备的所有防御牌各扣 1 点(仅在对骰成立时)
        if (opposed) {
            consumeAllDefenseCardDurability(target);
        }
    }

    // ============================================================================================
    // 原版伤害修饰器接入(2026-09-26 裁决:方案 A + 分拆暴击 + 冷却 + 附魔 + 抗性/保护通道)
    // ⚠️ 1.20.1 与 NeoForge 两线**不得互抄**:本侧附魔加伤是**绝对量**(getDamageBonus)、
    //    保护点数取 int 重载(传 armorSlots),且事件为单类 LivingDamageEvent。
    // ============================================================================================

    /**
     * 武器附魔带来的**附加伤害**（原版 {@code Player#attack} 的 {@code f1} 与
     * {@code Mob#doHurtTarget} 里的同名项）。
     *
     * <p>1.20.1 的口径是**绝对量**（{@code EnchantmentHelper.getDamageBonus(ItemStack, MobType)}），
     * 不是「附魔后总值 − 属性值」的增量 —— 与 NeoForge 两线的 {@code modifyDamage} 链不同签名，
     * 但数值等价（锋利 V 均为 +3）。
     *
     * @return 附魔加伤（玩家与生物共用）；调用方需按源码 {@code f1 *= f2} 自行乘一次冷却系数
     */
    private static double enchantDamageBonus(LivingEntity attacker, Entity target) {
        net.minecraft.world.entity.MobType mobType = target instanceof LivingEntity living
                ? living.getMobType()
                : net.minecraft.world.entity.MobType.UNDEFINED;
        return net.minecraft.world.item.enchantment.EnchantmentHelper
                .getDamageBonus(attacker.getMainHandItem(), mobType);
    }

    /**
     * 上述临时抵消修饰器的 id(仅在单次求值内存在,取值后立即移除)。
     * 1.20.1 的属性修饰器以 UUID 标识;此处的取值按固定字符串派生,
     * 独立于任何真实筹码,避免与玩家属性上既有修饰器撞 id
     * (撞 id 会让 {@code addTransientModifier} 抛
     * {@code IllegalArgumentException: Modifier is already applied on this attribute!})。
     */
    private static final UUID COOLDOWN_PROBE_ID = UUID.nameUUIDFromBytes(
            "astral_dice:dice_battle_cooldown_probe".getBytes(java.nio.charset.StandardCharsets.UTF_8));

    /**
     * 骰战攻击冷却系数所用的 {@code getAttackStrengthScale(0.5F)} —— **已扣除「永动机」筹码的攻击速度贡献**。
     *
     * <p><b>为什么需要它(2026-10-15 修复的副作用)</b>:永动机以 {@code ATTACK_SPEED} 的
     * {@code MULTIPLY_TOTAL = 1.0}(属性终值 ×2)实现「原版近战攻击冷却 -50%」。原版蓄力系数
     * {@code 0.2 + f2²·0.8} 只应反映**蓄力程度**,而攻速 ×2 会让**同一真实点击节奏**下的 f2 变大
     * ⇒ 骰战基础段被这条修饰器顺带抬高(不只是挥得更快)。本方法把该修饰器的贡献临时扣除,
     * 用**等价于「未装备永动机」的攻击速度**去求同一个原版公式 ⇒ 骰战系数只随真实蓄力时长变化。
     *
     * <p><b>为什么不按比例换算({@code f2 × AS'/AS})</b>:原版
     * {@code getCurrentItemAttackStrengthDelay() = 20 / 攻击速度}(1.20.1 {@code Player.java:2004-2005})、
     * {@code getAttackStrengthScale} 对 {@code (ticker + 0.5) / delay} 取 clamp(同文件 2011-2012),
     * 而 {@code attackStrengthTicker} 在 {@code Player#tick} 里**无上限自增**、只在 {@code attack()}
     * 末尾清零(同文件 275 / 1274;1.21.1 同构)⇒ 一旦 f2 被 clamp 到 1
     * (很常见:真实点击间隔 ≥ 芯片冷却时长),ticker 信息已丢失,比例换算法只能给出
     * 「等效值下限 0.5」的错误结果(等价于把永动机变成纯负收益)。故这里改为**在真实属性实例上
     * 临时叠加一个抵消修饰器**({@code MULTIPLY_TOTAL = 1/(1+a) − 1}),让原版公式自己算出
     * 精确等价值,再用 {@code finally} 立刻移除、不留残留。
     *
     * <p>未装备永动机(属性实例上没有这条修饰器)时直接返回原版取值,零副作用。
     *
     * @return 扣除永动机攻击速度贡献后的 {@code getAttackStrengthScale(0.5F)};该值同时供
     *         {@link #isVanillaCriticalHit} 与附魔加伤缩放使用(与原版「同一个 f2 驱动三处」同构)
     */
    private static float diceBattleAttackStrengthScale(Player player) {
        AttributeInstance speed = player.getAttribute(Attributes.ATTACK_SPEED);
        if (speed == null) {
            return player.getAttackStrengthScale(0.5F);
        }
        AttributeModifier chipModifier = speed.getModifier(PerpetualMotionChipItem.attackSpeedModifierId());
        if (chipModifier == null) {
            return player.getAttackStrengthScale(0.5F);
        }
        // 抵消项:把「本修饰器带来的 ×2」再乘回 1/2 ⇒ 属性终值 × (1 + counter) = 未装备永动机时的终值
        double factor = 1.0D + chipModifier.getAmount();
        if (factor <= 0.0D) {
            return player.getAttackStrengthScale(0.5F);
        }
        double counter = 1.0D / factor - 1.0D;
        if (counter == 0.0D) {
            return player.getAttackStrengthScale(0.5F);
        }
        if (speed.getModifier(COOLDOWN_PROBE_ID) != null) {
            speed.removeModifier(COOLDOWN_PROBE_ID);
        }
        speed.addTransientModifier(new AttributeModifier(COOLDOWN_PROBE_ID,
                "astral_dice:dice_battle_cooldown_probe", counter,
                AttributeModifier.Operation.MULTIPLY_TOTAL));
        try {
            return player.getAttackStrengthScale(0.5F);
        } finally {
            speed.removeModifier(COOLDOWN_PROBE_ID);
        }
    }

    /**
     * 原版暴击判定（逐条照搬 {@code Player#attack}，2026-09-26 裁决②）。
     *
     * <pre>
     *   flag  = 攻击冷却 &gt; 0.9
     *   crit  = flag &amp;&amp; fallDistance &gt; 0 &amp;&amp; !onGround &amp;&amp; !onClimbable &amp;&amp; !isInWater
     *           &amp;&amp; !hasEffect(BLINDNESS) &amp;&amp; !isPassenger
     *           &amp;&amp; target instanceof LivingEntity &amp;&amp; !isSprinting
     * </pre>
     *
     * <p>⚠️ <b>不派发</b> Forge 的 {@code CriticalHitEvent}：骰战已在 {@code LivingDamageEvent} 之内，
     * 派发它会让同一次攻击出现两条独立的暴击判定链（重复派发）。第三方若需修改骰战暴击，
     * 应走 {@code registerDiceCombatFactor} 通道。
     *
     * @param attackCooldown 已取好的 {@code getAttackStrengthScale(0.5F)}（原版用同一个值判 {@code flag}）;
     *                       骰战内取的是 {@link #diceBattleAttackStrengthScale(Player)}——
     *                       已扣除永动机筹码的攻击速度贡献（2026-10-15）
     */
    private static boolean isVanillaCriticalHit(Player attacker, Entity target, float attackCooldown) {
        return attackCooldown > 0.9F
                && attacker.fallDistance > 0.0F
                && !attacker.onGround()
                && !attacker.onClimbable()
                && !attacker.isInWater()
                && !attacker.hasEffect(MobEffects.BLINDNESS)
                && !attacker.isPassenger()
                && target instanceof LivingEntity
                && !attacker.isSprinting();
    }

    /** 受击方「抗性提升」效果的 amplifier（未持有该效果时返回 {@code null}）。 */
    private static Integer resistanceAmplifierOf(LivingEntity victim) {
        MobEffectInstance effect = victim.getEffect(MobEffects.DAMAGE_RESISTANCE);
        return effect == null ? null : effect.getAmplifier();
    }

    /** 受击方护甲上的**保护附魔**点数（1.20.1 为 {@code int} 重载：传 {@code getArmorSlots()}）。 */
    private static float protectionPointsOf(LivingEntity victim, DamageSource source) {
        return net.minecraft.world.item.enchantment.EnchantmentHelper
                .getDamageProtection(victim.getArmorSlots(), source);
    }

    /** 玩家是否装备骰子(curios 骰子槽)。 */
    private static boolean hasDice(Player player) {
        var curios = CuriosCompat.getCuriosInventory(player);
        if (curios.isEmpty()) return false;
        return curios.get().findFirstCurio(DiceCurioItem::isDiceItem).isPresent();
    }

    /**
     * 已装备骰子的战斗牌点数合计(与 {@code DiceCombatModifiers} 的防御卡修饰器同源口径:
     * 遍历全部已装卡逐张走 {@link CardRegistry#roll};玻璃骰子取最大值)。
     * 供生物攻击玩家时计算玩家侧战斗牌防御点数使用。
     */
    private static int sumEquippedDefenseCardPoints(Player defender, ItemStack dice, WeaponEnhancement enh) {
        DiceCombatContext ctx = new DiceCombatContext(
                defender, defender, null, 0, dice, enh, false, false, 0, 0);
        ctx.targetEnhancement = enh;
        ctx.targetCardsMax = dice.is(ModItems.GLASS_DICE.get());
        int sum = 0;
        for (AppliedStone stone : enh.appliedStones()) {
            sum += CardRegistry.roll(stone.type(), ctx, ctx.targetCardsMax);
        }
        return sum;
    }

    @SubscribeEvent
    public static void onLivingChangeTarget(LivingChangeTargetEvent event) {
        if (event.isCanceled()) return;
        // 肉弹战车立牌「嘲讽」:带嘲讽的目标只能攻击施加嘲讽的玩家
        var tauntSource = ModAttachments.getPandamanTauntSource(event.getEntity());
        if (event.getEntity().hasEffect(ModEffects.PANDAMAN_TAUNT.get()) && tauntSource.isPresent()) {
            Player taunter = event.getEntity().level().getPlayerByUUID(tauntSource.get());
            if (taunter != null && taunter.isAlive()) {
                event.setNewTarget(taunter);
                return;
            }
        }
        var newTarget = event.getNewTarget();
        if (!(newTarget instanceof Player player)) return;
        if (player.level().isClientSide()) return;
        // 骇客立牌完全隐身:绝对禁止生物将该玩家设为索敌目标
        if (NancyLuSignItem.isHidden(player)) {
            event.setCanceled(true);
            return;
        }
        // 秘密侦探"调查阶段"的「隐匿」(2026-09-28 用户裁决:取代原版「隐身」):持有该效果的玩家
        // 不会被生物设为索敌目标(攻击怪物后该效果被解除 ⇒ 恢复可被索敌,见 onLivingDamagePre)。
        if (player.hasEffect(com.merlinkitsune.astral_dice.effect.ModEffects.CONCEALMENT.get())) {
            event.setCanceled(true);
            return;
        }
        if (!player.hasEffect(ModEffects.DICE_BLESSING.get())) return;
        var curios = CuriosCompat.getCuriosInventory(player);
        if (curios.isEmpty()) return;
        var diceResult = curios.get().findFirstCurio(DiceCurioItem::isDiceItem);
        if (diceResult.isEmpty()) return;
        WeaponEnhancement enhancement = ModDataComponents.WEAPON_ENHANCEMENT.getOrDefault(diceResult.get().stack(), null);
        if (enhancement == null) return;
        for (var stone : enhancement.appliedStones()) {
            if ("shadow_strike".equals(stone.type())) {
                event.setCanceled(true);
                return;
            }
        }
    }

    @SubscribeEvent
    public static void onDiceBlessingExpired(MobEffectEvent.Expired event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (player.level().isClientSide()) return;
        MobEffectInstance effect = event.getEffectInstance();
        if (effect == null || effect.getEffect() == null
                || effect.getEffect() != ModEffects.DICE_BLESSING.get()) {
            return;
        }

        // 赐福结束:重置防御牌消耗标记
        ModAttachments.setDefenseCardConsumedThisBlessing(player, false);

        // 星币锤筹码:赐福结束清除本次攻击加成
        com.merlinkitsune.astral_dice.item.chip.StarCoinHammerChipItem.onBlessingEnd(player);
        // 银行卡-用不完:赐福结束后使自身及团队所有成员获得 3 星币(死亡清场等已死亡时不发放)
        com.merlinkitsune.astral_dice.item.chip.BankCardUnlimitedChipItem.onBlessingEnd(player);
        // 大碗炖肉筹码:赐福结束后,16 格范围内所有友方目标 +1 治愈并恢复 2 点生命值
        com.merlinkitsune.astral_dice.item.chip.BigBowlStewChipItem.onBlessingEnd(player);
        // 骇客立牌:赐福结束刷新被动(攻击/防御,覆盖旧类型);
        // 蛟龙立牌(mamushi):赐福结束清除撕咬加成锁存(立牌 tick 另有"无赐福且锁存为真 ⇒ 清"的兜底)。
        // 两者统一走立牌钩子分发(按玩家当前装备的立牌 instanceof 派发,新增立牌无需改本文件)。
        SignHookDispatcher.fireDiceBlessingEnded(player);
        // 枪匠「弱点识破」/ 怪力侦探「推理时间」的减层**自 2026-09-30 起不再挂在本事件上**:
        //   两者改为**独立的 1 分钟计时器**(各自效果自然到期时扣 1 层并重置计时),见 onPassiveStackExpired。
        // 风水师立牌(zhao)的「白泽赐福」两分支收尾**不在这里**,也**不**订阅本 Expired 事件:
        // 判定入口 = 玩家级 tick 的下降沿(ZhaoSignItem#tickBlessing,由 PlayerTickEvents 每 tick 驱动;
        // 规格 §4.4 冻结口径)。理由:Expired 在"效果被外力移除 / 死亡 / 重连清场"时不触发会漏掉结束,
        // 且与下降沿同时订阅会重复消费 skip 计数。本处理器对骰神赐福自身的语义保持原样。

        var curios = CuriosCompat.getCuriosInventory(player);
        if (curios.isEmpty()) return;
        var diceResult = curios.get().findFirstCurio(DiceCurioItem::isDiceItem);
        if (diceResult.isEmpty()) return;
        ItemStack dice = diceResult.get().stack();
        WeaponEnhancement enh = ModDataComponents.WEAPON_ENHANCEMENT.getOrDefault(dice, null);
        if (enh == null) return;

        List<AppliedStone> newStones = new ArrayList<>();
        boolean foundCharge = false;
        int costFreed = 0;
        // 蓄力:本次赐福结束后一律转换为全力攻击(赐福期间卡牌栏锁定,蓄力只可能预先放置)
        for (AppliedStone stone : enh.appliedStones()) {
            if ("charge".equals(stone.type())) {
                foundCharge = true;
                costFreed += AppliedStone.cost(stone.type());
            } else {
                newStones.add(stone);
            }
        }
        if (!foundCharge) return;

        ModDataComponents.WEAPON_ENHANCEMENT.set(dice, new WeaponEnhancement(
                        enh.usedCost() - costFreed,
                        enh.maxCost(),
                        enh.usedDefenseCost(),
                        enh.maxDefenseCost(),
                        enh.starLevel(),
                        newStones
                ));

        ItemStack card = new ItemStack(ModItems.ATTACK_CARD_FULL_POWER.get());
        VitaminPillChipItem.giveCard(player, card);
        if (player instanceof ServerPlayer sp) {
            ModNetwork.sendToPlayer(sp,
                    new ModNetwork.ActionBarMessage(Component.translatable("msg.astral_dice.charge_refund_full_power")
                            .withStyle(ChatFormatting.YELLOW), GameplayConstants.ACTIONBAR_DURATION_TICKS));
        }
    }

    /**
     * 待办的「弱点识破 / 推理时间」自然到期减层,下一 tick 服务端 tick 末尾统一执行。
     *
     * <p>⚠️ <b>为什么必须在下一 tick 执行</b>:{@code MobEffectEvent.Expired} 在
     * {@code LivingEntity#tickEffects} 的 {@code iterator.remove()} <b>之前</b>发出 —— 此刻处理器若
     * 新增/移除任何效果,就会结构性修改正在被 for-each 遍历的 {@code activeEffects}
     * ⇒ {@code iterator.remove()} 抛 {@link java.util.ConcurrentModificationException},
     * 而原版把它 {@code catch (ConcurrentModificationException) {}} <b>静默吞掉</b>
     * ⇒ <b>该效果本拍不被移除</b>,且其 duration 已为 0 ⇒ 下一拍 {@code tick()} 直接返回 false
     * ⇒ {@code Expired} <b>再发一次</b> ⇒ 减层被逐拍重放,直到「不再改 map」为止。
     *
     * <p><b>2026-09-25 两轮实测</b>(当时仍挂在骰神赐福结束上):一次减层调用次数
     * <b>恰等于当时层数</b>(4 层 ⇒ 连续 4 拍各扣 1、直接归 0),即用户所报「骰神赐福结束变成全扣」。
     *
     * <p>两条减层的补写实现都必然「先 removeEffect 再 addEffect」(原版 {@code MobEffectInstance#update}
     * 只接受更高的 amplifier)⇒ 必然命中上述陷阱。范式同 {@code MarkManager#PENDING_DECAY}。
     */
    private record PassiveDecay(Player player, boolean sherry, int expiredAmplifier) {
    }

    private static final List<PassiveDecay> PASSIVE_DECAY_PENDING = new ArrayList<>();

    /**
     * 「弱点识破」/「推理时间」效果<b>自然到期</b>时登记减层。
     *
     * <p>2026-09-30 用户裁决:这两个被动由「骰神赐福结束时扣 1 层」改为
     * <b>独立的 1 分钟计时器</b>(各自的效果时长 = {@code 20 * 60} tick,自然到期即扣 1 层并重置计时)
     * ⇒ 衰减不再与骰神赐福的起止绑定。
     */
    @SubscribeEvent
    public static void onPassiveStackExpired(MobEffectEvent.Expired event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (player.level().isClientSide()) return;
        MobEffectInstance effect = event.getEffectInstance();
        if (effect == null || effect.getEffect() == null) return;
        net.minecraft.world.effect.MobEffect type = effect.getEffect();
        boolean sherry;
        if (type == ModEffects.SHERRY_REASONING.get()) {
            sherry = true;
        } else if (type == ModEffects.WEAKNESS_REVEAL.get()) {
            sherry = false;
        } else {
            return;
        }
        PASSIVE_DECAY_PENDING.add(new PassiveDecay(player, sherry, effect.getAmplifier()));
    }

    /** 下一 tick 补写「弱点识破 / 推理时间」减 1 层(见 {@link #onPassiveStackExpired} 注释)。 */
    @SubscribeEvent
    public static void onPassiveStackDecay(TickEvent.ServerTickEvent event) {
        if (PASSIVE_DECAY_PENDING.isEmpty()) return;
        List<PassiveDecay> pending = new ArrayList<>(PASSIVE_DECAY_PENDING);
        PASSIVE_DECAY_PENDING.clear();
        for (PassiveDecay decay : pending) {
            Player player = decay.player();
            if (player == null || player.isRemoved() || !player.isAlive()) continue;
            if (player.level().isClientSide()) continue;
            if (decay.sherry()) {
                // 「推理时间」层数真值在附件:到期前层数 = amplifier + 1 ⇒ 新层数 = amplifier
                com.merlinkitsune.astral_dice.item.sign.SherrySignItem
                        .setLayers(player, decay.expiredAmplifier());
            } else {
                // 「弱点识破」层数真值 = 效果实例本身:还有剩余层数时按更低层数重施加(重置 1 分钟计时)
                WeaknessRevealEffect.applyDecayed(player, decay.expiredAmplifier());
            }
        }
    }

    // 标记效果自然结束时:每分钟减少 1 层标记(层数>1 时重新施加并重置计时,否则标记消失)

    // 风水师立牌(zhao)「白泽赐福」的溢出治疗转攻击力**不在这里**:它由
    // ZhaoSignItem#onLivingHeal 自己挂在 Forge 的 LivingHealEvent 上(与 1.21.1 侧同一形状:
    // 处理逻辑与状态归立牌类所有,骰战类只保留"骰点定稿后发放符卡"这一处挂点)。

    /** 狂暴「伤害增加」真伤段的**重入闸门**:该段真伤自身也会派发伤害事件 ⇒ 不加闸门会无限递归。
     *  (2026-10-27 注:原注释写的「与 DamageEffectCardHandler 的法伤真伤段同范式」已失效 ——
     *  法伤加成折叠进同一事件后不再有独立真伤段,那个 ThreadLocal 闸门也已随批移除;本闸门仍是
     *  唯一需要它的地方。) */
    private static final ThreadLocal<Boolean> BERSERK_BACKLASH_APPLYING =
            ThreadLocal.withInitial(() -> Boolean.FALSE);

    // 「伤害增加」口径(2026-10-02 用户裁决):狂暴的「受到任意伤害 +1/层」**不再并入本次伤害**
    // (并入会被护甲/保护/抗性削减,并被磨刀石等固定点数减伤吃掉),改为**独立真伤段** ——
    // 重入闸门 + ModDamageTypes.unreducibleDamage。(2026-10-27:法伤加成已改为折叠进同一事件,
    // 不再是同范式,此处只保留本效果自己的真伤段口径。)
    // ⚠️ 该段登记 bypasses_armor/enchantments/resistance ⇒ 护甲/保护/抗性全跳过;
    //    并被 ChipDamageHandler 识别为「不可削减」⇒ 磨刀石 -2 与怪力侦探立牌 -N 也跳过;
    //    **保命**机制仍对这一段独立结算(安全气囊/不死图腾/末影骰子/磨刀石「不可被一次击倒」)。
    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onBerserkDamageTaken(LivingDamageEvent event) {
        LivingEntity target = event.getEntity();
        if (target.level().isClientSide()) return;
        // N7:内部波及(AOE 顺劈/溅射/法伤波及)**不触发**狂暴反噬。
        // 理由:它们都是「本次受击的派生物」而非新的受击事实 —— AOE 扫 N 个目标就会反向开 N 段反噬
        //       (审计 §2 不变量⑤:惩罚伤害只随真实受击发生,且必须走 hurt())。
        // 2026-10-27 法伤折叠(用户裁决):法伤加成不再发嵌套真伤 hurt,原先并列排除加成段的
        //    isApplyingTrueBonus() 闸门已无用、随批移除;法伤命中现在会正常触发**一次**反噬(一击一次),
        //    不再出现「一次受击结算两次惩罚」的旧问题。
        // 下面的 BERSERK_BACKLASH_APPLYING 重入闸门必须保留:反噬自己 hurt() 出来的
        //    unreducibleDamage **不在**上面的窗口里(故意如此,否则反噬会把自己吞掉),
        //    自递归只能靠 ThreadLocal 拦。
        if (isInternalAoe()) {
            return;
        }
        if (BERSERK_BACKLASH_APPLYING.get()) return;
        var berserk = target.getEffect(ModEffects.BERSERK.get());
        if (berserk == null) return;
        int extra = berserk.getAmplifier() + 1;
        if (extra <= 0) return;
        BERSERK_BACKLASH_APPLYING.set(Boolean.TRUE);
        try {
            // 2026-10-27 批次F(P2-A 的同源第三点):本处理器 @LOW,排在 settle(NORMAL)**之后**、平台
            // :1684 **之前**,且 hurt 的正是同一个目标 ⇒ 不剥幻影 H 的话,这段反噬真伤会被平台
            // :1669-1670 整段吞掉(与额外加伤 / 大当家溅射完全同一机制)。
            withPhantomPeeled(target, () -> target.hurt(com.merlinkitsune.astral_dice.damage.ModDamageTypes
                    .unreducibleDamage(target.level(), event.getSource().getEntity()), extra));
        } finally {
            BERSERK_BACKLASH_APPLYING.set(Boolean.FALSE);
        }
    }

    // 虚弱印记:目标受到任意伤害 +10%;攻击者拥有"命运的指引"效果时对带虚弱印记的目标额外 +20%
    /**
     * 骰战外部伤害影响因子:作用于骰神赐福最终伤害的乘算修饰器。
     * 附属内容/联动(如"命运的指引")可通过 {@link #registerDiceCombatFactor} 注册,
     * 按注册顺序依次作用于最终伤害;因子负责自行判断生效条件,不生效时原样返回。
     */
    @FunctionalInterface
    public interface DiceCombatFactor {
        double modify(Player attacker, LivingEntity target, double damage);
    }

    private static final List<DiceCombatFactor> EXTERNAL_DAMAGE_FACTORS = new ArrayList<>();

    // 注册骰战外部伤害影响因子(供附属内容/联动扩展)
    public static void registerDiceCombatFactor(DiceCombatFactor factor) {
        EXTERNAL_DAMAGE_FACTORS.add(factor);
    }

    // 内置因子:目标佩戴七咒之戒时,应用神秘遗物+ 已计算的第一诅咒【实际倍率】
    // (由 onCurseMitigation 在 LivingIncomingDamageEvent 捕获,动态适配模组配置/修正物品/救赎转换);
    // "命运的指引"激活时第一诅咒影响 -50%(加幅减半)。使用后清零捕获,避免残留。
    static {
        registerDiceCombatFactor((attacker, target, damage) -> {
            if (target instanceof Player cursed) {
                float ratio = com.merlinkitsune.astral_dice.component.ModAttachments.getDiceCurseRatio(cursed);
                if (ratio > 1.0f) {
                    damage *= FateGuidanceCardItem.isFateGuidanceActive(attacker) ? (1.0 + (ratio - 1.0) * 0.5) : ratio;
                }
                com.merlinkitsune.astral_dice.component.ModAttachments.setDiceCurseRatio(cursed, 1.0f);
            }
            return damage;
        });
    }

    // 伤害放大须先于 ChipDamageHandler(安全气囊,LOWEST)执行,故用 LOW
    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onWeakMarkDamage(LivingDamageEvent event) {
        LivingEntity target = event.getEntity();
        if (target.level().isClientSide()) return;
        if (!target.hasEffect(ModEffects.WEAK_MARK.get())) return;
        // 骰战路径(玩家赐福近战 / 生物近战)的倍率已并入骰战终值,跳过以免双重应用。
        // 判据与写终值处同源,见 isDiceBattleSettledDamage(N8)。
        if (isDiceBattleSettledDamage(event.getSource(), target)) {
            return;
        }
        float multiplier = WEAK_MARK_DAMAGE_MULTIPLIER;
        if (event.getSource().getEntity() instanceof Player attacker2
                && FateGuidanceCardItem.isFateGuidanceActive(attacker2)) {
            multiplier += 0.20f;
        }
        event.setAmount(event.getAmount() * multiplier);
        // 2026-10-27 批次F(P2-B):本处理器 @LOW,若这一击由骰战 settle 写终值(吸收里含幻影 H),
        // 改了金额就必须同步重对齐吸收,否则平台 :1684 的扣减量 != H。无登记时是 no-op。
        compensateAbsorption(target, event.getAmount());
    }

    /** N8:该次伤害是否属于「骰战自算终值」路径 —— 是则虚弱印记倍率已在骰战里并入,外部处理器必须跳过。
     *  <p>判定顺序(与 {@link #onLivingDamagePre} 的入口分流**逐条同源**):<br>
     *  ① 客户端 / 内部波及(AOE 顺劈、溅射、法伤波及)与反击链中的伤害不是骰战本体 ⇒ false;<br>
     *  ② 直接伤害实体是玩家:必须近战武器、非**法伤**(法伤走 SpellDamageRegistry 加成链)、
     *     不是自伤;装备骰子 ⇒ 看是否赐福激活(骰战入口同判据),未装备 ⇒ 看目标是否为
     *     「骰神赐福合法目标」(入口的 diceStack == null 分支);<br>
     *  ③ 直接伤害实体是生物且目标是玩家 ⇒ 生物近战 —— {@link #resolveMobMeleeAttack} 无论是否
     *     对骰成立都会自算并 setAmount,故整条路径都是骰战终值;<br>
     *  ④ 其余(投掷物 / 无直接实体 / 环境伤害)⇒ false。<br>
     *  必须连内部窗口一起判:入口在 {@code isInternalAoe() || counterDepth > 0} 时直接 return,
     *  此时骰战**没有**接管金额 => 本判据也必须返回 false,否则那段伤害会丢掉虚弱印记增益。 */
    public static boolean isDiceBattleSettledDamage(DamageSource source, LivingEntity target) {
        if (source == null || target == null) return false;
        if (target.level().isClientSide()) return false;
        if (isInternalAoe() || isInCounterChain()) return false;
        Entity direct = source.getDirectEntity();
        if (direct instanceof Player attacker) {
            // F2(2026-10-27 近战审计):判据必须与 onLivingDamagePre 的入口分流**逐条同源** ——
            //   入口是「有骰 ⇒ 必须赐福激活 / 无骰 ⇒ 必须是赐福合法目标」。旧判据只写了前半句,
            //   于是「无骰玩家打赐福合法目标」(入口显式支持)走进骰战并乘过一次虚弱印记后,
            //   这里仍返回 false ⇒ 外部处理器再乘一次 ⇒ ×1.21(指引激活 ×1.69)。
            if (!isMeleeWeaponAttack(attacker)) return false;
            // 法伤链不参与骰战结算(入口 :650 的法伤闸门同源)⇒ 不算骰战终值。
            if (com.merlinkitsune.astral_dice.combat.SpellDamageRegistry.isSpellDamage(source, direct)) {
                return false;
            }
            if (attacker == target) return false;
            if (attackerHasDiceCurio(attacker)) return attacker.hasEffect(ModEffects.DICE_BLESSING.get());
            return isBlessingTarget(target, attacker);
        }
        return direct instanceof Mob && target instanceof Player;
    }

    // 玩家是否持有骰子(curio 骰子槽)
    public static boolean attackerHasDiceCurio(Player player) {
        var curios = CuriosCompat.getCuriosInventory(player);
        return curios.isPresent() && curios.get().findFirstCurio(DiceCurioItem::isDiceItem).isPresent();
    }

    private static int rollDice(int max) {
        return ThreadLocalRandom.current().nextInt(1, max + 1);
    }

    /**
     * 玩家战斗骰(d1-6)统一掷骰入口(骰子槽位仅一个):
     * 佩戴诡异骰子 → 低点数(1-3)偏置;佩戴绯红骰子 → 高点数(4-6)偏置(掷出 1 时自伤 6 点);
     * 未佩戴特殊骰子 → 均匀分布。
     */
    private static int rollCombatDie(Player roller) {
        int roll;
        if (roller == null) {
            roll = ThreadLocalRandom.current().nextInt(1, 7);
        } else if (com.merlinkitsune.astral_dice.event.WeirdDiceHandler.hasWeirdDice(roller)) {
            roll = com.merlinkitsune.astral_dice.event.WeirdDiceHandler.rollD6(roller);
        } else if (com.merlinkitsune.astral_dice.event.CrimsonDiceHandler.hasCrimsonDice(roller)) {
            roll = com.merlinkitsune.astral_dice.event.CrimsonDiceHandler.rollD6(roller);
        } else {
            roll = ThreadLocalRandom.current().nextInt(1, 7);
        }
        // 枪匠立牌:弱点识破每层使骰点最低数 +1
        if (roller != null && MosesSignItem.isEquipped(roller)) {
            roll = Math.max(roll, 1 + WeaknessRevealEffect.getStacks(roller));
        }
        return roll;
    }

    // ===== 枪械类武器本体(2026-10-03 用户裁决)=====
    // 判据全部取自各模组**实物 jar**(见下);不使用反射、不猜类名。
    /** 「枪械类武器本体」所在的**模组命名空间**:持该命名空间的任何物品都不算近战武器,不得用于近战砸击。 */
    private static final java.util.Set<String> FIREARM_ITEM_NAMESPACES = java.util.Set.of(
            "tacz",                 // 永恒枪械工坊：零 + 其 1.21.1 NeoForge 非官方移植(两个 jar 同 modId)
            "bf",                   // 方块前线 BlockFront(全部类名混淆为 a/b/A…,只能按命名空间识别)
            "irons_artifice",       // Iron's Arms 'n Artifice
            "immersiveengineering", // 沉浸工程
            "pneumaticcraft",       // 气动工艺：重压
            "superbwarfare",        // 卓越前线
            "terra_guns");          // 汇流来世**内置子模组**:枪械全部在此

    /**
     * 「枪械类武器本体」按**物品 id 点名** —— 仅用于命名空间**混装**的模组
     * (该命名空间同时承载大量近战武器,整段排除会误伤)。
     * <p>汇流来世本体命名空间 {@code confluence} 即属此类:它既有星炮 / 蜜蜂枪 / 太空枪,
     * 也有整套泰拉近战武器 ⇒ 只点名枪械本体。三者取自该模组 lang 物品表与 {@code GunItems} 注册表。
     */
    private static final java.util.Set<String> FIREARM_ITEM_IDS = java.util.Set.of(
            "confluence:star_cannon",
            "confluence:bee_gun",
            "confluence:space_gun");

    /**
     * 「该物品是否为**枪械类武器本体**」—— 枪械不得直接用于近战砸击。
     * <p>先按模组命名空间整段判定(2026-10-03 用户裁决「按命名空间整段排除」),
     * 再对命名空间混装的模组按物品 id 点名。取 {@code BuiltInRegistries} 的注册名,
     * 不触碰任何第三方类 ⇒ 未安装对应模组时恒为 {@code false},无副作用。
     */
    public static boolean isFirearmItem(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        var id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        if (id == null) return false;
        if (FIREARM_ITEM_NAMESPACES.contains(id.getNamespace())) return true;
        return FIREARM_ITEM_IDS.contains(id.toString());
    }

    /**
     * 「饕餮之锅」两口的物品注册名 —— **显式纳入**近战武器(2026-10-03 用户裁决)。
     *
     * <p>两口锅都按武器使用(各自带 {@code weapon_attributes}),但在「黑名单」判定下各自栽在不同排除项上,
     * 故必须显式放行。物品 id / 类名逐条取自**实物 jar**(lang 物品表 + {@code javap} 继承链,禁止按命名推测):
     * <ul>
     *   <li>{@code enigmaticlegacy:eldritch_pan} —— 1.20.1 联动模组的饕餮之锅
     *       (英文名 {@code The Voracious Pan};类 {@code EldritchPan extends TieredItem};
     *       ⚠️ 它**不在任何物品标签里**,黑名单本已放行,列入清单是为了自文档化 + 防日后被塞进「工具」标签);</li>
     *   <li>{@code enigmaticdelicacy:voracious_pan} —— 1.21.1 联动模组的饕餮之锅
     *       (中文名直译即「饕餮之锅」;类 {@code VoraciousPan extends BlockItem},
     *       ⚠️ 它同时是**可放置的方块** ⇒ 「方块不算近战」那条会把它排除,故**必须**显式纳入)。</li>
     * </ul>
     * ⚠️ 按**注册名**匹配 ⇒ 四线共用同一份清单,未安装该模组的线永不命中,也不需要任何第三方依赖。
     */
    private static final java.util.Set<String> MELEE_WEAPON_EXTRA_INCLUDES = java.util.Set.of(
            "enigmaticlegacy:eldritch_pan",
            "enigmaticdelicacy:voracious_pan");

    /** 该物品是否在「显式纳入的近战武器」清单里(按注册名匹配;未安装对应模组时恒为 false)。 */
    private static boolean isExplicitMeleeWeapon(ItemStack held) {
        var id = BuiltInRegistries.ITEM.getKey(held.getItem());
        return id != null && MELEE_WEAPON_EXTRA_INCLUDES.contains(id.toString());
    }

    // 近战武器攻击判定(**黑名单模式**,2026-10-03 用户裁决;同日二版把「非武器工具」重新纳入黑名单):
    // 只排除「空手 / 盾牌 / 远程专用武器(弓、弩、弹弓) / 方块 / 枪械类武器本体 /
    // 非武器工具(剪刀 / 钓竿 / 打火石 / 刷子)」,
    // 其余一律视为可触发骰神赐福的近战武器 —— **包括全部挖掘工具与各模组的武器**
    // (镐 / 锹 / 锄 / 斧…以及未知模组的工具与武器)。
    // 目的是兼容匠魂、灾变等第三方模组 —— 它们大多**不继承** SwordItem/PickaxeItem,
    // 2026-10-03 一度改成「按原版物品标签的白名单」正是栽在这一点上(已回退)。
    // ⚠️ 只按**原版物品标签 + 接口 + 注册名**判定,不用 SwordItem / DiggerItem / TieredItem 之类的类名
    //   (26.1.2 已把这三类整体重构掉,物品包内已无它们)。
    // ⚠️ **同时具备近战与远程的武器不排除**(例:三叉戟;夸克的回旋镐 pickarang 亦属此类)——
    //   只有「远程专用」物品才排除,判据是 `ProjectileWeaponItem`(实物 jar 实证覆盖各模组弹弓:
    //   锦致装饰 `SlingshotItem`、铁砧工艺 `SpectralSlingshotItem` 及其子类 `SpectralWeaponLauncherItem`)。
    // ⚠️ 盾牌不是武器,仍排除。
    // ⚠️ **显式纳入清单优先于所有排除**(见 MELEE_WEAPON_EXTRA_INCLUDES)。
    public static boolean isMeleeWeaponAttack(Player player) {
        ItemStack held = player.getMainHandItem();
        // 空手
        if (held.isEmpty()) return false;
        // 显式纳入的近战武器:命中即算 —— 「方块化的锅」必须走这一步(否则被下面的 BlockItem 排除)
        if (isExplicitMeleeWeapon(held)) return true;
        // 盾牌:不是武器
        if (held.is(Items.SHIELD)) return false;
        // 非武器工具:剪刀 / 钓竿 / 打火石 / 刷子 —— 拿着它们打人不算「近战武器攻击」
        // (2026-10-03 用户裁决,同日二版重新纳入黑名单。⚠️ 必须排在「显式纳入清单」之后 ——
        //  将来若有某模组的合法武器恰好是这几件的子类/同 id,靠那条清单才能救回来)
        if (held.is(Items.SHEARS) || held.is(Items.FISHING_ROD)
                || held.is(Items.FLINT_AND_STEEL) || held.is(Items.BRUSH)) return false;
        // 远程专用武器:弓 / 弩 / 各模组弹弓(三叉戟等双模武器不在内)
        if (held.getItem() instanceof net.minecraft.world.item.ProjectileWeaponItem) return false;
        // 方块:拿着方块打人不算「近战武器攻击」
        if (held.getItem() instanceof net.minecraft.world.item.BlockItem) return false;
        // 枪械类武器本体:不得直接用于近战砸击
        if (isFirearmItem(held)) return false;
        return true;
    }

    /**
     * 「本次伤害是否来自**该玩家本人的近战武器攻击（骰战）**」——
     * 筹码侧「仅玩家自身攻击可触发」类判定的**唯一入口**（2026-10-01 用户裁决：
     * 电磁炮不得由任意攻击、荆棘 / 反伤、宠物攻击触发）。
     *
     * <p>三条缺一不可：
     * <ol>
     *   <li>伤害的 <b>归属者</b>（{@code getEntity()}）与 <b>直接制造者</b>（{@code getDirectEntity()}）
     *       都必须是该玩家本人 ⇒ 排除宠物攻击（狼与车万女仆的攻击都走 {@code mob_attack}、
     *       来源是宠物实体本身）、弹射物（箭 / 三叉戟，直接制造者是弹射物实体）、
     *       以及一切没有来源实体的环境伤害；</li>
     *   <li>伤害类型必须是 <b>玩家攻击类型</b> ⇒ 排除「以玩家为归属、但并非玩家挥击」的伤害：
     *       荆棘附魔反伤（{@code minecraft:thorns} —— 其归属者正是被攻击的玩家本人，
     *       这正是旧判据漏掉的那条路径）、其它反伤机制，以及法术 / 技能 / 爆炸等各自独立的伤害类型；</li>
     *   <li>主手必须持有 <b>近战武器</b>（复用 {@link #isMeleeWeaponAttack(Player)}，与骰战结算同门槛）
     *       ⇒ 与「骰战」口径一致：能结算骰战的攻击才触发电磁炮。</li>
     * </ol>
     *
     * <p>⚠️ 本判据<b>故意不覆盖</b>「玩家以玩家身份伪造的其他攻击」（例如某模组用
     * {@code damageSources().playerAttack(owner)} 替宠物出刀）—— 那类伤害在伤害源层面与本尊攻击
     * 完全同形、无法区分；项目内既有的「技能类伤害」正是为避开该形态才另立
     * {@code astral_dice:skill_damage}（见 {@code SherryThrowManager} 注释）。
     */
    public static boolean isPlayerMeleeAttack(Player player, DamageSource source) {
        if (player == null || source == null) return false;
        // ① 归属者与直接制造者都必须是玩家本人
        if (source.getEntity() != player || source.getDirectEntity() != player) return false;
        // ② 伤害类型必须是「玩家攻击」类型
        if (!isPlayerAttackType(source)) return false;
        // ③ 与骰战结算同门槛:主手必须持有近战武器
        return isMeleeWeaponAttack(player);
    }

    /**
     * 本次伤害的类型是否为原版「玩家攻击」类型。
     * <p>⚠️ 平台差异：1.20.1 <b>没有</b> {@code DamageTypeTags.IS_PLAYER_ATTACK} 常量，且原版数据包
     * 本版也没有 {@code is_player_attack} 这个 tag 文件（已核 {@code client-extra} 的 24 个
     * {@code damage_type} tag 里没有它）⇒ 自建 TagKey 会恒空、判据永不成立；
     * 只能按具体类型判定。本版重锤 / 长枪尚不存在、玩家近战只有 {@code player_attack}，两者等价。
     */
    private static boolean isPlayerAttackType(DamageSource source) {
        return source.is(DamageTypes.PLAYER_ATTACK);
    }

    // 骰神赐福触发目标判定:敌对生物、非团队内玩家(队伍识别统一走 PartyRelations:
    // 原版计分板 ∪ FTB Teams ∪ OPAC)、中立生物(宠物除外),以及其余非被动动物实体
    // ⚠️ 试验假人不再在此单列特例 —— 它已由库的 HostileTargets「额外敌对判定」seam 统一计入敌对目标
    // (注入见本类 static 块),故下一行 HostileTargets.isHostile 即覆盖它,与其它调用点口径一致。
    public static boolean isBlessingTarget(LivingEntity target, Player player) {
        if (target instanceof Player other) {
            return !PartyRelations.isSameTeam(player, other);
        }
        if (HostileTargets.isHostile(target)) return true;
        if (target instanceof Mob mob) {
            // Boss 允许触发;其余生物仅在被激怒/正在攻击玩家时允许
            if (com.merlinkitsune.starengine.item.BossEntityUtil.isBossEntity(target)) return true;
            return mob.getTarget() == player || mob.isAggressive();
        }
        // 被动/友好生物不允许触发骰神赐福(中立生物已在上面由 HostileTargets 计入)
        return false;
    }

    /**
     * 「本次伤害是否来自**敌对目标的攻击**」——玩家侧受击类效果的**唯一闸门**(2026-09-29 用户裁决)。
     *
     * <p>三条缺一不可:
     * <ol>
     *   <li>必须存在**实际攻击者**({@code source.getEntity()})⇒ 排除环境伤害(摔落 / 仙人掌 / 着火 /
     *       溺水 / 饥饿 / 虚空…)与「无来源实体」的真伤(如符卡-祸:其直接伤害实体与击杀归属均为 null);</li>
     *   <li>攻击者不得是自己 ⇒ 排除自伤;</li>
     *   <li>攻击者须对该玩家构成敌对目标 —— 走库的唯一入口
     *       {@link HostileTargets#isHostile(net.minecraft.world.entity.Entity, net.minecraft.world.entity.Entity)}
     *       的**两参重载**(敌对生物 ∪ 中立生物(宠物除外) ∪ 被激怒的可驯服动物 ∪ 消费方声明的实体
     *       ∪「非同队伍、且曾主动攻击过该玩家」的玩家)。</li>
     * </ol>
     *
     * <p><b>2026-10-27(P2-5) 增补</b>:在此之上再排除**本模组自身产出的惩罚类伤害类型**——
     * {@code ModDamageTypes.UNREDUCIBLE_DAMAGE}(狂暴反噬 / 事件牌惩罚)与 {@code ModDamageTypes.CARD_COST}
     * (「王之力」的使用代价)。这些伤害带着攻击者归属只为跳字与统计,**并不代表「被敌对目标打了」**:
     * 若不排除,同一次受击会因紧随其后的反噬**再触发一遍**受击被动(立牌 onHurt / BufferShield / Papara 回血)。
     *
     * <p>消费者:{@link BaseSignItem#invokeHurtHooks}(立牌受击被动)、
     * {@code item/chip/BufferShieldChipItem#onHurt}、{@code item/sign/PaparaSignItem#settleBiteHeal}。
     * <p>不额外判 {@code amount > 0} —— 与本模组受击钩子的既有约定一致(该数值仅用于「是否受击」判定;
     * 闪避取消路径传入的是未减免的原始值)。
     */
    public static boolean isHostileAttack(Player player, DamageSource source) {
        if (player == null || source == null) return false;
        // 2026-10-27(P2-5):本模组**自身的惩罚类伤害**不算「敌对目标的攻击」(两线与 P1 线口径见下)。
        //   反噬(onBerserkDamageTaken)与事件牌惩罚(EventEffects#hurtAsEventCardPenalty)都走
        //   unreducible_damage、且攻击者归属 = 原攻击者;卡代价(card_cost)的攻击者恒为使用者本人
        //   (本已被下面 attacker == player 拦掉,这里纳入只为让「惩罚类伤害」在闸门上**成文**)。
        //   不排除的后果:原击已触发过一次受击被动,反噬落在同一次受击流程里会**再触发一遍**;
        //   牌惩罚在 PvP 里也会被误算成「被人打了」。
        if (source.is(com.merlinkitsune.astral_dice.damage.ModDamageTypes.UNREDUCIBLE_DAMAGE)
                || source.is(com.merlinkitsune.astral_dice.damage.ModDamageTypes.CARD_COST)
                // A3(2026-10-27 近战审计):额外加伤(星光/星币/治愈点)是**同一次攻击的第二段**伤害
                //   (嵌套 hurt,entity=攻击者、directEntity=null)。不排除会让受击被动一次攻击触发两次
                //   (立牌 onHurt / 缓冲盾牌回血 / 吸血鬼汲血)。
                || source.is(com.merlinkitsune.astral_dice.damage.ModDamageTypes.EXTRA_DAMAGE)) {
            return false;
        }
        if (!(source.getEntity() instanceof LivingEntity attacker)) return false;
        if (attacker == player) return false;
        return PartyRelations.isHostileTo(player, attacker);
    }

    // 试验假人(dummmmmmy)识别:实体注册 id 命名空间为 dummmmmmy,或类名包含 dummy(兼容不同版本/命名)。
    // 由本类 static 块注入给库的 combat/HostileTargets(ExtraHostileProbe)⇒ 该假人在**所有**
    // 「需要敌对目标」的判定里都算敌对(骰神赐福、法伤修饰符链、导弹/轨道类技能的波及选目标,以及
    // 目标选择器的可选中判定),不再是「骰神赐福」一处特例。参数取 Entity 是为满足 seam 签名
    // (库的判定入参是 Entity,不是 LivingEntity);null 返回 false。
    public static boolean isTrainingDummy(Entity target) {
        if (target == null) return false;
        ResourceLocation id = BuiltInRegistries.ENTITY_TYPE.getKey(target.getType());
        if ("dummmmmmy".equals(id.getNamespace())) return true;
        String name = target.getClass().getSimpleName().toLowerCase(Locale.ROOT);
        return name.contains("dummy");
    }

    // 通用掷骰提示:自定义 actionbar(5s+1s淡出)显示骰点结果
    private static void notifyRoll(Player player, int roll) {
        if (player.level().isClientSide()) return;
        if (!(player instanceof ServerPlayer serverPlayer)) return;
        Component msg = Component.translatable("msg.astral_dice.dice_roll", roll).withStyle(ChatFormatting.YELLOW);
        ModNetwork.sendToPlayer(serverPlayer,
                new ModNetwork.ActionBarMessage(msg,
                        GameplayConstants.ACTIONBAR_DURATION_TICKS));
    }

    // ⚠️ 本类原有的 sendDamageNumber 私有重载(红色默认色 / 指定 ARGB)已于 2026-09-26 移除:
    // 跳数字的**唯一出口**是 combat/DamageNumberAggregator(取伤害管线终值 + 按组别聚合),
    // 任何调用点自行发包都会被它在客户端单槽覆盖(见该类的类头)。

    // === 闪避统一取消入口(必须在伤害判定最前置处"取消") ===
    // 为什么必须"取消"而不是"把伤害改成 0":
    //  - 派发点:Forge 的 LivingAttackEvent 由 ForgeHooks.onLivingAttack 在 LivingEntity.hurt
    //    的**第一条语句**派发(forge 47.4.10 LivingEntity.java:1089;玩家实体走 Player.hurt
    //    首行的 ForgeHooks.onPlayerAttack,Player.java:842),
    //    setCanceled(true) 后 hurt 直接 return false。
    //  - 只有在这一层取消,攻击方 Mob#doHurtTarget 才拿到 false(1.20.1 Mob.java:1455-1471),
    //    从而不再执行"命中后附加效果"。原版尸壳 Husk#doHurtTarget 为:
    //      boolean flag = super.doHurtTarget(entity);
    //      if (flag && this.getMainHandItem().isEmpty() && entity instanceof LivingEntity)
    //          ((LivingEntity)entity).addEffect(new MobEffectInstance(MobEffects.HUNGER, 140 * (int)f), this);
    //    (1.20.1 Husk.java:48-56)flag 为 false 时饥饿不会被施加。
    //  - 同时不产生受伤反馈:hurt 在赋值 invulnerableTime / hurtDuration / hurtTime、
    //    broadcastDamageEvent、markHurt、knockback、indicateDamage(ClientboundHurtAnimationPacket)、
    //    playHurtSound 之前就已经返回。
    // 旧实现只在伤害阶段 setAmount(0)(LivingDamageEvent 在 actuallyHurt 内派发,
    // LivingEntity.java:1680):此时 hurt 早已走完全部流程并返回 true,
    // 所以命中附加效果与红屏/晃动/受伤音效照旧发生。
    public static void applyDodgeCancel(LivingAttackEvent event) {
        LivingEntity target = event.getEntity();
        // 先取消:立牌 onHurt / 缓冲盾牌等受击联动即使抛异常,也不得让"闪避"退化成命中
        event.setCanceled(true);
        // 保留旧实现(伤害阶段)下的受击联动:旧代码只把伤害改成 0,hurt 仍走完全流程,
        // LivingDamageEvent 照常派发,因此 BaseSignItem.invokeHurtHooks 与
        // BufferShieldChipItem.onHurt 在"被闪避的那一击"上依然会触发。上移到最前置处后,
        // 取消会跳过整段伤害处理,故在此显式补发一次(取消后伤害阶段不再派发 → 不会重复触发)。
        // 注:此处传入的是减伤前原始值;两个钩子实现都不读取该数值(仅用于"是否受击"判定)。
        if (!target.level().isClientSide() && target instanceof Player player) {
            BaseSignItem.invokeHurtHooks(player, event.getSource(), event.getAmount());
            com.merlinkitsune.astral_dice.item.chip.BufferShieldChipItem.onHurt(player, event.getSource(), event.getAmount());
            // 感测装置筹码:闪避成功 ⇒ 攻击者获得 1 层标记。本方法是**全部闪避来源**的统一取消入口
            // (枪匠破绽 / 肾上腺素闪避 / 魔女漂浮闪避)⇒ 挂在此处即全覆盖;
            // 守卫 isInCounterChain:反击链中的伤害不参与(与破绽闪避 / 嘲讽反击 / 鼠鼠反击同一递归截断)。
            if (!isInCounterChain()) {
                com.merlinkitsune.astral_dice.item.chip.SensorChipItem.onDodgeSuccess(player, event.getSource());
            }
        }
    }

    // 1.20.1 平台差异前置判定:LivingAttackEvent 在 hurt 的**第一条语句**派发,早于
    // isInvulnerableTo / 创造模式无敌 / isDeadOrDying 判定(1.21.1 的 LivingIncomingDamageEvent
    // 派发在 LivingEntity.java:1152-1153,位于这些判定之后),两者所处阶段不同。
    // 为保持双版本对等、并与本模组旧实现(伤害阶段)一致,1.20.1 侧在此补回同等前置判定,
    // 避免创造/无敌/濒死状态下仍然触发闪避与反击。
    public static boolean isImmuneToDamage(LivingEntity target, DamageSource source) {
        if (target.isInvulnerableTo(source)) return true;
        if (target instanceof Player player && player.getAbilities().invulnerable
                && !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            return true;
        }
        return target.isDeadOrDying();
    }

    // === 枪匠立牌(Moses)破绽闪避/反击 ===
    // 破绽持续 2:00,期间**每一次**目标攻击都会被闪避并触发反击(不再被"每目标已发放"标记拦掉);
    // 「弱点识破」层数的"每次都给 +1 层(2026-10-02 起)"限制由 MosesSignItem.onDodgeCounter 内部判定。
    // 闪避改在伤害判定最前置处"取消"(LivingAttackEvent)而不是在伤害阶段把伤害改成 0:
    // 只有前者能让攻击方 Mob#doHurtTarget 拿到 hurt()==false,从而不施加尸壳饥饿等命中附加效果、
    // 也不产生红屏/屏幕震动/受伤音效与击退同步。详见 applyDodgeCancel 的注释。
    @SubscribeEvent(priority = EventPriority.NORMAL)
    public static void onMosesBrokenDodge(LivingAttackEvent event) {
        LivingEntity victim = event.getEntity();
        if (victim.level().isClientSide()) return;
        if (!(victim instanceof Player player)) return;
        // 平台差异补位:LivingAttackEvent 早于无敌/濒死判定,复刻 1.21.1 的事件阶段
        if (isImmuneToDamage(player, event.getSource())) return;
        if (!MosesSignItem.isEquipped(player)) return;
        // 反击链中的伤害不参与破绽闪避/反击判定(结构性递归截断):
        // 否则 A/B 双方各自都带破绽时会 A 闪避 B → 反击注入 B → B 闪避 → 反击注入 A → ... 无限互相递归
        if (isInCounterChain()) return;
        if (!(event.getSource().getEntity() instanceof LivingEntity attacker)) return;
        if (!attacker.hasEffect(ModEffects.MOSES_BROKEN.get())) return;
        // 闪避本次攻击(最前置取消)
        applyDodgeCancel(event);
        // 获得弱点识破并标记该目标已闪避(每次都给 +1 层(2026-10-02 起))
        SignHookDispatcher.fireDodgeCounter(player, attacker);
        // 单次反击伤害注入(不进入反击效果/层数体系)
        injectCounterDamage(player, attacker);
    }

    // 肉弹战车立牌(pandaman)主动「嘲讽」:被嘲讽目标攻击施加者时触发反击
    // (沿用反击伤害公式;不消耗“反击”层数,每次嘲讽目标成功攻击时触发)
    @SubscribeEvent(priority = EventPriority.NORMAL)
    public static void onPandamanTauntCounter(LivingDamageEvent event) {
        LivingEntity victim = event.getEntity();
        if (victim.level().isClientSide()) return;
        // 反击链中不再触发嘲讽反击(与破绽闪避共用同一结构性递归截断)
        if (isInCounterChain()) return;
        if (!(victim instanceof Player player)) return;
        if (!player.isAlive()) return;
        if (!(event.getSource().getEntity() instanceof LivingEntity attacker)) return;
        // 视者 = 被攻击的玩家(player):被嘲讽目标若为"曾主动攻击过本玩家的非同队玩家"同样计入敌对
        if (!PartyRelations.isHostileTo(player, attacker)) return;
        if (!attacker.hasEffect(ModEffects.PANDAMAN_TAUNT.get())) return;
        Optional<UUID> tauntSource = ModAttachments.getPandamanTauntSource(attacker);
        if (tauntSource.isEmpty() || !tauntSource.get().equals(player.getUUID())) return;
        injectCounterDamage(player, attacker);
    }

    // 游戏大师立牌(ren)「鼠鼠护盾」自带的一次性反击:带盾玩家被攻击时消耗 1 层,对攻击者注入一次
    // 现有反击伤害(沿用同一公式)。1.20.1 的 LivingDamageEvent 派发于吸收结算**之后**,
    // 但它同样只是「伤害已确认」的钩子,与是否被吸收无关 ⇒ **被黄心完全吃掉的一击同样触发**;
    // 若这一击正好打空黄心,护盾的清空由 RenShieldManager 的每 tick 轮询在稍后完成(先反击、后破盾)。
    @SubscribeEvent(priority = EventPriority.NORMAL)
    public static void onRenShieldCounter(LivingDamageEvent event) {
        LivingEntity victim = event.getEntity();
        if (victim.level().isClientSide()) return;
        // 反击链中不再触发(与破绽闪避 / 嘲讽反击共用同一结构性递归截断)
        if (isInCounterChain()) return;
        if (!(victim instanceof Player player)) return;
        if (!player.isAlive()) return;
        if (!player.hasEffect(ModEffects.REN_SHIELD.get())) return;
        if (ModAttachments.getRenCounterCharges(player) <= 0) return;
        if (!(event.getSource().getEntity() instanceof LivingEntity attacker)) return;
        if (attacker == player) return;
        // 2026-09-29 收紧(与效果文案「佩戴者被攻击时消耗该层」对齐):只有**敌对目标**的攻击
        // 才消耗反击层数。视者 = 带盾玩家(player);于是队友 / 已驯服宠物 / 未激怒中立生物的
        // 攻击不再误消耗层数、也不再把反击打在友军身上(自伤已由上一行排除)。
        if (!PartyRelations.isHostileTo(player, attacker)) return;
        // 一次性充能:先消耗层数(并同步摘掉「反击」图标),再注入伤害
        ModAttachments.setRenCounterCharges(player, 0);
        RenShieldManager.refreshCounterEffect(player);
        injectCounterDamage(player, attacker);
        if (player instanceof ServerPlayer sp) {
            ModNetwork.sendToPlayer(sp, new ModNetwork.ActionBarMessage(
                    Component.translatable("msg.astral_dice.ren_counter_fired")
                            .withStyle(ChatFormatting.YELLOW),
                    GameplayConstants.ACTIONBAR_DURATION_TICKS));
        }
    }

    // === 反击伤害注入(Counterattack Damage Injection) ===
    // 反击不再作为效果/流派存在:没有 counterattack 效果、没有层数、没有持续反噬周期。
    // 具体触发源(肉弹战车嘲讽、枪匠破绽闪避)命中时调用 injectCounterDamage 做单次伤害计算并注入,
    // 不登记反噬目标、不持续返还。

    // 对当前目标注入一次反击伤害(视为玩家伤害来源,不进入骰战结算/不递归触发)
    // 结构性递归截断:本方法自身拒绝在反击链中再入——即使将来新增调用点忘记加守卫,
    // 反击链也不可能自我递归(而不是"限制递归层数"这类可被绕过的软限制)。
    // 2026-10-08 起同时被安全气囊筹码复用(见 item/chip/AirbagChipItem#counterAttacker) ⇒ 提升为 public。
    public static void injectCounterDamage(Player player, LivingEntity attacker) {
        if (isInCounterChain()) return;
        // MELEE-07(2026-10-27 用户裁决):一次挥击只结算一次反击。
        //   同一次命中可能派发多个伤害事件(本模组额外加伤段的嵌套 hurt / 第三方多段攻击)，
        //   嘲讽、破绽闪避、鼠鼠护盾、安全气囊共用本出口 ⇒ 不去重就会对同一挥击注入多次反击。
        //   按「攻击者 + 受害者」在 1 刻窗口内只放行一次；反击伤害本身在 isInCounterChain() 内，不会自我递归。
        if (!DeathTriggerGuard.claimPair(attacker, player, "counter")) return;
        // 整个「反击伤害计算 + 注入」都必须落在反击链内:计算过程本身也会造成伤害——
        // computeCounterDamage → rollCombatDie → CrimsonDiceHandler.rollD6 掷出 1 时会对掷骰者
        // 本人施加 6 点 astral_dice:dice_damage(CrimsonDiceHandler.java:63-65)。若把计算留在
        // 守卫之外,这份自伤仍可能再次进入破绽闪避/反击判定,构成第二条(概率性、无上界的)递归路径。
        counterDepth++;
        try {
            double dmg = computeCounterDamage(player, attacker);
            if (dmg <= 0) return;
            attacker.hurt(com.merlinkitsune.astral_dice.damage.ModDamageTypes.diceDamage(attacker.level(), player),
                    (float) dmg);
            // 跳数字:不再直发 —— 组别走**类型回落**即可(astral_dice:dice_damage ⇒ 攻击力类红)。
        } finally {
            // try/finally:异常/提前返回都会复位;计数器只减不置零 → 嵌套同样安全
            counterDepth--;
        }
    }

    // 反击返还伤害 = 手持最高近战武器基础伤害 + 骰战攻击力加成链 + 已装备攻击牌随机掷骰
    // (与正常攻击同一上下文以收集加成/掷骰,但不加入 1d6,也不自动赐福/施加诅咒/×1.5)
    private static double computeCounterDamage(Player player, LivingEntity attacker) {
        double weaponBase = highestHeldMeleeBaseDamage(player);

        // 骰子与卡牌(与正常攻击路径一致的上下文,仅用于攻击力加成链与攻击牌随机掷骰)
        ItemStack diceStack = null;
        WeaponEnhancement enhancement = null;
        var curios = CuriosCompat.getCuriosInventory(player);
        if (curios.isPresent()) {
            var diceResult = curios.get().findFirstCurio(DiceCurioItem::isDiceItem);
            if (diceResult.isPresent()) {
                diceStack = diceResult.get().stack();
                enhancement = ModDataComponents.WEAPON_ENHANCEMENT.getOrDefault(diceStack, null);
            }
        }
        if (enhancement == null) enhancement = WeaponEnhancement.EMPTY;

        // 未装备骰子 ⇒ 骰点恒为 0(2026-09-26 用户裁决:一方没有骰点,另一方也不得掷)。
        int baseDice = (diceStack != null) ? rollCombatDie(player) : 0; // 特殊骰子掷骰(诡异骰子低点数偏置/绯红骰子高点数偏置)
        int misakiStar = enhancement.starLevel();
        int misakiStacks = 0;
        boolean misakiBurst = false;
        if (curios.isPresent()) {
            var misakiResult = curios.get().findFirstCurio(s -> s.is(ModItems.MISAKI_SIGN.get()));
            if (misakiResult.isPresent()) {
                misakiStacks = ModDataComponents.MISAKI_SIGN_STACKS.getOrDefault(misakiResult.get().stack(), 0);
                misakiBurst = player.hasEffect(ModEffects.MISAKI_BURST.get());
            }
        }
        DiceCombatContext ctx = new DiceCombatContext(
                player, attacker, null, baseDice, diceStack, enhancement, false,
                misakiBurst, misakiStar, misakiStacks);
        double modifiersSum = 0;
        for (var modifier : DiceCombatModifiers.attackModifiers()) {
            modifiersSum = modifier.apply(ctx, modifiersSum);
        }
        double total = weaponBase + ctx.attackCardSum + modifiersSum;
        // 七咒减益作用于反击总伤害(含修正物:启示之证/倒转之启/恩惠之典/护法爆发)
        total = applyCurseToDicePoints(player, total);
        // 可受到修正影响:装备「全力攻击」时返还伤害 ×1.5(与正常攻击结算一致)
        boolean hasFullPower = ctx.hasFullPower;
        if (!hasFullPower && enhancement != null) {
            for (AppliedStone stone : enhancement.appliedStones()) {
                if ("full_power".equals(stone.type())) {
                    hasFullPower = true;
                    break;
                }
            }
        }
        if (hasFullPower) {
            total = Math.ceil(total * 1.5);
        }
        // 肉弹战车立牌(pandaman)常驻被动:反击时若生命未满,附加缺失生命值等值的伤害
        if (PandamanSignItem.isEquipped(player)) {
            double missingHp = Math.max(0.0, player.getMaxHealth() - player.getHealth());
            if (missingHp > 0) {
                total += missingHp;
            }
        }
        return total;
    }

    // 手持(主手+副手)近战武器的基础伤害最大值(不含附魔/属性效果);无近战武器回退空手 1.0
    private static double highestHeldMeleeBaseDamage(Player player) {
        double best = 1.0;
        for (ItemStack stack : new ItemStack[]{player.getMainHandItem(), player.getOffhandItem()}) {
            if (stack.isEmpty()) continue;
            for (var entry : stack.getItem()
                    .getAttributeModifiers(net.minecraft.world.entity.EquipmentSlot.MAINHAND, stack).entries()) {
                if (entry.getKey() == Attributes.ATTACK_DAMAGE
                        && entry.getValue().getOperation()
                        == net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADDITION) {
                    best = Math.max(best, entry.getValue().getAmount());
                }
            }
        }
        return best;
    }

}
