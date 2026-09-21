package com.merlinkitsune.astral_dice.economy;

/**
 * 星币钱包的**客户端显示缓存** —— 余额条上那个数字的唯一来源。
 *
 * <p>为什么要有这个类：钱包余额的权威副本在**服务端**（库 {@code StarEngineEconomy} 的玩家持久化 NBT），
 * 客户端看不到。余额条要显示数字，就必须由服务端把值推上来（{@code StarCoinBalanceSync} → S2C 包），
 * 再在这里落成一份只读缓存供 GUI 读取。
 *
 * <p>本类**刻意不含任何客户端专有类型的 import**（只有基本类型）：服务端也会加载它
 * （S2C 包的 handler 类引用到它），一旦引入 {@code GuiGraphics}/{@code Screen} 之类的东西，
 * 专用服务端就可能因为类初始化而炸。
 *
 * <p>数值只用于**显示**：一次点击存/取多少、能不能取，全部由服务端按真实物品栏与账本判定
 * （见 {@link StarCoinWalletActions}），客户端缓存被篡改也拿不到额外星币。
 */
public final class StarCoinWalletState {

    /** 最近一次由服务端推来的余额；未收到过同步时为 0。 */
    private static volatile long balance = 0L;

    private StarCoinWalletState() {
    }

    /** 当前显示的余额（恒 ≥ 0）。 */
    public static long balance() {
        return balance;
    }

    /** 服务端同步落点；负值一律按 0 处理（防御异常数据）。 */
    public static void setBalance(long value) {
        balance = Math.max(0L, value);
    }
}
