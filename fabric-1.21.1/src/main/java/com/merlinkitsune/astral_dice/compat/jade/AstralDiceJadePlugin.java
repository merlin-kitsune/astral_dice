package com.merlinkitsune.astral_dice.compat.jade;

import com.merlinkitsune.astral_dice.AstralDiceMod;
import com.merlinkitsune.astral_dice.block.CardShopBlock;
import com.merlinkitsune.astral_dice.block.ChipShopBlock;
import com.merlinkitsune.astral_dice.block.EventDrawBlock;
import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.IWailaPlugin;
import snownee.jade.api.WailaPlugin;

/**
 * Jade 插件入口（Fabric 1.20.1）：卡牌商店 / 筹码商店 / 抽奖盒的冷却倒计时 + 抽奖盒花费行。
 *
 * <p><b>发现方式（与 Forge 蓝本不同，必须知道）</b>：Forge 侧 Jade 扫全部模组 jar 的
 * {@code @WailaPlugin("<modid>")}（{@code ModFileScanData}）；Fabric 变体的 Jade **不扫注解**，
 * 走 Fabric Loader 入口点：{@code snownee.jade.util.CommonProxy#loadComplete} 用
 * {@code FabricLoader.getInstance().getEntrypointContainers("jade", IWailaPlugin.class)}
 * 取出所有在本模组 {@code fabric.mod.json} 的 {@code entrypoints."jade"} 里登记的类，
 * 逐个回调 {@code register} / {@link #registerClient}。故本类**必须在 fabric.mod.json 登记**，
 * 否则永远不会被实例化（本仓已登记）。
 *
 * <p><b>{@code @WailaPlugin} 保留，但只是自检而非发现机制</b>：Fabric 变体的 {@code CommonProxy}
 * 取到入口点实例后仍会读该注解（{@code getDeclaredAnnotation}，注解为 {@code RetentionPolicy.RUNTIME}），
 * 只用于「注解值的 modid 未加载则跳过」。这里沿用蓝本写 {@code @WailaPlugin(AstralDiceMod.MODID)}，
 * 语义与蓝本一致，且多一层保护。
 *
 * <p><b>入口点类可反射实例化</b>：Fabric Loader 用无参构造造实例，本类为
 * {@code public 类 + 隐式 public 无参构造}，满足要求。
 *
 * <p><b>软依赖</b>：Jade 缺席时没有任何代码会读 {@code "jade"} 这个入口点键（该键只由 Jade 自己读）
 * ⇒ 本类不被类加载、不被构造，静默跳过、不报错、不影响启动。依赖只写在 {@code build.gradle} 的
 * {@code modImplementation}，**不进** {@code fabric.mod.json} 的 {@code depends}；
 * Jade 相关的两个 mixin 也继续走 {@code required:false}。
 *
 * <p><b>为什么没有服务端数据提供器</b>：与 Forge 蓝本同一条约束——Jade 11.13.3 的
 * {@code IWailaCommonRegistration#registerBlockDataProvider} 只接受
 * {@code Class<? extends BlockEntity>}，且服务端在 {@code BlockAccessor.getBlockEntity() == null}
 * 时直接不发数据包；本模组三个方块都没有 BlockEntity。故客户端直接读**已同步的**玩家冷却值
 * （{@code SHOP_TRANSFER_COOLDOWN_END} / {@code CHIP_SHOP_STATE} 在本线都是 {@code .sync()} 键）。
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
