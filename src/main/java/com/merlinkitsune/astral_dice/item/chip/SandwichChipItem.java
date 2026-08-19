package com.merlinkitsune.astral_dice.item.chip;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import top.theillusivec4.curios.api.SlotContext;

/**
 * 夹心饼干筹码(一般/可口/美味):最大生命值 +2/+4/+8(属性修饰器,装备期间生效)。
 */
public class SandwichChipItem extends BaseChipItem {
    /** 夹心饼干-一般最大生命加成 */
    public static final int HEALTH_LOW = 2;
    /** 夹心饼干-可口最大生命加成 */
    public static final int HEALTH_MEDIUM = 4;
    /** 夹心饼干-美味最大生命加成 */
    public static final int HEALTH_HIGH = 8;

    private final int healthBonus;

    public SandwichChipItem(Properties properties, int healthBonus) {
        super(properties);
        this.healthBonus = healthBonus;
    }

    @Override
    public Multimap<Holder<Attribute>, AttributeModifier> getAttributeModifiers(
            SlotContext slotContext, ResourceLocation id, ItemStack stack) {
        Multimap<Holder<Attribute>, AttributeModifier> map = HashMultimap.create();
        map.put(Attributes.MAX_HEALTH,
                new AttributeModifier(attributeModifierId("health"), healthBonus,
                        AttributeModifier.Operation.ADD_VALUE));
        return map;
    }
}
