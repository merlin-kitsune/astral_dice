package com.merlinkitsune.astral_dice.effect;

import com.merlinkitsune.astral_dice.AstralDiceMod;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

/**
 * 破防(蛟龙立牌 mamushi 专属战斗牌「龙之咆哮」的命中减益):
 * 效果期间护甲值 {@code -8}(ADD_VALUE;按"1 防御 = 2 护甲"折算 ⇒ **减 4 点防御**)。
 *
 * <p>写法 = 「岿然不动」({@link UnwaveringEffect})的**正值版取负**:同一条
 * {@code MobEffect#addAttributeModifier(Attributes.ARMOR, id, ADD_VALUE, curve)} 重载,
 * curve 入参为 amplifier(本效果恒为 0,D8:重复命中只刷新时长、不叠层 ⇒ 恒 {@code -8.0})。
 * 直接改真实护甲值 ⇒ 原版伤害与骰战防御力同时正确生效,不存在双重计算。
 *
 * <p>时长常量 {@code ROAR_DEBUFF_TICKS}(1200 = 1:00)定义在施加方
 * {@code item/sign/MamushiSignItem}(规格 §1 常量表),本类只承载属性修饰器。
 */
public class DragonRoarBreakEffect extends MobEffect {
    /**
     * 护甲变化量:1 防御 = 2 护甲 ⇒ {@code -8} 护甲 = 减 4 点防御(规格 §1 / D3)。
     * 施加方 {@code MamushiSignItem#ROAR_ARMOR_DELTA} 直接引用本常量,保证单一真值。
     */
    public static final double ARMOR_DELTA = -8.0;

    public DragonRoarBreakEffect() {
        super(MobEffectCategory.HARMFUL, 0x8B0000);
        this.addAttributeModifier(Attributes.ARMOR,
                ResourceLocation.fromNamespaceAndPath(AstralDiceMod.MODID, "dragon_roar_break_armor"),
                AttributeModifier.Operation.ADD_VALUE,
                amp -> ARMOR_DELTA);
    }
}
