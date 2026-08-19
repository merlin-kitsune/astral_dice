package com.merlinkitsune.astral_dice.component;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

import java.util.ArrayList;
import java.util.List;

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

    public static final StreamCodec<ByteBuf, WeaponEnhancement> STREAM_CODEC = StreamCodec.of(
            (buf, data) -> {
                ByteBufCodecs.INT.encode(buf, data.usedCost());
                ByteBufCodecs.INT.encode(buf, data.maxCost());
                ByteBufCodecs.INT.encode(buf, data.usedDefenseCost());
                ByteBufCodecs.INT.encode(buf, data.maxDefenseCost());
                ByteBufCodecs.INT.encode(buf, data.starLevel());
                List<AppliedStone> stones = data.appliedStones();
                ByteBufCodecs.VAR_INT.encode(buf, stones.size());
                for (AppliedStone stone : stones) {
                    AppliedStone.STREAM_CODEC.encode(buf, stone);
                }
            },
            buf -> {
                int usedCost = ByteBufCodecs.INT.decode(buf);
                int maxCost = ByteBufCodecs.INT.decode(buf);
                int usedDefenseCost = ByteBufCodecs.INT.decode(buf);
                int maxDefenseCost = ByteBufCodecs.INT.decode(buf);
                int starLevel = ByteBufCodecs.INT.decode(buf);
                int size = ByteBufCodecs.VAR_INT.decode(buf);
                List<AppliedStone> stones = new ArrayList<>(size);
                for (int i = 0; i < size; i++) {
                    stones.add(AppliedStone.STREAM_CODEC.decode(buf));
                }
                return new WeaponEnhancement(usedCost, maxCost, usedDefenseCost, maxDefenseCost, starLevel, stones);
            });

    public int freeCost() {
        return maxCost - usedCost;
    }
}
