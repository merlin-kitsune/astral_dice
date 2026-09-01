package com.merlinkitsune.astral_dice.item.chip;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;

/**
 * 贯穿之铳筹码:伤害效果牌生效时,对敌对目标造成的远程/魔法伤害额外增加目标防御力点数的伤害。
 *
 * <p>目标防御力按本模组骰战的基础防御公式计算(不含随机防御骰与防御卡):
 * 玩家与非玩家敌对目标公式同步 = 2 + 护甲/2 + 1.4×韧性(1 防御力 = 2 护甲值)。
 */
public class PiercingGunChipItem extends BaseChipItem {
    public PiercingGunChipItem(Properties properties) {
        super(properties);
    }

    // 计算目标当前基础防御力点数(向下取整;怪物与玩家公式同步)
    public static int getTargetDefense(LivingEntity target) {
        double rawArmor = Math.min(target.getArmorValue(), 20);
        double toughness = target.getAttributeValue(Attributes.ARMOR_TOUGHNESS);
        double dp = 2 + rawArmor / 2.0 + 1.4 * toughness;
        return (int) Math.floor(dp);
    }
}
