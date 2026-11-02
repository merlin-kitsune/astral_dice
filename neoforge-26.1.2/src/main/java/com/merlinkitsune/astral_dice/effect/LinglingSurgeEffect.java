package com.merlinkitsune.astral_dice.effect;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;

/**
 * 「能量突击」状态效果（{@code astral_dice:lingling_surge}，怪力乱神立牌主动，§5）。
 *
 * <p><b>本效果自身不带任何原版属性修饰符</b> —— 攻击力 / 移速的实时数值由
 * {@code LinglingSignManager#refreshActiveModifiers} 每 10~20t 用固定 id 的
 * {@code AttributeModifier} 写到玩家属性上（保持「随充能层数实时变化」的语意；
 * 写进 {@code addAttributeModifier} 就只能吃施加瞬间那一档，且无法在 buff 期间
 * 随充能涨落、也无法在 buff 结束时干净摘除）。
 *
 * <p>因此本类只承担「状态标记 + 时长」两件事：图标 / 语言在 T2 资源批次里补。
 */
public class LinglingSurgeEffect extends MobEffect {

    /** 效果时长（tick）：1200t = 1 分钟。 */
    public static final int DURATION_TICKS = 1200;

    public LinglingSurgeEffect() {
        super(MobEffectCategory.BENEFICIAL, 0xFF6B2A);
    }
}
