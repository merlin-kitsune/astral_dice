package com.merlinkitsune.astral_dice.effect;

import com.merlinkitsune.astral_dice.item.MarkManager;
import com.merlinkitsune.starenginelib.event.ModEffectRemoval;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/**
 * 隐匿(秘密侦探立牌「调查阶段」;2026-09-28 用户裁决:取代原版「隐身」,图标沿用隐身图标)。
 *
 * <h2>三条行为</h2>
 * <ol>
 *   <li><b>阻止索敌</b>:持有本效果的玩家不会被生物设为索敌目标 —— 挂点 =
 *       {@code combat/DiceCombatEvents#onLivingChangeTarget}(取消事件);</li>
 *   <li><b>攻击即解除</b>:玩家对**非玩家实体**造成有效伤害(近战 / 远程 / 法术……只要是有效攻击)
 *       ⇒ 立即解除本效果(见 {@link #breakOnAttack});</li>
 *   <li><b>解除后追加伤害</b>:本效果已解除、而调查阶段增益({@code ModEffects#INVESTIGATION_BONUS})
 *       仍在 ⇒ 骰战中**追加**「目标身上「标记」层数」的伤害,直到调查阶段增益结束
 *       (由 {@code combat/DiceCombatModifiers} 的额外加伤修饰器调用 {@link #revealedMarkDamage},
 *       走独立伤害类型 {@code astral_dice:extra_damage},不进骰战攻击力)。</li>
 * </ol>
 *
 * <p><b>为什么「已解除」用「没有本效果」来判</b>:隐匿与「解除后的加伤」在时间上互斥
 * (加伤恰恰从隐匿消失那一刻开始),故判据直接读效果有无,不引入第三个附件键 —— 少一个状态就少一条
 * 可能不同步的路径。
 *
 * <p>时长由调用方决定(调查阶段四个阶段统一 1:00,见 {@code item/InvestigationEventUtil})。
 * 图标实装路径 = {@code textures/mob_effect/concealment.png}(与原版隐身图标同图)。
 */
public class ConcealmentEffect extends MobEffect {
    public ConcealmentEffect() {
        super(MobEffectCategory.BENEFICIAL, 0x8A7FB5);
    }

    /** 施加/刷新隐匿({@code durationTicks} 由调用方给;调查阶段统一 1:00)。 */
    public static void apply(Player player, int durationTicks) {
        if (player == null || player.level().isClientSide()) return;
        // 自定义效果(astral_dice:*)的计时由 MobEffectEvent.Added 统一记录(见 event/EffectTimerGuard
        // 的类注释),故此处**不走** EffectTimerGuard.apply(那是给"本模组施加的原版效果"用的包装)。
        player.addEffect(new MobEffectInstance(ModEffects.CONCEALMENT, durationTicks, 0, false, true));
    }

    /** 当前是否处于隐匿(「阻止索敌」的判据) */
    public static boolean has(Player player) {
        return player != null && player.hasEffect(ModEffects.CONCEALMENT);
    }

    /**
     * 玩家攻击怪物 ⇒ 解除隐匿(幂等;由 {@code combat/DiceCombatEvents#onLivingDamagePre} 调用)。
     * 未处于隐匿时什么都不做。
     */
    public static void breakOnAttack(Player player) {
        if (player == null || player.level().isClientSide()) return;
        if (!player.hasEffect(ModEffects.CONCEALMENT)) return;
        ModEffectRemoval.remove(player, ModEffects.CONCEALMENT);
    }

    /**
     * 调查阶段「已解除隐匿」后的骰战追加伤害 = 目标身上「标记」层数。
     * 仍在隐匿(尚未解除)、调查阶段增益已结束、或目标就是自己 ⇒ 0。
     */
    public static int revealedMarkDamage(Player attacker, LivingEntity victim) {
        if (attacker == null || victim == null) return 0;
        if (attacker.level().isClientSide()) return 0;
        // ① 调查阶段增益已结束 ⇒ 加伤一并结束(用户口径:持续到调查阶段增益结束)
        if (!attacker.hasEffect(ModEffects.INVESTIGATION_BONUS)) return 0;
        // ② 仍处于隐匿 ⇒ 尚未被攻击解除,不追加
        if (attacker.hasEffect(ModEffects.CONCEALMENT)) return 0;
        if (victim == attacker) return 0;
        return Math.max(0, MarkManager.getLevel(victim));
    }
}
