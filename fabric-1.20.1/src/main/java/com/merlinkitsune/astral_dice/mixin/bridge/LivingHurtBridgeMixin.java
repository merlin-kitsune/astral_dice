package com.merlinkitsune.astral_dice.mixin.bridge;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.merlinkitsune.astral_dice.platform.event.LoaderBus;
import com.merlinkitsune.astral_dice.platform.event.entity.living.LivingHurtEvent;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * {@code LivingHurtEvent} 桥 —— <b>Puzzles Lib 没有这一项，必须自写</b>。
 *
 * <h2>为什么 Puzzles 替代不了</h2>
 * 实测其 mixin：{@code LivingHurtCallback} 注入在 {@code LivingEntity#actuallyHurt} 的
 * <b>HEAD</b>。而 Forge 的 {@code LivingHurtEvent} 在 {@code hurt()} 内部、{@code actuallyHurt}
 * <b>调用之前</b>（{@code ForgeHooks.onLivingHurt} 的返回值随后作为实参传给 {@code actuallyHurt}）——
 * 两者相隔一次调用，且 Forge 在 {@code onLivingHurt} 之后还有一次 {@code if (f1 <= 0) return false;}
 * 的早退。所以 {@code LivingHurtCallback} 只能桥 {@code LivingDamageEvent}（见
 * {@code platform/PuzzlesBridges}），{@code LivingHurtEvent} 必须自己注入。
 *
 * <h2>注入方式:包裹 {@code actuallyHurt} 调用</h2>
 * <p>用 MixinExtras 的 {@link WrapOperation} 而不是 {@code @ModifyArg}：
 * <ul>
 *   <li>能同时表达「改值」与「取消」—— Forge 的语义正是
 *       {@code f1 = onLivingHurt(...); if (取消) 不调用 actuallyHurt;}；</li>
 *   <li>新注入器**可链式共存**（Fabric Loader 0.17+ 内置 MixinExtras）⇒ 与其它模组对同一调用点的
 *       注入不会互相顶掉，这是整合包环境下的实际收益。</li>
 * </ul>
 * <p>⚠️ 1.20.1 的 {@code hurt()} 里 {@code actuallyHurt} 只被调用一次，
 * 且 {@code ServerPlayer#hurt} 覆写后仍会走 {@code super.hurt} ⇒ 一处注入即覆盖全部路径。
 * <p>⚠️ 目标描述符写的是 **Mojang 名**，由 Loom 在 {@code remapJar} 阶段重映射为 intermediary
 * （产物级实证可见；含描述符的目标同样会被正确重映射）。
 */
@Mixin(LivingEntity.class)
public abstract class LivingHurtBridgeMixin {

    @WrapOperation(
            method = "hurt",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/LivingEntity;actuallyHurt(Lnet/minecraft/world/damagesource/DamageSource;F)V"))
    private void astralDice$bridgeLivingHurt(LivingEntity self, DamageSource source, float amount,
                                             Operation<Void> original) {
        LivingHurtEvent event = new LivingHurtEvent(self, source, amount);
        LoaderBus.INSTANCE.post(event);
        if (event.isCanceled()) {
            // Forge:onLivingHurt 被取消 ⇒ 返回 0 ⇒ 后续 f1 <= 0 早退 ⇒ 根本不进 actuallyHurt
            return;
        }
        original.call(self, source, event.getAmount());
    }
}
