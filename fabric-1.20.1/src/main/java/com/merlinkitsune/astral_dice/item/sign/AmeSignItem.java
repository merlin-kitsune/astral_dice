package com.merlinkitsune.astral_dice.item.sign;

import com.google.common.collect.MapMaker;
import com.merlinkitsune.astral_dice.AstralDiceMod;
import com.merlinkitsune.astral_dice.combat.DiceCombatModifiers;
import com.merlinkitsune.astral_dice.compat.curios.CuriosApi;
import com.merlinkitsune.astral_dice.compat.curios.SlotContext;
import com.merlinkitsune.astral_dice.component.SignAttachments;
import com.merlinkitsune.astral_dice.effect.AmeLoveEffect;
import com.merlinkitsune.astral_dice.effect.AmeOverdoseEffect;
import com.merlinkitsune.astral_dice.effect.ModEffects;
import com.merlinkitsune.astral_dice.event.EffectTimerGuard;
import com.merlinkitsune.astral_dice.item.ModItems;
import com.merlinkitsune.astral_dice.platform.event.EventPriority;
import com.merlinkitsune.astral_dice.platform.event.SubscribeEvent;
import com.merlinkitsune.astral_dice.platform.event.TickEvent;
import com.merlinkitsune.astral_dice.platform.event.entity.living.LivingDamageEvent;
import com.merlinkitsune.astral_dice.platform.event.entity.living.MobEffectEvent;
import com.merlinkitsune.starengine.combat.PartyRelations;
import com.merlinkitsune.starengine.event.ModEffectRemoval;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 地雷女立牌(ame,奇特)。Fabric 1.20.1 移植版,语义与 forge-1.20.1 蓝本逐条等价。
 *
 * <h3>主动「感情过量摄入」</h3>
 * 立即恢复 {@code 爱层数 × 2} 生命值(不超过最大生命),并经 {@link EffectTimerGuard} 施加
 * {@link AmeOverdoseEffect}({@value AmeOverdoseEffect#DURATION_TICKS} tick = 1:00,移速 +20%,
 * 图标可见计时)。**三态化锁定**:锁定跟随 {@code ame_overdose} 存续,到期后进入冷却(无空档)。
 * **仅自然到期**({@link MobEffectEvent.Expired})触发结算:爱 −{@value #EXPIRE_COST}(下限 0)、
 * 爱上限 +1(最高 {@value #AME_LOVE_CAP_MAX});奶/命令清除不派发 Expired 事件 ⇒ **不触发结算**
 * (卸下立牌走 {@code clearSignData} 显式移除,同样不结算)。结算**延到同一 tick 的服务端 tick 末尾**
 * 执行且按实例幂等(一次自然到期恰好一次)。
 *
 * <h3>被动「无法承受的爱」</h3>
 * 佩戴期间基础防御 {@value #BASE_DEFENSE} 点;每层爱:骰战**攻击力 +1**、**防御力 −1(最低 0)**、
 * **爱 &gt; 4 时**最大生命 {@code + (爱 − 4)}。受到敌对攻击 ⇒ +1 层,每 {@value #LOVE_GAIN_INTERVAL_TICKS}
 * tick(= 30 秒)最多 1 层(附件 {@code AME_LOVE_GAIN_CD_END}),受自身上限约束。
 * {@code onEquip} 时若当前 0 层 ⇒ 置 {@value #EQUIP_INITIAL_LOVE} 层。层数与上限**跨死亡保留**;
 * **卸下清零**。
 *
 * <h3>平台差异(Fabric 1.20.1 / 逐条理由)</h3>
 * <ul>
 *   <li>① {@code @Mod.EventBusSubscriber} 在本线不存在 ⇒ 删注解,改由 {@code AstralDiceMod} 里
 *       {@code LoaderBus.INSTANCE.register(AmeSignItem.class)} 显式登记(**漏登记 = 编译通过、
 *       运行无声**:主动自然到期结算与近战「一心同体」不再发生);</li>
 *   <li>② forge 事件基类 → 本线 {@code platform.event.*} shim:{@code SubscribeEvent} /
 *       {@code EventPriority} / {@code TickEvent.ServerTickEvent}({@code phase} 字段同形)/
 *       {@code entity.living.LivingDamageEvent}({@code getEntity()}/{@code getSource()})/
 *       {@code entity.living.MobEffectEvent.Expired}({@code getEffectInstance()});</li>
 *   <li>③ Curios:{@code CuriosCompat} → 本线门面 {@link CuriosApi};官方签名
 *       {@code onEquip(SlotContext, ItemStack prevStack, ItemStack stack)} 同形;</li>
 *   <li>④ 施效通道:{@code PlayerEffectProtection#apply} ⇒ {@link EffectTimerGuard#apply}
 *       (本线既有立牌现行写法);</li>
 *   <li>⑤ 附件:{@code ModAttachments.get/setAmeLove*} ⇒ {@link SignAttachments}(同名键、同 codec、
 *       同 sync/copyOnDeath 标记);</li>
 *   <li>⑥ {@code isClientSide} 统一走**方法** {@code level().isClientSide()}。</li>
 * </ul>
 *
 * <p><b>⚠️ 与 forge 的一处口径差(需裁决)</b>:forge 在 {@code MobEffectEvent.Expired} 上靠
 * {@code MapMaker().weakKeys()} 做「同实例只结算一次」。本线 {@code ModEffectEvents} 的 Remove/Expired
 * 派发顺序由本地 {@link EffectTimerGuard} 驱动(强制到期路径同样是「先派发、后移除」),逻辑照搬;
 * 见 porting/fabric-1.20.1/B4-SIGNS-REPORT.md 的待验证项。
 */
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
    /** 「超出部分转为最大生命」的基准层数(爱 > 此值 ⇒ 每层 +1 最大生命) */
    public static final int LOVE_SURPLUS_BASE = 4;
    /** 主动恢复生命的倍率(爱层数 × 此值) */
    public static final int HEAL_PER_LOVE = 2;
    /** 「爱」获得间隔闸门 = 30 秒 */
    public static final int LOVE_GAIN_INTERVAL_TICKS = 600;
    /** 佩戴期间的基础防御力 */
    public static final int BASE_DEFENSE = 6;
    /** 防御力修饰器的 key(经 {@code setDefenseArmorBonus} 按 1÷玩家护甲系数 折真实护甲) */
    public static final String DEFENSE_KEY = "ame_love";
    /** 最大生命修饰器的 key(爱 &gt; 4 时 = 爱 − 4,瞬态) */
    public static final String MAX_HEALTH_KEY = "ame_love_health";
    /** 修饰器重挂的节流间隔(tick) */
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
        return SignAttachments.getAmeLoveLayers(holder);
    }

    /** 当前「爱」层数上限(默认 {@value #AME_LOVE_CAP_DEFAULT},最高 {@value #AME_LOVE_CAP_MAX}) */
    public static int getLoveCap(Player holder) {
        if (holder == null) return AME_LOVE_CAP_DEFAULT;
        int cap = SignAttachments.getAmeLoveCap(holder);
        return Math.max(1, Math.min(AME_LOVE_CAP_MAX, cap));
    }

    /** 直接写入层数(夹逼到 0..上限;同时刷新 HUD 镜像与属性修饰器) */
    public static void setLove(Player holder, int value) {
        if (holder == null || holder.level().isClientSide()) return;
        int clamped = Math.max(0, Math.min(getLoveCap(holder), value));
        SignAttachments.setAmeLoveLayers(holder, clamped);
        AmeLoveEffect.mirror(holder, clamped);
        applyLoveModifiers(holder, clamped);
    }

    /** 层数增减(夹逼;同时刷新 HUD 镜像与属性修饰器) */
    public static void addLove(Player holder, int amount) {
        if (holder == null || amount == 0) return;
        setLove(holder, getLove(holder) + amount);
    }

    /**
     * 「爱」获得的**唯一闸门**:每 {@value #LOVE_GAIN_INTERVAL_TICKS} tick 最多 +1 层,且受自身上限约束。
     * 受击触发与**一心同体供给方**送入的层数都经此闸门。
     */
    public static void addLoveWithGate(Player holder) {
        if (holder == null || holder.level().isClientSide()) return;
        long now = holder.level().getGameTime();
        if (now < SignAttachments.getAmeLoveGainCdEnd(holder)) return;
        if (getLove(holder) >= getLoveCap(holder)) return;
        SignAttachments.setAmeLoveGainCdEnd(holder, now + LOVE_GAIN_INTERVAL_TICKS);
        addLove(holder, 1);
    }

    /** 玩家是否佩戴地雷女立牌(功能闸门:需已佩戴骰子) */
    public static boolean isEquipped(Player player) {
        return SignEquipCheck.isEquipped(player, ModItems.AME_SIGN.get());
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
     *   <li>防御力 = {@code max(0, 6 − 爱)}(经 {@link DiceCombatModifiers#setDefenseArmorBonus} 按 1÷玩家护甲系数 折真实护甲);</li>
     *   <li>爱 &gt; 4 时最大生命 {@code + (爱 − 4)}({@code ADDITION} 瞬态修饰器);否则移除。</li>
     * </ul>
     */
    public static void applyLoveModifiers(Player player, int love) {
        if (player == null || player.level().isClientSide()) return;
        DiceCombatModifiers.setDefenseArmorBonus(player, DEFENSE_KEY, Math.max(0, BASE_DEFENSE - love));
        setMaxHealthBonus(player, Math.max(0, love - LOVE_SURPLUS_BASE));
    }

    /**
     * 施加/覆盖最大生命加成(≤ 0 ⇒ 移除);与 {@code setDefenseArmorBonus} 同一口径(仅数值变化时写)。
     * 1.20.1 的修饰器 id 是 {@code UUID}({@link SignEquipCheck} 同源的 UUIDv5 派生),操作为 {@code ADDITION}。
     */
    private static void setMaxHealthBonus(Player player, int bonus) {
        if (player == null || player.level().isClientSide()) return;
        SignAttributeUtil.apply(player, Attributes.MAX_HEALTH, MAX_HEALTH_KEY, bonus,
                AttributeModifier.Operation.ADDITION);
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
        if (level.isClientSide()) {
            return InteractionResultHolder.success(stack);
        }
        int love = getLove(player);
        // ① 立即恢复 爱层数 × 2 生命值(不超过最大生命)
        float heal = love * HEAL_PER_LOVE;
        if (heal > 0.0F) {
            player.heal(heal);
        }
        // ② 施加「感情过量摄入」(1:00,移速 +20%,图标可见计时)
        EffectTimerGuard.apply(player, new MobEffectInstance(ModEffects.AME_OVERDOSE.get(),
                AmeOverdoseEffect.DURATION_TICKS, 0, false, true, true));
        sendSignActionBar(player, "msg.astral_dice.ame_overdose", love * HEAL_PER_LOVE);
        return InteractionResultHolder.success(stack);
    }

    // 三态化锁定:主动施加的「感情过量摄入」计时器仍在跑 ⇒ 锁定(生效中)
    @Override
    protected boolean startActiveLockOnUse(Player player, long now) {
        MobEffectInstance instance = player.getEffect(ModEffects.AME_OVERDOSE.get());
        if (instance == null) return false;
        beginActiveLock(player, SIGN_ID, now + instance.getDuration());
        return true;
    }

    // 门控效果实例仍在:效果被外力提前移除(奶/命令)时锁定提前结束(硬上界不延长)
    @Override
    protected boolean isGateEffectActive(Player player) {
        return player.hasEffect(ModEffects.AME_OVERDOSE.get());
    }

    /**
     * 已结算过的「感情过量摄入」实例:弱引用 + **身份比较**({@code MapMaker().weakKeys()})。
     * 用途 = 一次自然到期只结算一次,见 {@link #onOverdoseExpired}。
     */
    private static final Map<MobEffectInstance, Boolean> SETTLED_OVERDOSE =
            new MapMaker().weakKeys().makeMap();

    /** 待结算的自然到期(只存玩家):延到同一 tick 的服务端 tick 末尾执行 */
    private record PendingOverdoseExpiry(Player player) {
    }

    private static final List<PendingOverdoseExpiry> PENDING_OVERDOSE_EXPIRY = new ArrayList<>();

    /**
     * 「感情过量摄入」**自然到期**结算:爱 −{@value #EXPIRE_COST}(下限 0)、爱上限 +1(最高 32)。
     *
     * <p>只在本事件派发时**登记** —— 奶 / {@code /effect clear} / 卸下立牌都走 {@code removeEffect},
     * **不派发**本事件 ⇒ 如规格所要求「奶/命令清除不触发该结算」。
     *
     * <p><b>⚠️ 为什么必须延到同一 tick 的服务端 tick 末尾再改数据</b>:到期事件在
     * {@code LivingEntity#tickEffects} 的 {@code iterator.remove()} <b>之前</b>发出 —— 此刻若
     * 新增/移除任何效果(本结算里的镜像 {@code AmeLoveEffect.mirror} 必然 remove+add),
     * 就会结构性修改正在被遍历的 {@code activeEffects} ⇒ {@code iterator.remove()} 抛
     * {@code ConcurrentModificationException},原版把它静默吞掉 ⇒ 该到期实例本拍不被移除,
     * 下一拍 Expired **再发一次** ⇒ 结算被逐拍重放。范式同 {@code MarkManager#onMarkExpired}。
     *
     * <p><b>幂等</b>:同一个 {@link MobEffectInstance} 只会在 {@link #SETTLED_OVERDOSE} 里登记一次。
     * 键用**弱引用 + 身份比较**,不会因两个参数相同的实例(到期时 duration 都为 0)被
     * {@code equals} 判等而误吞下一次真实结算。
     */
    @SubscribeEvent
    public static void onOverdoseExpired(MobEffectEvent.Expired event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (player.level().isClientSide()) return;
        MobEffectInstance instance = event.getEffectInstance();
        if (instance == null) return;
        if (instance.getEffect() != ModEffects.AME_OVERDOSE.get()) return;
        // 未佩戴立牌时不结算(卸下立牌已由 clearSignData 清理,不应再扣层)
        if (!wearsSign(player)) return;
        // 幂等:同一到期实例只登记一次
        if (SETTLED_OVERDOSE.put(instance, Boolean.TRUE) != null) return;
        PENDING_OVERDOSE_EXPIRY.add(new PendingOverdoseExpiry(player));
    }

    /** 服务端 tick 末尾统一结算排队中的自然到期(见 {@link #onOverdoseExpired})。 */
    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
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
            SignAttachments.setAmeLoveCap(player, Math.min(AME_LOVE_CAP_MAX, getLoveCap(player) + 1));
            AmeLoveEffect.mirror(player, getLove(player));
            sendSignActionBar(player, "msg.astral_dice.ame_overdose_expire", getLove(player));
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  被动「无法承受的爱」/ 备注「一心同体」
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * 佩戴者**受到敌对攻击** ⇒ +1 层「爱」(过 30 秒闸门与上限);若「一心同体」成立
     * ⇒ 攻击者**同时**被施加「粉丝」(粉丝归队内每个超天酱佩戴者)。
     * 由 {@link BaseSignItem} 的受击钩子分发(已统一过 {@code isHostileAttack} 闸门)。
     */
    @Override
    protected void onHurt(Player player, DamageSource source, float amount) {
        if (player.level().isClientSide()) return;
        addLoveWithGate(player);
        if (KangelSignItem.hasTeamKangelWearer(player)
                && source.getEntity() instanceof LivingEntity attacker && attacker != player) {
            KangelSignItem.applyFanTo(player, attacker, true);
        }
    }

    /**
     * 「一心同体」施加入口(近战侧):地雷女佩戴者**近战直接命中**敌对目标、且队内存在超天酱佩戴者
     * ⇒ 目标被施加「粉丝」,队内每个超天酱佩戴者各 +1 粉丝。
     *
     * <p>判据:服务端、{@code getDirectEntity()} 是玩家本人的近战(弹射物/法术/AOE 不算)、非自伤、
     * 目标须为该玩家的敌对目标({@link PartyRelations#isHostileTo});额外要求自己佩戴地雷女立牌
     * 且队内存在超天酱佩戴者。施加统一走 kangel 侧的唯一入口 {@link KangelSignItem#applyFanTo}。
     */
    @SubscribeEvent(priority = EventPriority.NORMAL)
    public static void onAttackHostile(LivingDamageEvent event) {
        LivingEntity victim = event.getEntity();
        if (victim.level().isClientSide()) return;
        if (!(event.getSource().getDirectEntity() instanceof Player attacker)) return;
        if (attacker == victim) return;
        if (!wearsSign(attacker)) return;
        if (!KangelSignItem.hasTeamKangelWearer(attacker)) return;
        if (!PartyRelations.isHostileTo(attacker, victim)) return;
        KangelSignItem.applyFanTo(attacker, victim, true);
    }

    /** 队内全部地雷女佩戴者(无队伍时只有触发者自己那一份) */
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
     * 装备时:当前 0 层「爱」⇒ 置 {@value #EQUIP_INITIAL_LOVE} 层。
     * Curios 官方签名 {@code onEquip(slotContext, prevStack, stack)}:第 2 参是**槽位原内容**,
     * 第 3 参才是刚装上的那件。
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
     * 每 tick 维护(节流 20t):① 按附件真值幂等重挂防御 + 最大生命(防死亡/重登丢失瞬态修饰器);
     * ② 层数镜像校正(死亡重生后效果实例被清空,而带 copyOnDeath 的层数附件仍在)。
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
        SignAttachments.setAmeLoveLayers(player, 0);
        SignAttachments.setAmeLoveCap(player, AME_LOVE_CAP_DEFAULT);
        SignAttachments.setAmeLoveGainCdEnd(player, 0L);
        AmeLoveEffect.clear(player);
        ModEffectRemoval.remove(player, ModEffects.AME_OVERDOSE.get());
        clearModifiers(player);
    }
}
