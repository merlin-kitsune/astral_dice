package com.merlinkitsune.astral_dice.item.chip;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;

/**
 * 贯穿之铳筹码:伤害效果牌生效时,对敌对目标造成的远程/魔法伤害额外增加目标防御力点数的伤害。
 *
 * <p>目标防御力按本模组骰战的基础防御公式计算(与骰战 defensePower 前三项逐字同构、与骰战界面显示口径
 * 完全一致;不含随机防御骰与防御卡)。公式自 2026-09-26 起**下沉至库**
 * {@code com.merlinkitsune.starenginelib.combat.CombatFormula},玩家与生物各自一套:
 * <ul>
 *   <li>玩家:{@code 4 + 护甲×0.30 + 0.85×韧性};</li>
 *   <li>生物:{@code 初始值(敌对 0 / 中立 0 / 被动 0 / 友好 0) + 护甲×0.40 + 1.0×韧性}。</li>
 * </ul>
 * 结果向下取整;护甲 20 硬上限与此前「怪物与玩家公式同步」的口径已于 2026-09-26 一并作废
 * (1 防御力 = 2 护甲值不变)。
 */
public class PiercingGunChipItem extends BaseChipItem {
    public PiercingGunChipItem(Properties properties) {
        super(properties);
    }

    // 计算目标当前基础防御力点数(玩家/生物各按自身公式,结果向下取整;与骰战结算同源)
    public static int getTargetDefense(LivingEntity target) {
        double armor = target.getArmorValue();
        double toughness = target.getAttributeValue(Attributes.ARMOR_TOUGHNESS);
        if (target instanceof Player) {
            return (int) Math.floor(
                    com.merlinkitsune.starenginelib.combat.CombatFormula.playerDefense(armor, toughness));
        }
        return com.merlinkitsune.starenginelib.combat.CombatFormula.mobDefenseInt(
                com.merlinkitsune.starenginelib.combat.TargetCategory.classify(target), armor, toughness);
    }
}
