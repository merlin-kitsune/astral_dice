package com.merlinkitsune.astral_dice.mixin.trade;

import com.merlinkitsune.astral_dice.trade.EmeraldDiceTrade;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Optional;

/**
 * 在绿宝石骰子交易上下文中,把村民报价的绿宝石费用替换为星币并打 20% 折扣。
 * 覆盖报价读取({@code getItemCostA/B})、实际费用({@code getCostA/B})与匹配判断({@code satisfiedBy}),
 * 使原版村民交易全流程(显示/自动放入/匹配/成交)直接使用星币。
 */
@Mixin(MerchantOffer.class)
public abstract class MerchantOfferMixin {

    @Shadow @Final private ItemCost baseCostA;
    @Shadow @Final private Optional<ItemCost> costB;
    @Shadow private int demand;
    @Shadow @Final private float priceMultiplier;
    @Shadow private int specialPriceDiff;

    @Inject(method = "getItemCostA", at = @At("HEAD"), cancellable = true)
    private void astralDice$getItemCostA(CallbackInfoReturnable<ItemCost> cir) {
        if (!EmeraldDiceTrade.isSwapActive()) return;
        cir.setReturnValue(EmeraldDiceTrade.transform(this.baseCostA));
    }

    @Inject(method = "getItemCostB", at = @At("HEAD"), cancellable = true)
    private void astralDice$getItemCostB(CallbackInfoReturnable<Optional<ItemCost>> cir) {
        if (!EmeraldDiceTrade.isSwapActive()) return;
        cir.setReturnValue(this.costB.map(EmeraldDiceTrade::transform));
    }

    @Inject(method = "getCostA", at = @At("HEAD"), cancellable = true)
    private void astralDice$getCostA(CallbackInfoReturnable<ItemStack> cir) {
        if (!EmeraldDiceTrade.isSwapActive()) return;
        ItemCost transformed = EmeraldDiceTrade.transform(this.baseCostA);
        cir.setReturnValue(transformed.itemStack().copyWithCount(astralDice$modifiedCount(transformed)));
    }

    @Inject(method = "getCostB", at = @At("HEAD"), cancellable = true)
    private void astralDice$getCostB(CallbackInfoReturnable<ItemStack> cir) {
        if (!EmeraldDiceTrade.isSwapActive()) return;
        this.costB.ifPresent(c -> {
            if (EmeraldDiceTrade.isEmerald(c)) {
                cir.setReturnValue(EmeraldDiceTrade.transform(c).itemStack().copy());
            }
        });
    }

    @Inject(method = "satisfiedBy", at = @At("HEAD"), cancellable = true)
    private void astralDice$satisfiedBy(ItemStack a, ItemStack b, CallbackInfoReturnable<Boolean> cir) {
        if (!EmeraldDiceTrade.isSwapActive()) return;
        ItemCost transformedA = EmeraldDiceTrade.transform(this.baseCostA);
        ItemStack costA = transformedA.itemStack().copyWithCount(astralDice$modifiedCount(transformedA));
        if (!ItemStack.isSameItemSameComponents(a, costA) || a.getCount() < costA.getCount()) {
            cir.setReturnValue(false);
            return;
        }
        if (this.costB.isPresent()) {
            ItemCost cb = this.costB.get();
            if (EmeraldDiceTrade.isEmerald(cb)) {
                ItemStack costBStack = EmeraldDiceTrade.transform(cb).itemStack();
                if (!ItemStack.isSameItemSameComponents(b, costBStack) || b.getCount() < costBStack.getCount()) {
                    cir.setReturnValue(false);
                    return;
                }
            } else if (!cb.test(b) || b.getCount() < cb.count()) {
                cir.setReturnValue(false);
                return;
            }
        } else if (!b.isEmpty()) {
            cir.setReturnValue(false);
            return;
        }
        cir.setReturnValue(true);
    }

    /** 复刻原版 {@code getModifiedCostCount},用于对替换后的星币费用计算需求/乘数/特殊价格。 */
    private int astralDice$modifiedCount(ItemCost cost) {
        int i = cost.count();
        int j = Math.max(0, Mth.floor((float) (i * this.demand) * this.priceMultiplier));
        return Mth.clamp(i + j + this.specialPriceDiff, 1, cost.itemStack().getMaxStackSize());
    }
}
