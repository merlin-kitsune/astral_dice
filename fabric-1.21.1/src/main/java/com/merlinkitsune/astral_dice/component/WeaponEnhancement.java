package com.merlinkitsune.astral_dice.component;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.List;

/**
 * 骰子卡牌栏增强数据(星级 + 攻/防费用 + 已装配卡牌)。
 * 1.20.1:仅保留 Codec(NBT 持久化经 {@link ItemDataKey});1.21 的 STREAM_CODEC 网络同步
 * 由 ItemStack NBT 自动同步承担,故移除。
 */
public record WeaponEnhancement(int usedCost, int maxCost, int usedDefenseCost, int maxDefenseCost, int starLevel, List<AppliedStone> appliedStones) {
    public static final WeaponEnhancement EMPTY = new WeaponEnhancement(0, 3, 0, 3, 0, List.of());

    public static final Codec<WeaponEnhancement> CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                    Codec.INT.fieldOf("used_cost").forGetter(WeaponEnhancement::usedCost),
                    Codec.INT.fieldOf("max_cost").forGetter(WeaponEnhancement::maxCost),
                    Codec.INT.optionalFieldOf("used_defense_cost", 0).forGetter(WeaponEnhancement::usedDefenseCost),
                    Codec.INT.optionalFieldOf("max_defense_cost", 3).forGetter(WeaponEnhancement::maxDefenseCost),
                    Codec.INT.fieldOf("star_level").forGetter(WeaponEnhancement::starLevel),
                    AppliedStone.CODEC.listOf().fieldOf("applied_stones").forGetter(WeaponEnhancement::appliedStones)
            ).apply(instance, WeaponEnhancement::new));

    public int freeCost() {
        return maxCost - usedCost;
    }
}
