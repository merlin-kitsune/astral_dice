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
 * 速度轮滑筹码(初级/中级/高级):移动速度 +5%/+15%/+25%(属性修饰器,装备期间生效)。
 */
public class SpeedSkatesChipItem extends BaseChipItem {
    /** 速度轮滑-初级移动速度加成 */
    public static final double SPEED_LOW = 0.05;
    /** 速度轮滑-中级移动速度加成 */
    public static final double SPEED_MEDIUM = 0.15;
    /** 速度轮滑-高级移动速度加成 */
    public static final double SPEED_HIGH = 0.25;

    private final double speedBonus;

    public SpeedSkatesChipItem(Properties properties, double speedBonus) {
        super(properties);
        this.speedBonus = speedBonus;
    }

    @Override
    public Multimap<Holder<Attribute>, AttributeModifier> getAttributeModifiers(
            SlotContext slotContext, ResourceLocation id, ItemStack stack) {
        Multimap<Holder<Attribute>, AttributeModifier> map = HashMultimap.create();
        map.put(Attributes.MOVEMENT_SPEED,
                new AttributeModifier(attributeModifierId("speed"), speedBonus,
                        AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        return map;
    }
}
