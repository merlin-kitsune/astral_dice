package com.merlinkitsune.astral_dice.item.sign;

import com.merlinkitsune.astral_dice.AstralDiceMod;
import com.merlinkitsune.astral_dice.combat.DiceCombatModifiers;
import com.merlinkitsune.astral_dice.compat.curios.CuriosApi;
import com.merlinkitsune.astral_dice.compat.curios.SlotContext;
import com.merlinkitsune.astral_dice.component.SignAttachments;
import com.merlinkitsune.astral_dice.effect.KangelFanEffect;
import com.merlinkitsune.astral_dice.effect.KangelFanIndicatorEffect;
import com.merlinkitsune.astral_dice.item.ModItems;
import com.merlinkitsune.astral_dice.platform.event.EventPriority;
import com.merlinkitsune.astral_dice.platform.event.SubscribeEvent;
import com.merlinkitsune.astral_dice.platform.event.entity.living.LivingDamageEvent;
import com.merlinkitsune.astral_dice.resource.ResourceConversion;
import com.merlinkitsune.starengine.combat.PartyRelations;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 超天酱立牌(kangel,奇特)。Fabric 1.20.1 移植版,语义与 forge-1.20.1 蓝本逐条等价。
 *
 * <h3>主动「网络天使」</h3>
 * 立即获得 {@code min(1 + 粉丝数, 10)} 星币;粉丝数 ≥ {@value #FAN_FULL_HEAL} ⇒ 生命值立即恢复全满;
 * 粉丝数 ≥ {@value #FAN_ALLY_GRANT} ⇒ 友方玩家各得 {@value #ALLY_COINS} 星币
 * (**有队伍** ⇒ 全队队友;**无队伍** ⇒ 自身 {@value #ALLY_RADIUS} 格内其他玩家;两者都不含自己)。
 * 瞬时技能,不进入锁定态;冷却走全局默认链。
 *
 * <h3>被动「小天使请安」</h3>
 * 佩戴者(及一心同体的地雷女队友)**近战直接命中**目标、或**受到敌对攻击** ⇒ 该目标被施加
 * 「粉丝」({@link KangelFanEffect})标记;该标记的目标攻击佩戴者时造成的伤害 −1。
 *
 * <p><b>两个效果 id(2026-10-17 拆分)</b>:目标标记 = {@code kangel_fan}(**战斗判据的唯一真值**);
 * 佩戴者的计数指示器 = {@code kangel_fan_indicator}(**只做 HUD 展示**)。二者绝不混用。
 *
 * <p><b>计数</b>:粉丝数 = 玩家级附件(本线 {@link SignAttachments#KANGEL_FAN_COUNT}),上限
 * {@value #MAX_FANS};**每个目标对每个超天酱佩戴者只计 1 次**(已登记目标 UUID 集,超
 * {@value #MAX_TRACKED_TARGETS} 按最旧淘汰);**跨死亡保留**;**卸下立牌清零**。
 *
 * <h3>平台差异(Fabric 1.20.1 / 逐条理由)</h3>
 * <ul>
 *   <li>① {@code @Mod.EventBusSubscriber} 在本线不存在 ⇒ 删注解,改由 {@code AstralDiceMod} 里
 *       {@code LoaderBus.INSTANCE.register(KangelSignItem.class)} 显式登记(**漏登记 = 编译通过、
 *       运行无声**:近战命中挂「粉丝」整段不再发生);</li>
 *   <li>② forge 事件基类 → {@code platform.event.*} shim({@code SubscribeEvent} /
 *       {@code EventPriority} / {@code entity.living.LivingDamageEvent});</li>
 *   <li>③ Curios:{@code CuriosCompat} → 本线门面 {@link CuriosApi};</li>
 *   <li>④ 附件:{@code ModAttachments.get/setKangelFan*} ⇒ {@link SignAttachments}
 *       (同名键、同 codec、同 sync/copyOnDeath 标记);</li>
 *   <li>⑤ {@code isClientSide} 统一走**方法** {@code level().isClientSide()}。</li>
 * </ul>
 */
public class KangelSignItem extends BaseSignItem {

    /** 立牌注册 id(锁定态/调试用) */
    public static final String SIGN_ID = AstralDiceMod.MODID + ":kangel_sign";

    /** 粉丝数上限 */
    public static final int MAX_FANS = 10;
    /** 「每个目标只提供 1 个粉丝」的已登记目标上限(超出按最旧淘汰) */
    public static final int MAX_TRACKED_TARGETS = 256;
    /** 无队伍时主动「网络天使」的友方发放半径(格) */
    public static final double ALLY_RADIUS = 64.0D;
    /** 生命值恢复全满所需的粉丝数门槛 */
    public static final int FAN_FULL_HEAL = 3;
    /** 给友方玩家发放星币所需的粉丝数门槛 */
    public static final int FAN_ALLY_GRANT = 9;
    /** 该项给每个友方玩家发放的星币数 */
    public static final int ALLY_COINS = 3;

    /** 「一心同体」给超天酱玩家提供的基础防御力(修饰器由本立牌自维护自撤销) */
    public static final int KANGEL_LINK_DEFENSE = 6;
    /** 「一心同体」防御力修饰器的 key(施加在超天酱玩家身上) */
    public static final String KANGEL_LINK_DEFENSE_KEY = "ame_link";
    /** 「一心同体」防御修饰器重挂/撤销的节流间隔(tick) */
    public static final int LINK_REFRESH_INTERVAL_TICKS = 20;

    public KangelSignItem(Properties properties) {
        super(properties);
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  粉丝计数(真值 = 附件;效果只是镜像)
    // ══════════════════════════════════════════════════════════════════════════

    /** 当前粉丝数(0..{@link #MAX_FANS}) */
    public static int getFanCount(Player holder) {
        if (holder == null) return 0;
        return SignAttachments.getKangelFanCount(holder);
    }

    /** 直接写入粉丝数(夹逼到 0..MAX;同时刷新 HUD 指示器) */
    public static void setFanCount(Player holder, int value) {
        if (holder == null || holder.level().isClientSide()) return;
        int clamped = Math.max(0, Math.min(MAX_FANS, value));
        SignAttachments.setKangelFanCount(holder, clamped);
        KangelFanIndicatorEffect.mirrorCount(holder, clamped);
    }

    /** 粉丝数增减(夹逼;同时刷新 HUD 指示器) */
    public static void addFans(Player holder, int amount) {
        if (holder == null || amount == 0) return;
        setFanCount(holder, getFanCount(holder) + amount);
    }

    /** 清空「已登记目标」记录(卸下立牌时,与粉丝数同寿命) */
    public static void clearFanTargets(Player player) {
        if (player == null) return;
        SignAttachments.setKangelFanTargets(player, "");
    }

    private static List<String> readFanTargets(Player player) {
        String raw = SignAttachments.getKangelFanTargets(player);
        if (raw == null || raw.isEmpty()) return new ArrayList<>();
        return new ArrayList<>(Arrays.asList(raw.split(",")));
    }

    /**
     * 登记「该目标已提供过 1 个粉丝」;返回 {@code true} = **本次是新目标**,{@code false} = 已登记过。
     * 记录超 {@link #MAX_TRACKED_TARGETS} 时按最旧淘汰。
     */
    private static boolean markFanTarget(Player player, LivingEntity target) {
        String uuid = target.getUUID().toString();
        List<String> granted = readFanTargets(player);
        if (granted.contains(uuid)) return false;
        granted.add(uuid);
        while (granted.size() > MAX_TRACKED_TARGETS) {
            granted.remove(0);
        }
        SignAttachments.setKangelFanTargets(player, String.join(",", granted));
        return true;
    }

    /** 玩家是否佩戴超天酱立牌(功能闸门:需已佩戴骰子) */
    public static boolean isEquipped(Player player) {
        return SignEquipCheck.isEquipped(player, ModItems.KANGEL_SIGN.get());
    }

    /** 该玩家是否装着超天酱立牌(**纯**槽位判定,不带骰子闸门;供跨玩家判定) */
    public static boolean wearsSignFor(Player player) {
        if (player == null) return false;
        var curios = CuriosApi.getCuriosInventory(player);
        if (curios.isEmpty()) return false;
        return curios.get().findFirstCurio(s -> {
            var key = BuiltInRegistries.ITEM.getKey(s.getItem());
            return key != null && SIGN_ID.equals(key.toString());
        }).isPresent();
    }

    /**
     * 触发者队内是否存在**超天酱立牌佩戴者**(「一心同体」前提:须先有队伍;无队伍一律 false)。
     *
     * <p>立牌槽唯一 ⇒ 地雷女佩戴者不可能同时戴着超天酱立牌,故无需自判。
     */
    public static boolean hasTeamKangelWearer(Player player) {
        if (player == null) return false;
        if (!PartyRelations.hasTeam(player)) return false;
        for (Player ally : PartyRelations.collectTeamPlayers(player)) {
            if (wearsSignFor(ally)) return true;
        }
        return false;
    }

    /**
     * 本玩家队内是否存在**地雷女立牌佩戴者**(超天酱侧自维护「一心同体」防御的判据;
     * 同样须先有队伍,同样无需自判 —— 立牌槽唯一)。
     */
    private static boolean hasTeamAmeWearer(Player player) {
        if (player == null) return false;
        if (!PartyRelations.hasTeam(player)) return false;
        for (Player ally : PartyRelations.collectTeamPlayers(player)) {
            if (AmeSignItem.wearsSign(ally)) return true;
        }
        return false;
    }

    /**
     * 一次施加应计入的**全部**超天酱佩戴者:
     * <ul>
     *   <li>触发者是超天酱佩戴者 ⇒ 加上她自己;</li>
     *   <li>触发者**有队伍** ⇒ 再加上队内**全部**超天酱佩戴者(「任何一次施加使触发者队内每个
     *       超天酱佩戴者各 +1」)。</li>
     * </ul>
     * 无队伍 ⇒ 只有她自己的那一份。
     */
    private static List<Player> wearersFor(Player triggerer) {
        List<Player> wearers = new ArrayList<>();
        if (wearsSignFor(triggerer)) wearers.add(triggerer);
        if (!PartyRelations.hasTeam(triggerer)) return wearers;
        for (Player ally : PartyRelations.collectTeamPlayers(triggerer)) {
            if (ally == triggerer) continue;
            if (wearsSignFor(ally)) wearers.add(ally);
        }
        return wearers;
    }

    /**
     * 「小天使请安」+「一心同体」的**唯一施加入口**:给 {@code victim} 挂上「粉丝」标记,
     * 并使触发者队内每个超天酱佩戴者各 +1 粉丝(各自去重、各自不超 {@link #MAX_FANS})。
     *
     * <p>**一心同体**:地雷女佩戴者侧的施加(她近战命中 / 她被敌对攻击)同样走此入口 ——
     * 由 {@link AmeSignItem} 在确认「队内存在超天酱佩戴者」后调用。
     *
     * @param applier    本次触发的来源玩家(是否超天酱佩戴者决定「她自己那一份」)
     * @param victim     被施加「粉丝」的目标(**必须是触发者的敌对目标**)
     * @param markEffect 是否给目标挂「粉丝」标记(近战命中/受击两条入口都为 true;
     *                   计数在达到上限后不再增长,但标记照挂)
     */
    public static void applyFanTo(Player applier, LivingEntity victim, boolean markEffect) {
        if (applier == null || victim == null) return;
        if (applier.level().isClientSide()) return;
        // 目标标记走 kangel_fan(= 「是否粉丝」的唯一真值);佩戴者的计数指示器是另一个 id,不在此处施加
        if (markEffect) KangelFanEffect.applyMark(victim);
        for (Player wearer : wearersFor(applier)) {
            if (getFanCount(wearer) >= MAX_FANS) continue;
            if (markFanTarget(wearer, victim)) addFans(wearer, 1);
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  主动「网络天使」
    // ══════════════════════════════════════════════════════════════════════════

    @Override
    protected InteractionResultHolder<ItemStack> handleUse(Level level, Player player, ItemStack stack) {
        if (level.isClientSide()) {
            return InteractionResultHolder.success(stack);
        }
        int fans = getFanCount(player);
        // ① 自身:min(1 + 粉丝数, 10) 星币
        int coins = Math.min(1 + fans, MAX_FANS);
        giveCoins(player, coins);
        // ② 粉丝数 ≥ 3 ⇒ 生命值立即恢复全满
        boolean fullHeal = fans >= FAN_FULL_HEAL;
        if (fullHeal) {
            player.setHealth(player.getMaxHealth());
        }
        // ③ 粉丝数 ≥ 9 ⇒ 友方玩家各得 3 星币(有队伍 ⇒ 全队;无队伍 ⇒ 64 格内其他玩家)
        int granted = 0;
        if (fans >= FAN_ALLY_GRANT) {
            for (Player ally : alliesFor(player)) {
                giveCoins(ally, ALLY_COINS);
                granted++;
            }
        }
        if (!fullHeal && granted <= 0) {
            sendSignActionBar(player, "msg.astral_dice.kangel_active", coins);
        } else if (fullHeal && granted <= 0) {
            sendSignActionBar(player, "msg.astral_dice.kangel_active_heal", coins);
        } else if (!fullHeal) {
            sendSignActionBar(player, "msg.astral_dice.kangel_active_ally", coins, granted);
        } else {
            sendSignActionBar(player, "msg.astral_dice.kangel_active_full", coins, granted);
        }
        return InteractionResultHolder.success(stack);
    }

    /** 友方玩家集合(不含自己):有队伍 ⇒ 全队队友;无队伍 ⇒ 自身 {@link #ALLY_RADIUS} 格内其他玩家 */
    private static List<Player> alliesFor(Player player) {
        List<Player> allies = new ArrayList<>();
        if (PartyRelations.hasTeam(player)) {
            for (Player teammate : PartyRelations.collectTeamPlayers(player)) {
                if (teammate != player) allies.add(teammate);
            }
            return allies;
        }
        allies.addAll(player.level().getEntitiesOfClass(Player.class,
                player.getBoundingBox().inflate(ALLY_RADIUS), p -> p != player && p.isAlive()));
        return allies;
    }

    /** 走既有发币漏斗(优先入钱包) */
    private static void giveCoins(Player player, int count) {
        if (player == null || count <= 0) return;
        ItemStack coins = new ItemStack(ModItems.STAR_COIN.get(), count);
        ResourceConversion.giveItem(player, coins);
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  被动「小天使请安」/ 备注「一心同体」
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * 佩戴者**近战直接命中**敌对目标 ⇒ 目标被施加「粉丝」,队内每个超天酱佩戴者各 +1 粉丝。
     *
     * <p>只认 {@code getDirectEntity()} 是玩家本人的近战 —— 弹射物、法术、AOE 与环境伤害一律不计;
     * 目标须为该玩家的敌对目标({@link PartyRelations#isHostileTo})。
     */
    @SubscribeEvent(priority = EventPriority.NORMAL)
    public static void onAttackHostile(LivingDamageEvent event) {
        LivingEntity victim = event.getEntity();
        if (victim.level().isClientSide()) return;
        if (!(event.getSource().getDirectEntity() instanceof Player attacker)) return;
        if (attacker == victim) return;
        if (!wearsSignFor(attacker)) return;
        if (!PartyRelations.isHostileTo(attacker, victim)) return;
        applyFanTo(attacker, victim, true);
    }

    /**
     * 佩戴者(含一心同体的地雷女)**受到敌对攻击** ⇒ 攻击者被施加「粉丝」,队内每个超天酱佩戴者各 +1 粉丝。
     *
     * <p>由基类的受击钩子分发(已统一过 {@code isHostileAttack} 闸门)。本立牌自身也走这里 ——
     * 用于「一心同体」:地雷女被攻击时,队内每个超天酱佩戴者各 +1 粉丝(无需双方都装同一张立牌)。
     */
    @Override
    protected void onHurt(Player player, DamageSource source, float amount) {
        if (player.level().isClientSide()) return;
        if (!(source.getEntity() instanceof LivingEntity attacker)) return;
        if (attacker == player) return;
        if (wearsSignFor(player)) {
            applyFanTo(player, attacker, true);
        }
        // 备注「一心同体」供给方:超天酱玩家受到敌对伤害 ⇒ 队内**所有**地雷女佩戴者各 +1 爱
        for (Player ameWearer : AmeSignItem.collectAmeWearers(player)) {
            AmeSignItem.addLoveWithGate(ameWearer);
        }
    }

    /** 伤害减免判据:目标带有「粉丝」,且受伤方是超天酱佩戴者本人 / 「一心同体」的地雷女佩戴者 */
    public static boolean fanMitigationActive(Player victimPlayer, DamageSource source) {
        if (victimPlayer == null || source == null) return false;
        if (victimPlayer.level().isClientSide()) return false;
        if (!(source.getEntity() instanceof LivingEntity attacker)) return false;
        if (!KangelFanEffect.has(attacker)) return false;
        return wearsFanPassive(victimPlayer);
    }

    /**
     * 该玩家是否受「小天使请安」减伤保护 —— **恰好**规格的两个主体:
     * <ul>
     *   <li>超天酱立牌佩戴者本人(任意带「粉丝」的目标打她都 −1);</li>
     *   <li>「一心同体」下的**地雷女立牌佩戴者**,且其队内确实存在超天酱佩戴者。</li>
     * </ul>
     * 其他普通队友**不**享有本被动。
     */
    public static boolean wearsFanPassive(Player victimPlayer) {
        if (victimPlayer == null) return false;
        if (wearsSignFor(victimPlayer)) return true;   // 超天酱佩戴者本人
        // 一心同体:地雷女佩戴者受其队内超天酱佩戴者的「小天使请安」保护
        return AmeSignItem.wearsSign(victimPlayer) && hasTeamKangelWearer(victimPlayer);
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  每 tick 维护
    // ══════════════════════════════════════════════════════════════════════════

    @Override
    protected void onCurioTick(SlotContext slotContext, ItemStack stack) {
        if (!(slotContext.entity() instanceof Player player)) return;
        if (player.level().isClientSide()) return;
        // 指示器每 tick 校正(死亡重生后效果实例被清空,而粉丝数附件(带 copyOnDeath)仍在)
        int fans = getFanCount(player);
        if (KangelFanIndicatorEffect.getCount(player) != fans) {
            KangelFanIndicatorEffect.mirrorCount(player, fans);
        }
        // 「一心同体」防御的自维护(节流 20t):队内存在地雷女佩戴者 ⇒ 自挂 6 点基础防御,否则幂等移除
        if (player.level().getGameTime() % LINK_REFRESH_INTERVAL_TICKS != 0) return;
        boolean link = hasTeamAmeWearer(player);
        DiceCombatModifiers.setDefenseArmorBonus(player, KANGEL_LINK_DEFENSE_KEY,
                link ? KANGEL_LINK_DEFENSE : 0);
    }

    /** 卸下立牌:粉丝数、已登记目标与显示指示器一起清空 */
    @Override
    protected void clearSignData(Player player, ItemStack stack) {
        super.clearSignData(player, stack);
        SignAttachments.setKangelFanCount(player, 0);
        clearFanTargets(player);
        // 只移除**指示器** —— 玩家身上的「粉丝」目标标记是别人给的,不随她自己卸牌消失
        KangelFanIndicatorEffect.clear(player);
        // 「一心同体」防御随佩戴结束一并撤销(key 归属本立牌;0 = 移除,幂等)
        DiceCombatModifiers.setDefenseArmorBonus(player, KANGEL_LINK_DEFENSE_KEY, 0);
    }
}
