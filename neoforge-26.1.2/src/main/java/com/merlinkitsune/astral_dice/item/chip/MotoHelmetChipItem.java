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
 * 摩托头盔筹码(一般/中级/高级):**防御力** +2/+4/+6;盔甲韧性 +2 仅高级拥有(属性修饰器,装备期间生效)。
 *
 * <p><b>防御力口径</b>:本 mod 文案一律使用「防御力」,由代码层按 {@link #ARMOR_PER_DEFENSE}
 * (1 防御力 = 2 护甲值)折算为原版护甲值(`Attributes.ARMOR`)后落到属性修饰器上,
 * 与肾上腺素/磨刀石等防御力加成保持同一口径——描述中的数字始终是防御力,不是护甲值。
 */
public class MotoHelmetChipItem extends BaseChipItem {
    /** 防御力 → 护甲值 换算比例(1 防御力 = 2 护甲值) */
    public static final double ARMOR_PER_DEFENSE = 2.0;
    /** 摩托头盔-一般 防御力加成 */
    public static final int DEFENSE_LOW = 2;
    /** 摩托头盔-中级 防御力加成 */
    public static final int DEFENSE_MEDIUM = 4;
    /** 摩托头盔-高级 防御力加成 */
    public static final int DEFENSE_HIGH = 6;
    /** 摩托头盔-高级盔甲韧性加成(一般/中级无韧性加成) */
    public static final int TOUGHNESS_BONUS = 2;

    private final int defenseBonus;
    private final int toughnessBonus;

    public MotoHelmetChipItem(Properties properties, int defenseBonus, int toughnessBonus) {
        super(properties);
        this.defenseBonus = defenseBonus;
        this.toughnessBonus = toughnessBonus;
    }

    @Override
    public Multimap<Holder<Attribute>, AttributeModifier> getAttributeModifiers(
            SlotContext slotContext, ResourceLocation id, ItemStack stack) {
        Multimap<Holder<Attribute>, AttributeModifier> map = HashMultimap.create();
        // 防御力按 1:2 折算为真实护甲值(仅在代码层换算)
        map.put(Attributes.ARMOR,
                new AttributeModifier(attributeModifierId("armor"), defenseBonus * ARMOR_PER_DEFENSE,
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
