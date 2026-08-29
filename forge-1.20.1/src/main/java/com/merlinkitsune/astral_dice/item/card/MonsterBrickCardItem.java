package com.merlinkitsune.astral_dice.item.card;

import com.merlinkitsune.astral_dice.effect.ModEffects;
import net.minecraft.world.effect.MobEffect;

/** 对怪板砖(效果牌):使用后远程和魔法伤害 +6,持续 60 秒 */
public class MonsterBrickCardItem extends BaseEffectCardItem {
    public MonsterBrickCardItem(Properties properties) {
        super(properties);
    }

    @Override
    protected MobEffect getEffect() {
        return ModEffects.MONSTER_BRICK.get();
    }
}
