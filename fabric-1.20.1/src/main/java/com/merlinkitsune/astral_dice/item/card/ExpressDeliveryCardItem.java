package com.merlinkitsune.astral_dice.item.card;

import com.merlinkitsune.astral_dice.event.EffectTimerGuard;

import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * 加急加快(效果牌):**目标选择器类(手持即选择)** —— 主手手持本牌即自动进入目标选择模式(移出手持立即退出),瞄准玩家后左键确认,
 * 或按下鼠标右键对自身使用;此类会话**没有倒计时**,取消/移出手持不消耗卡牌(2026-09-25 用户裁决,取代旧的
 * 「右键自身 / 下蹲右键对其他玩家」两段式)。
 * 生效后:使目标获得 迅捷 II 1:00。
 */
public class ExpressDeliveryCardItem extends BaseEffectCardItem {
    /** 迅捷效果时长(tick) */
    public static final int DURATION_TICKS = 1200;

    /** 目标选择器动作 id(skill 名与动作注册键共用) */
    public static final String ACTION_ID = "express_delivery";

    static {
        // 可对自身使用(旧方案的「右键-自身使用」)
        registerSelectorAction(ACTION_ID, true);
    }

    public ExpressDeliveryCardItem(Properties properties) {
        super(properties);
    }
    @Override
    protected String cardTypeId() {
        return "express_delivery";
    }

    @Override
    public String selectorActionId() {
        return ACTION_ID;
    }

    @Override
    protected void applyEffect(Level level, Player user, LivingEntity applyTo, ItemStack stack) {
        EffectTimerGuard.apply(applyTo, new MobEffectInstance(MobEffects.MOVEMENT_SPEED, DURATION_TICKS, 1, false, true));
    }
}
