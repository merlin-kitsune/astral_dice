package com.merlinkitsune.astral_dice.mixin;

import com.merlinkitsune.astral_dice.event.DorothyWarmthHealContext;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 多萝西被动：标记「生命恢复」的治疗来源与效果等级。
 *
 * <p>1.20.1 没有 {@code RegenerationMobEffect}：生命恢复与其他 tick 效果共用
 * {@code MobEffect#applyEffectTick}，在方法内按 {@code this == MobEffects.REGENERATION}
 * 分支调用 {@code heal(1.0F)} ⇒ 只能注入 {@code MobEffect}。文件 / 类名沿用计划命名。
 *
 * <p>HEAD 压入 {@code amplifier + 1}、RETURN 弹出；只在生命恢复分支标记，
 * 毒 / 凋零 / 饥饿等 tick 直接跳过。{@code LivingHealEvent} 据此按效果等级叠温暖。
 */
@Mixin(MobEffect.class)
public abstract class RegenerationMobEffectMixin {

    @Inject(method = "applyEffectTick", at = @At("HEAD"))
    private void astralDice$pushRegenerationHealSource(LivingEntity target, int amplifier,
                                                       CallbackInfo ci) {
        if ((Object) this != MobEffects.REGENERATION) return;
        DorothyWarmthHealContext.push(DorothyWarmthHealContext.Kind.REGENERATION, amplifier + 1);
    }

    @Inject(method = "applyEffectTick", at = @At("RETURN"))
    private void astralDice$popRegenerationHealSource(LivingEntity target, int amplifier,
                                                      CallbackInfo ci) {
        if ((Object) this != MobEffects.REGENERATION) return;
        DorothyWarmthHealContext.pop();
    }
}
