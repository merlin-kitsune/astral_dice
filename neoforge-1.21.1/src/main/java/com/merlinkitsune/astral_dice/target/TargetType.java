package com.merlinkitsune.astral_dice.target;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;

/**
 * 目标选择器的可指定目标类型。
 * 每次选择会话由 {@link TargetSelectionAction#targetType()} 决定允许的目标种类，
 * 客户端用于过滤准星目标（避免错选），服务端用于确认时二次校验（权威判定）。
 */
public enum TargetType {
    /** 仅玩家（排除选择者自身） */
    PLAYER {
        @Override
        public boolean matches(Player selector, LivingEntity target) {
            return target instanceof Player && target != selector;
        }
    },
    /** 仅敌对生物（vanilla {@link Enemy} 标记接口：僵尸/骷髅/掠夺者等） */
    ENEMY {
        @Override
        public boolean matches(Player selector, LivingEntity target) {
            return target instanceof Enemy;
        }
    },
    /** 任意活体目标（玩家/敌对/中立/被动，排除选择者自身） */
    LIVING {
        @Override
        public boolean matches(Player selector, LivingEntity target) {
            return target != selector;
        }
    };

    public abstract boolean matches(Player selector, LivingEntity target);
}
