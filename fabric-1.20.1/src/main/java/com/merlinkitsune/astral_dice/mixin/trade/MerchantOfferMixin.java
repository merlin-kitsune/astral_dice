package com.merlinkitsune.astral_dice.mixin.trade;

import com.merlinkitsune.astral_dice.trade.EmeraldDiceTrade;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.MerchantOffer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 1.20.1:在绿宝石骰子交易上下文中,把村民报价的绿宝石费用替换为星币并打 20% 折扣。
 * 1.20.1 的 MerchantOffer 费用为 ItemStack(无 ItemCost 记录),故直接覆写 getBaseCostA/getCostA/getCostB/satisfiedBy。
 */
@Mixin(MerchantOffer.class)
public abstract class MerchantOfferMixin {

    @Shadow @Final private ItemStack baseCostA;
    @Shadow @Final private ItemStack costB;
    @Shadow private int demand;
    @Shadow private float priceMultiplier;
    @Shadow private int specialPriceDiff;

    @Inject(method = "getBaseCostA", at = @At("HEAD"), cancellable = true)
    private void astralDice$getBaseCostA(CallbackInfoReturnable<ItemStack> cir) {
        if (!EmeraldDiceTrade.isSwapActive()) return;
        cir.setReturnValue(EmeraldDiceTrade.transform(this.baseCostA));
    }

    @Inject(method = "getCostA", at = @At("HEAD"), cancellable = true)
    private void astralDice$getCostA(CallbackInfoReturnable<ItemStack> cir) {
        if (!EmeraldDiceTrade.isSwapActive()) return;
        cir.setReturnValue(astralDice$transformedCostA());
    }

    @Inject(method = "getCostB", at = @At("HEAD"), cancellable = true)
    private void astralDice$getCostB(CallbackInfoReturnable<ItemStack> cir) {
        if (!EmeraldDiceTrade.isSwapActive()) return;
        cir.setReturnValue(EmeraldDiceTrade.transform(this.costB));
    }

    @Inject(method = "satisfiedBy", at = @At("HEAD"), cancellable = true)
    private void astralDice$satisfiedBy(ItemStack a, ItemStack b, CallbackInfoReturnable<Boolean> cir) {
        if (!EmeraldDiceTrade.isSwapActive()) return;
        ItemStack costA = astralDice$transformedCostA();
        if (!astralDice$isRequiredItem(a, costA) || a.getCount() < costA.getCount()) {
            cir.setReturnValue(false);
            return;
        }
        ItemStack transformedB = EmeraldDiceTrade.transform(this.costB);
        if (!astralDice$isRequiredItem(b, transformedB) || b.getCount() < transformedB.getCount()) {
            cir.setReturnValue(false);
            return;
        }
        cir.setReturnValue(true);
    }

    /** 复刻原版 getCostA:对替换后的星币费用计算需求/价格乘数/特殊价格。 */
    private ItemStack astralDice$transformedCostA() {
        ItemStack base = EmeraldDiceTrade.transform(this.baseCostA);
        if (base.isEmpty()) return ItemStack.EMPTY;
        int i = base.getCount();
        int j = Math.max(0, Mth.floor((float) (i * this.demand) * this.priceMultiplier));
        return base.copyWithCount(Mth.clamp(i + j + this.specialPriceDiff, 1, base.getMaxStackSize()));
    }

    /** 复刻原版 isRequiredItem(含可损坏物品与 NBT 比较)。 */
    private static boolean astralDice$isRequiredItem(ItemStack offer, ItemStack cost) {
        if (cost.isEmpty() && offer.isEmpty()) {
            return true;
        }
        ItemStack itemstack = offer.copy();
        if (itemstack.getItem().isDamageable(itemstack)) {
            itemstack.setDamageValue(itemstack.getDamageValue());
        }
        return ItemStack.isSameItem(itemstack, cost)
                && (!cost.hasTag() || itemstack.hasTag() && NbtUtils.compareNbt(cost.getTag(), itemstack.getTag(), false));
    }
}
