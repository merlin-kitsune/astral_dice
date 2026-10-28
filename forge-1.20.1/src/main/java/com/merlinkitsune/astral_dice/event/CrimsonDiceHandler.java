package com.merlinkitsune.astral_dice.event;

import com.merlinkitsune.astral_dice.damage.ModDamageTypes;
import com.merlinkitsune.starengine.item.CuriosCompat;
import com.merlinkitsune.astral_dice.item.ModItems;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

import java.util.concurrent.ThreadLocalRandom;

/**
 * 绯红骰子:与下界合金骰子同阶。
 *
 * 功能:
 * - 战斗骰(d1-6)高点数(4-6)出现概率提升 50%(权重法:4/5/6 权重 3.0,1/2/3 权重 1,
 *   即 P(高点数) 由 50% 升至 75%,相对提升 50%,与 tooltip 文案一致);
 * - 佩戴者掷出的战斗骰若为 1,立即受到 6 点伤害(骰子伤害类型,
 *   见 {@link ModDamageTypes#diceDamage})。
 */
public final class CrimsonDiceHandler {

    /** 高点数权重提升(4-6 点权重 ×3.0,P(高点数) 50% → 75%) */
    public static final float HIGH_FACE_WEIGHT = 3.0F;
    /** 骰出 1 时立即受到的伤害 */
    public static final float SELF_DAMAGE_ON_ONE = 6.0F;
    /** 骰面数 */
    public static final int D6_FACES = 6;

    private CrimsonDiceHandler() {
    }

    public static boolean hasCrimsonDice(Player player) {
        return CuriosCompat.getCuriosInventory(player)
                .map(h -> h.findFirstCurio(s -> s.is(ModItems.CRIMSON_DICE.get())).isPresent())
                .orElse(false);
    }

    /**
     * 掷一枚战斗骰(d1-6):佩戴绯红骰子时高点数(4-6)权重 ×1.5,
     * 未佩戴(或无玩家上下文)时为均匀分布。
     * 若掷出 1(仅限佩戴者,服务端且存活),立即受到 {@link #SELF_DAMAGE_ON_ONE} 点伤害。
     */
    public static int rollD6(Player roller) {
        if (roller == null) {
            return ThreadLocalRandom.current().nextInt(1, D6_FACES + 1);
        }
        int result = hasCrimsonDice(roller)
                ? rollWeightedHighFaceD6()
                : ThreadLocalRandom.current().nextInt(1, D6_FACES + 1);
        applySelfDamageIfOne(roller, result);
        return result;
    }

    /**
     * 纯权重掷骰（不含佩戴判据）：4-6 面权重 ×{@link #HIGH_FACE_WEIGHT}，1-3 面权重 1。
     *
     * <p>2026-10-28：玩家路径与非玩家骰战能力（{@code combat.DiceCombatAbilities}）共用本方法。
     */
    public static int rollWeightedHighFaceD6() {
        // 总权重 = 3×1 + 3×3.0 = 12(P(高点数) = 9/12 = 75%)
        float total = 3 * 1.0F + 3 * HIGH_FACE_WEIGHT;
        float r = ThreadLocalRandom.current().nextFloat() * total;
        int result = D6_FACES;
        for (int face = 1; face <= D6_FACES; face++) {
            float w = face >= 4 ? HIGH_FACE_WEIGHT : 1.0F;
            if (r < w) {
                result = face;
                break;
            }
            r -= w;
        }
        return result;
    }

    /**
     * 骰出 1 时对持有者造成 {@link #SELF_DAMAGE_ON_ONE} 点骰子伤害（服务端且存活才结算）。
     *
     * <p>2026-10-28：玩家路径与非玩家骰战能力共用本方法。
     */
    public static void applySelfDamageIfOne(LivingEntity roller, int result) {
        if (result == 1 && roller != null && !roller.level().isClientSide() && roller.isAlive()) {
            roller.hurt(ModDamageTypes.diceDamage(roller.level(), roller), SELF_DAMAGE_ON_ONE);
        }
    }
}
