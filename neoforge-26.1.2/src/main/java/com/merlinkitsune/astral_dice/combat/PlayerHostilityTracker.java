package com.merlinkitsune.astral_dice.combat;

import com.merlinkitsune.astral_dice.AstralDiceMod;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 敌对玩家记录表(用户裁决,全局规则):
 * 「非同队伍玩家,且死亡前主动攻击过你的玩家,一律视为敌对目标;若玩家死亡则清除敌对立场」。
 *
 * <p><b>记录</b>:玩家 A 对玩家 B 造成伤害(近战/远程/魔法/投掷物等一切"A 对 B 直接造成伤害"的路径)
 * 时,把 A 记入 B 的敌对名单。挂点选在最终伤害事件 {@link LivingDamageEvent.Pre} —— 它晚于
 * {@code LivingIncomingDamageEvent}:被最前置取消的攻击(骇客隐身免疫、枪匠破绽闪避等)不会产生该事件,
 * 故不计入"主动攻击过";而被安全气囊/磨刀石无效化的攻击仍算主动攻击
 * (本记录为默认优先级,早于 LOWEST 的保命处理)。
 *
 * <p><b>不记录的情形</b>(口径 = 只记「主动攻击」):① 本模组内部的范围/波及伤害
 * ({@code DiceCombatEvents.aoeProcessing} 窗口:大当家溅射、电击手套/定向爆破 AOE)与反击注入
 * ({@code counterDepth} 窗口)—— 内部波及不是主动攻击,否则会自建敌对立场并互相升级;
 * ② 自伤(攻击者 == 受击者,如绯红骰/王之力自伤);③ 任一侧不是玩家;④ 被取消的伤害。
 *
 * <p><b>查询</b>:由 {@link HostileTargets#isHostile(Entity, Entity)} 传入"视谁为敌"的上下文后调用
 * {@link #hasAttacked(Player, Player)};同队豁免、"未加入队伍不算同队"的口径均在 HostileTargets 内。
 *
 * <p><b>清理</b>:任意玩家死亡(LOWEST —— 晚于安全气囊/末影骰的"取消死亡"判定,死亡被救回时不清理)、
 * 死亡重生克隆、退出服务器时,把它作为攻击者的记录与作为目标的记录**一并**清除 ⇒
 * 死亡重生后名单为空,不跨死亡残留。
 *
 * <p><b>存储</b>:服务端内存静态表(先例:{@code item.chip.ElectricSwordChipItem.KILL_COUNTS}),
 * 不做持久化(服务器重启即清空,与"死亡清除"同一口径);仅在服务端主线程读写。
 */
@EventBusSubscriber(modid = AstralDiceMod.MODID)
public final class PlayerHostilityTracker {

    /** 受害者 UUID → 「主动攻击过该受害者」的玩家 UUID 集合 */
    private static final Map<UUID, Set<UUID>> HOSTILE_ATTACKERS = new HashMap<>();

    private PlayerHostilityTracker() {
    }

    /** 记录「攻击者主动攻击过受害者」(仅玩家对玩家,且非本人;仅服务端) */
    @SubscribeEvent
    public static void onLivingDamagePre(LivingDamageEvent.Pre event) {
        // 只记「**主动**攻击」:本模组内部的范围/波及伤害(大当家溅射、电击手套/定向爆破 AOE)与
        // 反击注入都不是主动攻击 —— 前者在 DiceCombatEvents.aoeProcessing、后者在 counterDepth
        // 窗口内,一律不记录(否则溅射会"自建敌对立场"并互相升级:A 溅射 C ⇒ C 敌视 A ⇒ C 溅射 A ⇒ …)。
        if (DiceCombatEvents.isInternalAoe() || DiceCombatEvents.isInCounterChain()) return;
        LivingEntity victim = event.getEntity();
        if (victim.level().isClientSide()) return;
        if (!(victim instanceof Player victimPlayer)) return;
        Entity attacker = event.getSource().getEntity();
        if (!(attacker instanceof Player attackerPlayer)) return;
        if (attackerPlayer == victimPlayer) return;
        HOSTILE_ATTACKERS.computeIfAbsent(victimPlayer.getUUID(), key -> new HashSet<>())
                .add(attackerPlayer.getUUID());
    }

    /** 任意玩家死亡:它作为攻击者与作为目标的敌对立场一并清除 */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onLivingDeath(LivingDeathEvent event) {
        if (event.isCanceled()) return;
        if (event.getEntity().level().isClientSide()) return;
        forget(event.getEntity().getUUID());
    }

    /** 死亡重生(克隆)兜底:死亡事件被别的模组提前吞掉时同样清空(非死亡克隆如跨维度返回不动) */
    @SubscribeEvent
    public static void onPlayerClone(PlayerEvent.Clone event) {
        if (!event.isWasDeath()) return;
        forget(event.getEntity().getUUID());
    }

    /** 退出服务器:清除其全部记录,避免离线 UUID 常驻 */
    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        forget(event.getEntity().getUUID());
    }

    /** {@code attacker} 是否曾主动攻击过 {@code victim}(名单查询;同队豁免等口径见 {@link HostileTargets}) */
    public static boolean hasAttacked(Player victim, Player attacker) {
        if (victim == null || attacker == null) return false;
        Set<UUID> attackers = HOSTILE_ATTACKERS.get(victim.getUUID());
        return attackers != null && attackers.contains(attacker.getUUID());
    }

    /** 清除该 UUID 的全部敌对立场(它作为攻击者的记录 + 它作为目标的记录) */
    private static void forget(UUID uuid) {
        if (uuid == null) return;
        HOSTILE_ATTACKERS.remove(uuid);
        for (Set<UUID> attackers : HOSTILE_ATTACKERS.values()) {
            attackers.remove(uuid);
        }
    }
}
