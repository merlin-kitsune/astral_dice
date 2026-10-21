package com.merlinkitsune.astral_dice.network;

import com.merlinkitsune.astral_dice.AstralDiceMod;
import com.merlinkitsune.astral_dice.client.BeamFxRenderer;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * 服务端 -> 客户端：一段「光线」FX（激光 / 轨道炮光柱）。
 * 坐标在服务端冻结后下发，客户端只做静态绘制；{@code ttlTicks} 到期即回收。
 */
public record BeamFxPayload(double fromX, double fromY, double fromZ,
                            double toX, double toY, double toZ,
                            int colorArgb, float widthBlocks, int ttlTicks)
        implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<BeamFxPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(AstralDiceMod.MODID, "beam_fx"));

    public static final StreamCodec<RegistryFriendlyByteBuf, BeamFxPayload> STREAM_CODEC =
            StreamCodec.of(BeamFxPayload::encode, BeamFxPayload::decode);

    private static void encode(RegistryFriendlyByteBuf buf, BeamFxPayload p) {
        buf.writeDouble(p.fromX);
        buf.writeDouble(p.fromY);
        buf.writeDouble(p.fromZ);
        buf.writeDouble(p.toX);
        buf.writeDouble(p.toY);
        buf.writeDouble(p.toZ);
        buf.writeInt(p.colorArgb);
        buf.writeFloat(p.widthBlocks);
        buf.writeVarInt(p.ttlTicks);
    }

    private static BeamFxPayload decode(RegistryFriendlyByteBuf buf) {
        return new BeamFxPayload(buf.readDouble(), buf.readDouble(), buf.readDouble(),
                buf.readDouble(), buf.readDouble(), buf.readDouble(),
                buf.readInt(), buf.readFloat(), buf.readVarInt());
    }

    /** 客户端处理：入列交给渲染器。 */
    public static void handle(BeamFxPayload p) {
        BeamFxRenderer.add(p.fromX, p.fromY, p.fromZ, p.toX, p.toY, p.toZ,
                p.colorArgb, p.widthBlocks, p.ttlTicks);
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
