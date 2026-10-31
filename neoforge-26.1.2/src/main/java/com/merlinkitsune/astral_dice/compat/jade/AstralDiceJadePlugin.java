package com.merlinkitsune.astral_dice.compat.jade;

import com.merlinkitsune.astral_dice.AstralDiceMod;
import com.merlinkitsune.astral_dice.block.CardShopBlock;
import com.merlinkitsune.astral_dice.block.ChipShopBlock;
import com.merlinkitsune.astral_dice.block.EventDrawBlock;
import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.IWailaCommonRegistration;
import snownee.jade.api.IWailaPlugin;
import snownee.jade.api.WailaPlugin;

/**
 * Jade 插件入口（NeoForge 1.21.1）：卡牌商店 / 筹码商店 / 抽奖盒的冷却倒计时。
 *
 * <p><b>发现方式</b>：Jade 在自己的加载完成阶段扫描**全部模组 jar** 的
 * {@code @WailaPlugin("<modid>")} 注解（{@code ModFileScanData}，
 * 见 Jade 15.10.6 {@code snownee.jade.util.CommonProxy}），注解值只用来确认「该 modid 已加载」，
 * 然后反射构造本类并依次回调 {@link #register} / {@link #registerClient}。
 * 因此：<b>Jade 缺席时本类根本不会被加载</b>（没有任何代码引用它），无需 mods.toml 依赖，
 * 也不会在缺席时阻断启动。
 *
 * <p><b>为什么服务端数据走 {@code registerBlockDataProvider}</b>：本模组三个方块都<b>没有 BlockEntity</b>，
 * 而冷却真值是**玩家级**附件（服务端权威、客户端未同步）⇒ 由服务端在 Jade 请求数据时
 * 按「当前看向该方块的玩家」现算剩余 tick 写进数据包，客户端只负责渲染。
 * NeoForge 侧 Jade 15.x 的该注册方法接受**方块类本身**（{@code Class<?>}），
 * 且服务端查找时同时按方块与 BlockEntity 匹配（{@code getBlockNBTProviders(Block, BlockEntity)}），
 * 故无 BlockEntity 的方块也能收到数据。
 *
 * <p>forge-1.20.1 侧的同名插件类结构相同，但 Jade 11.13.3 的注册方法只接受
 * {@code Class<? extends BlockEntity>}（且 BE 为 null 时直接不发数据包），
 * 故那边改为读取已同步到客户端的冷却值——差异见 {@code docs/features/jade-shop-cooldown-spec.md}。
 */
@WailaPlugin(AstralDiceMod.MODID)
public final class AstralDiceJadePlugin implements IWailaPlugin {

    @Override
    public void register(IWailaCommonRegistration registration) {
        registration.registerBlockDataProvider(ShopCooldownServerDataProvider.INSTANCE, CardShopBlock.class);
        registration.registerBlockDataProvider(ShopCooldownServerDataProvider.INSTANCE, ChipShopBlock.class);
        registration.registerBlockDataProvider(ShopCooldownServerDataProvider.INSTANCE, EventDrawBlock.class);
    }

    @Override
    public void registerClient(IWailaClientRegistration registration) {
        registration.registerBlockComponent(ShopCooldownClientProvider.INSTANCE, CardShopBlock.class);
        registration.registerBlockComponent(ShopCooldownClientProvider.INSTANCE, ChipShopBlock.class);
        registration.registerBlockComponent(ShopCooldownClientProvider.INSTANCE, EventDrawBlock.class);
        registration.registerBlockComponent(EventDrawerCostProvider.INSTANCE, EventDrawBlock.class);
    }
}
