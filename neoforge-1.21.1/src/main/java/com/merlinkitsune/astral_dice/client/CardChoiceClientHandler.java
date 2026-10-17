package com.merlinkitsune.astral_dice.client;

import com.merlinkitsune.astral_dice.screen.CardChoiceScreen;
import net.minecraft.client.Minecraft;

/**
 * 「选卡界面」的客户端侧入口（dist 隔离用；口径 = {@code client/EventChoiceClientHandler}）。
 *
 * <p>本类只在客户端加载：{@code network/CardChoiceClosePayload} 是双端类，它的方法体里
 * 不得直接引用 {@code Minecraft} / {@code CardChoiceScreen}，否则专服在符号解析阶段即崩。
 */
public final class CardChoiceClientHandler {

    private CardChoiceClientHandler() {
    }

    /** 当前界面是本 token 的选卡界面才关闭（token 不匹配 = no-op）。 */
    public static void closeIfToken(int token) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen instanceof CardChoiceScreen screen && screen.token() == token) {
            minecraft.setScreen(null);
        }
    }
}
