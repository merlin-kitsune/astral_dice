package com.merlinkitsune.astral_dice.platform.client.event;

import java.util.ArrayList;
import java.util.List;

import com.merlinkitsune.astral_dice.platform.client.gui.overlay.IGuiOverlay;
import com.merlinkitsune.astral_dice.platform.event.Event;
import com.merlinkitsune.astral_dice.platform.fml.event.IModBusEvent;

import net.minecraft.resources.ResourceLocation;

/**
 * HUD 覆盖层注册事件(Fabric 侧手写)。形状对齐 Forge 的
 * {@code client.event.RegisterGuiOverlaysEvent}。
 *
 * <p>注册结果由 `AstralDiceClient` 取出,逐帧在 {@code HudRenderCallback} 里按注册顺序渲染。
 */
public class RegisterGuiOverlaysEvent extends Event implements IModBusEvent {

    /** 一条注册项。 */
    public record Entry(ResourceLocation anchor, ResourceLocation id, IGuiOverlay overlay) {
    }

    private final List<Entry> entries = new ArrayList<>();

    /** 注册在某个原版 HUD 元素之上({@code id} 为纯 path,自动拼 modid 前缀)。 */
    public void registerAbove(ResourceLocation anchor, String id, IGuiOverlay overlay) {
        entries.add(new Entry(anchor, new ResourceLocation(
                com.merlinkitsune.astral_dice.AstralDiceMod.MODID, id), overlay));
    }

    public List<Entry> getEntries() {
        return entries;
    }
}
