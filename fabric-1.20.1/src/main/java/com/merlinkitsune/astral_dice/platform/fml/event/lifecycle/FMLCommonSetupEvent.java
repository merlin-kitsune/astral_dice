package com.merlinkitsune.astral_dice.platform.fml.event.lifecycle;

import com.merlinkitsune.astral_dice.platform.event.Event;
import com.merlinkitsune.astral_dice.platform.fml.event.IModBusEvent;

/**
 * 通用初始化事件(Fabric 侧手写;形状对齐 Forge 的 {@code FMLCommonSetupEvent} 中本模组用到的部分)。
 *
 * <p>Fabric 侧由 {@code AstralDiceMod#onInitialize} 在注册表提交之后派发。
 * {@link #enqueueWork(Runnable)} 在 Forge 上是「推迟到并行工作队列」;Fabric 无该机制,
 * 这里**直接同步执行** —— 与 Forge 在 {@code enqueueWork} 内部语义等价(任务最终都会在
 * 世界加载前跑完),且避免了「延迟任务与其它 mod 交错」的不确定性。
 */
public class FMLCommonSetupEvent extends Event implements IModBusEvent {

    public FMLCommonSetupEvent() {
    }

    /** 同步执行(见类注释:语义等价于 Forge 的延迟工作队列,但时机更确定)。 */
    public void enqueueWork(Runnable work) {
        work.run();
    }
}
