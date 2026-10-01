package com.merlinkitsune.astral_dice.effect;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;

/**
 * 「魔女漂浮」:人偶师立牌(hanna)主动「漂浮魔法」施加于**自身**的有限时长效果(1:00 = 1200 tick,
 * 1.20.1 Forge 移植版)。
 *
 * <p>技能原文(用户 2026-09-21):「使自身获得 魔女漂浮 (1:00),在此期间你的移动速度 +20%,
 * 掉落伤害 -100%,任何对你的近战攻击都会被闪避,你也无法使用末影珍珠进行转移。」四条语义的落点:
 * <ul>
 *   <li><b>移动速度 +20%</b> —— 本类的 {@code MOVEMENT_SPEED} 修饰器({@code ADD_MULTIPLIED_TOTAL} 0.2),
 *       照 {@link JasmineSweepEffect} 同款;</li>
 *   <li><b>掉落伤害 -100%</b> —— {@code HannaSignItem} 在 {@code LivingAttackEvent}
 *       对 {@code DamageTypes.FALL} 直接取消(照骇客立牌末影珍珠摔落免疫同款判据);</li>
 *   <li><b>近战攻击被闪避</b> —— 同一事件里判定「伤害源的直接实体为生物」后走
 *       {@code DiceCombatEvents#applyDodgeCancel}(**最前置取消**,照枪匠破绽闪避同款入口);</li>
 *   <li><b>无法使用末影珍珠</b> —— {@code HannaSignItem} 取消 {@code PlayerInteractEvent.RightClickItem}。</li>
 * </ul>
 *
 * <p><b>平台差异(与 1.21.1 逐字等价)</b>:1.21.1 挂在 {@code LivingIncomingDamageEvent},本线等价物是
 * {@code LivingAttackEvent},并需按本仓既定口径补一步 {@code DiceCombatEvents#isImmuneToDamage} 复刻
 * 1.21.1 的事件阶段(见该线 {@code onMosesBrokenDodge} 的「平台差异补位」注释)。
 *
 * <p>图标 = 立牌本体图(实装路径 {@code textures/mob_effect/hanna_float.png},与
 * {@code textures/item/hanna_sign.png} 逐字节相同)。
 */
public class HannaFloatEffect extends MobEffect {
    /** 效果时长:1:00 = 1200 tick(技能原文「魔女漂浮 (1:00)」) */
    public static final int DURATION_TICKS = 1200;

    public HannaFloatEffect() {
        super(MobEffectCategory.BENEFICIAL, 0x9B7EDE);
        // 1.20.1 的 addAttributeModifier(Attribute, String, ...) 要求 **UUID 字符串**
        // (内部 UUID.fromString),不能像 1.21.1 那样传 ResourceLocation 路径
        // ⇒ 用与语义名绑定的确定性 UUIDv5:uuid5(NAMESPACE_URL, "astral_dice:hanna_float_speed"),
        // 与既有 JasmineSweepEffect 的生成口径完全一致。
        this.addAttributeModifier(Attributes.MOVEMENT_SPEED, "aef19f28-557c-564f-bb6c-e72d5f6926d9",
                0.2, AttributeModifier.Operation.MULTIPLY_TOTAL);
    }

    /** 是否处于「魔女漂浮」生效期(被动「幻想千金」的星币档位判据) */
    public static boolean has(Player player) {
        return player != null && player.hasEffect(ModEffects.HANNA_FLOAT.get());
    }
}
