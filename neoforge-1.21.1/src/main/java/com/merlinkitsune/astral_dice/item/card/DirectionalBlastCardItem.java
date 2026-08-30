package com.merlinkitsune.astral_dice.item.card;

import com.merlinkitsune.astral_dice.effect.ModEffects;
import net.minecraft.core.Holder;
import net.minecraft.world.effect.MobEffect;

/** 定向爆破(效果牌):使用后远程和魔法伤害 +5,并对目标周围 6 格敌对目标造成同样伤害(AOE 在 DamageEffectCardHandler 结算),持续 60 秒 */
public class DirectionalBlastCardItem extends BaseEffectCardItem {
    public DirectionalBlastCardItem(Properties properties) {
        super(properties);
    }

    @Override
    protected Holder<MobEffect> getEffect() {
        return ModEffects.DIRECTIONAL_BLAST;
    }
}
