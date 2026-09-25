package com.merlinkitsune.astral_dice.combat;

import com.merlinkitsune.astral_dice.AstralDiceMod;
import com.merlinkitsune.starenginelib.combat.PlayerHostilityTracker;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 敌对玩家记录的**平台事件挂点**(数据表与口径已下沉到库
 * {@link PlayerHostilityTracker},2026-09-22)。
 *
 * <p>本类只做三件事:① 把平台事件翻译成库的 API 调用;② 施加平台相关的守卫
 * (客户端侧判定、事件取消语义);③ 排除本模组内部伤害窗口。
 * 「谁是敌对」「记谁、清谁」的口径全在库里,两侧共用。
 *
 * <p><b>记录</b>:玩家 A 对玩家 B 造成伤害(近战/远程/魔法/投掷物等一切"A 对 B 直接造成伤害"的路径)
 * 时,把 A 记入 B 的敌对名单。挂点选在最终伤害事件 {@link LivingDamageEvent} —— 它晚于
 * {@code LivingAttackEvent}:被最前置取消的攻击(骇客隐身免疫、枪匠破绽闪避等)不会产生该事件,
 * 故不计入"主动攻击过";而被安全气囊/磨刀石无效化的攻击仍算主动攻击
 * (本记录为默认优先级,早于 LOWEST 的保命处理)。
 *
 * <p><b>不记录的情形</b>(口径 = 只记「主动攻击」):① 本模组内部的范围/波及伤害
 * ({@code DiceCombatEvents.aoeProcessing} 窗口:大当家溅射、电击手套/定向爆破 AOE)与反击注入
 * ({@code counterDepth} 窗口)—— 内部波及不是主动攻击,否则会自建敌对立场并互相升级;
 * ② 自伤(攻击者 == 受击者,如绯红骰/王之力自伤);③ 任一侧不是玩家;④ 被取消的伤害。
 * (②③ 由库的 {@code recordAttack} 兜底,①由 {@code recordAttackIfExternal},④ 见下。)
 *
 * <p><b>清理</b>:任意玩家死亡(LOWEST —— 晚于安全气囊/末影骰的"取消死亡"判定,死亡被救回时不清理)、
 * 死亡重生克隆、退出服务器时,把它作为攻击者的记录与作为目标的记录**一并**清除 ⇒
 * 死亡重生后名单为空,不跨死亡残留。
 */
@Mod.EventBusSubscriber(modid = AstralDiceMod.MODID)
public final class PlayerHostilityTrackerEvents {

    private PlayerHostilityTrackerEvents() {
    }

    /** 记录「攻击者主动攻击过受害者」(仅玩家对玩家,且非本人;仅服务端) */
    @SubscribeEvent(priority = EventPriority.NORMAL)
    public static void onLivingDamage(LivingDamageEvent event) {
        // 只记"确实成立的伤害":LivingDamageEvent 在 Forge 可取消(被取消 ⇒ 该伤害不生效)
        if (event.isCanceled()) return;
        // 只记「**主动**攻击」:本模组内部的范围/波及伤害(大当家溅射、电击手套/定向爆破 AOE)与
        // 反击注入都不是主动攻击 —— 前者在 DiceCombatEvents.aoeProcessing、后者在 counterDepth
        // 窗口内,一律不记录(否则溅射会"自建敌对立场"并互相升级:A 溅射 C ⇒ C 敌视 A ⇒ C 溅射 A ⇒ …)。
        // 窗口开关状态仍由 DiceCombatEvents 持有,通过库的 InternalDamageWindows seam 读取。
        LivingEntity victim = event.getEntity();
        if (victim.level().isClientSide()) return;
        if (!(victim instanceof Player victimPlayer)) return;
        Entity attacker = event.getSource().getEntity();
        if (!(attacker instanceof Player attackerPlayer)) return;
        PlayerHostilityTracker.recordAttackIfExternal(victimPlayer, attackerPlayer);
    }

    /** 任意玩家死亡:它作为攻击者与作为目标的敌对立场一并清除 */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onLivingDeath(LivingDeathEvent event) {
        if (event.isCanceled()) return;
        if (event.getEntity().level().isClientSide()) return;
        PlayerHostilityTracker.forget(event.getEntity().getUUID());
    }

    /** 死亡重生(克隆)兜底:死亡事件被别的模组提前吞掉时同样清空(非死亡克隆如跨维度返回不动) */
    @SubscribeEvent
    public static void onPlayerClone(PlayerEvent.Clone event) {
        if (!event.isWasDeath()) return;
        PlayerHostilityTracker.forget(event.getEntity().getUUID());
    }

    /** 退出服务器:清除其全部记录,避免离线 UUID 常驻 */
    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        PlayerHostilityTracker.forget(event.getEntity().getUUID());
    }
}
