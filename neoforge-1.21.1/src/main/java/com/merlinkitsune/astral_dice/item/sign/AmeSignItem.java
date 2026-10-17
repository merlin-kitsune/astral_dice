package com.merlinkitsune.astral_dice.item.sign;

import com.google.common.collect.MapMaker;
import com.merlinkitsune.astral_dice.AstralDiceMod;
import com.merlinkitsune.astral_dice.combat.DiceCombatModifiers;
import com.merlinkitsune.astral_dice.combat.PartyRelations;
import com.merlinkitsune.astral_dice.component.ModAttachments;
import com.merlinkitsune.astral_dice.effect.AmeLoveEffect;
import com.merlinkitsune.astral_dice.effect.AmeOverdoseEffect;
import com.merlinkitsune.astral_dice.effect.ModEffects;
import com.merlinkitsune.astral_dice.event.EffectTimerGuard;
import com.merlinkitsune.astral_dice.item.CurioSlotUtil;
import com.merlinkitsune.astral_dice.item.ModItems;
import com.merlinkitsune.starenginelib.event.ModEffectRemoval;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.MobEffectEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.SlotContext;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 地雷女立牌(ame,奇特)。
 *
 * <h3>主动「感情过量摄入」</h3>
 * 立即恢复 {@code 爱层数 × 2} 生命值(不超过最大生命),并经 {@link EffectTimerGuard} 施加
 * {@link AmeOverdoseEffect}({@value AmeOverdoseEffect#DURATION_TICKS} tick = 1:00,移速 +20%,
 * 图标可见计时)。**三态化锁定**(照 fanny/fen/jasmine):锁定跟随 {@code ame_overdose} 存续,
 * 到期后进入冷却(无空档)。**仅自然到期**({@link MobEffectEvent.Expired})触发结算:
 * 爱 −{@value #EXPIRE_COST}(下限 0)、爱上限 +1(最高 {@value #AME_LOVE_CAP_MAX});
 * 奶/命令清除不派发 Expired 事件 ⇒ **不触发结算**(卸下立牌走 {@code clearSignData} 显式移除,同样不结算)。
 * 结算**延到同一 tick 的服务端 tick 末尾**执行且按实例幂等(一次自然到期恰好一次),
 * 原因见 {@link #onOverdoseExpired}。
 *
 * <h3>被动「无法承受的爱」</h3>
 * 佩戴期间基础防御 {@value #BASE_DEFENSE} 点(经 {@code setDefenseArmorBonus} ×2.0 折真实护甲);
 * 每层爱:骰战**攻击力 +1**({@code DiceCombatModifiers} 注册点)、**防御力 −1(最低 0)**、
 * 且**爱 &gt; 4 时**最大生命 {@code + (爱 − 4)}({@code Attributes.MAX_HEALTH} 瞬态修饰器 ADD_VALUE)。
 * 受到敌对攻击 ⇒ +1 层,**每 {@value #LOVE_GAIN_INTERVAL_TICKS} tick(= 30 秒)最多 1 层**
 * (附件 {@code AME_LOVE_GAIN_CD_END};该闸门同时约束一心同体供给方送入的层数),受自身上限约束。
 * {@code onEquip} 时若当前 0 层 ⇒ 置 {@value #EQUIP_INITIAL_LOVE} 层。
 *
 * <p><b>寿命</b>:层数与上限**跨死亡保留**({@code copyOnDeath});**卸下清零**
 * (层数 0、上限回 {@value #AME_LOVE_CAP_DEFAULT}、移除 {@code ame_love}/{@code ame_overdose}
 * 与全部修饰器)。
 *
 * <h3>备注「一心同体」(供给方口径,裁决 #3)</h3>
 * 同队内存在**超天酱立牌佩戴者**时:① **施加入口** —— 地雷女佩戴者的「近战命中 / 被敌对攻击」
 * 两条入口同样成立(目标上「粉丝」、粉丝归队内每个超天酱佩戴者,见 {@link KangelSignItem#applyFanTo});
 * ② 超天酱玩家获得 {@value KangelSignItem#KANGEL_LINK_DEFENSE} 点基础防御(key {@code ame_link};
 * 该修饰器**改由 kangel 侧自维护自撤销**,见 {@code KangelSignItem#onCurioTick};
 * 不攒爱、无攻防修正、无最大生命加成);
 * ③ 超天酱玩家受到敌对伤害 ⇒ 队内**所有**地雷女佩戴者各 +1 爱(各自过自身 30 秒闸门与上限)。
 *
 * <p><b>动态修饰器重挂(防死亡/重登丢失)</b>:附件跨死亡保留、瞬态属性修饰器不跨 ⇒
 * {@code onCurioTick} **节流 20t** 按附件真值幂等重挂防御 + 最大生命(自带重生/登录/队伍变动自愈)。
 */
@EventBusSubscriber(modid = AstralDiceMod.MODID)
public class AmeSignItem extends BaseSignItem {

    /** 立牌注册 id */
    public static final String SIGN_ID = AstralDiceMod.MODID + ":ame_sign";

    /** 「爱」层数上限默认值(初始上限;主动自然到期后 +1) */
    public static final int AME_LOVE_CAP_DEFAULT = 4;
    /** 「爱」层数上限的**硬上限**(最多 32 层) */
    public static final int AME_LOVE_CAP_MAX = 32;
    /** 装备时的初始「爱」层数(当前 0 层时置此值) */
    public static final int EQUIP_INITIAL_LOVE = 2;
    /** 主动自然到期时扣除的「爱」层数 */
    public static final int EXPIRE_COST = 4;
    /** 「超出部分转为最大生命」的基准层数(爱 > 此值 ⇒ 每层 +1 最大生命;追加需求 2026-10-16) */
    public static final int LOVE_SURPLUS_BASE = 4;
    /** 主动恢复生命的倍率(爱层数 × 此值) */
    public static final int HEAL_PER_LOVE = 2;
    /** 「爱」获得间隔闸门 = 30 秒(每 30 秒最多获得 1 层;含一心同体供给) */
    public static final int LOVE_GAIN_INTERVAL_TICKS = 600;
    /** 佩戴期间的基础防御力(未受「爱」层数影响前的值) */
    public static final int BASE_DEFENSE = 6;
    /** 防御力修饰器的 key({@code setDefenseArmorBonus},爱层数修正) */
    public static final String DEFENSE_KEY = "ame_love";
    /** 最大生命修饰器的 key(爱 &gt; 4 时 = 爱 − 4,瞬态) */
    public static final String MAX_HEALTH_KEY = "ame_love_health";
    /** 修饰器重挂的节流间隔(tick;附件真值幂等重挂,防死亡/重登丢失瞬态修饰器) */
    public static final int MODIFIER_REFRESH_INTERVAL_TICKS = 20;

    public AmeSignItem(Properties properties) {
        super(properties);
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  「爱」层数 / 上限(真值 = 附件;效果只是镜像)
    // ══════════════════════════════════════════════════════════════════════════

    /** 当前「爱」层数(0..上限) */
    public static int getLove(Player holder) {
        if (holder == null) return 0;
        return ModAttachments.getAmeLoveLayers(holder);
    }

    /** 当前「爱」层数上限(默认 {@value #AME_LOVE_CAP_DEFAULT},最高 {@value #AME_LOVE_CAP_MAX}) */
    public static int getLoveCap(Player holder) {
        if (holder == null) return AME_LOVE_CAP_DEFAULT;
        int cap = ModAttachments.getAmeLoveCap(holder);
        return Math.max(1, Math.min(AME_LOVE_CAP_MAX, cap));
    }

    /** 直接写入层数(夹逼到 0..上限;同时刷新 HUD 镜像与属性修饰器) */
    public static void setLove(Player holder, int value) {
        if (holder == null || holder.level().isClientSide()) return;
        int clamped = Math.max(0, Math.min(getLoveCap(holder), value));
        ModAttachments.setAmeLoveLayers(holder, clamped);
        AmeLoveEffect.mirror(holder, clamped);
        applyLoveModifiers(holder, clamped);
    }

    /** 层数增减(夹逼;同时刷新 HUD 镜像与属性修饰器) */
    public static void addLove(Player holder, int amount) {
        if (holder == null || amount == 0) return;
        setLove(holder, getLove(holder) + amount);
    }

    /**
     * 「爱」获得的**唯一闸门**(2026-10-16 规格 §3.5):每 {@value #LOVE_GAIN_INTERVAL_TICKS} tick
     * 最多 +1 层,且受自身上限约束。受击触发与**一心同体供给方**送入的层数都经此闸门。
     */
    public static void addLoveWithGate(Player holder) {
        if (holder == null || holder.level().isClientSide()) return;
        long now = holder.level().getGameTime();
        if (now < ModAttachments.getAmeLoveGainCdEnd(holder)) return;
        if (getLove(holder) >= getLoveCap(holder)) return;
        ModAttachments.setAmeLoveGainCdEnd(holder, now + LOVE_GAIN_INTERVAL_TICKS);
        addLove(holder, 1);
    }

    /** 玩家是否佩戴地雷女立牌(功能闸门:需已佩戴骰子) */
    public static boolean isEquipped(Player player) {
        if (player == null) return false;
        if (!CurioSlotUtil.hasDiceEquipped(player)) return false;
        var curios = CuriosApi.getCuriosInventory(player);
        return curios.isPresent()
                && curios.get().findFirstCurio(s -> s.is(ModItems.AME_SIGN.get())).isPresent();
    }

    /** 该玩家是否装着地雷女立牌(**纯**槽位判定,不带骰子闸门;照 sherry/hanna 的 wearsSign 口径,供跨玩家判定) */
    public static boolean wearsSign(Player player) {
        if (player == null) return false;
        var curios = CuriosApi.getCuriosInventory(player);
        if (curios.isEmpty()) return false;
        return curios.get().findFirstCurio(s -> {
            var key = BuiltInRegistries.ITEM.getKey(s.getItem());
            return key != null && SIGN_ID.equals(key.toString());
        }).isPresent();
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  属性修饰器(防御 + 最大生命;瞬态、幂等重挂)
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * 按当前「爱」层数幂等重挂两条瞬态修饰器:
     * <ul>
     *   <li>防御力 = {@code max(0, }{@value #BASE_DEFENSE}{@code  − 爱)}(经
     *       {@link DiceCombatModifiers#setDefenseArmorBonus} ×2.0 折真实护甲;≤ 0 自动移除);</li>
     *   <li>爱 &gt; {@value #AME_LOVE_CAP_DEFAULT} 时,最大生命 {@code + (爱 − 上限基准)} =
     *       {@code 爱 − }{@value #LOVE_SURPLUS_BASE}({@code ADD_VALUE} 瞬态修饰器);
     *       否则移除。</li>
     * </ul>
     */
    public static void applyLoveModifiers(Player player, int love) {
        if (player == null || player.level().isClientSide()) return;
        DiceCombatModifiers.setDefenseArmorBonus(player, DEFENSE_KEY, Math.max(0, BASE_DEFENSE - love));
        setMaxHealthBonus(player, Math.max(0, love - LOVE_SURPLUS_BASE));
    }

    /** 施加/覆盖最大生命加成(≤ 0 ⇒ 移除);与 {@code setDefenseArmorBonus} 同一口径(仅数值变化时写) */
    private static void setMaxHealthBonus(Player player, int bonus) {
        AttributeInstance attr = player.getAttribute(Attributes.MAX_HEALTH);
        if (attr == null) return;
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath(AstralDiceMod.MODID, MAX_HEALTH_KEY);
        var existing = attr.getModifier(id);
        if (bonus <= 0) {
            if (existing != null) attr.removeModifier(id);
            return;
        }
        if (existing == null || existing.amount() != bonus) {
            attr.removeModifier(id);
            attr.addTransientModifier(new AttributeModifier(id, bonus, AttributeModifier.Operation.ADD_VALUE));
        }
    }

    /** 移除本立牌施加的全部瞬态修饰器(卸下立牌 / 层数归零) */
    public static void clearModifiers(Player player) {
        if (player == null || player.level().isClientSide()) return;
        DiceCombatModifiers.setDefenseArmorBonus(player, DEFENSE_KEY, 0);
        setMaxHealthBonus(player, 0);
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  主动「感情过量摄入」
    // ══════════════════════════════════════════════════════════════════════════

    @Override
    protected InteractionResultHolder<ItemStack> handleUse(Level level, Player player, ItemStack stack) {
        if (level.isClientSide) {
            return InteractionResultHolder.success(stack);
        }
        int love = getLove(player);
        // ① 立即恢复 爱层数 × 2 生命值(不超过最大生命)
        float heal = love * HEAL_PER_LOVE;
        if (heal > 0.0F) {
            player.heal(heal);
        }
        // ② 施加「感情过量摄入」(1:00,移速 +20%,图标可见计时)
        //    有限时长效果经 EffectTimerGuard 记账 ⇒ HUD 计时器严格 20 t/s(照 hanna 同款)
        EffectTimerGuard.apply(player, new MobEffectInstance(ModEffects.AME_OVERDOSE,
                AmeOverdoseEffect.DURATION_TICKS, 0, false, true, true));
        sendSignActionBar(player, "msg.astral_dice.ame_overdose", love * HEAL_PER_LOVE);
        return InteractionResultHolder.success(stack);
    }

    // 三态化锁定:主动施加的「感情过量摄入」计时器仍在跑 ⇒ 锁定(生效中)
    @Override
    protected boolean startActiveLockOnUse(Player player, long now) {
        MobEffectInstance instance = player.getEffect(ModEffects.AME_OVERDOSE);
        if (instance == null) return false;
        beginActiveLock(player, SIGN_ID, now + instance.getDuration());
        return true;
    }

    // 门控效果实例仍在:效果被外力提前移除(奶/命令)时锁定提前结束(硬上界不延长)
    @Override
    protected boolean isGateEffectActive(Player player) {
        return player.hasEffect(ModEffects.AME_OVERDOSE);
    }

    /**
     * 已结算过的「感情过量摄入」实例:弱引用 + **身份比较**({@code MapMaker().weakKeys()})。
     * 用途 = 一次自然到期只结算一次,见 {@link #onOverdoseExpired}。
     */
    private static final Map<MobEffectInstance, Boolean> SETTLED_OVERDOSE =
            new MapMaker().weakKeys().makeMap();

    /** 待结算的自然到期(只存玩家):延到同一 tick 的服务端 tick 末尾执行,见 {@link #onOverdoseExpired} */
    private record PendingOverdoseExpiry(Player player) {
    }

    private static final List<PendingOverdoseExpiry> PENDING_OVERDOSE_EXPIRY = new ArrayList<>();

    /**
     * 「感情过量摄入」**自然到期**结算(规格 §3.4):爱 −{@value #EXPIRE_COST}(下限 0)、
     * 爱上限 +1(最高 {@value #AME_LOVE_CAP_MAX})。
     *
     * <p>只在本事件派发时**登记** —— 该事件由 {@code LivingEntity#tickEffects} 在效果剩余时长归零时派发
     * (见 {@code EffectTimerGuard} 的强制到期路径);奶 / {@code /effect clear} / 卸下立牌都走
     * {@code removeEffect},**不派发**本事件 ⇒ 如规格所要求「奶/命令清除不触发该结算」。
     *
     * <p><b>⚠️ 为什么必须延到同一 tick 的服务端 tick 末尾再改数据</b>:{@code MobEffectEvent.Expired}
     * 在 {@code LivingEntity#tickEffects} 的 {@code iterator.remove()} <b>之前</b>发出 —— 此刻若
     * 新增/移除任何效果(本结算里的「爱」层数镜像 {@code AmeLoveEffect.mirror} 必然 remove+add),
     * 就会结构性修改正在被遍历的 {@code activeEffects} ⇒ {@code iterator.remove()} 抛
     * {@code ConcurrentModificationException},而原版把它 {@code catch (ConcurrentModificationException) {}}
     * **静默吞掉** ⇒ **该到期实例本拍不被移除**,其 duration 已为 0 ⇒ 下一拍 {@code Expired}
     * <b>再发一次</b> ⇒ 结算被逐拍重放(2026-10-16 实机:爱 8→4→0、上限 20→21→22→23,恰好 3 次;
     * 层数归 0 后镜像不再改 map,第三拍才真正移除)。范式同 {@code MarkManager#onMarkExpired} 与
     * {@code DiceCombatEvents#onPassiveStackExpired}。
     *
     * <p><b>幂等</b>:即使事件因其它监听者改动效果表(或取消 Expired)而重复派发,同一个
     * {@link MobEffectInstance} 也只会在 {@link #SETTLED_OVERDOSE} 里登记一次 ⇒ 一次自然到期
     * **恰好结算一次**。键用**弱引用 + 身份比较**({@code MapMaker().weakKeys()}),不会因为两个
     * 参数相同的实例(到期时 duration 都为 0)被 {@code MobEffectInstance#equals} 判等而误吞下一次真实结算。
     *
     * <p><b>不可</b>用 {@code player.getEffect(AME_OVERDOSE) != null} 作「是否本玩家当前那一份」的判据:
     * NeoForge 21.1.235 的 {@code LivingEntity#tickEffects} 在派发本事件**之后**才从 activeEffects
     * 移除实例 ⇒ 事件处理期间 {@code getEffect} 必非 null,该判据会把结算整段跳过(2026-10-16 修复 A1)。
     * 「卸下立牌不结算」仍由 {@link #wearsSign} 拦截覆盖(登记与执行各判一次)。
     */
    @SubscribeEvent
    public static void onOverdoseExpired(MobEffectEvent.Expired event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (player.level().isClientSide()) return;
        MobEffectInstance instance = event.getEffectInstance();
        if (instance == null) return;
        if (!instance.getEffect().equals(ModEffects.AME_OVERDOSE)) return;
        // 未佩戴立牌时不结算(卸下立牌已由 clearSignData 清理,不应再扣层)
        if (!wearsSign(player)) return;
        // 幂等:同一到期实例只登记一次(重复派发 / 取消后重放都不再结算)
        if (SETTLED_OVERDOSE.put(instance, Boolean.TRUE) != null) return;
        PENDING_OVERDOSE_EXPIRY.add(new PendingOverdoseExpiry(player));
    }

    /** 服务端 tick 末尾统一结算排队中的「感情过量摄入」自然到期(见 {@link #onOverdoseExpired})。 */
    @SubscribeEvent
    public static void onServerTickPost(ServerTickEvent.Post event) {
        if (PENDING_OVERDOSE_EXPIRY.isEmpty()) return;
        List<PendingOverdoseExpiry> pending = new ArrayList<>(PENDING_OVERDOSE_EXPIRY);
        PENDING_OVERDOSE_EXPIRY.clear();
        for (PendingOverdoseExpiry expiry : pending) {
            Player player = expiry.player();
            if (player == null || player.isRemoved() || !player.isAlive()) continue;
            if (player.level().isClientSide()) continue;
            // 同一 tick 内卸下立牌 / 清场 ⇒ 不结算(维持「卸下清零不结算」口径)
            if (!wearsSign(player)) continue;
            setLove(player, getLove(player) - EXPIRE_COST);
            ModAttachments.setAmeLoveCap(player, Math.min(AME_LOVE_CAP_MAX, getLoveCap(player) + 1));
            AmeLoveEffect.mirror(player, getLove(player));
            sendSignActionBar(player, "msg.astral_dice.ame_overdose_expire", getLove(player));
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  被动「无法承受的爱」/ 备注「一心同体」
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * 佩戴者**受到敌对攻击** ⇒ +1 层「爱」(过 {@link #addLoveWithGate} 的 30 秒闸门与上限);
     * 若「一心同体」成立(队内存在超天酱佩戴者)⇒ 攻击者**同时**被施加「粉丝」
     * (规格 §3.3 施加入口②;粉丝归队内每个超天酱佩戴者)。
     *
     * <p>由 {@link BaseSignItem#invokeHurtHooks} 分发(该处已统一过 {@code isHostileAttack} 闸门:
     * 环境伤害与自伤不算 ⇒ 此处不重复判)。「一心同体」的**供给侧**(超天酱玩家受击 ⇒
     * 队内所有地雷女各 +1 爱)由 {@code KangelSignItem#onHurt} 直接驱动
     * {@link #collectAmeWearers} + {@link #addLoveWithGate},不在此处重复实现。
     */
    @Override
    protected void onHurt(Player player, DamageSource source, float amount) {
        if (player.level().isClientSide()) return;
        addLoveWithGate(player);
        // 一心同体:被敌对攻击 ⇒ 攻击者也上「粉丝」(isHostileAttack 闸门已由 invokeHurtHooks 统一过)
        if (KangelSignItem.hasTeamKangelWearer(player)
                && source.getEntity() instanceof LivingEntity attacker && attacker != player) {
            KangelSignItem.applyFanTo(player, attacker, true);
        }
    }

    /**
     * 「一心同体」施加入口(近战侧,规格 §3.3):地雷女佩戴者**近战直接命中**敌对目标、
     * 且队内存在超天酱佩戴者 ⇒ 目标被施加「粉丝」,队内每个超天酱佩戴者各 +1 粉丝。
     *
     * <p>判据照 {@code KangelSignItem#onAttackHostile} 同款:服务端、{@code getDirectEntity()}
     * 是玩家本人的近战(弹射物/法术/AOE 不算)、非自伤、目标须为该玩家的敌对目标
     * ({@link PartyRelations#isHostileTo});额外要求自己佩戴地雷女立牌({@link #wearsSign})
     * 且队内存在超天酱佩戴者({@link KangelSignItem#hasTeamKangelWearer})。
     * 施加统一走 kangel 侧的唯一入口 {@link KangelSignItem#applyFanTo}。
     */
    @SubscribeEvent(priority = EventPriority.NORMAL)
    public static void onAttackHostile(LivingDamageEvent.Pre event) {
        LivingEntity victim = event.getEntity();
        if (victim.level().isClientSide()) return;
        if (!(event.getSource().getDirectEntity() instanceof Player attacker)) return;
        if (attacker == victim) return;
        if (!wearsSign(attacker)) return;
        if (!KangelSignItem.hasTeamKangelWearer(attacker)) return;
        if (!PartyRelations.isHostileTo(attacker, victim)) return;
        KangelSignItem.applyFanTo(attacker, victim, true);
    }

    /** 队内全部地雷女佩戴者(无队伍时只有触发者自己那一份;照 {@code collectTeamPlayers} 口径) */
    public static List<Player> collectAmeWearers(Player triggerer) {
        List<Player> wearers = new ArrayList<>();
        if (wearsSign(triggerer)) wearers.add(triggerer);
        if (!PartyRelations.hasTeam(triggerer)) return wearers;
        for (Player ally : PartyRelations.collectTeamPlayers(triggerer)) {
            if (ally == triggerer) continue;
            if (wearsSign(ally)) wearers.add(ally);
        }
        return wearers;
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  装备 / 卸下 / 每 tick 维护
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * 装备时:当前 0 层「爱」⇒ 置 {@value #EQUIP_INITIAL_LOVE} 层(用户原文「玩家装备地雷女立牌时的
     * 初始层数为2层」)。Curios 官方签名 {@code onEquip(slotContext, prevStack, stack)}:
     * 第 2 参 prevStack 是**槽位原内容**,第 3 参 stack 才是刚装上的那件(照芯片类先例)。
     */
    @Override
    public void onEquip(SlotContext slotContext, ItemStack prevStack, ItemStack stack) {
        if (!(slotContext.entity() instanceof Player player)) return;
        if (player.level().isClientSide()) return;
        if (getLove(player) <= 0) {
            setLove(player, EQUIP_INITIAL_LOVE);
        }
    }

    /**
     * 每 tick 维护(节流 {@value #MODIFIER_REFRESH_INTERVAL_TICKS}t):
     * ① 按附件真值幂等重挂防御 + 最大生命(防死亡/重登丢失瞬态修饰器);
     * ② 层数镜像校正(死亡重生后效果实例被清空,而层数附件(带 copyOnDeath)仍在)。
     * 「一心同体」给超天酱玩家的 {@code ame_link} 防御**改由 kangel 侧自维护**
     * (见 {@code KangelSignItem#onCurioTick}),本立牌不再代挂。
     */
    @Override
    protected void onCurioTick(SlotContext slotContext, ItemStack stack) {
        if (!(slotContext.entity() instanceof Player player)) return;
        if (player.level().isClientSide()) return;
        long now = player.level().getGameTime();
        if (now % MODIFIER_REFRESH_INTERVAL_TICKS != 0) return;
        int love = getLove(player);
        applyLoveModifiers(player, love);
        if (AmeLoveEffect.getLayers(player) != love) {
            AmeLoveEffect.mirror(player, love);
        }
    }

    /** 卸下立牌:层数清零、上限回默认、移除镜像效果与全部修饰器(含「感情过量摄入」) */
    @Override
    protected void clearSignData(Player player, ItemStack stack) {
        super.clearSignData(player, stack);
        ModAttachments.setAmeLoveLayers(player, 0);
        ModAttachments.setAmeLoveCap(player, AME_LOVE_CAP_DEFAULT);
        ModAttachments.setAmeLoveGainCdEnd(player, 0L);
        AmeLoveEffect.clear(player);
        ModEffectRemoval.remove(player, ModEffects.AME_OVERDOSE);
        clearModifiers(player);
    }
}
