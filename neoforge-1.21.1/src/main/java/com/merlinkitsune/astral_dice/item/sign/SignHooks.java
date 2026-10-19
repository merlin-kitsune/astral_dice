package com.merlinkitsune.astral_dice.item.sign;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/**
 * 立牌战斗钩子契约（B1 注册式分发）。
 *
 * <p>每个钩子 = 一个接口；立牌类按需 {@code implements} 对应接口并实现
 * {@code handleXxx(...)}（实例方法内转发到各立牌既有的 {@code static onXxx(...)}，
 * 保持 KubeJS 探针等外部静态调用不变）。
 *
 * <p>分发入口在 {@link SignHookDispatcher}：它遍历玩家**当前装备的立牌**（Curios），
 * 用 {@code instanceof} 判定是否实现该接口 —— 不维护任何 {@code signId → 回调} 注册表。
 *
 * <p>⇒ 新增立牌只要实现对应接口即可接入，**不再需要修改 {@code combat/DiceCombatEvents}**。
 */
public final class SignHooks {
    private SignHooks() {
    }

    /** 骰神赐福触发（无返回值形态，如修女立牌「鼓起勇气」）。 */
    public interface BlessingTriggered {
        void handleBlessingTriggered(Player player);
    }

    /** 骰神赐福触发，并返回本次攻击是否需要引爆溅射（大当家立牌）。 */
    public interface SplashBlessingTrigger {
        boolean handleBlessingTriggered(Player player);
    }

    /** 骰神赐福触发（蛟龙立牌「觉醒 +1 层 / 锁存撕咬加成」）。 */
    public interface BiteBlessingTrigger {
        void handleDiceBlessingTriggered(Player player);
    }

    /** 骰神赐福结束（骇客 / 蛟龙）。 */
    public interface BlessingEnded {
        void handleDiceBlessingEnded(Player player);
    }

    /** 攻击已带破绽的目标（枪匠「弱点识破」）。 */
    public interface BrokenTargetAttack {
        void handleAttackBrokenTarget(Player player, LivingEntity target);
    }

    /** 骰点定稿（风水师「福祸相倚」，返回发放的卡牌类型）。 */
    public interface DiceJudgmentRoll {
        String handleDiceRollResult(Player player, int dice);
    }

    /** 骰点定稿（人偶师「幻想千金」）。 */
    public interface DiceRollResult {
        void handleDiceRollResult(Player player, int dice);
    }

    /** 闪避/反击类触发（枪匠获得 1 层「弱点识破」）。 */
    public interface DodgeCounter {
        void handleDodgeCounter(Player player, LivingEntity target);
    }

    /** 命中时施加龙之咆哮减益（蛟龙）。 */
    public interface DragonRoar {
        void handleDragonRoar(Player attacker, LivingEntity victim);
    }

    /** 扣除非门控立牌主动的实际代价（大当家「战斗爽·溅射」）。 */
    public interface SplashCost {
        void handleSplashCost(Player player);
    }
}
