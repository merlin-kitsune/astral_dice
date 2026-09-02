package com.merlinkitsune.astral_dice.target;

import com.merlinkitsune.astral_dice.component.GameplayConstants;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;

/**
 * 目标选择器的动作定义：立牌主动技能 / 效果牌等通过注册本接口到
 * {@link TargetSelectionRegistry}，再调用 {@link TargetSelectionManager#start} 进入选择模式；
 * 玩家确认目标后服务端调用 {@link #apply} 对目标施加实际效果。
 *
 * 调用方自行负责前置校验（如立牌冷却 / 效果牌出牌锁）；选择确认成功与否由
 * {@link TargetSelectionManager} 统一回显 ActionBar 反馈。
 */
public interface TargetSelectionAction {

    /** 唯一动作 id（注册表键，如 "haiqing_weak_mark"、"express_delivery"） */
    String id();

    /** 允许指定的目标类型（客户端过滤 + 服务端权威校验共用） */
    TargetType targetType();

    /**
     * 选择半径（格）。默认取配置值 {@link GameplayConstants#TARGET_SELECT_RADIUS}（默认 16，上限 32）；
     * 动作可覆写，但服务端确认校验仍以配置值为准（配置上限 32 不可突破）。
     */
    default double radius() {
        return GameplayConstants.TARGET_SELECT_RADIUS;
    }

    /** 选择会话开始时的服务端钩子（可选：设置等待状态/效果等） */
    default void onStarted(ServerPlayer player) {
    }

    /** 玩家确认目标后，对目标施加效果（仅服务端调用；距离/类型/token 已通过校验） */
    void apply(ServerPlayer player, LivingEntity target);
}
