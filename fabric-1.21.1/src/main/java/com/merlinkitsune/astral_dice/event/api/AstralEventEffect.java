package com.merlinkitsune.astral_dice.event.api;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;

/**
 * 事件效果。
 *
 * <p>2026-10-10 用户裁决:本轮<b>全部做空壳</b> —— 16 张图标对应的 13 条事件一律用 {@link #NOOP},
 * 具体功能由用户后续逐个补全。效果按<b>单个受影响目标</b>调用(作用域由分发器算好:64 格 ∪ 团队全体)。
 */
@FunctionalInterface
public interface AstralEventEffect {

    /** <b>空壳效果</b>:什么都不做(本轮所有正式事件都用它)。 */
    AstralEventEffect NOOP = (level, triggerer, target, event, source, tier) -> {
    };

    /**
     * 把事件效果施加到一个目标上。
     *
     * @param level     服务端世界
     * @param triggerer 触发者
     * @param target    受影响目标(作用域内某一名玩家)
     * @param event     事件注册项
     * @param source    触发来源
     * @param tier      当前阶级(0 = 无阶级概念的事件)
     */
    void apply(ServerLevel level, Player triggerer, Player target, AstralEvent event,
               AstralEventTrigger source, int tier);
}
