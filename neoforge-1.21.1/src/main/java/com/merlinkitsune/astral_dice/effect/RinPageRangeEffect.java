package com.merlinkitsune.astral_dice.effect;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;

/**
 * 「书页射程」(调查员立牌 rin 主动追加的状态载体;2026-10-03 用户裁决「主动技能追加:
 * 活体书页使用射程 +50%,持续 2:00」)。
 *
 * <p><b>作用</b>:持有期间使活体书页({@code living_page})的目标选择器**锁定范围 ×1.5**。加成的
 * **唯一读取点** = {@code target/SelectorRangeModifiers#apply}(在 {@code TargetSelectionManager#start}
 * 里对会话半径统一加成),与探天卫星筹码的常驻 +50% **相加**后按
 * {@code SelectorRangeModifiers#MAX_ENHANCED_RADIUS}(64 格)夹取 —— 本类自身不改半径、不碰任何
 * 选择器状态,只充当「是否持有该加成」的真值。
 *
 * <p><b>形态</b>:有限时长 2:00({@value #DURATION_TICKS} tick)、**可见**(HUD 图标 +
 * 悬停说明 {@code effect.astral_dice.rin_page_range.description})、无粒子、无属性修饰符。
 * 重复施放只刷新时长(同一效果实例),**不会叠加**加成倍率。
 *
 * <p><b>为什么不做成「佩戴立牌才生效」</b>:本状态由主动技能**施放当刻**登记、自带 2:00 计时,
 * 效果实例即真值(与「白泽赐福」「降神」等同款)。立牌的持续加成(活体书页伤害 +1)仍按「佩戴时
 * 生效」由 {@code SpellDamageRegistry} 判定,两者互不影响。
 *
 * <p>图标 = {@code textures/mob_effect/rin_page_range.png},生成器
 * {@code tools/gen_rin_page_range_texture.py}(书页 + 蓝色双向箭头)。
 */
public class RinPageRangeEffect extends MobEffect {
    /** 状态时长(tick,2:00) */
    public static final int DURATION_TICKS = 20 * 120;

    /** 活体书页锁定范围的加成比例(0.5 = +50%) */
    public static final double RANGE_BONUS = 0.5D;

    public RinPageRangeEffect() {
        super(MobEffectCategory.BENEFICIAL, 0x2A7FD4);
    }
}
