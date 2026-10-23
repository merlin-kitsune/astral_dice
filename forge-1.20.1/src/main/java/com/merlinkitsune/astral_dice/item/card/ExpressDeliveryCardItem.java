package com.merlinkitsune.astral_dice.item.card;

import com.merlinkitsune.starengine.target.TargetType;

import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import com.merlinkitsune.astral_dice.event.PlayerEffectProtection;

/**
 * 加急加快(效果牌):**目标选择器类(手持即选择)** —— 主手手持本牌即自动进入目标选择模式(移出手持立即退出),瞄准非敌对生物后左键确认,
 * 或按下鼠标右键对自身使用;此类会话**没有倒计时**,取消/移出手持不消耗卡牌(2026-09-25 用户裁决,取代旧的
 * 「右键自身 / 下蹲右键对其他玩家」两段式)。
 * 生效后:使目标获得 迅捷 II 1:00。
 */
public class ExpressDeliveryCardItem extends BaseEffectCardItem {
    /** 迅捷效果时长(tick) */
    public static final int DURATION_TICKS = 1200;

    /** 目标选择器动作 id(skill 名与动作注册键共用) */
    public static final String ACTION_ID = "express_delivery";

    /**
     * 锁定范围（格）：「加急加快」的初始射程（2026-10-08 用户裁决）。
     *
     * <p>该值同时决定客户端准星的射线长度、半径高亮圆与服务端确认允许的最大距离（距离一律
     * 按三维计算，含垂直高度差）。声明值上限 32 格；玩家侧加成（探天卫星筹码 / 书页射程）经
     * {@code target/SelectorRangeModifiers} 统一生效。半径契约见 AGENTS.md 第 15 条。
     */
    public static final double LOCK_RANGE = 16.0D;

    static {
        // 目标 = 非敌对生物(敌对生物不可选) ∪ 自身;允许对自身使用(旧方案的「右键-自身使用」)
        registerSelectorAction(ACTION_ID, TargetType.NON_HOSTILE, true, LOCK_RANGE);
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
        PlayerEffectProtection.apply(applyTo, new MobEffectInstance(MobEffects.MOVEMENT_SPEED, DURATION_TICKS, 1, false, true));
    }
}
