package com.merlinkitsune.astral_dice.mixin.trade;

import com.merlinkitsune.astral_dice.trade.EmeraldDiceTrade;
import net.minecraft.world.inventory.MerchantContainer;
import net.minecraft.world.item.trading.Merchant;
import net.minecraft.world.item.trading.MerchantOffers;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 在报价匹配(updateSellItem)时开启绿宝石骰子交易上下文。 */
@Mixin(MerchantContainer.class)
public abstract class MerchantContainerMixin {

    @Shadow @Final private Merchant merchant;

    @Inject(method = "updateSellItem", at = @At("HEAD"))
    private void astralDice$beginSwap(CallbackInfo ci) {
        if (this.merchant != null) {
            EmeraldDiceTrade.begin(this.merchant.getTradingPlayer());
        }
    }

    @Inject(method = "updateSellItem", at = @At("RETURN"))
    private void astralDice$endSwap(CallbackInfo ci) {
        EmeraldDiceTrade.end();
    }

    /**
     * 报价匹配也要看到**过滤后**的列表：`updateSellItem` 用 `merchant.getOffers()` 调
     * `getRecipeFor(槽位A, 槽位B, selectionHint)`，而 selectionHint 是客户端菜单里的索引 ——
     * 列表顺序一旦与服务端不同，就会「放对了材料却匹配到另一条报价」。
     *
     * <p>平台差异(对照 1.21.1)：1.21.1 的 @Redirect 描述符写的是
     * `Lnet/minecraft/world/item/trading/Merchant;getOffers()Lnet/minecraft/world/item/trading/MerchantOffers;`，
     * 1.20.1 的 Merchant 接口在 `net.minecraft.world.item.trading` 包
     * （Merchant.java:12 `public interface Merchant`，:18 `MerchantOffers getOffers();`），
     * 故描述符必须写成 `Lnet/minecraft/world/item/trading/Merchant;...`。
     * 另已取证 `MerchantContainer.updateSellItem` 内只有这一处 getOffers 调用（MerchantContainer.java:119），
     * 不会多重匹配。
     */
    @Redirect(method = "updateSellItem", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/item/trading/Merchant;getOffers()Lnet/minecraft/world/item/trading/MerchantOffers;"))
    private MerchantOffers astralDice$filterEmeraldExchange(Merchant merchant) {
        MerchantOffers offers = merchant.getOffers();
        MerchantOffers filtered = EmeraldDiceTrade.filteredFor(merchant.getTradingPlayer(), offers);
        return filtered != null ? filtered : offers;
    }
}
