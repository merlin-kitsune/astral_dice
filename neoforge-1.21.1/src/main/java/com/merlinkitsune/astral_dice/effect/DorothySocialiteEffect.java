package com.merlinkitsune.astral_dice.effect;

import com.merlinkitsune.astral_dice.AstralDiceMod;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

/**
 * 交际花(多萝西立牌 dorothy 主动给队友的攻击力增益):攻击力 +1,固定 1:00。
 *
 * <p>2026-10-25 用户裁决:与本立牌主动窗口**解耦** —— 窗口结束不回收,只按自身计时到期;
 * 重复获得只把时长刷回满(同一 amplifier,不叠层)。加成的唯一事实源 = 本效果自带的
 * ATTACK_DAMAGE 修饰符(替掉原先队友身上的瞬态修饰符 dorothy_partner_attack)。
 */
public class DorothySocialiteEffect extends MobEffect {
    /** 固定时长(1:00),与主动窗口无关。 */
    public static final int DURATION_TICKS = 1200;
    /** 每份「交际花」提供的攻击力(固定 +1,不随 amplifier 放大)。 */
    public static final double ATTACK_BONUS = 1.0D;

    public DorothySocialiteEffect() {
        super(MobEffectCategory.BENEFICIAL, 0xFF7FA8);
        this.addAttributeModifier(Attributes.ATTACK_DAMAGE,
                ResourceLocation.fromNamespaceAndPath(AstralDiceMod.MODID, "dorothy_socialite_attack"),
                AttributeModifier.Operation.ADD_VALUE,
                amp -> ATTACK_BONUS);
    }
}
