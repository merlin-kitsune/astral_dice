package com.merlinkitsune.astral_dice.event;

import com.merlinkitsune.starenginelib.component.GameplayConstants;
import com.merlinkitsune.astral_dice.item.ChargeManager;
import com.merlinkitsune.starenginelib.item.CuriosCompat;
import com.merlinkitsune.astral_dice.item.ModItems;
import net.minecraft.world.entity.player.Player;

import java.util.concurrent.ThreadLocalRandom;

/**
 * 诡异骰子:与钻石骰子同阶。
 *
 * 功能:
 * - 立牌主动技能冷却时间 -50%(所有「开始冷却」的入口统一经 {@link #signCooldownTicks} 取值);
 * - 战斗骰(d1-6)低点数(1-3)出现概率提升 50%(权重法:1/2/3 权重 3.0,4/5/6 权重 1,
 *   即 P(低点数) 由 50% 升至 75%,相对提升 50%,与 tooltip 文案一致)。
 */
public final class WeirdDiceHandler {

    /** 低点数权重提升(1-3 点权重 ×3.0,P(低点数) 50% → 75%) */
    public static final float LOW_FACE_WEIGHT = 3.0F;
    /** 骰面数 */
    public static final int D6_FACES = 6;

    private WeirdDiceHandler() {
    }

    public static boolean hasWeirdDice(Player player) {
        return CuriosCompat.getCuriosInventory(player)
                .map(h -> h.findFirstCurio(s -> s.is(ModItems.WEIRD_DICE.get())).isPresent())
                .orElse(false);
    }

    /** 立牌主动技能冷却 tick:拥有充能时先把基础值封顶(180→160 秒),再按诡异骰子减半 */
    public static int signCooldownTicks(Player player) {
        int ticks = (int) ChargeManager.signCooldownTicks(player, GameplayConstants.SIGN_ACTIVE_COOLDOWN_TICKS);
        if (hasWeirdDice(player)) {
            ticks = Math.max(1, ticks / 2);
        }
        return ticks;
    }

    /**
     * 掷一枚战斗骰(d1-6):佩戴诡异骰子时低点数(1-3)权重 ×1.5,
     * 未佩戴(或无玩家上下文)时为均匀分布。
     */
    public static int rollD6(Player roller) {
        if (roller == null || !hasWeirdDice(roller)) {
            return ThreadLocalRandom.current().nextInt(1, D6_FACES + 1);
        }
        // 总权重 = 3×3.0 + 3×1 = 12(P(低点数) = 9/12 = 75%)
        float total = 3 * LOW_FACE_WEIGHT + 3 * 1.0F;
        float r = ThreadLocalRandom.current().nextFloat() * total;
        for (int face = 1; face <= D6_FACES; face++) {
            float w = face <= 3 ? LOW_FACE_WEIGHT : 1.0F;
            if (r < w) return face;
            r -= w;
        }
        return D6_FACES;
    }
}
