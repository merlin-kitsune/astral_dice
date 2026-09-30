package com.merlinkitsune.astral_dice.platform.network;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Supplier;

import net.fabricmc.api.EnvType;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * 双向通道(对齐 Forge 的 {@code network.simple.SimpleChannel}
 * 中本模组用到的部分)。
 *
 * <h2>线格式</h2>
 * <pre>
 *   [VarInt messageId][payload...]
 * </pre>
 * 1.20.1 的 FAPI 没有 `PayloadTypeRegistry`/原版 `CustomPayload`(那是 1.20.5+),
 * 一个 {@link ResourceLocation} 只能对应一个裸字节通道 ⇒ 用前置 VarInt 区分 12 种消息。
 * 每个方向的包都在**同一个 channel id** 上收发(原版按方向区分,不会撞)。
 *
 * <h2>线程模型</h2>
 * 接收回调在网络线程;`NetworkEvent.Context#enqueueWork` 负责切回主线程
 * (server: {@code server.execute};client: {@code client.execute}),
 * 语义与 Forge 一致 ⇒ 12 个 handler 的方法体不用改。
 *
 * <p>⚠️ 与 Forge 的差异(已登记):Forge 的通道版本号由 FML 登录握手交换并校验;
 * Fabric 侧无该机制,版本判定改由本模组的显式握手包承担(见 {@code VersionGate} 的调用点)。
 */
public final class SimpleChannel {

    private record MessageType<T>(Class<T> type,
                                  BiConsumer<T, FriendlyByteBuf> encoder,
                                  Function<FriendlyByteBuf, T> decoder,
                                  BiConsumer<T, Supplier<NetworkEvent.Context>> handler) {
    }

    private static volatile MinecraftServer server;

    /** 由 mod 初始化阶段调用,登记当前 server(供 ALL 目标广播使用)。 */
    public static void setServer(MinecraftServer s) {
        server = s;
    }

    private final ResourceLocation id;
    private final Map<Integer, MessageType<?>> byId = new HashMap<>();
    private final Map<Class<?>, Integer> byClass = new HashMap<>();

    SimpleChannel(ResourceLocation id) {
        this.id = id;
        installReceivers();
    }

    /** 通道标识(Fabric 侧即一个 ResourceLocation;Forge 侧为通道名)。 */
    public ResourceLocation getId() {
        return id;
    }

    /** 登记一种消息(与 Forge 同名同参)。 */
    public <T> void registerMessage(int messageId, Class<T> type,
                                    BiConsumer<T, FriendlyByteBuf> encoder,
                                    Function<FriendlyByteBuf, T> decoder,
                                    BiConsumer<T, Supplier<NetworkEvent.Context>> handler) {
        byId.put(messageId, new MessageType<>(type, encoder, decoder, handler));
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
            ServerPlayNetworking.send(player, id, encode(message));
        }
    }

    /** 客户端 → 服务端。 */
    public void sendToServer(Object message) {
        FriendlyByteBuf buf = encode(message);
        ClientSendHolder.send(id, buf);
    }

    private FriendlyByteBuf encode(Object message) {
        Integer messageId = byClass.get(message.getClass());
        if (messageId == null) {
            throw new IllegalArgumentException("unregistered message type: " + message.getClass().getName());
        }
        FriendlyByteBuf buf = PacketByteBufs.create();
        buf.writeVarInt(messageId);
        @SuppressWarnings("unchecked")
        MessageType<Object> type = (MessageType<Object>) byId.get(messageId);
        type.encoder().accept(message, buf);
        return buf;
    }

    // === 接收 ===

    private void installReceivers() {
        // C→S:服务端接收器在专用服务端也要装 ⇒ 无条件注册
        ServerPlayNetworking.registerGlobalReceiver(id, (srv, player, handler, buf, responseSender) -> {
            int messageId = buf.readVarInt();
            MessageType<?> type = byId.get(messageId);
            if (type == null) {
                return;
            }
            dispatch(type, buf, player, srv);
        });
        if (FabricLoader.getInstance().getEnvironmentType() == EnvType.CLIENT) {
            ClientReceiverHolder.install(this);
        }
    }

    /** 客户端接收器:独立持有类,避免专用服务端加载客户端类(NoClassDefFoundError)。 */
    private static final class ClientReceiverHolder {
        static void install(SimpleChannel channel) {
            ClientPlayNetworking.registerGlobalReceiver(channel.id,
                    (client, handler, buf, responseSender) -> {
                        int messageId = buf.readVarInt();
                        MessageType<?> type = channel.byId.get(messageId);
                        if (type == null) {
                            return;
                        }
                        channel.dispatch(type, buf, null, client);
                    });
        }
    }

    /** 客户端发包:同样隔离在持有类里。 */
    private static final class ClientSendHolder {
        static void send(ResourceLocation id, FriendlyByteBuf buf) {
            ClientPlayNetworking.send(id, buf);
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
