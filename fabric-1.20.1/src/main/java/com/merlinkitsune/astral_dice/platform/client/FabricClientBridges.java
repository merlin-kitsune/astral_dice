package com.merlinkitsune.astral_dice.platform.client;

import java.util.List;

import com.merlinkitsune.astral_dice.AstralDiceMod;
import com.merlinkitsune.astral_dice.platform.client.event.ClientPlayerNetworkEvent;
import com.merlinkitsune.astral_dice.platform.client.event.RegisterGuiOverlaysEvent;
import com.merlinkitsune.astral_dice.platform.client.event.RegisterKeyMappingsEvent;
import com.merlinkitsune.astral_dice.platform.client.event.RegisterParticleProvidersEvent;
import com.merlinkitsune.astral_dice.platform.client.event.RenderLevelStageEvent;
import com.merlinkitsune.astral_dice.platform.client.event.ScreenEvent;
import com.merlinkitsune.astral_dice.platform.client.gui.overlay.ForgeGui;
import com.merlinkitsune.astral_dice.platform.event.LoaderBus;
import com.merlinkitsune.astral_dice.platform.event.TickEvent;
import com.merlinkitsune.astral_dice.platform.fml.event.lifecycle.FMLClientSetupEvent;
import com.merlinkitsune.astral_dice.mixin.bridge.ScreenInvokerMixin;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.particle.v1.ParticleFactoryRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;

/**
 * <b>客户端事件桥</b> —— 把 Fabric API 的客户端回调转成自建事件并派发。
 *
 * <p>对应服务端的 {@code FabricBridges}。此前本线**没有**客户端对应物 ⇒
 * {@code client/*} 里 12 个 {@code @SubscribeEvent} 处理器全部处于
 * 「注册了但从不派发」的静默失效状态（按键 / HUD / 粒子 / 世界渲染 / 界面挂件都不可用）。
 *
 * <h2>三类处理方式</h2>
 * <ol>
 *   <li><b>注册类</b>（{@code FMLClientSetupEvent} / {@code RegisterKeyMappingsEvent} /
 *       {@code RegisterParticleProvidersEvent} / {@code RegisterGuiOverlaysEvent}）：
 *       Forge 在 mod 总线上派发一次、由监听者往事件对象里塞条目。这里在客户端初始化时
 *       **派发一次**，然后逐条落到 FAPI 的真正注册 API 上（{@code KeyBindingHelper} /
 *       {@code ParticleFactoryRegistry} / {@code HudRenderCallback}）。</li>
 *   <li><b>生命周期</b>：{@code ClientTickEvent} ← {@code ClientTickEvents}；
 *       {@code ClientPlayerNetworkEvent.LoggingOut} ← {@code ClientPlayConnectionEvents}。</li>
 *   <li><b>界面</b>：{@code ScreenEvent.Init.Post} ← {@code ScreenEvents.AFTER_INIT}；
 *       其中 add/remove 通过 {@link ScreenInvokerMixin} 调 {@code addRenderableWidget} /
 *       {@code removeWidget}（⚠️ 只往 {@code children()} 塞是不够的：渲染遍历的是另一个列表）。</li>
 *   <li><b>世界渲染</b>：{@code RenderLevelStageEvent} ← {@code WorldRenderEvents} 的对应阶段。</li>
 * </ol>
 *
 * <h2>⚠️ 仍未接线的客户端事件（见 PORT_STATUS_HANDOVER）</h2>
 * 需要精确注入点、且必须在 GUI 环境逐个核对才能确认不静默失效的四个渲染/输入事件：
 * {@code RenderHandEvent}（手持物品渲染）、{@code RenderPlayerEvent.Pre}、
 * {@code RenderLivingEvent.Post}（实体描边）、{@code RenderTooltipEvent.Color}（tooltip 边框染色）、
 * {@code InputEvent.MouseButton.Pre} / {@code MouseScrollingEvent}、{@code ScreenEvent.Opening}。
 */
public final class FabricClientBridges {

    private static boolean installed = false;

    /** 安装全部客户端桥（幂等）。由 {@code AstralDiceClient#onInitializeClient} 调用。 */
    public static void install() {
        if (installed) {
            return;
        }
        installed = true;
        installModBusEvents();
        installLifecycle();
        installScreens();
        installWorldRender();
        AstralDiceMod.LOGGER.info("[Astral Dice] 客户端事件桥已安装(注册类/生命周期/界面/世界渲染)");
    }

    // ------------------------------------------------------------------
    // 注册类(派发一次 → 落到 FAPI 注册 API)
    // ------------------------------------------------------------------

    private static void installModBusEvents() {
        // FMLClientSetupEvent:其 enqueueWork 在本线是**同步执行**(见该类注释)⇒ 直接派发即落地
        LoaderBus.INSTANCE.post(new FMLClientSetupEvent());

        RegisterKeyMappingsEvent keyEvent = new RegisterKeyMappingsEvent();
        LoaderBus.INSTANCE.post(keyEvent);
        for (KeyMapping mapping : keyEvent.getKeys()) {
            KeyBindingHelper.registerKeyBinding(mapping);
        }

        RegisterParticleProvidersEvent particleEvent = new RegisterParticleProvidersEvent();
        LoaderBus.INSTANCE.post(particleEvent);
        for (RegisterParticleProvidersEvent.Entry<?> entry : particleEvent.getEntries()) {
            registerParticle(entry);
        }

        RegisterGuiOverlaysEvent overlayEvent = new RegisterGuiOverlaysEvent();
        LoaderBus.INSTANCE.post(overlayEvent);
        // 拷一份:事件的 entries 是可变的,而 HudRenderCallback 会在每帧遍历它
        List<RegisterGuiOverlaysEvent.Entry> overlays = List.copyOf(overlayEvent.getEntries());
        HudRenderCallback.EVENT.register((guiGraphics, tickDelta) -> {
            for (RegisterGuiOverlaysEvent.Entry entry : overlays) {
                entry.overlay().render(ForgeGui.INSTANCE, guiGraphics, tickDelta,
                        guiGraphics.guiWidth(), guiGraphics.guiHeight());
            }
        });

        AstralDiceMod.LOGGER.info("[Astral Dice] 客户端注册桥:按键 {} 个 / 粒子 {} 个 / HUD 覆盖层 {} 个",
                keyEvent.getKeys().size(), particleEvent.getEntries().size(), overlays.size());
    }

    /** {@code RegisterParticleProvidersEvent} 的条目 → FAPI 的 {@code ParticleFactoryRegistry}。 */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void registerParticle(RegisterParticleProvidersEvent.Entry<?> entry) {
        // ⚠️ FAPI 的嵌套接口名是 PendingParticleFactory(不是 PendingParticleProvider),
        //    其 create(SpriteSet) 与本模组的 SpriteParticleRegistration 形状一致 ⇒ 方法引用即可。
        ParticleFactoryRegistry.getInstance().register(
                (net.minecraft.core.particles.ParticleType) entry.type(),
                (net.fabricmc.fabric.api.client.particle.v1.ParticleFactoryRegistry.PendingParticleFactory)
                        entry.registration()::create);
    }

    // ------------------------------------------------------------------
    // 生命周期
    // ------------------------------------------------------------------

    private static void installLifecycle() {
        ClientTickEvents.END_CLIENT_TICK.register(client ->
                LoaderBus.INSTANCE.post(new TickEvent.ClientTickEvent(TickEvent.Phase.END)));

        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            var minecraft = net.minecraft.client.Minecraft.getInstance();
            LoaderBus.INSTANCE.post(new ClientPlayerNetworkEvent.LoggingOut(
                    minecraft.gameMode, minecraft.player, handler.getConnection()));
        });
    }

    // ------------------------------------------------------------------
    // 界面
    // ------------------------------------------------------------------

    private static void installScreens() {
        ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
            ScreenInvokerMixin invoker = (ScreenInvokerMixin) screen;
            // Screen#children() 的返回类型是 List<? extends GuiEventListener> ⇒ 事件构造要 List<GuiEventListener>
            List<GuiEventListener> listeners = new java.util.ArrayList<>(screen.children());
            LoaderBus.INSTANCE.post(new ScreenEvent.Init.Post(
                    screen,
                    listeners,
                    listener -> invoker.astralDice$addRenderableWidget(asWidget(listener)),
                    listener -> invoker.astralDice$removeWidget(listener)));
        });
    }

    /** {@code ScreenEvent.Init} 的 add 回调只保证是 {@code GuiEventListener};这里下抛到 widget 三元组。 */
    @SuppressWarnings("unchecked")
    private static <T extends GuiEventListener & Renderable & NarratableEntry> T asWidget(GuiEventListener listener) {
        return (T) listener;
    }

    // ------------------------------------------------------------------
    // 世界渲染
    // ------------------------------------------------------------------

    private static void installWorldRender() {
        // Forge 会派发 11 个阶段;这里只映射**FAPI 有明确对应物**的几个。
        // ⚠️ 本模组当前的两个消费方(RenShieldRenderer / TargetSelectionHighlighter)都只认
        //    AFTER_ENTITIES ⇒ 功能完整;若将来新增消费方用到别的阶段,必须在此补映射,
        //    否则表现为「注册了但那次渲染永远不发生」(静默失效)。
        WorldRenderEvents.AFTER_ENTITIES.register(context -> postStage(
                RenderLevelStageEvent.Stage.AFTER_ENTITIES, context));
        WorldRenderEvents.AFTER_TRANSLUCENT.register(context -> postStage(
                RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS, context));
        WorldRenderEvents.END.register(context -> postStage(
                RenderLevelStageEvent.Stage.AFTER_LEVEL, context));
    }

    private static void postStage(RenderLevelStageEvent.Stage stage,
                                  net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext context) {
        LoaderBus.INSTANCE.post(new RenderLevelStageEvent(stage, context.matrixStack(), context.tickDelta(),
                context.camera(), context.projectionMatrix()));
    }

    private FabricClientBridges() {
    }
}
