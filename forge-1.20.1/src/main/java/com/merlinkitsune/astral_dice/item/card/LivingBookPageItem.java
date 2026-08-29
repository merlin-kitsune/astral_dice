package com.merlinkitsune.astral_dice.item.card;

import com.merlinkitsune.astral_dice.component.GameplayConstants;
import com.merlinkitsune.astral_dice.component.ModAttachments;
import com.merlinkitsune.astral_dice.effect.ModEffects;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import com.merlinkitsune.astral_dice.combat.SpellDamageRegistry;

/**
 * 活体书页(专属效果牌):调查员(rin)立牌专属。
 * 使用:调查员已使用数量 +1(上限),进入"活体书页"效果 60 秒:
 * 效果期间对所有敌对目标造成的远程/魔法伤害增加(基础 2 + 调查员已使用数量)点,并施加 1 层标记
 * (法伤加成结算在 SpellDamageRegistry)。
 */
public class LivingBookPageItem extends BaseEffectCardItem {
    public LivingBookPageItem(Properties properties) {
        super(properties);
    }

    @Override
    protected boolean isExclusive() {
        return true;
    }

    @Override
    protected void applyEffect(Level level, Player user, LivingEntity applyTo, ItemStack stack) {
        // 调查员(rin)已使用数量 +1(上限);活体书页效果期间提供临时出牌数 +1(效果驱动)
        int pages = ModAttachments.getRinPages(user);
        ModAttachments.setRinPages(user,
                Math.min(pages + 1, GameplayConstants.LIVING_BOOK_PAGE_BONUS_CAP));
        // 获得活体书页效果 60 秒
        user.addEffect(new MobEffectInstance(ModEffects.LIVING_BOOK_PAGE.get(), 1200, 0, false, true));
    }
}
