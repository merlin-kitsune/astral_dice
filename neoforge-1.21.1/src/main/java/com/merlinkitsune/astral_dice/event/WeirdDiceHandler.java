package com.merlinkitsune.astral_dice.event;

import com.merlinkitsune.astral_dice.component.GameplayConstants;
import com.merlinkitsune.astral_dice.item.ChargeManager;
import com.merlinkitsune.astral_dice.item.ModItems;
import net.minecraft.world.entity.player.Player;
import top.theillusivec4.curios.api.CuriosApi;

import java.util.concurrent.ThreadLocalRandom;

/**
 * 诡异骰子:与钻石骰子同阶。
 *
 * 功能:
 * - 立牌主动技能冷却时间 -50%(所有「开始冷却」的入口统一经 {@link #signCooldownTicks} 取值);
 * - 战斗骰(d1-6)低点数(1-3)出现概率提升 50%(权重法:1/2/3 权重 1.5,4/5/6 权重 1,
 *   即 P(低点数) 由 50% 升至 60%,相对提升 50%)。
 */
public final class WeirdDiceHandler {

    /** 低点数权重提升(1-3 点权重 ×1.5) */
    public static final float LOW_FACE_WEIGHT = 1.5F;
    /** 骰面数 */
    public static final int D6_FACES = 6;

    private WeirdDiceHandler() {
    }

    public static boolean hasWeirdDice(Player player) {
        var curios = CuriosApi.getCuriosInventory(player);
        return curios.isPresent()
                && curios.get().findFirstCurio(s -> s.is(ModItems.WEIRD_DICE.get())).isPresent();
    }

    /** 立牌主动技能冷却 tick:佩戴诡异骰子时减半;拥有充能时再 -20% */
    public static int signCooldownTicks(Player player) {
        int ticks = GameplayConstants.SIGN_ACTIVE_COOLDOWN_TICKS;
        if (hasWeirdDice(player)) {
            ticks = Math.max(1, ticks / 2);
        }
        return (int) ChargeManager.cooldownTicks(player, ticks);
    }

    /**
     * 掷一枚战斗骰(d1-6):佩戴诡异骰子时低点数(1-3)权重 ×1.5,
     * 未佩戴(或无玩家上下文)时为均匀分布。
     */
    public static int rollD6(Player roller) {
        if (roller == null || !hasWeirdDice(roller)) {
            return ThreadLocalRandom.current().nextInt(1, D6_FACES + 1);
        }
        // 总权重 = 3×1.5 + 3×1 = 7.5
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
