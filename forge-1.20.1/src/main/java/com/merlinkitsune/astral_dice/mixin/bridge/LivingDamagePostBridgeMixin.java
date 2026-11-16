package com.merlinkitsune.astral_dice.mixin.bridge;

import com.merlinkitsune.astral_dice.combat.DamageBridge;
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
 * Forge 1.20.1 没有原生的「实际扣血之后」事件：{@code LivingDamageEvent} 在扣血前派发，
 * {@code LivingDeathEvent} 只在死亡时派发。这里用 {@code actuallyHurt} 的 HEAD/RETURN 配对，
 * 以「进入方法时的生命值 - 返回时的生命值」作为实际扣血量，补齐 {@link DamagePhase#POST}。
 *
 * <p>用 ThreadLocal 栈而不是单个字段，保证伤害钩子内部再次造成伤害时（嵌套调用）仍能正确配对。
 * 早退路径（无敌、事件取消、伤害为 0）同样会走到 RETURN，此时生命值差为 0，不派发 POST。
 */
@Mixin(LivingEntity.class)
public abstract class LivingDamagePostBridgeMixin {

    private static final ThreadLocal<Deque<Float>> ASTRAL_DICE_HEALTH_BEFORE =
            ThreadLocal.withInitial(ArrayDeque::new);

    @Inject(
            method = "actuallyHurt(Lnet/minecraft/world/damagesource/DamageSource;F)V",
            at = @At("HEAD"))
    private void astralDice$recordHealthBefore(DamageSource source, float amount, CallbackInfo ci) {
        ASTRAL_DICE_HEALTH_BEFORE.get().push(((LivingEntity) (Object) this).getHealth());
    }

    @Inject(
            method = "actuallyHurt(Lnet/minecraft/world/damagesource/DamageSource;F)V",
            at = @At("RETURN"))
    private void astralDice$firePost(DamageSource source, float amount, CallbackInfo ci) {
        Deque<Float> stack = ASTRAL_DICE_HEALTH_BEFORE.get();
        Float before = stack.poll();
        if (before == null) {
            return;
        }
        LivingEntity self = (LivingEntity) (Object) this;
        float dealt = before - self.getHealth();
        if (dealt > 0.0F) {
            DamageBridge.fire(DamagePhase.POST, self, source, dealt);
        }
    }
}
