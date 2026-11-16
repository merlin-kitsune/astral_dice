package com.merlinkitsune.astral_dice.platform.network;

import java.util.concurrent.Executor;

import net.minecraft.server.level.ServerPlayer;

/**
 * 网络事件上下文(对齐 Forge 的 {@code network.NetworkEvent.Context}
 * 中本模组用到的部分:实测只有 {@code getSender} / {@code enqueueWork} / {@code setPacketHandled})。
 *
 * <p>Fabric 侧由 {@link SimpleChannel} 的接收器构造:把「当前线程」与「主线程执行器」
 * 一并传入,使 {@link #enqueueWork} 真正切回主线程执行 —— 这是 FAPI 的要求
 * (包接收在网络线程,触碰世界状态必须回主线程)。Forge 侧同样是这个语义,
 * 故 12 个 handler 的写法不用改。
 */
public final class NetworkEvent {

    public static final class Context {
        private final ServerPlayer sender;
        private final Executor mainThread;

        Context(ServerPlayer sender, Executor mainThread) {
            this.sender = sender;
            this.mainThread = mainThread;
        }

        /** 发送方(仅 C→S 方向非 null;S→C 方向恒为 null —— 与 Forge 语义一致)。 */
        public ServerPlayer getSender() {
            return sender;
        }

        /** 切回主线程执行。 */
        public void enqueueWork(Runnable work) {
            if (mainThread != null) {
                mainThread.execute(work);
            } else {
                work.run();
            }
        }

        /** Forge 用它标记「包已处理」;Fabric 无对应概念,保留为语义占位。 */
        public void setPacketHandled(boolean handled) {
            // no-op: Fabric 的接收回调返回即视为已处理
        }
    }

    private NetworkEvent() {
    }
}
