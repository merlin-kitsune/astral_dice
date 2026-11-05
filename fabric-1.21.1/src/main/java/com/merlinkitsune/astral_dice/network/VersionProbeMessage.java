package com.merlinkitsune.astral_dice.network;

import com.merlinkitsune.astral_dice.platform.network.NetworkEvent;
import net.minecraft.network.FriendlyByteBuf;

import java.util.function.Supplier;

/**
 * 版本握手 S→C(协议 id 28,B22):服务端在玩家加入后下发**本端**的互通版本号。
 *
 * <p>forge 侧这段语义由 FML 登录握手承担({@code NetworkRegistry.newSimpleChannel} 的
 * {@code versionSupplier} 会被 FML 交换并喂给两端的谓词,任一端谓词不通过即断开登录);
 * Fabric 没有该机制 ⇒ 本载荷是它的等价载体:服务端先发、客户端判完再回
 * {@link VersionReplyMessage},两端各用 {@code VersionGate.accepts} 判一次(见 {@link VersionHandshake})。
 *
 * <p>线格式:{@code [VarInt 28][Utf 版本串]},与既有 0..27 共用 channel
 * {@code astral_dice:main};**顺序即协议**,本组两条一律追加在末尾。
 */
public final class VersionProbeMessage {

    private final String version;

    public VersionProbeMessage(String version) {
        this.version = version;
    }

    /** 服务端本端的互通版本号({@code VersionGate#interopVersion()})。 */
    public String version() {
        return this.version;
    }

    public static void encode(VersionProbeMessage msg, FriendlyByteBuf buf) {
        buf.writeUtf(msg.version);
    }

    public static VersionProbeMessage decode(FriendlyByteBuf buf) {
        return new VersionProbeMessage(buf.readUtf());
    }

    public static void handle(VersionProbeMessage msg, Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context context = ctx.get();
        if (context.getSender() != null) {
            // 本载荷只应 S→C。若被 C→S 打上来(sender 非 null)则丢弃,不触发任何客户端逻辑。
            context.setPacketHandled(true);
            return;
        }
        context.enqueueWork(() -> VersionHandshake.onProbeReceived(msg.version));
        context.setPacketHandled(true);
    }
}
