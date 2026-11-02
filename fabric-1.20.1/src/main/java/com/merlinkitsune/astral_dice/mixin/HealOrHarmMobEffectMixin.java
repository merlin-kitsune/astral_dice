package com.merlinkitsune.astral_dice.mixin;

import com.merlinkitsune.astral_dice.event.DorothyWarmthHealContext;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 多萝西被动：标记「瞬间治疗」的治疗来源与效果等级。
 *
 * <p>1.20.1 没有 1.21.1 的 {@code HealOrHarmMobEffect}：瞬间治疗 / 伤害共用
 * {@code MobEffect#applyInstantenousEffect}，按 {@code this == MobEffects.HEAL}
 * 走治疗分支，且 {@code RegenerationMobEffect}、{@code InstantenousMobEffect}
 * 都不覆写该方法 ⇒ 只能注入 {@code MobEffect}。文件 / 类名沿用计划命名。
 *
 * <p>HEAD 压入 {@code amplifier + 1}、RETURN 弹出；仅治疗分支（HEAL）需要标记，
 * 其他瞬发效果（伤害 / 饱和）直接跳过。{@code LivingHealEvent} 据此按效果等级叠温暖。
 */
@Mixin(MobEffect.class)
public abstract class HealOrHarmMobEffectMixin {

    @Inject(method = "applyInstantenousEffect", at = @At("HEAD"))
    private void astralDice$pushInstantHealSource(Entity source, Entity indirectSource,
                                                  LivingEntity target, int amplifier, double health,
                                                  CallbackInfo ci) {
        if ((Object) this != MobEffects.HEAL) return;
        DorothyWarmthHealContext.push(DorothyWarmthHealContext.Kind.INSTANT, amplifier + 1);
    }

    @Inject(method = "applyInstantenousEffect", at = @At("RETURN"))
    private void astralDice$popInstantHealSource(Entity source, Entity indirectSource,
                                                 LivingEntity target, int amplifier, double health,
                                                 CallbackInfo ci) {
        if ((Object) this != MobEffects.HEAL) return;
        DorothyWarmthHealContext.pop();
    }
}
