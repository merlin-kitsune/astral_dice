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
 * <h2>⚠️ 尚未接线(移植线既有待办,非本轮 Accessories 兼容的范围)</h2>
 * <ul>
 *   <li>客户端 FAPI 桥:{@code platform/FabricBridges} 目前只装了
 *       {@code installEarly/installTicks/installPlayerLifecycle/installDamage/installCommands},
 *       <b>没有</b>客户端对应物。因此 {@code client/*} 里的 {@code @SubscribeEvent} 方法
 *       及其依赖的 {@code platform/client/event/*} 事件(<code>RegisterKeyMappingsEvent</code> /
 *       <code>RegisterParticleProvidersEvent</code> / <code>RegisterGuiOverlaysEvent</code> /
 *       <code>ScreenEvent</code> / <code>RenderTooltipEvent</code> / <code>ClientPlayerNetworkEvent</code> /
 *       <code>RenderLevelStageEvent</code> 等)目前<strong>不会被派发</strong>
 *       —— 即「注册了但不触发」,不会崩,但按键/HUD/粒子等客户端表现缺失。</li>
 *   <li>datagen 尚未按 {@code FabricDataGenerator} 重做;槽位图标以外的资源已随源文件打包。</li>
 * </ul>
 */
public class AstralDiceClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        LoaderBus.INSTANCE.register(com.merlinkitsune.astral_dice.client.ClientKeyNames.class);
        LoaderBus.INSTANCE.register(com.merlinkitsune.astral_dice.client.ClientSessionEvents.class);
        LoaderBus.INSTANCE.register(com.merlinkitsune.astral_dice.client.ClientTickHandler.class);
        LoaderBus.INSTANCE.register(com.merlinkitsune.astral_dice.client.KeyBindingSetup.class);
        LoaderBus.INSTANCE.register(com.merlinkitsune.astral_dice.client.ModClientEvents.class);
        LoaderBus.INSTANCE.register(com.merlinkitsune.astral_dice.client.NancyLuClientEvents.class);
        LoaderBus.INSTANCE.register(com.merlinkitsune.astral_dice.client.RarityTooltipFrame.class);
        LoaderBus.INSTANCE.register(com.merlinkitsune.astral_dice.client.RenShieldRenderer.class);
        LoaderBus.INSTANCE.register(com.merlinkitsune.astral_dice.client.TargetOutlineCapture.class);
        LoaderBus.INSTANCE.register(com.merlinkitsune.astral_dice.client.TargetSelectionClient.class);
        LoaderBus.INSTANCE.register(com.merlinkitsune.astral_dice.client.TargetSelectionHighlighter.class);
        LoaderBus.INSTANCE.register(com.merlinkitsune.astral_dice.client.gui.StarCoinWalletButtons.class);

        AstralDiceMod.LOGGER.info("Astral Dice client initialized.");
    }
}
