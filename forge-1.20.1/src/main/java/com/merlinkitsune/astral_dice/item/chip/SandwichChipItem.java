package com.merlinkitsune.astral_dice.item.chip;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import top.theillusivec4.curios.api.SlotContext;

import java.util.UUID;

/**
 * 夹心饼干筹码(一般/可口/美味):最大生命值 +4/+8/+12(属性修饰器,装备期间生效)。
 */
public class SandwichChipItem extends BaseChipItem {
    /** 夹心饼干-一般最大生命加成 */
    public static final int HEALTH_LOW = 4;
    /** 夹心饼干-可口最大生命加成 */
    public static final int HEALTH_MEDIUM = 8;
    /** 夹心饼干-美味最大生命加成 */
    public static final int HEALTH_HIGH = 12;

    private final int healthBonus;

    public SandwichChipItem(Properties properties, int healthBonus) {
        super(properties);
        this.healthBonus = healthBonus;
    }

    @Override
    public Multimap<Attribute, AttributeModifier> getAttributeModifiers(
            SlotContext slotContext, UUID defaultUUID, ItemStack stack) {
        Multimap<Attribute, AttributeModifier> map = HashMultimap.create();
        map.put(Attributes.MAX_HEALTH,
                new AttributeModifier(attributeModifierId("health"), "sandwich_health", healthBonus,
                        AttributeModifier.Operation.ADDITION));
        return map;
    }
}
