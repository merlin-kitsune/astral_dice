package com.merlinkitsune.astral_dice.item.card;

import com.merlinkitsune.astral_dice.effect.ModEffects;
import net.minecraft.core.Holder;
import net.minecraft.world.effect.MobEffect;

/** 轨道炮(效果牌):使用后远程和魔法伤害 +8,持续 60 秒 */
public class OrbitalStrikeCardItem extends BaseEffectCardItem {
    public OrbitalStrikeCardItem(Properties properties) {
        super(properties);
    }

    @Override
    protected Holder<MobEffect> getEffect() {
        return ModEffects.ORBITAL_STRIKE;
    }
}
