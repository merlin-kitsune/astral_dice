package com.merlinkitsune.astral_dice.effect;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;

/**
 * 「渊蚀印记」（魔渊幼体立牌被动 + 魔渊触须攻击施加）：负面、可叠层。
 *
 * <p>本类只是效果壳（分类 = 负面 ⇒ 原版会以红字显示、在 Jade 的效果列表里带图标与剩余时间），
 * 真机制在 {@code item/AbyssMarkManager}：amplifier = 层数 - 1，每 2:00 自然到期减 1 层，
 * 上限 {@link #MAX_STACKS} 层；每层使目标受到「魔渊幼体」与「魔渊触须」的伤害 +1。
 *
 * <p>图标 {@code textures/mob_effect/abyss_mark.png}（32x32，自 neoforge-1.21.1 侧逐字节复制）。
 *
 * <p><b>平台差异（fabric 1.20.1）</b>：本线 {@code ModEffects} 的条目是 {@code RegistryObject&lt;MobEffect&gt;}
 * （原版 {@code Registry} + 自建 shim），引用处一律 {@code .get()}；{@code MobEffectInstance} 收的是
 * **裸 {@code MobEffect}**（不是 1.21 的 {@code Holder&lt;MobEffect&gt;}）—— 故本类与方法签名无需任何 Holder 处理。
 */
public class AbyssMarkEffect extends MobEffect {

    /** 层数上限（用户裁决：最大 20 层）。 */
    public static final int MAX_STACKS = 20;

    /** 每层持续时长：2:00。 */
    public static final int DURATION_TICKS = 2400;

    public AbyssMarkEffect() {
        super(MobEffectCategory.HARMFUL, 0x5A3490);
    }
}
