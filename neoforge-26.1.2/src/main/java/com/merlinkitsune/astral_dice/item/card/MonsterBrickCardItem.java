package com.merlinkitsune.astral_dice.item.card;

import com.merlinkitsune.astral_dice.effect.ModEffects;
import net.minecraft.core.Holder;
import net.minecraft.world.effect.MobEffect;

/** 对怪板砖(效果牌):使用后远程和魔法伤害 +6,持续 60 秒 */
public class MonsterBrickCardItem extends BaseEffectCardItem {
    public MonsterBrickCardItem(Properties properties) {
        super(properties);
    }
    @Override
    protected String cardTypeId() {
        return "monster_brick";
    }


    @Override
    protected Holder<MobEffect> getEffect() {
        return ModEffects.MONSTER_BRICK;
    }
}
