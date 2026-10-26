package com.merlinkitsune.astral_dice.shop;

import com.merlinkitsune.astral_dice.AstralDiceMod;
import com.merlinkitsune.astral_dice.component.ModAttachments;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.event.TickEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 卡牌商店「沙漏归 0 ⇒ 换牌」的服务端 tick 驱动（任务O §1.3，2026-10-09 用户裁决）。
 *
 * <p><b>唯一不变量</b>：卡牌只在计时器（沙漏）刚好归 0 的那一刻更换；开界面 / 关界面 / 进出商店
 * 都**不得**重抽货架。因此重抽不能挂在菜单生命周期上（菜单随界面关闭而销毁），必须由服务端每 tick
 * 遍历在线玩家、按附件 shop_transfer_cooldown_end 到点即重抽并落盘（{@link CardShopShelf}）。
 *
 * <p><b>玩家不在店内也照刷</b>：循环遍历所有在线玩家，与「菜单是否打开」无关 —— 因为归 0 那一刻就是
 * 唯一时机；玩家关着界面错过整个周期时，下次上线后的第一个 tick 会补上（{@code end > 0 && now >= end}）。
 *
 * <p><b>例外（任务U′，2026-10-10 用户裁决）</b>：商店大酬宾事件、以及「装备经商立牌 / 处于大酬宾窗口时
 * 开店」都**不再换批**，只把货架补足到应有格数（{@code CardShopMenu#triggerPromotion} /
 * {@code CardShopMenu#topUpShelf}）—— 且补差额不动计时器。因此卡牌真正重排（含立牌取下 / 窗口过期后的
 * 缩架）仍然只发生在「沙漏归 0」这一刻，即本类负责的那一次重抽。
 *
 * <p><b>任务V §1.2（2026-10-10 用户裁决）</b>：本类除了「到点重抽」，还负责**界面已开着时的补差复检** ——
 * 每 {@value #TOPUP_CHECK_INTERVAL_TICKS} tick 对正持有 {@link CardShopMenu} 的玩家调一次
 * {@code CardShopMenu#topUpIfNeeded}（幂等，无缺口不写盘）。这样「界面开着时自己或队友装备 / 卸下经商立牌、
 * 或大酬宾窗口到来」都能在半秒内补齐，且与开店 / 大酬宾共用同一条补差判定。
 */
@Mod.EventBusSubscriber(modid = AstralDiceMod.MODID)
public final class CardShopCycleTicker {

    private static final Logger LOGGER = LoggerFactory.getLogger(CardShopCycleTicker.class);

    /**
     * 界面开着时复检「牌缺口」的节流间隔（tick）：10 tick = 0.5 秒。
     * <p>选 0.5 秒而不是每 tick：补差要对每个持菜单的玩家查一次 Curios（团队立牌）—— 0.5 秒足够「立即」，
     * 又不会每 tick 空转。真实换牌的时机与这个节流无关（换牌只在「沙漏归 0」那一刻，见 {@link #refreshIfDue}）。
     */
    public static final int TOPUP_CHECK_INTERVAL_TICKS = 10;

    private CardShopCycleTicker() {
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        // 平台差异：1.20.1 的 ServerTickEvent 分 START/END 两相，只在 END 相处理（对应 1.21.1 的 ServerTickEvent.Post）
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        MinecraftServer server = event.getServer();
        if (server == null) {
            return;
        }
        // 任务V §1.2：界面**开着**时也立即补差额牌 —— 与开店 / 大酬宾共用 CardShopMenu#topUpIfNeeded
        // （内部只有一种「缺口」判定，幂等）。队友在别处装备 / 卸下经商立牌、大酬宾窗口到来，
        // 界面开着时都能在 ≤0.5 秒内补上；卸下立牌 / 窗口过期不缩架（无缺口 ⇒ 不落盘、不动视图）。
        boolean checkTopUp = server.getTickCount() % TOPUP_CHECK_INTERVAL_TICKS == 0;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            refreshIfDue(player);
            if (checkTopUp && player.containerMenu instanceof CardShopMenu menu) {
                menu.topUpIfNeeded("tick");
            }
        }
    }

    /**
     * 若该玩家的本轮 60 秒周期已到点（附件终点 &gt; 0 且 gameTime 已越过），则**重抽货架 + 落盘 + 把
     * 周期归 0**（转账/兑换解锁、沙漏回到就绪态）；若此刻正开着商店界面，同步把菜单视图切到新货架。
     *
     * @return 本次是否发生了「到点换牌」
     */
    public static boolean refreshIfDue(ServerPlayer player) {
        long now = player.level().getGameTime();
        long end = ModAttachments.getShopTransferCooldownEnd(player);
        if (end <= 0L || now < end) {
            return false;
        }
        CardShopShelf shelf = CardShopShelf.roll(player);
        CardShopShelf.store(player, shelf);
        ModAttachments.setShopTransferCooldownEnd(player, 0L);
        // 到点换架 ⇒ 本轮转账额度一并恢复（2026-10-26 用户裁决：转账锁与换架周期同起同落）
        ModAttachments.setShopTransferUsed(player, false);
        boolean menuOpen = false;
        if (player.containerMenu instanceof CardShopMenu menu) {
            menuOpen = true;
            menu.reloadShelf();
        }
        // 机器可读的实机证据（服务端日志与客户端同一份 logs/latest.log）：到点换牌的精确时刻与新货架。
        // menu=1/0 = 到点那一刻该玩家是否正开着商店界面 —— §1.3 要证「玩家不在店内时到点也刷」⇒ 必须能读到 menu=0。
        LOGGER.info("[astral_dice] CARD_SHOP_CYCLE: reroll player={} tick={} end={} menu={} shelf={} prices={}",
                player.getName().getString(), now, end, menuOpen ? 1 : 0, shelf.items(), shelf.prices());
        return true;
    }
}
