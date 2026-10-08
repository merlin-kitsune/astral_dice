package com.merlinkitsune.astral_dice.mixin.trade;

import com.merlinkitsune.astral_dice.trade.EmeraldDiceTrade;
import net.minecraft.network.protocol.game.ClientboundMerchantOffersPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.trading.MerchantOffers;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 向客户端发送村民报价时的两个按玩家改动：
 * <ol>
 *   <li><b>换费用</b>：把「费用已是星币」的报价副本交给数据包，使客户端显示星币费用。</li>
 *   <li><b>过滤</b>：佩戴绿宝石骰子时，把「绿宝石 → 星币」兑换条目从**发给该玩家**的列表里剔除
 *       （否则该玩家看到的是「用星币买星币」，能凭空刷币；判据见
 *       {@link EmeraldDiceTrade#filteredFor(net.minecraft.world.entity.player.Player, MerchantOffers)}）。</li>
 * </ol>
 *
 * <p>1.20.1 的数据包直接持有本体报价列表(不复制)，而网络编码发生在 Netty 事件循环上 ——
 * 那时交换上下文已关闭，只靠 {@code MerchantOfferMixin} 的取值覆写无法影响载荷。
 * 因此这里直接构造并发送替换过费用的数据包(本体报价不变)，再取消原版发送。
 *
 * <p>平台差异(对照 1.21.1)：1.21.1 的过滤是独立的一条
 * {@code @ModifyVariable(method = "sendMerchantOffers", at = HEAD, argsOnly = true, index = 2)}，
 * 与换费用各走各的(1.21.1 那边换费用不由本 mixin 做)。1.20.1 这边换费用也落在同一个 HEAD 注入里，
 * 两者在同一次注入体里**按顺序**执行（先过滤、再把过滤后的列表换费用），从而不必依赖同一注入点上
 * 两个处理器的先后顺序 —— 语义与 1.21.1 完全一致：发出的是「已剔除兑换条目 + 其余条目星币价」的列表。
 */
@Mixin(ServerPlayer.class)
public abstract class ServerPlayerMixin {

    @Inject(method = "sendMerchantOffers", at = @At("HEAD"), cancellable = true)
    private void astralDice$sendTransformedOffers(int containerId, MerchantOffers offers, int level, int xp,
                                                  boolean showProgress, boolean canRestock, CallbackInfo ci) {
        ServerPlayer self = (ServerPlayer) (Object) this;
        if (!EmeraldDiceTrade.hasEmeraldDice(self)) return;
        MerchantOffers filtered = EmeraldDiceTrade.filteredFor(self, offers);
        MerchantOffers base = filtered != null ? filtered : offers;
        MerchantOffers transformed = EmeraldDiceTrade.transformOffers(base);
        MerchantOffers payload = transformed != null ? transformed : base;
        // 既没过滤也没换费用 —— 交还原版流程(零行为差异)
        if (payload == offers) return;
        self.connection.send(new ClientboundMerchantOffersPacket(
                containerId, payload, level, xp, showProgress, canRestock));
        ci.cancel();
    }
}
