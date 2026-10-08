package com.merlinkitsune.astral_dice.client;

import com.merlinkitsune.astral_dice.network.ModNetwork;

import java.util.List;

/**
 * 卡牌商店「二级转账界面」的客户端态：候选玩家名单（UUID + 玩家名 + 星币余额）。
 *
 * <p><b>下标口径</b>：本名单的下标 = 转账载荷（{@link ModNetwork.CardShopTransferMessage}）要发
 * 的**目标下标**——界面上的搜索过滤只发生在 {@code screen/CardShopScreen} 内部（过滤行号 → 完整名单下标
 * 由该屏的 {@code transferFiltered} 映射），这里保存的**永远是完整名单**。
 *
 * <p>**刻意不做任何过滤**（用户 2026-10-08 裁决：转账候选 = 全服在线玩家，不筛选队伍）；
 * 服务端在打开转账视图、点击刷新、以及每次转账落定后重发一次
 * （见 {@link ModNetwork.CardShopTransferListMessage}）。索引与名单同序，转账时按索引回传。
 *
 * <p><b>1.20.1 Forge 移植</b>：与 1.21.1 版逐条对等，仅元素类型由
 * {@code network/CardShopTransferListPayload.Entry} 换成
 * {@link ModNetwork.CardShopTransferListMessage.Entry}（1.20.1 无独立载荷类，消息类内嵌在
 * {@link ModNetwork} 中且沿用既有注册 id）。纯静态态，不依赖任何客户端专用类型，服务端加载本类亦安全。
 */
public final class CardShopClientState {

    private static volatile List<ModNetwork.CardShopTransferListMessage.Entry> transferCandidates = List.of();

    private CardShopClientState() {}

    public static void setTransferCandidates(List<ModNetwork.CardShopTransferListMessage.Entry> entries) {
        transferCandidates = List.copyOf(entries);
    }

    public static List<ModNetwork.CardShopTransferListMessage.Entry> transferCandidates() {
        return transferCandidates;
    }

    /** 关闭界面时清空，避免下次打开闪现上一次的名单 */
    public static void clear() {
        transferCandidates = List.of();
    }
}
