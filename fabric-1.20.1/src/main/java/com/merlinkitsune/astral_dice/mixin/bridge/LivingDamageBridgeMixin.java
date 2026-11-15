package com.merlinkitsune.astral_dice.mixin.bridge;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.merlinkitsune.astral_dice.combat.DamageBridge;
import com.merlinkitsune.starengine.api.combat.damage.DamageContext;
import com.merlinkitsune.starengine.api.combat.damage.DamagePhase;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Fabric 伤害链的 {@link DamagePhase#AFTER_ARMOR} / {@link DamagePhase#POST} 桥。
 *
 * <p>Fabric API 的 {@code ALLOW_DAMAGE} 在护甲之前且只能取消；1.20.1 没有
 * {@code AFTER_DAMAGE}，1.21.1 的 {@code AFTER_DAMAGE} 又不会在致死伤害时派发。
 * 因此统一在 {@code LivingEntity#actuallyHurt} 的 {@code setHealth} 调用处取「护甲/吸收之后、
 * 生命值真正写入之前」的伤害：先派发 AFTER_ARMOR，允许取消与改写，再调用原版写入生命值，
 * 最后按「写入前生命值 - 写入后生命值」派发 POST。
 *
 * <p>用 ThreadLocal 栈保存本次 actuallyHurt 的伤害来源，保证嵌套伤害调用仍能配对；
 * {@code setHealth} 只在该方法内被包裹一次，不会误伤治疗或其他调用点。
 */
@Mixin(LivingEntity.class)
public abstract class LivingDamageBridgeMixin {

    private static final ThreadLocal<Deque<DamageSource>> ASTRAL_DICE_SOURCES =
            ThreadLocal.withInitial(ArrayDeque::new);

    @Inject(
            method = "actuallyHurt(Lnet/minecraft/world/damagesource/DamageSource;F)V",
            at = @At("HEAD"))
    private void astralDice$pushSource(DamageSource source, float amount, CallbackInfo ci) {
        ASTRAL_DICE_SOURCES.get().push(source);
    }

    @WrapOperation(
            method = "actuallyHurt(Lnet/minecraft/world/damagesource/DamageSource;F)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/LivingEntity;setHealth(F)V"))
    private void astralDice$bridgeHealthWrite(LivingEntity self, float newHealth, Operation<Void> original) {
        DamageSource source = ASTRAL_DICE_SOURCES.get().peek();
        float oldHealth = self.getHealth();
        float proposed = Math.max(0.0F, oldHealth - newHealth);
        float applied = proposed;
        if (source != null) {
            DamageContext context = DamageBridge.fire(DamagePhase.AFTER_ARMOR, self, source, proposed);
            applied = context.isCanceled() ? 0.0F : Math.max(0.0F, context.amount());
        }
        float targetHealth = Math.max(0.0F, oldHealth - Math.min(applied, oldHealth));
        original.call(self, targetHealth);
        float dealt = oldHealth - self.getHealth();
        if (dealt > 0.0F && source != null) {
            DamageBridge.fire(DamagePhase.POST, self, source, dealt);
        }
    }

    @Inject(
            method = "actuallyHurt(Lnet/minecraft/world/damagesource/DamageSource;F)V",
            at = @At("RETURN"))
    private void astralDice$popSource(DamageSource source, float amount, CallbackInfo ci) {
        ASTRAL_DICE_SOURCES.get().poll();
    }
}
