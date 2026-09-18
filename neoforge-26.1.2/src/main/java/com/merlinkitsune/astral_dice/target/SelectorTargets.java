package com.merlinkitsune.astral_dice.target;

import com.merlinkitsune.astral_dice.combat.HostileTargets;
import com.merlinkitsune.starenginelib.target.TargetType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/**
 * 目标选择器的**可选中判定**（客户端射线 / 客户端半径内高亮 / 服务端确认三处共用）。
 *
 * <h2>为什么需要它（2026-09-18 用户实测缺陷）</h2>
 * 前置库 {@link TargetType} 的 {@code matches} 对「敌对」族（{@code ENEMY} / {@code ENEMY_OR_RIVAL}）
 * 只做裸的 {@code instanceof Enemy} 判定，而本模组的全局口径（见 {@link HostileTargets} 类注释，
 * 2026-09-14 用户裁决）是 {@code 敌对目标 = 敌对生物 ∪ 已被激怒的中立生物}：
 * 全原版 {@code NeutralMob} 的直接实现者里，<b>狼 / 铁傀儡 / 北极熊 / 蜜蜂</b> 四个只能靠 anger 判定
 * 进入敌对集合。⇒ 被激怒的铁傀儡在选择器里被判「不可选 / 对准错误目标」，与主线版本口径不符。
 *
 * <p>本类把「敌对」族并到唯一入口 {@link HostileTargets#isHostile(net.minecraft.world.entity.Entity)}
 * 上，其余类型仍走库的基础语义。
 *
 * <h2>口径（逐条）</h2>
 * <ul>
 *   <li>{@link TargetType#ENEMY} → {@code HostileTargets.isHostile(target)}
 *       （敌对生物 ∪ 已被激怒的中立生物；**不含玩家**，与库的「仅敌对生物」一致）；</li>
 *   <li>{@link TargetType#ENEMY_OR_RIVAL} → {@code HostileTargets.isHostile(target)}
 *       ∪ 库 {@code matches} 的「非队友玩家」分支；</li>
 *   <li>{@link TargetType#PLAYER} / {@link TargetType#LIVING} → 原样交给库的 {@code matches}。</li>
 * </ul>
 *
 * <p><b>禁止</b>在选择器代码里再直接调用 {@code targetType.matches(...)} 或写裸的
 * {@code instanceof Enemy} —— 那会漏掉被激怒的中立生物；新增判定一律调用本类。
 *
 * <p><b>26.1.2 移植说明</b>：本文件与 {@code neoforge-1.21.1} 基准**逐字同形**（无任何平台 API
 * 触点）：{@code HostileTargets.isHostile(Entity)} 与 {@code TargetType#matches} 在 26.1.2 均已存在
 * （见 {@code combat/HostileTargets.java:39-43} 与前置库 common 源集）。
 */
public final class SelectorTargets {
    private SelectorTargets() {
    }

    /**
     * 目标是否属于本次会话的可选集合。
     *
     * @param type     会话的目标类型（前置库枚举）
     * @param selector 选择者（{@link TargetType#PLAYER} / {@link TargetType#ENEMY_OR_RIVAL} 的玩家分支需要）
     * @param target   待判定的活体目标
     */
    public static boolean matches(TargetType type, Player selector, LivingEntity target) {
        if (type == null || target == null) return false;
        if (type == TargetType.ENEMY) {
            return HostileTargets.isHostile(target);
        }
        if (type == TargetType.ENEMY_OR_RIVAL) {
            return HostileTargets.isHostile(target) || type.matches(selector, target);
        }
        return type.matches(selector, target);
    }
}
