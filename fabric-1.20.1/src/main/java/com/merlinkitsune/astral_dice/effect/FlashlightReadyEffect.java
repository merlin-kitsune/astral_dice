package com.merlinkitsune.astral_dice.effect;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;

/**
 * 手电筒-强光状态效果(标记类):佩戴该筹码、处于骰神赐福状态且**星光 ≥ 4(确有额外加伤)**时
 * 显示效果图标,提示「星光/4」的额外加伤生效中。
 *
 * <p>该加伤走 {@code astral_dice:extra_damage} 额外加伤通道,且只在骰神赐福期间的骰战里结算
 * (见 {@code DiceCombatEvents} 无赐福时提前 return),故图标**跟随骰神赐福显示、不在赐福状态即隐藏**。
 * 生效条件与时长维护在 {@code PlayerTickEvents} 的玩家 tick 中(与美工刀状态效果同一范式)。
 */
public class FlashlightReadyEffect extends MobEffect {
    public FlashlightReadyEffect(int color) {
        super(MobEffectCategory.BENEFICIAL, color);
    }
}
