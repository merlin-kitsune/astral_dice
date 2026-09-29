package com.merlinkitsune.astral_dice;

import com.merlinkitsune.astral_dice.platform.event.LoaderBus;

import net.fabricmc.api.ClientModInitializer;

/**
 * 《星之骰戏》Fabric 1.20.1 客户端入口。
 *
 * <h2>为什么客户端监听登记搬到这里(2026-09-29)</h2>
 * <p>客户端订阅类(按键 / 粒子 / HUD overlay / tooltip 边框 / 目标高亮 …)只在客户端存在,
 * 绝不能被**专用服务端**加载 —— 否则 {@code NoClassDefFoundError}。
 * 原先这批 {@code LoaderBus.register(...)} 调用写在 {@link AstralDiceMod}(双端类)的一个
 * 包私有方法里,虽然靠「不调用即不解析」侥幸可用,但结构上是把客户端引用留在了双端类的常量池里。
 * 现改由本类承担:{@code fabric.mod.json} 的 {@code client} entrypoint 指向这里,
 * Fabric Loader 只在客户端加载它。
 *
 * <h2>客户端事件桥的接线状态(2026-09-29 复核)</h2>
 * <p>客户端桥 {@link com.merlinkitsune.astral_dice.platform.client.FabricClientBridges}
 * 已在本方法末尾安装,{@code client/*} 里的处理器均已实际派发:
 * {@code RegisterKeyMappingsEvent} / {@code RegisterParticleProvidersEvent} /
 * {@code RegisterGuiOverlaysEvent} / {@code ScreenEvent.Init.Post} / {@code ScreenEvent.Opening} /
 * {@code ClientPlayerNetworkEvent.LoggingOut} / {@code RenderLevelStageEvent} /
 * {@code RenderHandEvent} / {@code RenderPlayerEvent.Pre} / {@code RenderLivingEvent.Post} /
 * {@code InputEvent.MouseButton.Pre} / {@code InputEvent.MouseScrollingEvent} /
 * {@code TickEvent.ClientTickEvent} / {@code ItemTooltipEvent} / {@code RenderTooltipEvent.Color}。
 * <p>⚠️ 本类注释此前曾写「客户端桥不存在、按键/HUD/粒子表现缺失」—— 那是**尚未安装桥时**的
 * 状态描述,已在 2026-09-29 的复核中订正(实际安装点在 {@link #onInitializeClient()} 末尾)。
 */
public class AstralDiceClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        LoaderBus.INSTANCE.register(com.merlinkitsune.astral_dice.client.ClientKeyNames.class);
        LoaderBus.INSTANCE.register(com.merlinkitsune.astral_dice.client.ClientSessionEvents.class);
        LoaderBus.INSTANCE.register(com.merlinkitsune.astral_dice.client.ClientTickHandler.class);
        LoaderBus.INSTANCE.register(com.merlinkitsune.astral_dice.client.KeyBindingSetup.class);
        // ⚠️ 必须**单独登记内部类**:LoaderBus.scan 只遍历「该类自己声明的方法 + 父类」,
        //    **不递归内部类**。而另三线(forge/neoforge)是把 @EventBusSubscriber 标在
        //    KeyBindingSetup.ClientEvents 这个**内部类**上、由平台自动注册的 ⇒ 移植到 Fabric 时
        //    漏了这一步,导致 J(立牌主动技能)/H(卡牌栏)两个按键**完全不响应**
        //    (2026-09-29 客户端启动时由 SubscriptionAudit 抓出:枚举 582 个类、50 个已登记、
        //     这 1 个带 @SubscribeEvent 却未登记)。保留上面外层类的登记:外层目前无处理器,
        //    但登记它是无害的,且若将来外层类加了 @SubscribeEvent 方法无需再改这里。
        LoaderBus.INSTANCE.register(com.merlinkitsune.astral_dice.client.KeyBindingSetup.ClientEvents.class);
        LoaderBus.INSTANCE.register(com.merlinkitsune.astral_dice.client.ModClientEvents.class);
        LoaderBus.INSTANCE.register(com.merlinkitsune.astral_dice.client.NancyLuClientEvents.class);
        LoaderBus.INSTANCE.register(com.merlinkitsune.astral_dice.client.RarityTooltipFrame.class);
        LoaderBus.INSTANCE.register(com.merlinkitsune.astral_dice.client.RenShieldRenderer.class);
        LoaderBus.INSTANCE.register(com.merlinkitsune.astral_dice.client.TargetOutlineCapture.class);
        LoaderBus.INSTANCE.register(com.merlinkitsune.astral_dice.client.TargetSelectionClient.class);
        LoaderBus.INSTANCE.register(com.merlinkitsune.astral_dice.client.TargetSelectionHighlighter.class);
        LoaderBus.INSTANCE.register(com.merlinkitsune.astral_dice.client.gui.StarCoinWalletButtons.class);

        // 客户端事件桥:把 FAPI 的客户端回调接到自建事件上。
        // ⚠️ 与服务端的 FabricBridges.install() 同理 —— 不装则 client/* 的处理器
        //    全部「注册了但从不派发」。必须放在监听器注册之后。
        com.merlinkitsune.astral_dice.platform.client.FabricClientBridges.install();

        // 订阅类审计:此时服务端(AstralDiceMod)与客户端(本类)两批 register 都已完成,
        // 故这是最完整的一次扫描 —— 把「带 @SubscribeEvent 却从未登记」的类全列出来。
        // (dispatchReport() 只看得见已注册的事件类,对「忘了 register」是盲区。)
        com.merlinkitsune.astral_dice.platform.event.SubscriptionAudit.verifyClientSide();

        AstralDiceMod.LOGGER.info("Astral Dice client initialized.");
    }
}
