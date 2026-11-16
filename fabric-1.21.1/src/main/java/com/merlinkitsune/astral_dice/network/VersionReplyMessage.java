package com.merlinkitsune.astral_dice.network;

import com.merlinkitsune.astral_dice.platform.network.NetworkEvent;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;

import java.util.function.Supplier;

/**
 * 版本握手 C→S(协议 id 29,B22):客户端收到 {@link VersionProbeMessage} 并通过
 * {@code VersionGate.accepts(服务端版本)} 后,回发**本端**的互通版本号。
 *
 * <p>服务端收到后用同一个 {@code VersionGate.accepts(客户端版本)} 再判一次 —— 两个方向各判一次,
 * 与 forge 的 {@code NetworkRegistry.newSimpleChannel(name, supplier, clientAccepted, serverAccepted)}
 * 两端各判一次的口径一致。
 *
 * <p>线格式:{@code [VarInt 29][Utf 版本串]},**顺序即协议**,追加在末尾。
 */
public final class VersionReplyMessage {

    private final String version;

    public VersionReplyMessage(String version) {
        this.version = version;
    }

    /** 客户端本端的互通版本号({@code VersionGate#interopVersion()})。 */
    public String version() {
        return this.version;
    }

    public static void encode(VersionReplyMessage msg, FriendlyByteBuf buf) {
        buf.writeUtf(msg.version);
    }

    public static VersionReplyMessage decode(FriendlyByteBuf buf) {
        return new VersionReplyMessage(buf.readUtf());
    }

    public static void handle(VersionReplyMessage msg, Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context context = ctx.get();
        ServerPlayer sender = context.getSender();
        if (sender == null) {
            // 本载荷只应 C→S。S→C 方向不该出现它,丢弃。
            context.setPacketHandled(true);
            return;
        }
        context.enqueueWork(() -> VersionHandshake.onReplyReceived(sender, msg.version()));
        context.setPacketHandled(true);
    }
}
