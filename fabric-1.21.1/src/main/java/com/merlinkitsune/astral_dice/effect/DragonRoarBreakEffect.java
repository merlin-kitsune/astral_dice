package com.merlinkitsune.astral_dice.effect;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

/**
 * 「破防」(龙之咆哮命中时施加在**受击者**身上的负面效果,2026-09-27,规格 §2.7 / 裁决 D3、D8)。
 *
 * <ul>
 *   <li>类别 {@link MobEffectCategory#HARMFUL};时长 1:00({@link #DURATION_TICKS} = 1200 tick);</li>
 *   <li>携带 {@code Attributes.ARMOR} 的 {@link AttributeModifier.Operation#ADD_VALUE} 修饰器
 *       {@code -8.0}:按本仓统一折算口径「1 点防御力 = 2 点护甲值」即**减 4 点防御力**
 *       (与 {@link UnwaveringEffect} 的 {@code +8} 正值版互为镜像);</li>
 *   <li><b>重复命中只刷新时长、不叠层</b>(裁决 D8):施加方 {@code MamushiSignItem#applyRoarDebuff}
 *       恒用 {@code amplifier = 0} ⇒ 修饰器展开后恒为 {@link #ARMOR_DELTA}(-8);原版
 *       {@code LivingEntity#addEffect} → {@code MobEffectInstance#update} 对同放大器实例只取较长时长,
 *       因此"刷新"天然成立、层数不会随命中次数增长。</li>
 * </ul>
 *
 * <h2>1.21.1 平台适配</h2>
 * 1.21.1 的 {@code MobEffect#addAttributeModifier(Holder<Attribute>, ResourceLocation, double, Operation)}
 * 第 2 参是修饰器 id 的 {@link ResourceLocation}(不再是 1.20.1 的 UUID 字符串),此处用语义名
 * {@code astral_dice:dragon_roar_break_armor}(1.20.1 线取同一语义名的 UUIDv5 =
 * {@code 188e1666-fb04-5b95-9176-786413e1b491},与 {@code unwavering_armor} 同法)。
 *
 * <p>修饰器取值:1.21.1 已无 {@code getAttributeModifierValue} 覆写点,改由 {@code MobEffect.AttributeTemplate}
 * 在施加时按 {@code amount × (amplifier + 1)} 展开;施加方恒用 {@code amplifier = 0},
 * 因此减甲固定为 {@link #ARMOR_DELTA},"不重命中不叠层"依旧成立。
 *
 * <p>图标 = {@code images/龙之咆哮.png}(与战斗牌 {@code attack_card_dragon_roar} 同一张图;
 * 实装路径 {@code textures/mob_effect/dragon_roar_break.png},两文件逐字节相同)。
 */
public class DragonRoarBreakEffect extends MobEffect {
    /** 效果时长:1:00 = 1200 tick(规格 §1 效果表 {@code ROAR_DEBUFF_TICKS}) */
    public static final int DURATION_TICKS = 1200;

    /** 护甲修正量:−8 护甲 ≈ −2.4 点防御力(玩家侧 1 防御力 = 1÷0.30 ≈ 3.33 护甲值) */
    public static final double ARMOR_DELTA = -8.0;

    /** 修饰器 id(1.21.1 用 ResourceLocation;1.20.1 线取同一语义名的 UUIDv5 = 188e1666-fb04-5b95-9176-786413e1b491) */
    public static final ResourceLocation ARMOR_MODIFIER_ID =
            ResourceLocation.fromNamespaceAndPath("astral_dice", "dragon_roar_break_armor");

    public DragonRoarBreakEffect() {
        super(MobEffectCategory.HARMFUL, 0xB22222);
        this.addAttributeModifier(Attributes.ARMOR, ARMOR_MODIFIER_ID,
                ARMOR_DELTA, AttributeModifier.Operation.ADD_VALUE);
    }
}
