package com.merlinkitsune.astral_dice.item.chip;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import top.theillusivec4.curios.api.SlotContext;

/**
 * 夹心饼干筹码(一般/可口/美味):最大生命值 +4/+8/+8(属性修饰器,装备期间生效)。
 * 夹心饼干-美味额外被动:最大生命值超过 20 点时,超出部分每 4 点生命值 +1 攻击力
 * (经 DiceCombatModifiers 攻击修饰器计入,见 {@link #getAttackBonus})。
 */
public class SandwichChipItem extends BaseChipItem {
    /** 夹心饼干-一般最大生命加成 */
    public static final int HEALTH_LOW = 4;
    /** 夹心饼干-可口最大生命加成 */
    public static final int HEALTH_MEDIUM = 8;
    /** 夹心饼干-美味最大生命加成 */
    public static final int HEALTH_HIGH = 8;
    /** 夹心饼干-美味攻击力触发门槛(最大生命值,单位:点) */
    public static final int ATTACK_HP_THRESHOLD = 20;
    /** 夹心饼干-美味:超出门槛部分每多少点生命值 +1 攻击力 */
    public static final int ATTACK_HP_PER_POINT = 4;

    private final int healthBonus;

    public SandwichChipItem(Properties properties, int healthBonus) {
        super(properties);
        this.healthBonus = healthBonus;
    }

    @Override
    public Multimap<Attribute, AttributeModifier> getAttributeModifiers(
            SlotContext slotContext, java.util.UUID id, ItemStack stack) {
        Multimap<Attribute, AttributeModifier> map = HashMultimap.create();
        map.put(Attributes.MAX_HEALTH,
                new AttributeModifier(attributeModifierId("health"), "health", healthBonus,
                        AttributeModifier.Operation.ADDITION));
        return map;
    }

    // 夹心饼干-美味:当前最大生命值带来的攻击加成(超出 20 点的部分,每 4 点 +1;不满足门槛为 0)
    public static int getAttackBonus(Player player) {
        if (player == null) return 0;
        int maxHp = (int) Math.floor(player.getMaxHealth());
        if (maxHp <= ATTACK_HP_THRESHOLD) return 0;
        return (maxHp - ATTACK_HP_THRESHOLD) / ATTACK_HP_PER_POINT;
    }
}
