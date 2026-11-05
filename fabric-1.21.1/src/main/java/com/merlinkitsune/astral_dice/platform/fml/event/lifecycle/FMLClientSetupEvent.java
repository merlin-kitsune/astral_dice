package com.merlinkitsune.astral_dice.platform.fml.event.lifecycle;

import com.merlinkitsune.astral_dice.platform.event.Event;
import com.merlinkitsune.astral_dice.platform.fml.event.IModBusEvent;

/**
 * 客户端初始化事件(Fabric 侧手写)。由 `AstralDiceClient#onInitializeClient` 派发。
 * {@link #enqueueWork(Runnable)} 同步执行,理由见 {@link FMLCommonSetupEvent}。
 */
public class FMLClientSetupEvent extends Event implements IModBusEvent {

    public FMLClientSetupEvent() {
    }

    public void enqueueWork(Runnable work) {
        work.run();
    }
}
