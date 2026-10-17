package com.merlinkitsune.astral_dice.event;

import com.merlinkitsune.astral_dice.AstralDiceMod;
import com.merlinkitsune.astral_dice.combat.EliteTargets;
import com.merlinkitsune.astral_dice.entity.BarricadeEntity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent;

/**
 * 路障仇恨稳定性（2026-10-18 独立审查修复 · F3）。
 *
 * <p><b>要解决的问题</b>：{@link BarricadeEntity#tick()} 每 4 tick 才把怪物仇恨拉回自己身上，
 * 两次轮询之间的空窗里，怪物自带的 {@code TargetGoal} / {@code NearestAttackableTargetGoal}
 * 早已把 {@code target} 改成玩家或 {@code null} ⇒ 表现出来就是「路障还站着，怪物却跑去打玩家」。
 *
 * <p><b>为什么不在本类里做「每 tick 轮询」</b>：轮询只是**事后**再补一次，空窗永远存在
 * （周期调短只会更费性能，且仍有一瞬间打向玩家）；而所有目标变更**必然**经过
 * {@code Mob#setTarget} ⇒ 平台事件 {@link LivingChangeTargetEvent}。在这里把「改走」的这一次
 * 新目标直接改回路障，空窗降为 0 tick，而且没有常驻轮询开销。
 *
 * <p><b>职责划分（两半，互不重叠）</b>：<b>初次拉仇恨</b>仍由 {@code BarricadeEntity#pullAggro()}
 * （每 4 tick 的半径扫描）负责；本监听**只**处理「{@code target} 已经是路障、但有人要把它改走」
 * 这一半 —— 路障还没被谁盯上时，本监听不会凭空创造仇恨。
 *
 * <p><b>⚠️ 事件语义（已核 {@code neoforge-21.1.235-sources.jar} 的 Mob.java:245-250 与
 * LivingChangeTargetEvent.java）</b>：{@code Mob#setTarget} 是「先发事件、再赋值」，事件携带的是
 * **即将被设置的新目标**（{@code getOriginalAboutToBeSetTarget()} = MC 本次想设的值，**不是**旧值），
 * 因此「旧目标」只能从 {@code mob.getTarget()} 读（此刻尚未生效）。本类据此写两条判据：
 * 新目标本来就是路障 ⇒ 不用管（那是 pullAggro 自己的那条路）；真正要吃的是
 * <b>{@code mob.getTarget() == 路障 且 新目标 != 路障}</b>。
 *
 * <p><b>不取消事件</b>（只用 {@code setNewAboutToBeSetTarget} 改目标）：取消还会连带跳过
 * {@code LivingSetAttackTargetEvent}，属于超出需求的副作用。
 */
@EventBusSubscriber(modid = AstralDiceMod.MODID)
public final class BarricadeAggroHandler {

    /** 「强制留在路障上」的最大距离（格）的平方 = 6 格，与 {@link BarricadeEntity#AGGRO_RADIUS} 同值。 */
    private static final double HOLD_RANGE_SQR = 36.0D;

    private BarricadeAggroHandler() {
    }

    @SubscribeEvent
    public static void onLivingChangeTarget(LivingChangeTargetEvent event) {
        // 为什么不用「每 tick 轮询」：轮询只能在事后补一次，两次周期之间仍会有一瞬间打向玩家；
        // 而所有目标变更都走 Mob#setTarget ⇒ 本事件，在这里当场改回，空窗 = 0 tick、无轮询开销。
        // 本监听**只**吃「改走」这一半；初次拉仇恨仍由 BarricadeEntity#pullAggro 每 4 tick 负责。
        LivingEntity changer = event.getEntity();
        if (!(changer instanceof Mob mob)) return;
        if (!(mob instanceof Enemy)) return;
        if (EliteTargets.isEliteOrBoss(mob)) return;

        // 旧目标（事件尚未生效）必须是仍然存活、仍在本实体上的路障
        LivingEntity current = mob.getTarget();
        if (!(current instanceof BarricadeEntity barricade)) return;
        if (!barricade.isAlive() || barricade.isRemoved()) return;
        if (mob.distanceToSqr(barricade) > HOLD_RANGE_SQR) return;

        // 新目标本来就是这座路障：pullAggro 自己那条路，无需干预
        if (event.getNewAboutToBeSetTarget() instanceof BarricadeEntity) return;

        // 想把目标从路障改走 ⇒ 强制改回路障
        event.setNewAboutToBeSetTarget(barricade);
    }
}
