package com.merlinkitsune.astral_dice.item.sign;

import com.merlinkitsune.starengine.component.GameplayConstants;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

/**
 * 立牌主动技能的 actionbar 反馈**唯一发送入口**(自 {@link BaseSignItem} 原样抽出)。
 *
 * <p>三条发送口径(默认黄 / 显式色 / 带立牌名前缀)的文案键、颜色、时长与抽出前逐字一致:
 * 时长统一 {@link #DURATION_TICKS},默认色 {@link #DEFAULT_COLOR}(黄),拒绝/阻止类由调用方显式传
 * {@link #DENY_COLOR}(红)。
 */
public final class SignActionBar {
    private SignActionBar() {
    }

    /** actionbar 停留时长(tick),与抽出前一致 = {@code GameplayConstants.ACTIONBAR_DURATION_TICKS} */
    public static final int DURATION_TICKS = GameplayConstants.ACTIONBAR_DURATION_TICKS;

    /** 默认色(常规/成功类提示):黄 */
    public static final ChatFormatting DEFAULT_COLOR = ChatFormatting.YELLOW;

    /** 拒绝/阻止类提示色:红(2026-09-27 用户裁决) */
    public static final ChatFormatting DENY_COLOR = ChatFormatting.RED;

    /** 立牌主动技能反馈统一发送入口(黄色;供立牌类注册的 SignActiveTriggeredEvent 处理器与 handleUse 调用) */
    public static void sendSignActionBar(Player player, String langKey, Object... args) {
        if (!(player instanceof ServerPlayer serverPlayer)) return;
        Component msg = Component.translatable(langKey, args).withStyle(DEFAULT_COLOR);
        net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(serverPlayer,
                new com.merlinkitsune.astral_dice.network.ActionBarPayload(msg, DURATION_TICKS));
    }

    /**
     * 发一条 actionbar 提示（**显式颜色**）。
     *
     * <p>2026-09-27 用户裁决：**拒绝 / 阻止类**提示必须为**红色**。若文案自带 {@code §e…§7}，收尾的
     * {@code §7} 会把后半句重置成灰色 ⇒ 出现「前半黄、后半灰」的**断层**（实测：机械师立牌「手牌不足」提示）。
     * ⇒ 带色版本 + 文案**不带任何 {@code §} 码**，整条由外层颜色统一控制。
     */
    public static void sendSignActionBarColored(Player player, ChatFormatting color, String langKey, Object... args) {
        if (!(player instanceof ServerPlayer serverPlayer)) return;
        Component msg = Component.translatable(langKey, args).withStyle(color);
        net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(serverPlayer,
                new com.merlinkitsune.astral_dice.network.ActionBarPayload(msg, DURATION_TICKS));
    }

    /**
     * 服务端发送立牌技能反馈(actionbar 提示,带立牌名称前缀;统一由服务端判定成功/拒绝,避免客户端推测混淆)。
     */
    public static void notifyActionBar(Player player, String langKey, Component signName, ChatFormatting color) {
        if (!(player instanceof ServerPlayer serverPlayer)) return;
        Component msg = Component.translatable(langKey, signName).withStyle(color);
        net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(serverPlayer,
                new com.merlinkitsune.astral_dice.network.ActionBarPayload(msg, DURATION_TICKS));
    }
}
