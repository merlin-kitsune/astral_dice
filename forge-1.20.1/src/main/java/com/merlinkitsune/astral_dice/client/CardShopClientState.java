package com.merlinkitsune.astral_dice.client;

import java.util.List;

/**
 * 卡牌商店「二级转账界面」的客户端态：候选玩家名列表。
 *
 * <p>**刻意不做任何过滤**（用户 2026-10-08 裁决：转账候选 = 全服在线玩家，不筛选队伍）；
 * 服务端在打开转账视图、点击刷新、以及每次转账落定后重发一次
 * （见 {@code network/ModNetwork.CardShopTransferListMessage}）。索引与名单同序，转账时按索引回传。
 *
 * <p>1.20.1 Forge 移植：与 1.21.1 版**逐字相同**（纯静态态，不依赖任何客户端专用类型，
 * 服务端加载本类亦安全）。
 */
public final class CardShopClientState {

    private static volatile List<String> transferCandidates = List.of();

    private CardShopClientState() {}

    public static void setTransferCandidates(List<String> names) {
        transferCandidates = List.copyOf(names);
    }

    public static List<String> transferCandidates() {
        return transferCandidates;
    }

    /** 关闭界面时清空，避免下次打开闪现上一次的名单 */
    public static void clear() {
        transferCandidates = List.of();
    }
}
