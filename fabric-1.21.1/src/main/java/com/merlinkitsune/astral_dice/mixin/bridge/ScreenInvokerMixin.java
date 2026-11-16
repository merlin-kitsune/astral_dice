package com.merlinkitsune.astral_dice.mixin.bridge;

import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * {@code Screen#addRenderableWidget} 的调用器 —— 服务 {@code ScreenEvent.Init.Post} 桥。
 *
 * <p>Forge 的 {@code ScreenEvent.Init} 给监听者两个 {@code Consumer<GuiEventListener>}
 * （add / remove），它们内部走 {@code Screen#addRenderableWidget} / {@code removeWidget}。
 * 本模组的钱包按钮正是靠 {@code event.addListener(...)} 挂到物品栏界面上。
 *
 * <p>Fabric 侧没有等价回调，需要我们自己构造这个事件；而 {@code addRenderableWidget}
 * 在 1.20.1 是 <b>protected</b> 且带泛型边界（{@code <T extends GuiEventListener & Renderable & NarratableEntry>}）
 * ⇒ 用 Mixin 的 {@link Invoker} 打开它，比改 accesswidener 更不容易写错擦除后的描述符。
 *
 * <p>⚠️ 只把 {@code children()} 加进列表是**不够**的：{@code Screen#render} 遍历的是
 * 另一个 {@code renderables} 列表，只加 children 会出现「按钮在、但画不出来、也点不到」。
 */
@Mixin(Screen.class)
public interface ScreenInvokerMixin {

    @Invoker("addRenderableWidget")
    <T extends GuiEventListener & Renderable & NarratableEntry> T astralDice$addRenderableWidget(T widget);

    @Invoker("removeWidget")
    void astralDice$removeWidget(GuiEventListener widget);
}
