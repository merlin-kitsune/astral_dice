package com.merlinkitsune.astral_dice.item.dice;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import top.theillusivec4.curios.api.SlotContext;
import com.merlinkitsune.astral_dice.AstralDiceMod;

/**
 * 黑曜石骰子:与钻石骰子同阶(dice_t2),可由钻石骰子升级。
 *
 * 功能:
 * - 基础防御力 +3(按本模组「1 防御力 = 2 护甲值」折算,经 ARMOR 属性修饰器装备期间恒定 +6 护甲);
 * - 受到的爆炸伤害减少 50%(由 ObsidianDiceHandler 在伤害事件中处理)。
 */
public class ObsidianDiceItem extends DiceCurioItem {

    /** 基础防御力 +3(1 防御力 = 2 护甲值) */
    public static final int DEFENSE_BONUS = 3;
    /** 折算后的真实护甲加成 */
    public static final int ARMOR_BONUS = DEFENSE_BONUS * 2;
    /** 爆炸伤害减免比例(50%) */
    public static final float EXPLOSION_DAMAGE_REDUCTION = 0.5F;

    public ObsidianDiceItem(Properties properties) {
        super(properties);
    }

    @Override
    public Multimap<Holder<Attribute>, AttributeModifier> getAttributeModifiers(
            SlotContext slotContext, Identifier id, ItemStack stack) {
        Multimap<Holder<Attribute>, AttributeModifier> map = HashMultimap.create();
        map.put(Attributes.ARMOR,
                new AttributeModifier(modifierId("armor"), ARMOR_BONUS,
                        AttributeModifier.Operation.ADD_VALUE));
        return map;
    }

    /** 判定伤害源是否属于爆炸伤害(原版 is_explosion 标签:TNT/苦力怕/末影水晶/火球爆炸等) */
    public static boolean isExplosionDamage(DamageSource source) {
        return source.is(DamageTypeTags.IS_EXPLOSION);
    }

    private static Identifier modifierId(String suffix) {
        Identifier key = BuiltInRegistries.ITEM.getKey(
                com.merlinkitsune.astral_dice.item.ModItems.OBSIDIAN_DICE.get());
        return Identifier.fromNamespaceAndPath(AstralDiceMod.MODID,
                "dice_" + key.getPath() + "_" + suffix);
    }
}
