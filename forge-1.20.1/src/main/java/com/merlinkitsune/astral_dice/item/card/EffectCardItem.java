package com.merlinkitsune.astral_dice.item.card;

import com.merlinkitsune.astral_dice.component.GameplayConstants;
import com.merlinkitsune.astral_dice.damage.ModDamageTypes;
import com.merlinkitsune.astral_dice.effect.ModEffects;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * 王之力(功能效果牌):受到 8 点伤害,获得"王之力"效果(攻击力 +5/层,可叠 3 层,持续 3:00)。
 */
public class EffectCardItem extends BaseEffectCardItem {
    public EffectCardItem(Properties properties) {
        super(properties);
    }

    @Override
    protected boolean countsForCopy() {
        return true;
    }

    @Override
    protected String cardTypeId() {
        return "king_power";
    }

    @Override
    protected void applyEffect(Level level, Player user, LivingEntity applyTo, ItemStack stack) {
        // 受到 8 点伤害(骰子伤害类型)
        user.hurt(ModDamageTypes.diceDamage(level, user), 8.0f);

        // 王之力效果:层数叠加(上限 3 层),时长刷新为 3:00
        var existing = user.getEffect(ModEffects.KING_POWER.get());
        int newAmp = existing != null
                ? Math.min(existing.getAmplifier() + 1, GameplayConstants.MAX_EFFECT_STACKS - 1) : 0;
        int newDuration = existing != null ? Math.max(existing.getDuration(), 3600) : 3600;
        user.addEffect(new MobEffectInstance(ModEffects.KING_POWER.get(), newDuration, newAmp, false, false, true));
    }
}
