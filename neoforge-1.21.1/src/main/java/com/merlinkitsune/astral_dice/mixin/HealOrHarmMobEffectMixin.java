package com.merlinkitsune.astral_dice.mixin;

import com.merlinkitsune.astral_dice.event.DorothyWarmthHealContext;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 多萝西被动：标记「瞬间治疗」的治疗来源与效果等级。
 *
 * <p>1.21.1 的瞬间治疗 / 瞬间伤害由包私有的 {@code HealOrHarmMobEffect} 实现，
 * 类字面量在包外不可见 {@code (@Mixin(HealOrHarmMobEffect.class) 无法编译)}，
 * 因此用 {@code targets} 字符串指定目标。
 *
 * <p>HEAD 压入 {@code amplifier + 1}，RETURN 弹出；{@code LivingHealEvent} 据此
 * 让本次治疗按效果等级叠温暖。玩家（非亡灵）下 HEAL 走治疗、HARM 走伤害，伤害分支
 * 没有治疗事件、压栈/弹栈不影响结果；但 HARM 对亡灵仍会治疗，因此本 Mixin 对 HEAL/HARM
 * 两侧都压 {@code INSTANT} 来源（多萝西只对玩家生效，实际只影响 HEAL 分支）。
 */
@Mixin(targets = "net.minecraft.world.effect.HealOrHarmMobEffect")
public abstract class HealOrHarmMobEffectMixin {

    @Inject(method = "applyInstantenousEffect", at = @At("HEAD"))
    private void astralDice$pushInstantHealSource(Entity source, Entity indirectSource,
                                                  LivingEntity target, int amplifier, double health,
                                                  CallbackInfo ci) {
        DorothyWarmthHealContext.push(DorothyWarmthHealContext.Kind.INSTANT, amplifier + 1);
    }

    @Inject(method = "applyInstantenousEffect", at = @At("RETURN"))
    private void astralDice$popInstantHealSource(Entity source, Entity indirectSource,
                                                 LivingEntity target, int amplifier, double health,
                                                 CallbackInfo ci) {
        DorothyWarmthHealContext.pop();
    }

    @Inject(method = "applyEffectTick", at = @At("HEAD"))
    private void astralDice$pushInstantTickHealSource(LivingEntity target, int amplifier,
                                                      CallbackInfoReturnable<Boolean> cir) {
        DorothyWarmthHealContext.push(DorothyWarmthHealContext.Kind.INSTANT, amplifier + 1);
    }

    @Inject(method = "applyEffectTick", at = @At("RETURN"))
    private void astralDice$popInstantTickHealSource(LivingEntity target, int amplifier,
                                                     CallbackInfoReturnable<Boolean> cir) {
        DorothyWarmthHealContext.pop();
    }
}
