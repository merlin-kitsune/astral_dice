package com.merlinkitsune.astral_dice.shop;

import com.merlinkitsune.astral_dice.AstralDiceMod;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 筹码商店「刷新次数恢复」的服务端 tick 驱动（判据正本 = {@code docs/chip-shop-logic-plan.md} §3/§6，用户裁决 2）。
 *
 * <p><b>语义</b>：刷新次数不是「开店才恢复」——真值是玩家附件 {@link ChipShopState#regenAnchor()}，
 * 每 {@link ChipShopCatalog#REFRESH_REGEN_TICKS} tick（30:00）+1、上限
 * {@link ChipShopCatalog#REFRESH_MAX}；**玩家不在店内也照涨**（与卡牌商店沙漏口径一致），
 * 离线期间由世界 gameTime 自然回补（上线后逐 tick 补到上限）。
 *
 * <p><b>锚点为 0 = 从未刷新过 ⇒ 不恢复</b>：锚点只在**第一次成功刷新**时被写成 {@code now + 36000}
 * （见 {@link ChipShopMenu#handleRefresh}）——若把 0 当成「已到期」，新玩家一进游戏就会白得次数。
 *
 * <p><b>到上限后锚点停止顺延</b>（计划表 §6 原文）：恢复到上限即不再推进锚点，因此在上限时消耗一次刷新
 * 会在下一次 tick 立刻补回（这是既定口径，不是缺陷）——本类不做「防白嫖」的额外推断。
 * <p><b>平台差异（forge-1.20.1）</b>：1.21.1 蓝本是 {@code ServerTickEvent.Post} + {@code @EventBusSubscriber}；
 * 本线的服务器 tick 是 {@code TickEvent.ServerTickEvent}（start/end 两相位）⇒ 覆写签名换掉并**只在
 * {@code TickEvent.Phase.END} 记账**，订阅者注解写 {@code @Mod.EventBusSubscriber} —— 两处都照抄本线既有
 * {@code shop/CardShopCycleTicker}（同一平台差异、同一守卫写法，逐字对齐）。
 */
@Mod.EventBusSubscriber(modid = AstralDiceMod.MODID)
public final class ChipShopRegenTicker {

    private static final Logger LOGGER = LoggerFactory.getLogger(ChipShopRegenTicker.class);

    private ChipShopRegenTicker() {
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        MinecraftServer server = event.getServer();
        if (server == null) {
            return;
        }
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            ChipShopState state = ChipShopState.get(player);
            long anchor = state.regenAnchor();
            if (anchor <= 0L || state.refreshesLeft() >= ChipShopCatalog.REFRESH_MAX) {
                continue;
            }
            long now = player.level().getGameTime();
            if (now < anchor) {
                continue;
            }
            ChipShopState next = state.withRefreshes(state.refreshesLeft() + 1,
                    anchor + ChipShopCatalog.REFRESH_REGEN_TICKS);
            ChipShopState.store(player, next);
            LOGGER.info("[astral_dice] CHIP_SHOP_REGEN player={} refreshes={}/{} now={} anchor={}",
                    player.getGameProfile().getName(), next.refreshesLeft(), ChipShopCatalog.REFRESH_MAX, now,
                    next.regenAnchor());
        }
    }
}
