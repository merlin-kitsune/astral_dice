package com.merlinkitsune.astral_dice.event;

import com.merlinkitsune.starengine.component.GameplayConstants;
import com.merlinkitsune.astral_dice.item.ChargeManager;
import com.merlinkitsune.astral_dice.item.HealingManager;
import com.merlinkitsune.starengine.item.CuriosCompat;
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
 * - 立牌主动技能**结束**时获得 5 点治愈({@link #onSignActiveEnded},2026-10-20 新增):
 *   口径与挂点见该方法 javadoc。
 */
public final class WeirdDiceHandler {

    /** 低点数权重提升(1-3 点权重 ×3.0,P(低点数) 50% → 75%) */
    public static final float LOW_FACE_WEIGHT = 3.0F;
    /** 骰面数 */
    public static final int D6_FACES = 6;
    /** 主动技能结束后获得的治愈点数(用户需求 2026-10-20:5 点)。 */
    public static final int HEAL_ON_ACTIVE_END = 5;

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
     * 掷一枚战斗骰(d1-6):佩戴诡异骰子时低点数(1-3)权重 ×{@link #LOW_FACE_WEIGHT}(= 3.0),
     * 未佩戴(或无玩家上下文)时为均匀分布。
     *
     * <p>低点数概率由 50%(3/6)升到 75%(9/12),**相对提升 50%**,与 tooltip 文案一致。
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

    /**
     * 立牌主动技能**结束**时的结算点(2026-10-20 用户需求「主动技能结束后,获得 5 点治疗」)。
     *
     * <p><b>「结束」的三个真实出口(互斥,一次主动只结算一次)</b>:
     * <ol>
     *   <li><b>有生效期的立牌</b> ⇒ {@code SignActiveLockMachine#endLockAndStartCooldown}
     *       (锁定态结束那一刻,即技能效果真正寿终);</li>
     *   <li><b>瞬时型立牌</b> ⇒ {@code BaseSignItem#performSkill} 第 6 步写完冷却之后
     *       (没有生效期 ⇒ 结束与施放完成同一刻);</li>
     *   <li><b>目标选择器类立牌</b> ⇒ {@code BaseSignItem#resumeGatedActiveSkill}(确认目标、
     *       {@code TargetSelectionAction#apply} 已执行完毕之后);若该次施放同时进入了锁定态,
     *       则改由第 1 条在锁定结束时结算,本出口跳过。</li>
     * </ol>
     *
     * <p>治愈走既有资源路径 {@link HealingManager#add}(单一治愈点数池,受
     * {@code HEALING_POINT_CAP} 上限)—— **不直接改生命值**。
     */
    public static void onSignActiveEnded(Player player) {
        if (player == null || player.level().isClientSide) return;
        if (!hasWeirdDice(player)) return;
        HealingManager.add(player, HEAL_ON_ACTIVE_END);
    }
}
