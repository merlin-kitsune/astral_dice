package com.merlinkitsune.astral_dice.effect;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

/**
 * 「破防」(龙之咆哮命中时施加在**受击者**身上的负面效果,2026-09-27,规格 §2.7 / 裁决 D3、D8)。
 *
 * <ul>
 *   <li>类别 {@link MobEffectCategory#HARMFUL};时长 1:00({@link #DURATION_TICKS} = 1200 tick);</li>
 *   <li>携带 {@code Attributes.ARMOR} 的 {@link AttributeModifier.Operation#ADDITION} 修饰器
 *       {@code -8.0}:按本仓统一折算口径「1 点防御力 = 2 点护甲值」即**减 4 点防御力**
 *       (与 {@link UnwaveringEffect} 的 {@code +8} 正值版互为镜像);</li>
 *   <li><b>重复命中只刷新时长、不叠层</b>(裁决 D8):施加方 {@code MamushiSignItem#applyRoarDebuff}
 *       恒用 {@code amplifier = 0} ⇒ 下面覆写的取值方法恒返回 -8;原版
 *       {@code LivingEntity#addEffect} → {@code MobEffectInstance#update} 对同放大器实例只取较长时长,
 *       因此"刷新"天然成立、层数不会随命中次数增长。</li>
 * </ul>
 *
 * <h2>1.20.1 平台适配</h2>
 * 1.20.1 的 {@code MobEffect#addAttributeModifier(Attribute, String uuid, double amount, Operation)}
 * 第 2 参**必须是 UUID 字符串**(内部 {@code UUID.fromString},见 1.20.1 实测
 * {@code MobEffect.java:169-173}),不能像 1.21.1 那样传 {@code ResourceLocation} + {@code UnaryOperator}。
 * 此处沿用本线既有约定:取「语义名 {@code astral_dice:dragon_roar_break_armor} 的 UUIDv5」
 * (与 {@code unwavering_armor} / {@code blue_curse_armor} 两处逐字同法,已核对
 * {@code uuid5(NAMESPACE_URL, 'astral_dice:unwavering_armor') == a941d5ed-605a-55c2-834c-4cf3ba28dab0}
 * == UnwaveringEffect 里的字面量)。
 *
 * <p>另外,1.20.1 的修饰器值由可覆写的 {@link #getAttributeModifierValue(int, AttributeModifier)} 决定
 * (1.20.1 实测 {@code MobEffect.java:194-208}:施加时用该方法的返回值新建修饰器),
 * 默认实现是 {@code amount × (amplifier + 1)}。这里**显式固定为 {@link #ARMOR_DELTA}**,
 * 使"不叠层"成为实现上的硬保证(即便未来误用 amplifier &gt; 0 也不会放大减甲)。
 *
 * <p>图标 = {@code images/龙之咆哮.png}(与战斗牌 {@code attack_card_dragon_roar} 同一张图;
 * 实装路径 {@code textures/mob_effect/dragon_roar_break.png},两文件逐字节相同)。
 */
public class DragonRoarBreakEffect extends MobEffect {
    /** 效果时长:1:00 = 1200 tick(规格 §1 效果表 {@code ROAR_DEBUFF_TICKS}) */
    public static final int DURATION_TICKS = 1200;

    /** 护甲修正量:−8 护甲 = −4 点防御力(1 防御力 = 2 护甲值) */
    public static final double ARMOR_DELTA = -8.0;

    /** 修饰器 id(语义名 {@code astral_dice:dragon_roar_break_armor} 的 UUIDv5;两线同值) */
    public static final String ARMOR_MODIFIER_UUID = "188e1666-fb04-5b95-9176-786413e1b491";

    public DragonRoarBreakEffect() {
        super(MobEffectCategory.HARMFUL, 0xB22222);
        this.addAttributeModifier(Attributes.ARMOR, ARMOR_MODIFIER_UUID,
                ARMOR_DELTA, AttributeModifier.Operation.ADDITION);
    }

    /** 固定取值(不随 amplifier 放大):重复命中只刷新时长,层数永远不会让减甲变多 */
    @Override
    public double getAttributeModifierValue(int amplifier, AttributeModifier modifier) {
        return ARMOR_DELTA;
    }
}
