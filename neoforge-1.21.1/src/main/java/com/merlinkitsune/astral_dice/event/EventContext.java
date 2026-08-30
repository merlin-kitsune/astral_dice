package com.merlinkitsune.astral_dice.event;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

import java.util.List;

/**
 * 事件上下文:记录触发者与本次事件受影响的全部目标(自身/范围玩家/团队/女仆)。
 */
public class EventContext {
    private final Player triggerer;
    private final List<LivingEntity> targets;

    public EventContext(Player triggerer, List<LivingEntity> targets) {
        this.triggerer = triggerer;
        this.targets = targets;
    }

    public Player triggerer() {
        return triggerer;
    }

    public Player player() {
        return triggerer;
    }

    public List<LivingEntity> targets() {
        return targets;
    }
}
