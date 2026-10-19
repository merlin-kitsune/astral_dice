package com.merlinkitsune.astral_dice.compat.jade;

import com.merlinkitsune.astral_dice.AstralDiceMod;
import com.merlinkitsune.astral_dice.block.CardShopBlock;
import com.merlinkitsune.astral_dice.block.ChipShopBlock;
import com.merlinkitsune.astral_dice.block.EventDrawBlock;
import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.IWailaPlugin;
import snownee.jade.api.WailaPlugin;

/**
 * Jade 插件入口（Forge 1.20.1）：卡牌商店 / 筹码商店 / 抽奖盒的冷却倒计时。
 *
 * <p><b>发现方式</b>：Jade 在自己的加载完成阶段扫描**全部模组 jar** 的
 * {@code @WailaPlugin("<modid>")} 注解（{@code ModFileScanData}，
 * 见 Jade 11.13.3 {@code snownee.jade.util.CommonProxy}），注解值只用来确认「该 modid 已加载」，
 * 然后反射构造本类并依次回调 {@code register} / {@link #registerClient}。
 * <b>Jade 缺席时本类根本不会被加载</b>（没有任何代码引用它），无需 mods.toml 依赖。
 *
 * <p><b>与本线 API 的差异（必须知道）</b>：Jade 11.13.3 的
 * {@code IWailaCommonRegistration#registerBlockDataProvider} 只接受
 * {@code Class<? extends BlockEntity>}，且服务端在 {@code BlockAccessor.getBlockEntity() == null}
 * 时**直接不发数据包**（{@code BlockAccessorImpl#handleRequest} 的字节码判据）。
 * 本模组三个方块都没有 BlockEntity ⇒ 这一线**无法**用「服务端数据提供器」把玩家级冷却写进 Jade 数据包。
 * 因此本线改为**客户端直接读已同步的冷却值**（{@code SHOP_TRANSFER_COOLDOWN_END} /
 * {@code CHIP_SHOP_STATE} 在 {@code ModAttachments} 里补了 {@code .sync()}），
 * 用户可见行为与 1.21.1 完全一致——详见 {@code docs/features/jade-shop-cooldown-spec.md}。
 */
@WailaPlugin(AstralDiceMod.MODID)
public final class AstralDiceJadePlugin implements IWailaPlugin {

    @Override
    public void registerClient(IWailaClientRegistration registration) {
        registration.registerBlockComponent(ShopCooldownClientProvider.INSTANCE, CardShopBlock.class);
        registration.registerBlockComponent(ShopCooldownClientProvider.INSTANCE, ChipShopBlock.class);
        registration.registerBlockComponent(ShopCooldownClientProvider.INSTANCE, EventDrawBlock.class);
        registration.registerBlockComponent(EventDrawerCostProvider.INSTANCE, EventDrawBlock.class);
    }
}
