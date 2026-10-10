package com.merlinkitsune.astral_dice.client;

import com.merlinkitsune.astral_dice.network.CardShopTransferListPayload;

import java.util.List;

/**
 * 卡牌商店「二级转账界面」的客户端态：候选玩家名单（UUID + 玩家名 + 星币余额）。
 *
 * <p>**刻意不做任何过滤**（用户 2026-10-08 裁决：转账候选 = 全服在线玩家，不筛选队伍）；
 * 服务端在**打开转账视图**、**点击放大镜刷新**、以及**目标玩家已下线（名单失效）**时重发
 * （见 {@code network/CardShopTransferListPayload}）。⚠️ **转账成功后不会重发** —— 此时界面已退回商店主界面，
 * 且本店会话已锁定转账（{@code CardShopMenu#isTransferUsedThisSession()}）。
 *
 * <p><b>下标口径</b>：{@link #transferCandidates()} 的**完整名单**下标就是转账载荷的目标下标 ——
 * 界面上的搜索过滤只发生在 {@code CardShopScreen} 内部（过滤后行号 → 完整名单下标 的映射），
 * **绝不下发过滤后行号**。
 */
public final class CardShopClientState {

    private static volatile List<CardShopTransferListPayload.Entry> transferCandidates = List.of();

    /** 拒绝信号闪烁总时长（tick）：30t = 5t 一相 × 6 相 = 红白交替 **3 次**（2026-10-09 §4 用户裁决） */
    public static final int DENY_BLINK_TICKS = 30;
    /** 拒绝信号剩余 tick（0 = 不闪）；由 {@code CardShopDenyPayload} 置位、屏幕每 tick 递减 */
    private static volatile int denyTicks = 0;
    /** 拒绝原因（{@code CardShopMenu.DENY_NO_COIN} / {@code DENY_INVENTORY_FULL}；仅给提示文案用） */
    private static volatile int denyReason = 0;

    private CardShopClientState() {}

    /** 服务端下发「请求被拒」时调用：起 30t 倒计时（每 5t 交替一次红/白 = 共 3 次闪红） */
    public static void notifyDeny(int reason) {
        denyReason = reason;
        denyTicks = DENY_BLINK_TICKS;
    }

    /** 屏幕每 tick 调用一次；返回剩余 tick（0 = 已结束） */
    public static int tickDeny() {
        if (denyTicks > 0) {
            denyTicks--;
        }
        return denyTicks;
    }

    /** 当前相位是否画成**红色**：t∈[26,30] / [16,20] / [6,10] 三个红相（其余相为白/常规色） */
    public static boolean denyBlinkRed() {
        return denyTicks > 0 && ((denyTicks - 1) / 5) % 2 == 1;
    }

    /** 剩余闪烁 tick（0 = 不闪） */
    public static int denyTicks() {
        return denyTicks;
    }

    /** 拒绝原因（0 = 余额不足、1 = 物品栏已满） */
    public static int denyReason() {
        return denyReason;
    }

    public static void setTransferCandidates(List<CardShopTransferListPayload.Entry> entries) {
        transferCandidates = List.copyOf(entries);
    }

    /** 完整候选名单（只读；下标 = 转账载荷的目标下标） */
    public static List<CardShopTransferListPayload.Entry> transferCandidates() {
        return transferCandidates;
    }

    /** 关闭界面时清空，避免下次打开闪现上一次的名单 / 残留闪烁 */
    public static void clear() {
        transferCandidates = List.of();
        denyTicks = 0;
        denyReason = 0;
    }
}
