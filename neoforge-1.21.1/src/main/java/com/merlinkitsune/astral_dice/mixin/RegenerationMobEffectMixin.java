package com.merlinkitsune.astral_dice.mixin;

import com.merlinkitsune.astral_dice.event.DorothyWarmthHealContext;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 多萝西被动：标记「生命恢复」的治疗来源与效果等级。
 *
 * <p>1.21.1 的生命恢复由包私有的 {@code RegenerationMobEffect} 实现，
 * 类字面量在包外不可见 {@code (@Mixin(RegenerationMobEffect.class) 无法编译)}，
 * 因此用 {@code targets} 字符串指定目标。
 *
 * <p>HEAD 压入 {@code amplifier + 1}，RETURN 弹出；只有生命值未满时该方法才会
 * 调用 {@code heal}，因此治疗事件必然对应一次真实的恢复 tick。
 */
@Mixin(targets = "net.minecraft.world.effect.RegenerationMobEffect")
public abstract class RegenerationMobEffectMixin {

    @Inject(method = "applyEffectTick", at = @At("HEAD"))
    private void astralDice$pushRegenerationHealSource(LivingEntity target, int amplifier,
                                                       CallbackInfoReturnable<Boolean> cir) {
        DorothyWarmthHealContext.push(DorothyWarmthHealContext.Kind.REGENERATION, amplifier + 1);
    }

    @Inject(method = "applyEffectTick", at = @At("RETURN"))
    private void astralDice$popRegenerationHealSource(LivingEntity target, int amplifier,
                                                      CallbackInfoReturnable<Boolean> cir) {
        DorothyWarmthHealContext.pop();
    }
}
