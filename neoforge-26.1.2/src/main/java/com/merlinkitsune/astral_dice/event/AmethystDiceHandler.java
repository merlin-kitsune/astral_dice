package com.merlinkitsune.astral_dice.event;

import net.minecraft.world.entity.player.Player;

import java.util.concurrent.ThreadLocalRandom;

/**
 * 紫晶骰子:与钻石骰子同阶。
 *
 * 功能:
 * - 远程/魔法攻击命中时同样触发战斗骰(d1-6)并追加骰点伤害
 *   (经 {@link com.merlinkitsune.astral_dice.combat.SpellDamageRegistry} 注册为法伤修饰器,
 *   不触发骰神赐福、不消耗卡牌耐久);
 * - 紫晶骰子本身没有点数偏向,掷骰为均匀分布(1-6)。
 */
public final class AmethystDiceHandler {

    /** 骰面数 */
    public static final int D6_FACES = 6;

    private AmethystDiceHandler() {
    }

    /** 紫晶骰子的远程/魔法追加骰:均匀分布 1-6 */
    public static int rollD6(Player roller) {
        return ThreadLocalRandom.current().nextInt(1, D6_FACES + 1);
    }
}
