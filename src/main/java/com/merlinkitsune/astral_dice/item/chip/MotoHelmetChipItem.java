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
 * 摩托头盔筹码(一般/中级/高级):护甲值 +2/+4/+8;盔甲韧性 +2 仅高级拥有(属性修饰器,装备期间生效)。
 */
public class MotoHelmetChipItem extends BaseChipItem {
    /** 摩托头盔-一般护甲加成 */
    public static final int ARMOR_LOW = 2;
    /** 摩托头盔-中级护甲加成 */
    public static final int ARMOR_MEDIUM = 4;
    /** 摩托头盔-高级护甲加成 */
    public static final int ARMOR_HIGH = 8;
    /** 摩托头盔-高级盔甲韧性加成(一般/中级无韧性加成) */
    public static final int TOUGHNESS_BONUS = 2;

    private final int armorBonus;
    private final int toughnessBonus;

    public MotoHelmetChipItem(Properties properties, int armorBonus, int toughnessBonus) {
        super(properties);
        this.armorBonus = armorBonus;
        this.toughnessBonus = toughnessBonus;
    }

    @Override
    public Multimap<Holder<Attribute>, AttributeModifier> getAttributeModifiers(
            SlotContext slotContext, ResourceLocation id, ItemStack stack) {
        Multimap<Holder<Attribute>, AttributeModifier> map = HashMultimap.create();
        map.put(Attributes.ARMOR,
                new AttributeModifier(attributeModifierId("armor"), armorBonus,
                        AttributeModifier.Operation.ADD_VALUE));
        // 盔甲韧性仅高级拥有(一般/中级 toughnessBonus = 0,不添加修饰器)
        if (toughnessBonus > 0) {
            map.put(Attributes.ARMOR_TOUGHNESS,
                    new AttributeModifier(attributeModifierId("toughness"), toughnessBonus,
                            AttributeModifier.Operation.ADD_VALUE));
        }
        return map;
    }
}
