package com.merlinkitsune.astral_dice.item.card;

import com.merlinkitsune.astral_dice.component.ModAttachments;
import com.merlinkitsune.astral_dice.effect.ModEffects;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * 命运的指引(专属功能效果牌,击杀带虚弱印记的目标获取)。
 * 玩家可对自己使用,持续 5:00:
 * 1. 出牌数 +1(临时加成,周期归 0 时清除)
 * 2. 主动技能冷却时间减半(实时功能:立刻将当前最大冷却倒计时减少一半的时间)
 * 3. 对拥有"虚弱印记"的目标额外 +20% 伤害
 * 4. 所有食物提供的饱和度翻倍
 * 5. 神秘遗物+联动:装备七咒之戒时,第一诅咒(受到任何来源伤害加倍)影响 -50%
 * 6. Iron 的法术与魔法书联动:魔力消耗减半
 * 功能全部由 attachment(FATE_ACTIVE_UNTIL)驱动;FATE_GUIDANCE 效果仅作状态显示(5:00 倒计时图标)。
 * 专属牌:仅允许获得者使用;赠与他人的专属牌接收者无法使用。
 */
public class FateGuidanceCardItem extends BaseEffectCardItem {

    public FateGuidanceCardItem(Properties properties) {
        super(properties);
    }
    @Override
    protected String cardTypeId() {
        return "fate_guidance";
    }


    @Override
    protected boolean isExclusive() {
        return true;
    }

    @Override
    protected void applyEffect(Level level, Player user, LivingEntity applyTo, ItemStack stack) {
        // 1. 出牌数 +1(临时,命运的指引效果期间由效果驱动提供)
        // 2. 主动技能冷却时间减半(实时功能:立刻减少当前最大冷却倒计时一半的时间)
        reduceActiveSkillCooldown(user);
        // 3~6. 功能激活 5:00:写入激活截止时刻(attachment 驱动功能),状态效果仅作显示
        ModAttachments.setFateActiveUntil(user, user.level().getGameTime() + 6000);
        user.addEffect(new MobEffectInstance(ModEffects.FATE_GUIDANCE.get(), 6000, 0, false, false, true));
        // 专属牌:无所有者时绑定获得者
        ExclusiveCardUtil.bindIfAbsent(stack, user);
    }

    // 主动技能冷却时间减半(实时功能):冷却中则立刻把最大冷却倒计时剩余一半的时间
    // (剩余时间减半 = 最大冷却时长减半;玩家级冷却,不受立牌装卸影响)
    private static void reduceActiveSkillCooldown(Player player) {
        long cdEnd = ModAttachments.getSignActiveCooldownEnd(player);
        if (cdEnd > 0) {
            long now = player.level().getGameTime();
            long remaining = cdEnd - now;
            ModAttachments.setSignActiveCooldownEnd(player, now + Math.max(0, remaining / 2));
        }
    }
}
