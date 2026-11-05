package com.merlinkitsune.astral_dice.platform.event;

import java.util.List;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;

/**
 * 数据包同步事件 shim(对齐 Forge 的 {@code net.minecraftforge.event.OnDatapackSyncEvent})。
 *
 * <p>Forge 在「玩家进入服务器」或「{@code /reload} 之后、标签与配方下发给客户端之前」触发它。
 * Fabric 侧的等价物是 {@code ServerLifecycleEvents.SYNC_DATA_PACK_CONTENTS}
 * ({@code (ServerPlayer player, boolean joined)})——注意它**逐玩家**触发,
 * 而 Forge 的事件在 reload 时是一次性带 {@code player == null} 的「全体」语义。
 * 本 shim 保持 Forge 的形状({@link #getPlayer()} 可为 null、{@link #getPlayers()} 统一取人),
 * 桥接时对每个玩家各派发一次,于是消费方的两个分支都**零改动**可用。
 */
public class OnDatapackSyncEvent extends Event {

    private final PlayerList playerList;

    private final ServerPlayer player;

    public OnDatapackSyncEvent(PlayerList playerList, ServerPlayer player) {
        this.playerList = playerList;
        this.player = player;
    }

    public PlayerList getPlayerList() {
        return this.playerList;
    }

    /** 为 null 表示「对所有在线玩家同步」(Forge 的 reload 路径)。 */
    public ServerPlayer getPlayer() {
        return this.player;
    }

    /** 应当接收数据的玩家:指定玩家,或全部在线玩家。 */
    public List<ServerPlayer> getPlayers() {
        return this.player == null ? this.playerList.getPlayers() : List.of(this.player);
    }
}
