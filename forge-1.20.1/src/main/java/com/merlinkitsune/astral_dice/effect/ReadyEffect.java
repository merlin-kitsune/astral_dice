package com.merlinkitsune.astral_dice.effect;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;

/**
 * 主动技能"待命"提示效果:立牌主动技能已激活,等待对目标施加后才能生效(如攻击目标)。
 * 效果显示在玩家状态栏,提示玩家尽快对目标释放;成功施加后由触发逻辑移除。
 */
public class ReadyEffect extends MobEffect {
    public ReadyEffect(int color) {
        super(MobEffectCategory.BENEFICIAL, color);
    }
}
