package com.merlinkitsune.astral_dice.mixin.trade;

import com.merlinkitsune.astral_dice.trade.EmeraldDiceTrade;
import com.merlinkitsune.astral_dice.trade.MerchantOfferRawAccess;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Optional;

/**
 * 在绿宝石骰子交易上下文中,把村民报价的绿宝石费用替换为星币(1:1)。
 * 覆盖报价读取({@code getItemCostA/B})、实际费用({@code getCostA/B})与匹配判断({@code satisfiedBy}),
 * 使原版村民交易全流程(显示/自动放入/匹配/成交)直接使用星币。
 *
 * <p>1.21.1 口径(蓝本 {@code neoforge-1.21.1/src/main/java/com/merlinkitsune/astral_dice/mixin/trade/MerchantOfferMixin.java}):
 * 费用是 {@link ItemCost}(record),原始费用字段为 {@code baseCostA: ItemCost} 与
 * {@code costB: Optional<ItemCost>}。1.20.1 覆写 {@code getBaseCostA} 的那条注入在 1.21.1 **不存在** ——
 * 1.21.1 的 {@code getBaseCostA} 不换价,所以 {@link #astralDice$rawCostA()} 直接取它即可;
 * 组件匹配用 {@code ItemStack.isSameItemSameComponents}(1.21.1 已无 NBT,{@code hasTag()} 已删)。
 */
@Mixin(MerchantOffer.class)
public abstract class MerchantOfferMixin implements MerchantOfferRawAccess {

    // baseCostA / costB 在目标类中是 final;这里配合 @Mutable 声明为非 final,以便在「复制构造」时
    // 把副本的费用替换为星币(只改副本,村民本体报价不受影响)。
    @Shadow @Mutable private ItemCost baseCostA;
    @Shadow @Mutable private Optional<ItemCost> costB;
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

    /**
     * 复制构造(仅由 {@code MerchantOffer#copy()} 调用)完成时,若正处于「向佩戴绿宝石骰子的玩家发送报价」
     * 的交换窗口内,则把副本的两项费用替换为星币(1:1)。
     *
     * <p>为什么必须在数据层改:{@code ClientboundMerchantOffersPacket} 构造时**同步**执行
     * {@code offers.copy()},但真正的网络编码被投递到 Netty 事件循环后才执行(见 1.21.1
     * {@code Connection.sendPacket} 的 {@code eventLoop().execute(...)} 分支),那时交换上下文已经
     * 关闭 —— 仅靠取值覆写({@code getItemCostA} 等)影响不到客户端载荷,客户端会一直显示绿宝石费用。
     * 复制发生在上下文活跃期内,所以在这里把副本字段一次替换到位。
     *
     * <p>判定用 {@link EmeraldDiceTrade#shouldTransformCopy()}(= 上下文活跃 或
     * {@link EmeraldDiceTrade#transformOffers(net.minecraft.world.item.trading.MerchantOffers)}
     * 正在主动取副本),见该方法的注释。
     */
    @Inject(method = "<init>(Lnet/minecraft/world/item/trading/MerchantOffer;)V", at = @At("RETURN"))
    private void astralDice$transformCopy(MerchantOffer source, CallbackInfo ci) {
        if (!EmeraldDiceTrade.shouldTransformCopy()) return;
        if (EmeraldDiceTrade.isEmerald(this.baseCostA)) {
            this.baseCostA = EmeraldDiceTrade.transform(this.baseCostA);
        }
        this.costB = this.costB.map(cost -> EmeraldDiceTrade.isEmerald(cost)
                ? EmeraldDiceTrade.transform(cost) : cost);
    }

    /** 复刻原版 {@code getModifiedCostCount},用于对替换后的星币费用计算需求/乘数/特殊价格。 */
    private int astralDice$modifiedCount(ItemCost cost) {
        int i = cost.count();
        int j = Math.max(0, Mth.floor((float) (i * this.demand) * this.priceMultiplier));
        return Mth.clamp(i + j + this.specialPriceDiff, 1, cost.itemStack().getMaxStackSize());
    }

    /**
     * 软接口实现:把**原始费用 A** 暴露给 {@code trade/MerchantOfferRawAccess} 的调用方。
     *
     * <p>1.21.1 的 {@code getBaseCostA} 未被本类覆写,其字节码就是 {@code this.baseCostA.itemStack()}
     * (javap 已核),因此这里直接读字段 —— mixin 里调用目标类未声明的方法需要额外 {@code @Shadow} 方法,
     * 读同名字段更省事。1.20.1 读字段是为了绕开自身对 getBaseCostA 的覆写,理由不同。
     */
    @Override
    public ItemStack astralDice$rawCostA() {
        return this.baseCostA.itemStack();
    }
}
