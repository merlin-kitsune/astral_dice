package com.merlinkitsune.astral_dice.network;

import com.merlinkitsune.astral_dice.AstralDiceMod;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * 自定义 actionbar 消息:服务端发送文本与持续时间,客户端按指定时长显示并淡出。
 */
public record ActionBarPayload(Component message, int durationTicks) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<ActionBarPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(AstralDiceMod.MODID, "action_bar"));

    public static final StreamCodec<ByteBuf, ActionBarPayload> STREAM_CODEC = StreamCodec.composite(
            ComponentSerialization.TRUSTED_CONTEXT_FREE_STREAM_CODEC, ActionBarPayload::message,
            ByteBufCodecs.VAR_INT, ActionBarPayload::durationTicks,
            ActionBarPayload::new
    );

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
