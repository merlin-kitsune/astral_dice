package com.merlinkitsune.astral_dice.event;

import com.merlinkitsune.astral_dice.combat.EliteTargets;
import com.merlinkitsune.astral_dice.entity.BarricadeEntity;
import com.merlinkitsune.astral_dice.platform.event.SubscribeEvent;
import com.merlinkitsune.astral_dice.platform.event.entity.living.LivingChangeTargetEvent;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;

/**
 * 路障仇恨稳定性（2026-10-18 独立审查修复 · F3；forge-1.20.1 → fabric-1.20.1 1:1 移植）。
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
 * <p><b>⚠️ 事件语义</b>：{@code Mob#setTarget} 是「先发事件、再赋值」，事件携带的是
 * **即将被设置的新目标**（{@code getOriginalTarget()} = MC 本次想设的值，**不是**旧值），
 * 因此「旧目标」只能从 {@code mob.getTarget()} 读（此刻尚未生效）。本类据此写两条判据：
 * 新目标本来就是路障 ⇒ 不用管（那是 pullAggro 自己的那条路）；真正要吃的是
 * <b>{@code mob.getTarget() == 路障 且 新目标 != 路障}</b>。
 *
 * <p><b>不取消事件</b>（只用 {@code setNewTarget} 改目标）：取消会连带跳过后续目标设置，
 * 属于超出需求的副作用。
 *
 * <p><b>fabric-1.20.1 平台差异</b>（相对 forge-1.20.1 基线）：
 * <ul>
 *   <li>无 {@code @Mod.EventBusSubscriber}：改用本线垫片 {@link SubscribeEvent}；
 *       事件类来自 {@code platform.event.entity.living}（由 {@code platform/PuzzlesBridges} 的
 *       {@code LivingChangeTargetCallback(Mob#setTarget)} 真实派发）；</li>
 *   <li>⚠️ <b>必须在 {@code AstralDiceMod#registerListeners()} 里显式登记本类</b>
 *       （{@code LoaderBus.INSTANCE.register(BarricadeAggroHandler.class)}），
 *       否则本监听静默失效（Fabric 无注解自动订阅机制）；</li>
 *   <li>精英/Boss 判定用本线的 {@code combat/EliteTargets}（forge 基线是 starengine 库的同名类）。</li>
 * </ul>
 */
public final class BarricadeAggroHandler {

    /** 「强制留在路障上」的最大距离（格）的平方 = 8 格，与 {@link BarricadeEntity#AGGRO_RADIUS}（8 格）同值。 */
    private static final double HOLD_RANGE_SQR = 64.0D;

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
        if (event.getNewTarget() instanceof BarricadeEntity) return;

        // 想把目标从路障改走 ⇒ 强制改回路障
        event.setNewTarget(barricade);
    }
}
