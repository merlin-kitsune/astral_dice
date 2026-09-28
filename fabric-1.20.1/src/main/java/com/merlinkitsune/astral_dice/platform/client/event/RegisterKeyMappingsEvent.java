package com.merlinkitsune.astral_dice.platform.client.event;

import java.util.ArrayList;
import java.util.List;

import com.merlinkitsune.astral_dice.platform.event.Event;
import com.merlinkitsune.astral_dice.platform.fml.event.IModBusEvent;

import net.minecraft.client.KeyMapping;

/**
 * 按键绑定注册事件(Fabric 侧手写)。形状对齐 Forge 的
 * {@code client.event.RegisterKeyMappingsEvent}。
 *
 * <p>注册结果由 `AstralDiceClient` 转接到 FAPI 的 {@code KeyBindingHelper}。
 */
public class RegisterKeyMappingsEvent extends Event implements IModBusEvent {

    private final List<KeyMapping> keys = new ArrayList<>();

    public void register(KeyMapping mapping) {
        keys.add(mapping);
    }

    public List<KeyMapping> getKeys() {
        return keys;
    }
}
