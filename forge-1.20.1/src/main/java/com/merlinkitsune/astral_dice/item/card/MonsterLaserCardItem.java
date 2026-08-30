package com.merlinkitsune.astral_dice.item.card;

import com.merlinkitsune.astral_dice.effect.ModEffects;
import net.minecraft.world.effect.MobEffect;

/** 对怪激光(效果牌):使用后远程和魔法伤害 +4,持续 60 秒 */
public class MonsterLaserCardItem extends BaseEffectCardItem {
    public MonsterLaserCardItem(Properties properties) {
        super(properties);
    }
    @Override
    protected String cardTypeId() {
        return "monster_laser";
    }


    @Override
    protected MobEffect getEffect() {
        return ModEffects.MONSTER_LASER.get();
    }
}
