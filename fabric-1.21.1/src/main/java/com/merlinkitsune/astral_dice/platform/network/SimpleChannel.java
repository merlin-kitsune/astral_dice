package com.merlinkitsune.astral_dice.platform.network;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

import net.fabricmc.api.EnvType;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 双向通道(对齐 Forge 的 {@code network.simple.SimpleChannel}
 * 中本模组用到的部分)。
 *
 * <h2>线格式(1.21.1 版)</h2>
 * <pre>
 *   包体(byte[]) = [ResourceLocation channelId][VarInt messageId][payload...]
 * </pre>
 * 1.21.1 的 FAPI 只认 {@link CustomPacketPayload}(裸 {@code FriendlyByteBuf} +
 * {@code ResourceLocation} 频道的那套 API 已在 1.20.5 被删除)⇒ 本类把**全部通道**
 * 复用同一个 payload 类型,把 channelId 写进包体,再由全局接收器按 id 路由回各自的
 * {@link SimpleChannel}。这样对外 API 与 1.20.1 形状**完全一致**,
 * {@code ModNetwork} 里的 12 种消息与 30 个网络类一行都不用改。
 *
 * <h2>线程模型</h2>
 * 接收回调在网络线程;{@code NetworkEvent.Context#enqueueWork} 负责切回主线程
 * (server: {@code server.execute};client: {@code client.execute}),
 * 语义与 Forge 一致 ⇒ handler 的方法体不用改。
 *
 * <p>⚠️ 与 Forge 的差异(已登记):Forge 的通道版本号由 FML 登录握手交换并校验;
 * Fabric 侧无该机制,版本判定改由本模组的显式握手包承担(见 {@link VersionHandshake},
 * 协议 id 28/29,两端各调一次 {@code VersionGate.accepts})。
 */
public final class SimpleChannel {

    private static final Logger LOGGER = LoggerFactory.getLogger("astral_dice/net");

    /**
     * 消息方向。**权威来源是发送点**:每条消息实际出现在 {@code CHANNEL.send(...)}(S→C)
     * 还是 {@code CHANNEL.sendToServer(...)}(C→S)——以及 {@code ModNetwork} 里那些静态
     * 发送助手各自内部调用的是哪个——才决定它的方向;handler 内部分支必须与之一致。
     *
     * <p>方向是**路由约束**,不是注释:接收侧按方向丢弃错路由的包(理由见
     * {@link #dispatchC2S} / {@link #dispatchS2C})。缺方向声明 = 编译期报错,
     * 不允许「默认方向」把同名缺陷重新引回来。
     */
    public enum Direction {
        /** 客户端 → 服务端({@code CHANNEL.sendToServer} / {@code ModNetwork.sendToServer})。 */
        C2S,
        /** 服务端 → 客户端({@code CHANNEL.send} 及其 {@code sendToPlayer} / {@code sendToAllPlayers} /
         * {@code sendToPlayersTrackingEntity} 助手)。 */
        S2C
    }

    private record MessageType<T>(Direction direction,
                                  Class<T> type,
                                  BiConsumer<T, FriendlyByteBuf> encoder,
                                  Function<FriendlyByteBuf, T> decoder,
                                  BiConsumer<T, Supplier<NetworkEvent.Context>> handler) {
    }

    // ------------------------------------------------------------------
    // 1.21.1 网络承载:单一 payload 类型 + 包体内嵌 channelId
    // ------------------------------------------------------------------

    /** C→S 载体。 */
    public static final class C2SPayload implements CustomPacketPayload {

        public static final CustomPacketPayload.Type<C2SPayload> TYPE =
                new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(
                        "astral_dice", "raw_c2s"));

        public static final StreamCodec<RegistryFriendlyByteBuf, C2SPayload> CODEC =
                CustomPacketPayload.codec(C2SPayload::write, C2SPayload::new);

        private final byte[] data;

        C2SPayload(byte[] data) {
            this.data = data;
        }

        C2SPayload(FriendlyByteBuf buf) {
            this.data = buf.readByteArray();
        }

        private void write(FriendlyByteBuf buf) {
            buf.writeByteArray(this.data);
        }

        public byte[] data() {
            return data;
        }

        @Override
        public CustomPacketPayload.Type<C2SPayload> type() {
            return TYPE;
        }
    }

    /** S→C 载体。 */
    public static final class S2CPayload implements CustomPacketPayload {

        public static final CustomPacketPayload.Type<S2CPayload> TYPE =
                new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(
                        "astral_dice", "raw_s2c"));

        public static final StreamCodec<RegistryFriendlyByteBuf, S2CPayload> CODEC =
                CustomPacketPayload.codec(S2CPayload::write, S2CPayload::new);

        private final byte[] data;

        S2CPayload(byte[] data) {
            this.data = data;
        }

        S2CPayload(FriendlyByteBuf buf) {
            this.data = buf.readByteArray();
        }

        private void write(FriendlyByteBuf buf) {
            buf.writeByteArray(this.data);
        }

        public byte[] data() {
            return data;
        }

        @Override
        public CustomPacketPayload.Type<S2CPayload> type() {
            return TYPE;
        }
    }

    /** channelId → 通道(接收侧路由表)。 */
    private static final Map<ResourceLocation, SimpleChannel> BY_ID = new HashMap<>();

    /** 提前安装一次(静态初始化时执行,早于任何连接建立)。 */
    private static final boolean INSTALLED = installTransport();

    private static boolean installTransport() {
        PayloadTypeRegistry.playC2S().register(C2SPayload.TYPE, C2SPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(S2CPayload.TYPE, S2CPayload.CODEC);

        // C→S:服务端接收器在专用服务端也要装 ⇒ 无条件注册
        ServerPlayNetworking.registerGlobalReceiver(C2SPayload.TYPE, (payload, context) -> {
            FriendlyByteBuf buf = bufOf(payload.data());
            ResourceLocation channelId = buf.readResourceLocation();
            SimpleChannel channel = BY_ID.get(channelId);
            if (channel == null) {
                return;
            }
            channel.dispatchC2S(buf, context.player());
        });

        if (FabricLoader.getInstance().getEnvironmentType() == EnvType.CLIENT) {
            ClientTransport.installClientReceiver();
        }
        return true;
    }

    private static FriendlyByteBuf bufOf(byte[] data) {
        return new FriendlyByteBuf(io.netty.buffer.Unpooled.wrappedBuffer(data));
    }

    private static volatile MinecraftServer server;

    /** 由 mod 初始化阶段调用,登记当前 server(供 ALL 目标广播使用)。 */
    public static void setServer(MinecraftServer s) {
        server = s;
    }

    private final ResourceLocation id;
    private final Supplier<String> versionSupplier;
    private final Predicate<String> clientAcceptedVersions;
    private final Predicate<String> serverAcceptedVersions;
    private final Map<Integer, MessageType<?>> byId = new HashMap<>();
    private final Map<Class<?>, Integer> byClass = new HashMap<>();

    SimpleChannel(ResourceLocation id) {
        this(id, null, null, null);
    }

    /**
     * 带版本声明的通道(对齐 Forge {@code NetworkRegistry.newSimpleChannel} 的三参数形态)。
     *
     * <p>⚠️ 与 Forge 的差异:Forge 由 FML 登录握手交换 {@code versionSupplier} 的取值并喂给两个谓词,
     * 任一端谓词不通过即断开登录;Fabric 无该机制 ⇒ 本类只**保存声明**(不再丢弃参数),
     * 判定由 {@link VersionHandshake} 的显式握手包驱动(两端各调一次 {@code VersionGate.accepts})。
     */
    SimpleChannel(ResourceLocation id,
                  Supplier<String> versionSupplier,
                  Predicate<String> clientAcceptedVersions,
                  Predicate<String> serverAcceptedVersions) {
        this.id = id;
        this.versionSupplier = versionSupplier;
        this.clientAcceptedVersions = clientAcceptedVersions;
        this.serverAcceptedVersions = serverAcceptedVersions;
        BY_ID.put(id, this);
    }

    /** 通道标识(Fabric 侧即一个 ResourceLocation;Forge 侧为通道名)。 */
    public ResourceLocation getId() {
        return id;
    }

    /** 通道声明的协议版本号(= {@code NetworkRegistry.newSimpleChannel} 的 versionSupplier 取值)。 */
    public String protocolVersion() {
        return versionSupplier == null ? "" : versionSupplier.get();
    }

    /** 通道声明的「客户端接受的远端版本」谓词(未声明时为 null)。 */
    public Predicate<String> clientAcceptedVersions() {
        return clientAcceptedVersions;
    }

    /** 通道声明的「服务端接受的远端版本」谓词(未声明时为 null)。 */
    public Predicate<String> serverAcceptedVersions() {
        return serverAcceptedVersions;
    }

    /**
     * 登记一种消息(Forge {@code SimpleChannel} 同名方法 + 显式方向)。
     *
     * <p>⚠️ 无方向的旧 5 参重载已**删除**,不保留「默认方向」:方向是协议的一部分,
     * 留个默认值只会让新消息忘了声明方向、退回「按 id 查表就 dispatch」的老缺陷。
     * 少写一个参数 ⇒ 编译期报错,这是唯一能长期防呆的形态(全仓调用点只有
     * {@code ModNetwork.register()} 的 31 条)。
     */
    public <T> void registerMessage(int messageId, Direction direction, Class<T> type,
                                    BiConsumer<T, FriendlyByteBuf> encoder,
                                    Function<FriendlyByteBuf, T> decoder,
                                    BiConsumer<T, Supplier<NetworkEvent.Context>> handler) {
        if (direction == null) {
            throw new IllegalArgumentException("message " + messageId + " ("
                    + type.getName() + "): direction 不能为空");
        }
        byId.put(messageId, new MessageType<>(direction, type, encoder, decoder, handler));
        byClass.put(type, messageId);
    }

    // === 发送 ===

    /** 按目标发包(S→C)。 */
    public void send(PacketDistributor.Target target, Object message) {
        List<ServerPlayer> recipients;
        if (target == PacketDistributor.ALL) {
            MinecraftServer s = server;
            recipients = new ArrayList<>();
            if (s != null) {
                recipients.addAll(s.getPlayerList().getPlayers());
            }
        } else {
            recipients = target.resolve();
        }
        if (recipients.isEmpty()) {
            return;
        }
        // ⚠️ 每个收件人**单独编码**:同一个 FriendlyByteBuf 不能被多次 send
        //    (FAPI 会读取其内容并推进 readerIndex;复用会让第二个收件人收到空包)
        for (ServerPlayer player : recipients) {
            ServerPlayNetworking.send(player, new S2CPayload(encode(message)));
        }
    }

    /** 客户端 → 服务端。 */
    public void sendToServer(Object message) {
        ClientTransport.sendToServer(new C2SPayload(encode(message)));
    }

    private byte[] encode(Object message) {
        Integer messageId = byClass.get(message.getClass());
        if (messageId == null) {
            throw new IllegalArgumentException("unregistered message type: " + message.getClass().getName());
        }
        // ⚠️ 必须用**可扩容**的堆缓冲：Unpooled.wrappedBuffer(new byte[0]) 会返回 netty 的
        //    EMPTY_BUFFER（不可写），任何 writeByte/writeShort 都直接抛 IndexOutOfBoundsException
        //    ⇒ 每一次 send/sendToServer（含版本握手）都会炸。解码方向仍可继续用 bufOf(byte[])。
        FriendlyByteBuf buf = new FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
        buf.writeResourceLocation(id);
        buf.writeVarInt(messageId);
        @SuppressWarnings("unchecked")
        MessageType<Object> type = (MessageType<Object>) byId.get(messageId);
        type.encoder().accept(message, buf);
        byte[] out = new byte[buf.readableBytes()];
        buf.readBytes(out);
        return out;
    }

    // === 接收 ===

    /**
     * C→S 路由(仅服务端侧调用)。
     *
     * <p>⚠️ 方向校验:包体里的 {@code messageId} 是客户端**可任意伪造**的整数。
     * 不校验方向的话,客户端往 {@code raw_c2s} 里塞一个 S→C 专用 id(例如 id 5),
     * 服务端就会执行那条消息的 handler —— 而 S→C handler 通常直接 {@code new} 客户端类,
     * 于是专用服务端一侧抛 {@code NoClassDefFoundError},异常落在包处理路径上(整局脏)。
     * 因此方向不符的一律丢弃并 WARN(带 messageId + 类名,便于定位是哪个客户端/哪条消息)。
     */
    private void dispatchC2S(FriendlyByteBuf buf, ServerPlayer sender) {
        int messageId = buf.readVarInt();
        MessageType<?> type = byId.get(messageId);
        if (type == null) {
            return;
        }
        if (type.direction() != Direction.C2S) {
            LOGGER.warn("AP_NET_ROUTE_DROP_C2S: channel={} messageId={} class={} declaredDirection={} "
                            + "player={} —— 该消息只应 S→C,客户端却从 raw_c2s 发了上来(错路由);已丢弃,不进入 handler。",
                    id, messageId, type.type().getName(), type.direction(),
                    sender != null ? sender.getGameProfile().getName() : "<null>");
            return;
        }
        dispatch(type, buf, sender, server);
    }

    /**
     * S→C 路由(仅客户端侧调用,入口 {@link ClientTransport})。
     *
     * <p>方向校验与 {@link #dispatchC2S} 对称:服务端若把 C→S 专用 id 从 {@code raw_s2c}
     * 下发(例:连接被伪造成非预期方向),客户端不会执行那条只该在服务端跑的 handler。
     * {@code gameOwner} 用 {@code Object} 承接(客户端侧传 {@code context.client()}),
     * 使本类在专用服务端也不出现客户端类型签名。
     */
    private void dispatchS2C(FriendlyByteBuf buf, Object gameOwner) {
        int messageId = buf.readVarInt();
        MessageType<?> type = byId.get(messageId);
        if (type == null) {
            return;
        }
        if (type.direction() != Direction.S2C) {
            LOGGER.warn("AP_NET_ROUTE_DROP_S2C: channel={} messageId={} class={} declaredDirection={} "
                            + "—— 该消息只应 C→S,服务端却从 raw_s2c 下发了(错路由);已丢弃,不进入 handler。",
                    id, messageId, type.type().getName(), type.direction());
            return;
        }
        dispatch(type, buf, null, gameOwner);
    }

    /** 客户端侧隔离(避免专用服务端加载客户端类 ⇒ NoClassDefFoundError)。 */
    private static final class ClientTransport {
        static void installClientReceiver() {
            ClientPlayNetworking.registerGlobalReceiver(S2CPayload.TYPE, (payload, context) -> {
                FriendlyByteBuf buf = bufOf(payload.data());
                ResourceLocation channelId = buf.readResourceLocation();
                SimpleChannel channel = BY_ID.get(channelId);
                if (channel == null) {
                    return;
                }
                channel.dispatchS2C(buf, context.client());
            });
        }

        static void sendToServer(C2SPayload payload) {
            ClientPlayNetworking.send(payload);
        }
    }

    private <T> void dispatch(MessageType<T> type, FriendlyByteBuf buf, ServerPlayer sender, Object gameOwner) {
        T message = type.decoder().apply(buf);
        java.util.concurrent.Executor executor = null;
        if (gameOwner instanceof MinecraftServer s) {
            executor = s;
        } else if (gameOwner instanceof net.minecraft.client.Minecraft mc) {
            executor = mc;
        }
        NetworkEvent.Context context = new NetworkEvent.Context(sender, executor);
        type.handler().accept(message, () -> context);
    }
}
