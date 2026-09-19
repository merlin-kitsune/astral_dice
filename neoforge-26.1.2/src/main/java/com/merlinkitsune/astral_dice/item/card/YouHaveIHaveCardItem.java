package com.merlinkitsune.astral_dice.item.card;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * 你有我有(效果牌):**目标选择器类(手持即选择)** —— 主手手持本牌即自动进入目标选择模式(移出手持立即退出),瞄准**其他玩家**后左键确认
 * (被选中集合排除自己 ⇒ 鼠标右键只会提示「该技能无法对自己使用」),此类会话**没有倒计时**,取消/移出手持不消耗卡牌
 * (2026-09-25 用户裁决,取代旧的"只能点击玩家实体"释放方式)。
 * 生效后:使自身以及目标玩家各获得一张随机卡牌(战斗牌 + 效果牌,不含专属)。
 *
 * <p><b>26.1.2 移植说明</b>：本批删除了旧线自写的 {@code use()}（返回 {@code FAIL}）与
 * {@code interactLivingEntity()}（只放玩家通过）两个覆写 —— 1.21.1 最终态里这两个覆写已整段删除，
 * 语义改由「选择器动作 + {@code allowSelf=false}」表达（鼠标右键只会提示无法对自身使用，
 * 对实体右键走 {@code useViaSelector} 兜底而不会直接生效）。{@code applyEffect} 里的
 * 「目标必须是玩家且不是自己」兜底**保留**（与 1.21.1 逐字一致）。
 */
public class YouHaveIHaveCardItem extends BaseEffectCardItem {

    /** 目标选择器动作 id(skill 名与动作注册键共用) */
    public static final String ACTION_ID = "you_have_i_have";

    static {
        // 仅能对其他玩家使用(不可对自身)
        registerSelectorAction(ACTION_ID, false);
    }

    public YouHaveIHaveCardItem(Properties properties) {
        super(properties);
    }
    @Override
    protected String cardTypeId() {
        return "you_have_i_have";
    }

    @Override
    public String selectorActionId() {
        return ACTION_ID;
    }

    @Override
    protected boolean isHealingCard() {
        // 不属治疗类效果牌(不触发大当家被动)
        return false;
    }

    @Override
    protected void applyEffect(Level level, Player user, LivingEntity applyTo, ItemStack stack) {
        if (!(applyTo instanceof Player target)) return;
        if (target == user) return;
        // 自身以及目标玩家各获得一张随机卡牌
        RandomCardHandler.giveCardTo(user, RandomCardHandler.CardCategory.ALL);
        RandomCardHandler.giveCardTo(target, RandomCardHandler.CardCategory.ALL);
    }
}
