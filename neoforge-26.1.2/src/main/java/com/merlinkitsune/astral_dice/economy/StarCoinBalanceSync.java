package com.merlinkitsune.astral_dice.economy;

import com.merlinkitsune.astral_dice.network.StarCoinBalancePayload;
import com.merlinkitsune.starenginelib.economy.StarEngineEconomy;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 把钱包余额从服务端推到客户端（余额条要显示的数字）。
 *
 * <p><b>为什么不用「改动点各自推一次」</b>：余额的改动来源不止我们自己的按钮 ——
 * 中央发币漏斗 {@code ResourceConversion.giveItem}、拾取吸收钩子、库的 {@code /starcoin} 命令、
 * 以及第三方（FTB 等）经 {@link StarEngineEconomy} 的直接写入，全都会改余额。挨个挂勾必然漏。
 * 所以这里用**低频轮询 + 变化才发包**：每 {@value #INTERVAL_TICKS} tick（1 秒）读一次余额，
 * 与「上次发给该玩家的值」不同才发。任何来源的改动都会在 1 秒内自动出现在余额条上。
 *
 * <p>代价与收益：每玩家每秒最多一次本地读取；网络开销只在**值真的变了**时才产生
 * （正常游戏里几乎为零）。相比「每次改动都发」既更省也更不容易漏。
 *
 * <p>登录时客户端缓存不可信（可能是上一次会话的残值，也可能是 0），故登录/重生后
 * {@link #forceResend} 无条件重发一次。
 */
public final class StarCoinBalanceSync {

    /** 轮询间隔（tick）—— 1 秒。 */
    private static final int INTERVAL_TICKS = 20;

    /** 每玩家「上次已发送的值」；登录时移除该项即可强制重发。 */
    private static final Map<UUID, Long> LAST_SENT = new ConcurrentHashMap<>();

    private StarCoinBalanceSync() {
    }

    /** 玩家 tick 钩子（服务端）。按 1 秒节流后做「变化才发包」。 */
    public static void tick(ServerPlayer player) {
        if (player == null || player.level().isClientSide()) return;
        if (player.tickCount % INTERVAL_TICKS != 0) return;
        syncIfChanged(player);
    }

    /** 登录 / 重生：丢掉「上次已发送」记录并立刻重发一次。 */
    public static void forceResend(ServerPlayer player) {
        if (player == null || player.level().isClientSide()) return;
        LAST_SENT.remove(player.getUUID());
        syncIfChanged(player);
    }

    private static void syncIfChanged(ServerPlayer player) {
        // 钱包功能关闭时不产生任何网络流量（此时界面上也没有余额条）
        if (!StarCoinCurrency.isWalletEnabled()) return;
        long balance = currentBalance(player);
        Long last = LAST_SENT.get(player.getUUID());
        if (last != null && last.longValue() == balance) return;
        LAST_SENT.put(player.getUUID(), balance);
        PacketDistributor.sendToPlayer(player, new StarCoinBalancePayload(balance));
    }

    private static long currentBalance(ServerPlayer player) {
        if (!StarEngineEconomy.isAvailable()) return 0L;
        return Math.max(0L, StarEngineEconomy.getBalance(player));
    }
}
