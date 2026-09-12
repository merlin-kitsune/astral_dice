package com.merlinkitsune.astral_dice.item.dice;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import top.theillusivec4.curios.api.SlotContext;

import java.util.UUID;

/**
 * 黑曜石骰子:与钻石骰子同阶(dice_t2),可由钻石骰子升级。
 *
 * 功能:
 * - 基础防御力 +3(按本模组「1 防御力 = 2 护甲值」折算,经 ARMOR 属性修饰器装备期间恒定 +6 护甲);
 * - 受到的火焰伤害减少 70%(由 ObsidianDiceHandler 在伤害事件中处理)。
 */
public class ObsidianDiceItem extends DiceCurioItem {

    /** 基础防御力 +3(1 防御力 = 2 护甲值) */
    public static final int DEFENSE_BONUS = 3;
    /** 折算后的真实护甲加成 */
    public static final int ARMOR_BONUS = DEFENSE_BONUS * 2;
    /** 火焰伤害减免比例(70%) */
    public static final float FIRE_DAMAGE_REDUCTION = 0.7F;

    public ObsidianDiceItem(Properties properties) {
        super(properties);
    }

    @Override
    public Multimap<Attribute, AttributeModifier> getAttributeModifiers(
            SlotContext slotContext, UUID uuid, ItemStack stack) {
        Multimap<Attribute, AttributeModifier> map = HashMultimap.create();
        map.put(Attributes.ARMOR,
                new AttributeModifier(modifierId("armor"), "armor", ARMOR_BONUS,
                        AttributeModifier.Operation.ADDITION));
        return map;
    }

    /** 判定伤害源是否属于火焰伤害(原版 is_fire 标签:火焰/岩浆/炽足/火球等) */
    public static boolean isFireDamage(DamageSource source) {
        return source.is(DamageTypeTags.IS_FIRE);
    }

    private static UUID modifierId(String suffix) {
        ResourceLocation key = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(
                com.merlinkitsune.astral_dice.item.ModItems.OBSIDIAN_DICE.get());
        return UUID.nameUUIDFromBytes(
                (com.merlinkitsune.astral_dice.AstralDiceMod.MODID + ":dice_" + key.getPath() + "_" + suffix)
                        .getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }
}
