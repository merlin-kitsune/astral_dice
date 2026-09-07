package com.merlinkitsune.astral_dice.event;

import com.merlinkitsune.astral_dice.damage.ModDamageTypes;
import com.merlinkitsune.astral_dice.item.ModItems;
import net.minecraft.world.entity.player.Player;
import top.theillusivec4.curios.api.CuriosApi;

import java.util.concurrent.ThreadLocalRandom;

/**
 * 绯红骰子:与下界合金骰子同阶。
 *
 * 功能:
 * - 战斗骰(d1-6)高点数(4-6)出现概率提升(权重法:4/5/6 权重 1.5,1/2/3 权重 1,
 *   即 P(高点数) 由 50% 升至 60%,每个高点数面概率 ×1.5);
 * - 佩戴者掷出的战斗骰若为 1,立即受到 6 点伤害(骰子伤害类型,
 *   见 {@link ModDamageTypes#diceDamage})。
 */
public final class CrimsonDiceHandler {

    /** 高点数权重提升(4-6 点权重 ×1.5) */
    public static final float HIGH_FACE_WEIGHT = 1.5F;
    /** 骰出 1 时立即受到的伤害 */
    public static final float SELF_DAMAGE_ON_ONE = 6.0F;
    /** 骰面数 */
    public static final int D6_FACES = 6;

    private CrimsonDiceHandler() {
    }

    public static boolean hasCrimsonDice(Player player) {
        var curios = CuriosApi.getCuriosInventory(player);
        return curios.isPresent()
                && curios.get().findFirstCurio(s -> s.is(ModItems.CRIMSON_DICE.get())).isPresent();
    }

    /**
     * 掷一枚战斗骰(d1-6):佩戴绯红骰子时高点数(4-6)权重 ×1.5,
     * 未佩戴(或无玩家上下文)时为均匀分布。
     * 若掷出 1(仅限佩戴者,服务端且存活),立即受到 {@link #SELF_DAMAGE_ON_ONE} 点伤害。
     */
    public static int rollD6(Player roller) {
        int result;
        if (roller == null) {
            return ThreadLocalRandom.current().nextInt(1, D6_FACES + 1);
        }
        if (hasCrimsonDice(roller)) {
            // 总权重 = 3×1 + 3×1.5 = 7.5
            float total = 3 * 1.0F + 3 * HIGH_FACE_WEIGHT;
            float r = ThreadLocalRandom.current().nextFloat() * total;
            result = D6_FACES;
            for (int face = 1; face <= D6_FACES; face++) {
                float w = face >= 4 ? HIGH_FACE_WEIGHT : 1.0F;
                if (r < w) {
                    result = face;
                    break;
                }
                r -= w;
            }
        } else {
            result = ThreadLocalRandom.current().nextInt(1, D6_FACES + 1);
        }
        if (result == 1 && !roller.level().isClientSide() && roller.isAlive()) {
            roller.hurt(ModDamageTypes.diceDamage(roller.level(), roller), SELF_DAMAGE_ON_ONE);
        }
        return result;
    }
}
