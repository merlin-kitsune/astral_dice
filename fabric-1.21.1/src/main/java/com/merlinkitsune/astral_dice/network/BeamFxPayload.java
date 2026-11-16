package com.merlinkitsune.astral_dice.network;

import com.merlinkitsune.astral_dice.platform.api.distmarker.Dist;
import com.merlinkitsune.astral_dice.platform.fml.loading.FMLEnvironment;
import com.merlinkitsune.astral_dice.platform.network.NetworkEvent;
import net.minecraft.network.FriendlyByteBuf;

import java.util.function.Supplier;

/**
 * 服务端 -> 客户端：一段「光线」FX（激光 / 轨道炮光柱）。
 * 坐标在服务端冻结后下发，客户端只做静态绘制；{@code ttlTicks} 到期即回收。
 *
 * <p>与 1.21.1 的 {@code network.BeamFxPayload}（record + StreamCodec）**逐字节等价**：
 * 6 × double + int + float + varInt。本线无 payload 注册表，故与 forge-1.20.1 同形走
 * {@code SimpleChannel}（消息 id 追加在 {@link ModNetwork#register()} 末尾；本次落线为协议 id 15，
 * 对应 forge 的 id 26）。
 *
 * <p><b>平台差异（fabric-1.20.1）</b>：{@code Dist}/{@code FMLEnvironment} 走本线平台垫片
 * （{@code platform/api/distmarker/Dist}、{@code platform/fml/loading/FMLEnvironment}），
 * 语义与 forge 的 {@code FMLEnvironment.dist == Dist.CLIENT} 一致：客户端类引用只在客户端分支里解析，
 * 专用服务端不会加载 {@code client/BeamFxRenderer}。
 */
public final class BeamFxPayload {
    private final double fromX;
    private final double fromY;
    private final double fromZ;
    private final double toX;
    private final double toY;
    private final double toZ;
    private final int colorArgb;
    private final float widthBlocks;
    private final int ttlTicks;

    public BeamFxPayload(double fromX, double fromY, double fromZ,
                         double toX, double toY, double toZ,
                         int colorArgb, float widthBlocks, int ttlTicks) {
        this.fromX = fromX;
        this.fromY = fromY;
        this.fromZ = fromZ;
        this.toX = toX;
        this.toY = toY;
        this.toZ = toZ;
        this.colorArgb = colorArgb;
        this.widthBlocks = widthBlocks;
        this.ttlTicks = ttlTicks;
    }

    public static void encode(BeamFxPayload msg, FriendlyByteBuf buf) {
        buf.writeDouble(msg.fromX);
        buf.writeDouble(msg.fromY);
        buf.writeDouble(msg.fromZ);
        buf.writeDouble(msg.toX);
        buf.writeDouble(msg.toY);
        buf.writeDouble(msg.toZ);
        buf.writeInt(msg.colorArgb);
        buf.writeFloat(msg.widthBlocks);
        buf.writeVarInt(msg.ttlTicks);
    }

    public static BeamFxPayload decode(FriendlyByteBuf buf) {
        return new BeamFxPayload(buf.readDouble(), buf.readDouble(), buf.readDouble(),
                buf.readDouble(), buf.readDouble(), buf.readDouble(),
                buf.readInt(), buf.readFloat(), buf.readVarInt());
    }

    /** 客户端处理：入列交给渲染器。 */
    public static void handle(BeamFxPayload msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            if (FMLEnvironment.dist == Dist.CLIENT) {
                com.merlinkitsune.astral_dice.client.BeamFxRenderer.add(
                        msg.fromX, msg.fromY, msg.fromZ, msg.toX, msg.toY, msg.toZ,
                        msg.colorArgb, msg.widthBlocks, msg.ttlTicks);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}

