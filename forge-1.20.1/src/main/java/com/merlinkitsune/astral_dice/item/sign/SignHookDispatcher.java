package com.merlinkitsune.astral_dice.item.sign;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import com.merlinkitsune.starengine.item.CuriosCompat;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;

/**
 * 立牌战斗钩子的**唯一分发入口**（B1 注册式分发）。
 *
 * <p>分发口径与 {@link BaseSignItem#invokeKillHooks} / {@code invokeHurtHooks} 一致：
 * 遍历玩家当前**装备中的立牌**（Curios {@code findCurios}），按 {@code instanceof}
 * 调用实现了对应 {@link SignHooks} 接口的实例；不维护 {@code signId → 回调} 注册表。
 *
 * <p>调用顺序/触发条件/参数与改造前的直调逐字对齐：客户端早退、{@code null} 检查、
 * 各立牌内部的 {@code isEquipped} 闸门均保留在原有位置（分发器只多做一次"是否装备"判定，
 * 与原直调前的 {@code isEquipped} 前置门等价）。
 */
public final class SignHookDispatcher {
    private SignHookDispatcher() {
    }

    /**
     * 显式分发优先级表(2026-10-19 FX2 回归修复):改造前的直调是**固定顺序**,而 Curios 槽位序
     * 不保证与之一致 —— 同一钩子被多个立牌实现时(如骰神赐福结束:{@link NancyLuSignItem} 与
     * {@link MamushiSignItem} 都实现 {@link SignHooks.BlessingEnded}),只按槽位序分发会与旧实现相反。
     *
     * <p>表序 = 改造前 {@code combat/DiceCombatEvents} 的直调次序:赐福触发时**先 Fen**(大当家
     * 「战斗爽·溅射」排定溅射)**再 Mamushi**(蛟龙「撕咬」);赐福结束时**先 NancyLu 再 Mamushi**。
     * 这三者按表序前置后,其余立牌仍按 Curios 槽位序(稳定排序 ⇒ 表外/同级元素保持原序)。
     */
    private static final List<String> SIGN_PRIORITY = List.of(
            "astral_dice:fen_sign",
            "astral_dice:nancy_lu_sign",
            "astral_dice:mamushi_sign");

    /** 立牌在 {@link #SIGN_PRIORITY} 中的位次;表外立牌统一排在表尾(彼此仍按槽位序) */
    private static int priorityOf(BaseSignItem sign) {
        int index = SIGN_PRIORITY.indexOf(BuiltInRegistries.ITEM.getKey(sign).toString());
        return index < 0 ? SIGN_PRIORITY.size() : index;
    }

    /** 遍历玩家当前装备的全部立牌（客户端 / null 早退，与既有 invoke*Hooks 同口径）。 */
    private static void forEachSign(Player player, Consumer<BaseSignItem> action) {
        if (player == null || player.level().isClientSide()) return;
        CuriosCompat.getCuriosInventory(player).ifPresent(handler -> {
            var results = handler.findCurios(s -> s.getItem() instanceof BaseSignItem);
            List<BaseSignItem> signs = new ArrayList<>(results.size());
            for (var result : results) {
                if (result.stack().getItem() instanceof BaseSignItem sign) {
                    signs.add(sign);
                }
            }
            // 稳定排序:优先表内的立牌按表序前置,其余保持 Curios 槽位序
            signs.sort(Comparator.comparingInt(SignHookDispatcher::priorityOf));
            for (BaseSignItem sign : signs) {
                action.accept(sign);
            }
        });
    }

    /** 玩家造成击杀时,调用其全部已装备立牌的 {@code onKill} 钩子(实现自 {@code BaseSignItem#invokeKillHooks})。 */
    public static void fireKillHooks(Player killer, LivingEntity killed) {
        forEachSign(killer, sign -> sign.onKill(killer, killed));
    }

    /**
     * 玩家被**敌对目标攻击**时,调用其全部已装备立牌的 {@code onHurt} 钩子
     * (实现自 {@code BaseSignItem#invokeHurtHooks})。
     *
     * <p>2026-09-29 用户裁决:受击类被动一律只在该条件下触发 —— 此前是「任何来源的伤害」,
     * 于是摔落 / 仙人掌 / 着火等环境伤害、以及本模组「符卡-祸」的无来源真伤都会算数。
     * 闸门保持在**分发处**(先于遍历),与原直调口径一致。
     */
    public static void fireHurtHooks(Player player, DamageSource source, float amount) {
        if (player == null || player.level().isClientSide()) return;
        if (!com.merlinkitsune.astral_dice.combat.DiceCombatEvents.isHostileAttack(player, source)) return;
        forEachSign(player, sign -> sign.onHurt(player, source, amount));
    }

    /**
     * 骰神赐福触发：先派发无返回值钩子，再派发"是否需要溅射"钩子。
     *
     * @return 是否有立牌请求本次攻击引爆溅射（原 {@code fenSplashArmed} 语义）
     */
    public static boolean fireBlessingTriggered(Player player) {
        final boolean[] splashArmed = {false};
        forEachSign(player, sign -> {
            if (sign instanceof SignHooks.BlessingTriggered hook) {
                hook.handleBlessingTriggered(player);
            }
            if (sign instanceof SignHooks.SplashBlessingTrigger hook) {
                splashArmed[0] |= hook.handleBlessingTriggered(player);
            }
        });
        return splashArmed[0];
    }

    /** 骰神赐福触发（蛟龙：觉醒加层 / 锁存撕咬加成）。 */
    public static void fireDiceBlessingTriggered(Player player) {
        forEachSign(player, sign -> {
            if (sign instanceof SignHooks.BiteBlessingTrigger hook) {
                hook.handleDiceBlessingTriggered(player);
            }
        });
    }

    /** 骰神赐福结束。 */
    public static void fireDiceBlessingEnded(Player player) {
        forEachSign(player, sign -> {
            if (sign instanceof SignHooks.BlessingEnded hook) {
                hook.handleDiceBlessingEnded(player);
            }
        });
    }

    /** 攻击已带破绽的目标。 */
    public static void fireAttackBrokenTarget(Player player, LivingEntity target) {
        forEachSign(player, sign -> {
            if (sign instanceof SignHooks.BrokenTargetAttack hook) {
                hook.handleAttackBrokenTarget(player, target);
            }
        });
    }

    /** 骰点定稿（风水师：1 ⇒ 符卡-祸 / 6 ⇒ 符卡-福）。 */
    public static void fireDiceJudgmentRoll(Player player, int dice) {
        forEachSign(player, sign -> {
            if (sign instanceof SignHooks.DiceJudgmentRoll hook) {
                hook.handleDiceRollResult(player, dice);
            }
        });
    }

    /** 骰点定稿（人偶师：骰点 6 ⇒ 1 星币）。 */
    public static void fireDiceRollResult(Player player, int dice) {
        forEachSign(player, sign -> {
            if (sign instanceof SignHooks.DiceRollResult hook) {
                hook.handleDiceRollResult(player, dice);
            }
        });
    }

    /** 闪避/反击触发（枪匠获得「弱点识破」层数）。 */
    public static void fireDodgeCounter(Player player, LivingEntity target) {
        forEachSign(player, sign -> {
            if (sign instanceof SignHooks.DodgeCounter hook) {
                hook.handleDodgeCounter(player, target);
            }
        });
    }

    /** 命中时施加龙之咆哮减益（蛟龙）。 */
    public static void fireDragonRoar(Player attacker, LivingEntity victim) {
        forEachSign(attacker, sign -> {
            if (sign instanceof SignHooks.DragonRoar hook) {
                hook.handleDragonRoar(attacker, victim);
            }
        });
    }

    /** 扣除非门控立牌主动的实际代价（大当家「战斗爽·溅射」）。 */
    public static void fireSplashCost(Player player) {
        forEachSign(player, sign -> {
            if (sign instanceof SignHooks.SplashCost hook) {
                hook.handleSplashCost(player);
            }
        });
    }
}
