package com.merlinkitsune.astral_dice.component;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import com.merlinkitsune.astral_dice.combat.CardRegistry;

/**
 * 已插入骰子卡牌栏的卡牌(类型 + 剩余耐久)。
 * 类型定义(费用/耐久/攻防归属/掷骰)统一由 {@link com.merlinkitsune.astral_dice.combat.CardRegistry} 管理,
 * 本 record 仅作为数据载体。
 */
public record AppliedStone(String type, int uses) {
    public static final Codec<AppliedStone> CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                    Codec.STRING.fieldOf("type").forGetter(AppliedStone::type),
                    Codec.INT.fieldOf("uses").forGetter(AppliedStone::uses)
            ).apply(instance, AppliedStone::new));

    public static final StreamCodec<ByteBuf, AppliedStone> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, AppliedStone::type,
            ByteBufCodecs.INT, AppliedStone::uses,
            AppliedStone::new
    );

    public static AppliedStone of(String type) {
        return new AppliedStone(type, defaultUses(type));
    }

    // 默认耐久:委托 CardRegistry(未注册类型回退 10)
    public static int defaultUses(String type) {
        return com.merlinkitsune.astral_dice.combat.CardRegistry.defaultUses(type);
    }

    // 卡牌费用:委托 CardRegistry(未注册类型回退 1)
    public static int cost(String type) {
        return com.merlinkitsune.astral_dice.combat.CardRegistry.cost(type, null);
    }
}
