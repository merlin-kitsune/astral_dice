package com.merlinkitsune.astral_dice.target;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 「立牌主动技能前置门控」的待执行记录（纯内存、瞬态、不落盘）。
 *
 * <p>用途：三个目标选择器类立牌（占星师 / 秘密侦探 / 枪匠）按下主动键时**只**开启目标选择会话，
 * 原流程里「风扇筹码发牌 + 立牌主动响应事件（含默认提示）」这两步**推迟**到确认合法目标之后执行
 * （恢复点见 {@code BaseSignItem#resumeGatedActiveSkill}）。效果与玩家级冷却/电流核心充能仍由
 * 各 {@code TargetSelectionAction#apply} 负责，恢复流程不重复执行。
 *
 * <p>生命周期：确认成功 / 取消 / 超时 / 会话被替换 / 登出 / 死亡 / 测试清理都会清除记录
 * （清理点见 {@link TargetSelectionManager}）；未确认时记录被清除 ⇒ 该次主动等同「未使用」。
 */
public final class SignSelectionGate {
    /** 待执行记录：action id + 触发时立牌物品的快照（用于事件载荷与默认提示的立牌名） */
    public record Pending(String actionId, ItemStack stack) {
    }

    private static final Map<UUID, Pending> PENDING = new ConcurrentHashMap<>();

    private SignSelectionGate() {
    }

    /** 登记待执行记录（同一玩家至多一条：后写覆盖；仅由立牌门控的 performSkill 调用） */
    public static void arm(Player player, String actionId, ItemStack stack) {
        if (player == null || actionId == null) return;
        ItemStack snapshot = (stack == null) ? ItemStack.EMPTY : stack.copy();
        PENDING.put(player.getUUID(), new Pending(actionId, snapshot));
    }

    /** 该玩家是否有待执行记录（诊断/测试用） */
    public static boolean isArmed(Player player) {
        return player != null && PENDING.containsKey(player.getUUID());
    }

    /**
     * 取走并清除待执行记录（原子：确认路径最多执行一次）。
     *
     * @return 与 actionId 匹配的记录；无记录或 actionId 不匹配（陈旧/跨会话）时返回 null，
     *         且不匹配的记录会被一并丢弃 —— 避免跨会话误触发
     */
    public static Pending take(Player player, String actionId) {
        if (player == null) return null;
        Pending pending = PENDING.remove(player.getUUID());
        if (pending == null) return null;
        if (actionId != null && actionId.equals(pending.actionId())) return pending;
        return null;
    }

    /** 清除待执行记录（取消 / 超时 / 会话被替换 / 登出 / 死亡 / 测试清理；无记录时为空操作） */
    public static void clear(Player player) {
        if (player != null) PENDING.remove(player.getUUID());
    }
}
