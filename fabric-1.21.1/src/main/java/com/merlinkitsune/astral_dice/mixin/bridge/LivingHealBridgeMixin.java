package com.merlinkitsune.astral_dice.mixin.bridge;

import com.merlinkitsune.astral_dice.platform.event.LoaderBus;
import com.merlinkitsune.astral_dice.platform.event.entity.living.LivingHealEvent;

import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * {@code LivingEntity#heal(float)} → {@link LivingHealEvent}。
 *
 * <p>2026-09-29 补:此前无派发源 —— 而 {@code FabricBridges} 的类注释曾声称由
 * 「HealBridgeMixin」派发(该 mixin **并不存在**,属文档与实现不一致)。
 * 缺口影响:{@code ZhaoSignItem#onLivingHeal}(白泽赐福:把治疗溢出量累计进
 * {@code zhao_overflow_bonus})**永不执行**。
 *
 * <p>注入 HEAD 即 Forge 的派发位置({@code ForgeHooks.onLivingHeal} 在 heal 首行):
 * 消费方在事件里读的是 {@code getMaxHealth() - getHealth()}(heal 前的血量),
 * 若改到 RETURN 还会算错溢出量。
 */
@Mixin(LivingEntity.class)
public abstract class LivingHealBridgeMixin {

    @Inject(method = "heal(F)V", at = @At("HEAD"))
    private void astralDice$onHeal(float amount, CallbackInfo ci) {
        LivingEntity self = (LivingEntity) (Object) this;
        LoaderBus.INSTANCE.post(new LivingHealEvent(self, amount));
    }
}
