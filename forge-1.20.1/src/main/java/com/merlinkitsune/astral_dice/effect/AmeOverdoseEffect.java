package com.merlinkitsune.astral_dice.effect;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;

/**
 * 「感情过量摄入」:地雷女立牌(ame)主动施加于**自身**的有限时长效果(1:00 = 1200 tick)。
 *
 * <p>技能原文(用户 2026-10-16):「立即恢复"爱"层数x2的生命值,以及移动速度加成(百分比),持续1:00。
 * 时间结束后扣除4层"爱",并使"爱"层数上限+1(最多32层)」。三段语义的落点:
 * <ul>
 *   <li><b>恢复 爱×2 生命</b> —— {@code AmeSignItem#handleUse} 里的 {@code heal()}(不超过最大生命);</li>
 *   <li><b>移动速度 +20%</b> —— 本类的 {@code MOVEMENT_SPEED} 修饰器
 *       ({@code MULTIPLY_TOTAL} 0.2,固定值、非按层;裁决 #1,照 {@link HannaFloatEffect} 同款);</li>
 *   <li><b>到期结算(爱 -4、上限 +1,最高 32)</b> —— <b>仅</b>由 {@code MobEffectEvent.Expired}
 *       (自然到期)触发,见 {@code AmeSignItem#onOverdoseExpired};奶/命令清除属外力移除,
 *       不派发该事件 ⇒ <b>不触发结算</b>(卸下立牌走 {@code clearSignData} 显式移除,同样不结算)。</li>
 * </ul>
 *
 * <p>时长有限且由 {@code EffectTimerGuard} 记账(施加时经 {@code EffectTimerGuard.apply}),
 * 故 HUD 计时器严格按 20 t/s 流动。图标 = 立牌本体图(实装路径
 * {@code textures/mob_effect/ame_overdose.png},自 {@code images/地雷女立牌.png} 逐字节复制;
 * 照 hanna_float 先例)。
 *
 * <p>配色:{@link MobEffectCategory#BENEFICIAL}(裁决 #5)。
 *
 * <p><b>平台差异(1.20.1 Forge 移植版)</b>:① 修饰器 id 由 {@code ResourceLocation} 换为
 * **UUIDv5 字符串**({@code e275a2a9-48d8-5bb9-a4bb-396b2aff00d7} =
 * {@code uuid5(NAMESPACE_URL, "astral_dice:ame_overdose_speed")}),操作枚举
 * {@code ADD_MULTIPLIED_TOTAL} → {@code MULTIPLY_TOTAL}(照 {@link HannaFloatEffect} 先例);
 * ② {@code ModEffects.AME_OVERDOSE} 是 {@code RegistryObject} ⇒ 引用处加 {@code .get()}。
 */
public class AmeOverdoseEffect extends MobEffect {
    /** 效果时长:1:00 = 1200 tick(技能原文「持续1:00」) */
    public static final int DURATION_TICKS = 1200;
    /** 移速加成(固定 +20%;裁决 #1) */
    public static final double MOVEMENT_SPEED_BONUS = 0.2D;

    public AmeOverdoseEffect() {
        super(MobEffectCategory.BENEFICIAL, 0xD81B60);
        // 1.20.1 的 addAttributeModifier(Attribute, String, ...) 要求 **UUID 字符串**
        // (内部 UUID.fromString),不能像 1.21.1 那样传 ResourceLocation 路径
        // ⇒ 用与语义名绑定的确定性 UUIDv5:uuid5(NAMESPACE_URL, "astral_dice:ame_overdose_speed"),
        // 与既有 HannaFloatEffect / JasmineSweepEffect 的生成口径完全一致。
        // 操作枚举:1.21.1 的 ADD_MULTIPLIED_TOTAL ↔ 本线 MULTIPLY_TOTAL(同一语义)。
        this.addAttributeModifier(Attributes.MOVEMENT_SPEED, "e275a2a9-48d8-5bb9-a4bb-396b2aff00d7",
                MOVEMENT_SPEED_BONUS, AttributeModifier.Operation.MULTIPLY_TOTAL);
    }

    /** 是否处于「感情过量摄入」生效期(主动锁定态的门控效果判据) */
    public static boolean has(Player player) {
        return player != null && player.hasEffect(ModEffects.AME_OVERDOSE.get());
    }
}
