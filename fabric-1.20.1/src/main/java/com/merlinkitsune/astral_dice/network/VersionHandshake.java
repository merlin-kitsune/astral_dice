package com.merlinkitsune.astral_dice.network;

import com.merlinkitsune.astral_dice.AstralDiceMod;
import com.merlinkitsune.astral_dice.platform.network.PacketDistributor;
import com.merlinkitsune.astral_dice.platform.network.SimpleChannel;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 登录期版本握手(B22 建立;B25 按裁决调整「超时」与「对端未安装」的语义):
 * {@code VersionGate} 在 Fabric 线**唯一的运行时调用点**。
 *
 * <h2>为什么需要它</h2>
 * forge-1.20.1 / neoforge-1.21.1 的版本门由加载器自带的握手驱动:
 * {@code NetworkRegistry.newSimpleChannel(name, versionSupplier, clientAccepted, serverAccepted)}
 * 把本通道版本号交给 FML/NeoForge,登录阶段双向交换,任一端谓词拒绝即断开连接。
 * Fabric **没有**对应机制,{@code SimpleChannel} 也没有版本字段 ⇒ 不接这一层,
 * {@code VersionGate} 就是死代码(旧 {@code NetworkRegistry} 把三个参数直接丢弃)。
 *
 * <h2>协议(不侵占既有 0..27)</h2>
 * <pre>
 *   服务端 tick 循环: canSend(player, astral_dice:main) 为真时发 S→C VersionProbe(id 28,服务端互通号)
 *   客户端收到:      VersionGate.accepts(服务端互通号)
 *                      不通过 ⇒ AP_NET_VERSION_REJECT + 断开(文案含两端版本)
 *                      通过   ⇒ 回 C→S VersionReply(id 29,客户端互通号)
 *   服务端收到:      VersionGate.accepts(客户端互通号)
 *                      不通过 ⇒ AP_NET_VERSION_REJECT + player.connection.disconnect(Component)
 *                      通过   ⇒ 清 pending + AP_NET_VERSION_OK
 *   200t(10s) 到点: 拿不到对端版本 ⇒ 从 pending 移除 + AP_NET_VERSION_ABSENT，**不断开**
 * </pre>
 *
 * <h2>B25 裁决(2026-11-01):超时与「对端未安装」都不断开</h2>
 * <ul>
 *   <li><b>断开只保留一种情况</b>:双方**都装了**本模组、但互通号不兼容。
 *       这种判定在**收到对端版本串的那一刻**就做完(服务端 {@code onReplyReceived} /
 *       客户端 {@code onProbeReceived}),**绝不攒到超时才判**。</li>
 *   <li><b>对端未安装(ABSENT)不是错误</b>:{@code fabric.mod.json} 的 {@code environment} 是
 *       {@code *}、{@code depends} 不含自身,forge 的 {@code mods.toml} 与 neoforge 的
 *       {@code neoforge.mods.toml} 也没有 {@code displayTest} / {@code clientSideOnly} ⇒
 *       默认语义是「对端没装 = 功能不可用」,**不是**「踢人」。原版客户端必须能留在服务器里。</li>
 *   <li><b>超时也不是错误</b>:{@code HANDSHAKE_DEADLINE_TICKS}(200t = 10s)到点只**放弃判定**
 *       —— 从 pending 表移除 + 打一条 WARN 机器行(玩家名、已等待 tick 数、最后一次 canSend 状态),
 *       之后不再重试,**绝不因为超时踢人**。</li>
 * </ul>
 *
 * <h2>为什么在 tick 里重试而不是在 JOIN 里直接发</h2>
 * <p>FAPI 的 {@code ServerPlayNetworkAddon.onClientReady()} 先触发 JOIN、**然后**才发本端的
 * {@code minecraft:register};而客户端的 register 包要等它收到 GameJoin 之后才发
 * (源码注释:the client cannot send any packets until after GameJoinS2CPacket is received)。
 * 也就是说 JOIN 那一刻服务端还看不到客户端声明的通道 ⇒ 直接发会被 canSend 判假。
 * 反过来,收包侧只要求**本端**有全局 receiver、不要求对端先登记,所以握手包本身不会丢。
 *
 * <p>单人/局域网同一条路:IntegratedServer 也是真的 server,同样触发 JOIN 与 END_SERVER_TICK。
 */
public final class VersionHandshake {

    /**
     * 握手超时:200t = 10s。**只用于放弃判定** —— 到点拿不到对端版本 ⇒ 移除 pending + WARN,**不断开**
     * (B25 裁决 2)。真正的版本不兼容不走这条路径:它在收到回包的那一帧就判、就断。
     */
    private static final int HANDSHAKE_DEADLINE_TICKS = 200;

    /** 对端没有本模组(从未声明通道)。forge 的对应值即 {@code NetworkRegistry} 里的 "ABSENT"。 */
    private static final String ABSENT = "ABSENT";

    /** 对端声明了通道但握手期内没回版本(旧客户端 / 回包丢失)。同样是「判定不出来」,不断开。 */
    private static final String NO_REPLY = "NO_REPLY";

    /**
     * 断开文案前缀:与 forge(FML {@code HandshakeHandler})逐字一致;**只有版本不兼容才会用到它**。
     * 括号内的两端版本见 {@link #mismatchText(String, String)} 的说明(B25 裁决 3)。
     */
    private static final String DISCONNECT_PREFIX = "Connection closed - mismatched mod channel list";

    /**
     * 待握手的玩家。写入可能发生在非服务端线程(FAPI 的 JOIN 回调线程依实现而定),
     * 故用 ConcurrentHashMap;读/删都在服务端线程的 tick 循环里。
     */
    private static final Map<UUID, Pending> PENDING = new ConcurrentHashMap<>();

    private static volatile boolean installed;

    private VersionHandshake() {
    }

    private static final class Pending {
        /** 登记 pending 时的服务端 tick,用来算「已经等了多久」并写进机器行。 */
        private final int startTick;
        private final int deadline;
        private volatile boolean probeSent;
        /** 最后一次观察到的 {@code canSend} 结果(超时告警要带它,便于区分「没装」与「装了两端对不上」)。 */
        private volatile boolean lastCanSend;

        private Pending(int startTick) {
            this.startTick = startTick;
            this.deadline = startTick + HANDSHAKE_DEADLINE_TICKS;
        }
    }

    /** 由 {@code ModNetwork.register()} 在消息登记完成后调用(可重复调用,幂等)。 */
    public static void install() {
        if (installed) {
            return;
        }
        installed = true;
        selfCheck();
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> onJoin(server, handler.player));
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> PENDING.remove(handler.player.getUUID()));
        ServerTickEvents.END_SERVER_TICK.register(VersionHandshake::onServerTick);
    }

    /**
     * 启动自检:通道声明的版本号必须等于 {@code VersionGate} 的互通号,且必须被通道声明的两个谓词接受。
     *
     * <p>这一段是 {@code NetworkRegistry.newSimpleChannel} 那三个参数在 Fabric 侧的**实际用途** ——
     * 参数不再被丢弃:版本串上线上报,谓词用来证明「声明面」与「运行时判定」是同一个口径。
     */
    private static void selfCheck() {
        SimpleChannel channel = ModNetwork.CHANNEL;
        String declared = channel.protocolVersion();
        String local = VersionGate.interopVersion();
        if (!declared.equals(local)) {
            AstralDiceMod.LOGGER.error("AP_NET_VERSION_SELFCHECK_FAIL: channel_declared={} version_gate={}",
                    declared, local);
        }
        if (channel.clientAcceptedVersions() == null || channel.serverAcceptedVersions() == null) {
            AstralDiceMod.LOGGER.error("AP_NET_VERSION_SELFCHECK_FAIL: channel={} reason=predicates_not_declared",
                    channel.getId());
            return;
        }
        if (!channel.clientAcceptedVersions().test(local) || !channel.serverAcceptedVersions().test(local)) {
            AstralDiceMod.LOGGER.error("AP_NET_VERSION_SELFCHECK_FAIL: local={} reason=own_version_rejected", local);
        }
    }

    /** 本端互通版本号(唯一来源:通道声明面 = {@code NetworkRegistry.newSimpleChannel} 的 supplier)。 */
    private static String localVersion() {
        return ModNetwork.CHANNEL.protocolVersion();
    }

    // === 服务端 ===

    private static void onJoin(MinecraftServer server, ServerPlayer player) {
        if (player == null) {
            return;
        }
        PENDING.put(player.getUUID(), new Pending(server.getTickCount()));
        AstralDiceMod.LOGGER.info("AP_NET_VERSION_JOIN: player={} local={} ticks=0",
                nameOf(player), localVersion());
    }

    private static void onServerTick(MinecraftServer server) {
        if (PENDING.isEmpty()) {
            return;
        }
        String local = localVersion();
        int now = server.getTickCount();
        Iterator<Map.Entry<UUID, Pending>> it = PENDING.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Pending> entry = it.next();
            Pending pending = entry.getValue();
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            if (player == null) {
                it.remove();
                continue;
            }
            if (!pending.probeSent) {
                boolean canSend = ServerPlayNetworking.canSend(player, ModNetwork.CHANNEL.getId());
                pending.lastCanSend = canSend;
                if (canSend) {
                    ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                            new VersionProbeMessage(local));
                    pending.probeSent = true;
                    AstralDiceMod.LOGGER.info("AP_NET_VERSION_PROBE: player={} local={} ticks={}",
                            nameOf(player), local, waitedTicks(pending, now));
                    continue;
                }
                if (now >= pending.deadline) {
                    // B25 裁决 1/2:对端从未声明通道(没装本模组)⇒ 放弃判定 + WARN,**不断开**。
                    it.remove();
                    giveUpAbsent(player, local, waitedTicks(pending, now), pending.lastCanSend);
                }
                continue;
            }
            if (now >= pending.deadline) {
                // B25 裁决 2:通道声明了但没回包 ⇒ 同样只放弃判定 + WARN,**不断开**。
                it.remove();
                giveUpNoReply(player, local, waitedTicks(pending, now), pending.lastCanSend);
            }
        }
    }

    /**
     * 服务端判定客户端版本(双向判定之一)。
     *
     * <p>B25 裁决 1/3:这里是**唯一**在服务端断开玩家的位置 —— 能走到这里说明对端**确实装了**本模组
     * (它回了 {@code VersionReply}),只是互通号不兼容。判定发生在**收到回包的这一刻**,不等超时。
     */
    static void onReplyReceived(ServerPlayer player, String clientVersion) {
        if (player == null) {
            return;
        }
        String local = localVersion();
        Pending pending = PENDING.remove(player.getUUID());
        int ticks = waitedTicks(pending, serverTickOf(player));
        if (!VersionGate.accepts(clientVersion)) {
            AstralDiceMod.LOGGER.warn("AP_NET_VERSION_REJECT: player={} local={} remote={} side=server ticks={}",
                    nameOf(player), local, clientVersion, ticks);
            AstralDiceMod.LOGGER.warn(
                    "AP_NET_VERSION_REJECT_DETAIL: side=server reason=client_version_not_accepted player={}"
                            + " local={} remote={} ticks={} action=disconnect",
                    nameOf(player), local, clientVersion, ticks);
            disconnect(player, mismatchText(local, clientVersion));
            return;
        }
        AstralDiceMod.LOGGER.info("AP_NET_VERSION_OK: player={} local={} remote={} side=server ticks={}",
                nameOf(player), local, clientVersion, ticks);
    }

    /**
     * 超时且对端**从未声明**通道(没装本模组)。B25 裁决 1/2:**只告警,不断开**。
     *
     * <p>为什么不断开:{@code fabric.mod.json} 的 {@code environment} 是 {@code *}、{@code depends} 不含自身,
     * forge / neoforge 两条线也没有 {@code displayTest} / {@code clientSideOnly} ⇒ 本模组对客户端的默认语义是
     * 「装了才有功能」而不是「客户端必需」。原版客户端加入专用服务器必须能留在服里。
     */
    private static void giveUpAbsent(ServerPlayer player, String local, int ticks, boolean canSend) {
        AstralDiceMod.LOGGER.warn(
                "AP_NET_VERSION_ABSENT: player={} local={} remote={} ticks={} canSend={}",
                nameOf(player), local, ABSENT, ticks, canSend);
        AstralDiceMod.LOGGER.warn(
                "AP_NET_VERSION_ABSENT_DETAIL: side=server reason=client_channel_absent player={} local={}"
                        + " remote={} ticks={} canSend={} action=give_up",
                nameOf(player), local, ABSENT, ticks, canSend);
    }

    /**
     * 超时但通道已声明、回包没来(旧客户端 / 回包丢失)。B25 裁决 2:**只告警,不断开**。
     *
     * <p>之后若回包迟到,{@link #onReplyReceived} 仍会照常判一次:通过就记 OK,不兼容就断开
     * (那条路径属于「双方都装了但版本不兼容」,不在本裁决的豁免范围内)。
     */
    private static void giveUpNoReply(ServerPlayer player, String local, int ticks, boolean canSend) {
        AstralDiceMod.LOGGER.warn(
                "AP_NET_VERSION_ABSENT: player={} local={} remote={} ticks={} canSend={}",
                nameOf(player), local, NO_REPLY, ticks, canSend);
        AstralDiceMod.LOGGER.warn(
                "AP_NET_VERSION_ABSENT_DETAIL: side=server reason=client_no_reply player={} local={}"
                        + " remote={} ticks={} canSend={} action=give_up",
                nameOf(player), local, NO_REPLY, ticks, canSend);
    }

    private static void disconnect(ServerPlayer player, String text) {
        player.connection.disconnect(Component.literal(text));
    }

    // === 客户端(双向判定的另一半:客户端判定服务端版本)===

    /**
     * 客户端入口(由 {@link VersionProbeMessage#handle} 切到客户端主线程后调用)。
     *
     * <p>B25 裁决 1/3:客户端能收到 probe ⇒ 服务端**确实装了**本模组 ⇒ 判否即「双方都装了但版本不兼容」,
     * 属于唯一允许断开的场景,且**收到即判即断**,不等超时。
     */
    static void onProbeReceived(String serverVersion) {
        String local = localVersion();
        if (!VersionGate.accepts(serverVersion)) {
            AstralDiceMod.LOGGER.warn("AP_NET_VERSION_REJECT: local={} remote={} side=client",
                    local, serverVersion);
            AstralDiceMod.LOGGER.warn(
                    "AP_NET_VERSION_REJECT_DETAIL: side=client reason=server_version_not_accepted local={}"
                            + " remote={} action=disconnect",
                    local, serverVersion);
            ClientSide.disconnect(mismatchText(local, serverVersion));
            return;
        }
        AstralDiceMod.LOGGER.info("AP_NET_VERSION_OK: local={} remote={} side=client", local, serverVersion);
        ClientSide.reply(local);
    }

    /**
     * 断开文案(B25 裁决 3:**括号内的版本诊断保留**,这是与 forge 的**有意差异**)。
     *
     * <p>前缀 {@value #DISCONNECT_PREFIX} 与 forge FML {@code HandshakeHandler} 逐字一致,后面补
     * {@code (astral_dice main: local=<本端互通号>, remote=<对端互通号>)}。</p>
     *
     * <p>为什么 fabric 这侧**必须**留括号:forge 断开后会进 {@code ModMismatchDisconnectedScreen},
     * 用表格列出失配模组与两端版本;fabric(1.20.1 Fabric API)没有等价的断开屏幕,断开界面只有原版
     * {@code DisconnectedScreen} 的一行文案 —— 去掉括号,玩家就**完全不知道**自己为什么被踢、
     * 也不知道该换哪个版本。所以括号是**有意保留**的差异,不是与 forge 对齐时的遗漏;
     * 若将来本线补了自定义断开屏幕,才可以把括号收回日志。</p>
     */
    private static String mismatchText(String local, String remote) {
        return DISCONNECT_PREFIX + " (astral_dice main: local=" + local + ", remote=" + remote + ")";
    }

    /** 已等待的 tick 数;取不到(玩家已离线 / 回包在放弃判定之后才到)时返回 -1,便于测试台区分。 */
    private static int waitedTicks(Pending pending, int now) {
        if (pending == null || now < 0) {
            return -1;
        }
        return Math.max(0, now - pending.startTick);
    }

    private static int serverTickOf(ServerPlayer player) {
        MinecraftServer server = player.getServer();
        return server == null ? -1 : server.getTickCount();
    }

    private static String nameOf(ServerPlayer player) {
        return player.getGameProfile().getName();
    }

    /**
     * 客户端专属动作:独立持有类,避免专用服务端加载 {@code net.minecraft.client.*}(NoClassDefFoundError)。
     * 与 {@code SimpleChannel.ClientSendHolder} 同一套路。
     */
    private static final class ClientSide {

        private static void reply(String localVersion) {
            if (!ClientPlayNetworking.canSend(ModNetwork.CHANNEL.getId())) {
                // 收得到 probe 却回不出去 = 服务端没登记 C→S 收包端,属异常配置而非版本失配;
                // B25 裁决 1/3:断开只留给「双方都装了但版本不兼容」,故这里只告警。
                AstralDiceMod.LOGGER.warn(
                        "AP_NET_VERSION_ABSENT: local={} remote={} side=client canSend=false",
                        localVersion, ABSENT);
                AstralDiceMod.LOGGER.warn(
                        "AP_NET_VERSION_ABSENT_DETAIL: side=client reason=server_channel_not_registered"
                                + " local={} remote={} canSend=false action=give_up",
                        localVersion, ABSENT);
                return;
            }
            ModNetwork.CHANNEL.sendToServer(new VersionReplyMessage(localVersion));
        }

        private static void disconnect(String text) {
            Minecraft client = Minecraft.getInstance();
            ClientPacketListener listener = client.getConnection();
            if (listener == null) {
                return;
            }
            Component reason = Component.literal(text);
            // 与 ClientPacketListener#handleDisconnect 同序:先断底层 Connection,再让客户端进断开界面。
            // 1.20.1 的 ClientPacketListener 没有 disconnect(Component) —— 那是 ServerGamePacketListenerImpl 上的。
            listener.getConnection().disconnect(reason);
            listener.onDisconnect(reason);
        }
    }
}
