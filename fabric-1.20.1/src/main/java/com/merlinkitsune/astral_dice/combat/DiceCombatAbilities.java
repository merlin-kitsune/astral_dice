package com.merlinkitsune.astral_dice.combat;

import com.merlinkitsune.astral_dice.event.CrimsonDiceHandler;
import com.merlinkitsune.astral_dice.event.WeirdDiceHandler;
import com.merlinkitsune.astral_dice.item.ModItems;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Predicate;

/**
 * 「非玩家持有者的骰子能力」注册表（2026-10-28）。
 *
 * <p>用途：mob 侧骰战里，非玩家持有者掷骰时按<b>所持骰子</b>执行其能力 —— 内置诡异骰
 * （低点数权重 ×3）、绯红骰（高点数权重 ×3，掷出 1 自伤 6 点），以及第三方模组注册的自有骰子能力。
 *
 * <p><b>作用域</b>：只在 {@code DiceCombatEvents#resolveMobMeleeAttack} 的非玩家掷骰处调用
 * （攻守两侧）；<b>玩家路径完全不经过本表</b>（玩家仍走 {@code rollCombatDie} 的既有偏置链），
 * 避免同一能力被应用两次。
 *
 * <p><b>注册纪律</b>：只允许第三方模组在各自 Setup 期以代码显式注册；本模组不提供配置、数据包、
 * 命令或自动发现入口。未注册任何第三方能力时，行为与注册表不存在时逐值一致。
 *
 * <p>能力在服务端伤害热路径上执行：实现方不得阻塞、不得发网络包；抛出的任何异常会被吞掉并退回
 * 默认掷点（一个坏消费方不能打崩伤害管线）。
 */
public final class DiceCombatAbilities {

    private static final Logger LOGGER = LoggerFactory.getLogger(DiceCombatAbilities.class);

    private DiceCombatAbilities() {
    }

    /** 一次非玩家掷骰的上下文。字段全部只读。 */
    public static final class RollContext {
        /** 持有骰子的实体（本次掷骰方）。 */
        public final LivingEntity holder;
        /** 本次实际掷出的骰子物品栈（非空）。 */
        public final ItemStack die;
        /** 骰战对立方。攻击方掷骰时是受击方，防守方掷骰时是攻击方。 */
        public final LivingEntity opponent;
        /** true = 攻击方掷骰，false = 防守方掷骰。 */
        public final boolean attackerSide;
        /** 骰面数（当前全部骰子为 6）。 */
        public final int sides;
        /** 注册表未改写时本应采用的均匀掷点。 */
        public final int defaultRoll;

        public RollContext(LivingEntity holder, ItemStack die, LivingEntity opponent,
                           boolean attackerSide, int sides, int defaultRoll) {
            this.holder = holder;
            this.die = die;
            this.opponent = opponent;
            this.attackerSide = attackerSide;
            this.sides = sides;
            this.defaultRoll = defaultRoll;
        }
    }

    /** 骰子能力：给定上下文返回本次骰点。 */
    @FunctionalInterface
    public interface DiceRollAbility {
        /**
         * @return 本次使用的骰点，必须落在 {@code 1..context.sides} 内；越界视为无效，退回默认掷点
         */
        int roll(RollContext context);
    }

    private static final class Entry {
        final Predicate<ItemStack> matcher;
        final DiceRollAbility ability;

        Entry(Predicate<ItemStack> matcher, DiceRollAbility ability) {
            this.matcher = matcher;
            this.ability = ability;
        }
    }

    /** 内置骰能力：先查本表，再查第三方注册表。 */
    private static final List<Entry> BUILTIN = List.of(
            new Entry(stack -> stack.is(ModItems.WEIRD_DICE.get()), context -> WeirdDiceHandler.rollWeightedD6()),
            new Entry(stack -> stack.is(ModItems.CRIMSON_DICE.get()), context -> {
                int roll = CrimsonDiceHandler.rollWeightedHighFaceD6();
                CrimsonDiceHandler.applySelfDamageIfOne(context.holder, roll);
                return roll;
            }));

    /** 第三方注册的骰子能力（线程安全；Setup 期注册）。 */
    private static final List<Entry> EXTRA = new CopyOnWriteArrayList<>();

    /**
     * 注册一颗（或一类）骰子在<b>非玩家持有者</b>手里的掷骰能力。消费方在各自 Setup 期调用。
     *
     * <p>匹配顺序：内置能力优先，其次按注册顺序取第一个命中的能力；一颗骰子最多执行一个能力。
     *
     * @param dieMatcher 骰子物品栈判定（只做物品判定，不要读世界/存档）；null 会被忽略
     * @param ability    能力实现；null 会被忽略
     */
    public static void register(Predicate<ItemStack> dieMatcher, DiceRollAbility ability) {
        if (dieMatcher == null || ability == null) return;
        for (Entry existing : EXTRA) {
            if (existing.matcher == dieMatcher && existing.ability == ability) return;
        }
        EXTRA.add(new Entry(dieMatcher, ability));
    }

    /** 仅测试台 / 热重载使用：清空**第三方**注册（内置骰能力始终有效）。 */
    public static void clearForTests() {
        EXTRA.clear();
    }

    /** 供 DiceCombatEvents 调用：未命中任何能力时原样返回 {@code context.defaultRoll}。 */
    static int applyRoll(RollContext context) {
        if (context == null) return 0;
        if (context.die == null || context.die.isEmpty()) return context.defaultRoll;
        for (Entry entry : BUILTIN) {
            Integer roll = tryApply(entry, context);
            if (roll != null) return roll;
        }
        for (Entry entry : EXTRA) {
            Integer roll = tryApply(entry, context);
            if (roll != null) return roll;
        }
        return context.defaultRoll;
    }

    /** @return 有效骰点；未命中 / 越界 / 抛异常一律返回 null（调用方继续查下一条或退回默认值）。 */
    private static Integer tryApply(Entry entry, RollContext context) {
        boolean matched;
        try {
            matched = entry.matcher.test(context.die);
        } catch (Throwable t) {
            LOGGER.debug("DiceCombatAbilities: die matcher threw, ignoring this entry", t);
            return null;
        }
        if (!matched) return null;
        try {
            int roll = entry.ability.roll(context);
            if (roll >= 1 && roll <= context.sides) return roll;
            LOGGER.debug("DiceCombatAbilities: ability returned {} outside 1..{}, falling back",
                    roll, context.sides);
        } catch (Throwable t) {
            LOGGER.debug("DiceCombatAbilities: dice ability threw, falling back to default roll", t);
        }
        return null;
    }
}
