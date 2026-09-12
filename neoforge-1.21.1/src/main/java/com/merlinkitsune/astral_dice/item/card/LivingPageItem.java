package com.merlinkitsune.astral_dice.item.card;

import com.merlinkitsune.astral_dice.component.ModAttachments;
import com.merlinkitsune.astral_dice.effect.ModEffects;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * 活体书页(专属效果牌):调查员(rin)立牌专属。
 * 使用:调查员已使用数量 +1(无上限),进入"活体书页"效果 60 秒:
 * 效果期间对所有敌对目标造成的远程/魔法伤害增加(基础 2 + 调查员已使用数量)点,并施加 1 层标记
 * (法伤加成结算在 SpellDamageRegistry)。
 */
public class LivingPageItem extends BaseEffectCardItem {
    public LivingPageItem(Properties properties) {
        super(properties);
    }
    @Override
    protected String cardTypeId() {
        return "living_page";
    }


    @Override
    protected boolean isExclusive() {
        return true;
    }

    @Override
    protected void applyEffect(Level level, Player user, LivingEntity applyTo, ItemStack stack) {
        // 专属牌:绑定获得者(发放路径已绑定本人;指令/创造栏等未绑定副本在此兜底,首位使用者即获得者)
        ExclusiveCardUtil.bindIfAbsent(stack, user);
        // 调查员(rin)已使用数量 +1(无上限);活体书页效果期间提供临时出牌数 +1(效果驱动)
        ModAttachments.setRinPages(user, ModAttachments.getRinPages(user) + 1);
        // 获得活体书页效果 60 秒
        user.addEffect(new MobEffectInstance(ModEffects.LIVING_PAGE, 1200, 0, false, true));
    }
}
