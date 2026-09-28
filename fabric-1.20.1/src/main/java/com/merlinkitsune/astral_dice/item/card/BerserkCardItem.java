package com.merlinkitsune.astral_dice.item.card;

import com.merlinkitsune.starenginelib.component.GameplayConstants;
import com.merlinkitsune.astral_dice.effect.ModEffects;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * 狂暴(功能效果牌):获得"狂暴"效果(攻击力 +3/层,受到任意伤害 +1/层,可叠 3 层,持续 3:00)。
 * **目标选择器类(手持即选择)** —— 主手手持本牌即自动进入目标选择模式(移出手持立即退出),
 * 瞄准**玩家**后左键确认,或按下鼠标右键对自身使用;**此类会话没有倒计时**,取消/移出手持不消耗卡牌。⚠️ 2026-09-25 用户裁决:目标类型由「任意生物」收窄为**玩家**,不再能对其他生物使用。
 */
public class BerserkCardItem extends BaseEffectCardItem {
    /** 目标选择器动作 id(skill 名与动作注册键共用) */
    public static final String ACTION_ID = "berserk";

    static {
        // 可对自身使用(旧方案的「右键-自身使用」)
        registerSelectorAction(ACTION_ID, true);
    }

    public BerserkCardItem(Properties properties) {
        super(properties);
    }

    @Override
    protected String cardTypeId() {
        return "berserk";
    }

    @Override
    public String selectorActionId() {
        return ACTION_ID;
    }

    @Override
    protected void applyEffect(Level level, Player user, LivingEntity applyTo, ItemStack stack) {
        var existing = applyTo.getEffect(ModEffects.BERSERK.get());
        int newAmp = existing != null
                ? Math.min(existing.getAmplifier() + 1, GameplayConstants.MAX_EFFECT_STACKS - 1) : 0;
        int newDuration = existing != null ? Math.max(existing.getDuration(), 3600) : 3600;
        applyTo.addEffect(new MobEffectInstance(ModEffects.BERSERK.get(), newDuration, newAmp, false, false, true));
    }
}
