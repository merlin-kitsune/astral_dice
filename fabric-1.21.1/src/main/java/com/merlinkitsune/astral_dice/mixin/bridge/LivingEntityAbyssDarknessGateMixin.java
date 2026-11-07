package com.merlinkitsune.astral_dice.mixin.bridge;

import com.merlinkitsune.astral_dice.item.sign.SykesSignItem;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 「佩戴魔渊幼体立牌 ⇒ 不吃尖啸体的黑暗」的效果下发闸门（fabric-1.21.1 线）。
 *
 * <h3>为什么是 mixin（2026-11-07 订正）</h3>
 * <p>P0（neoforge-1.21.1）用 {@code MobEffectEvent.Applicable} 一句 {@code DO_NOT_APPLY} 就够。
 * 本线**原先**不派发 Applicable（{@code platform/PuzzlesBridges} 只桥 Apply / Remove / Expire），
 * 已于 2026-11-07 接线：{@code MobEffectEvents.AFFECTS} → {@code MobEffectEvent.Applicable}
 * （注入点 = {@code LivingEntity#canBeAffected} 的 HEAD）。{@link SykesSignItem#blocksDarkness}
 * 只判「黑暗 + 佩戴者」⇒ 该规则**已可由事件表达**（{@code Result.DENY} 等价）。
 * <p>本批仍<b>保留</b>该 mixin：移除属于行为面变更（需重新验证黑暗免疫与触须路径），
 * 不在「接线」这批的范围。两处当前互不冲突 —— 本 mixin 在 {@code addEffect} 的 HEAD 直接
 * {@code setReturnValue(false)}，{@code canBeAffected} 因此不会被调用，事件也就不会为该实例派发。
 *
 * <h3>注入点</h3>
 * <ul>
 *   <li>{@code addEffect(Lnet/minecraft/world/effect/MobEffectInstance;)Z} —— 外部施加的常规入口
 *       （{@code /effect give}、监守者的黑暗、药水等）；</li>
 *   <li>{@code addEffect(Lnet/minecraft/world/effect/MobEffectInstance;Lnet/minecraft/world/entity/Entity;)Z}
 *       —— 带来源实体的重载（1 参版本内部委托它）。两处都拦，避免「换个重载就绕过」。</li>
 * </ul>
 * <p>判定函数放在 {@link SykesSignItem#blocksDarkness}（唯一定义处），mixin 里只做转发，
 * 保证「佩戴判定」与立牌其它被动共用同一套 Trinkets {@code stand} 槽逻辑。
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityAbyssDarknessGateMixin {

    @Inject(method = "addEffect(Lnet/minecraft/world/effect/MobEffectInstance;)Z", at = @At("HEAD"), cancellable = true)
    private void astralDice$abyssSignDarknessGate(MobEffectInstance instance, CallbackInfoReturnable<Boolean> cir) {
        LivingEntity self = (LivingEntity) (Object) this;
        if (SykesSignItem.blocksDarkness(self, instance)) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "addEffect(Lnet/minecraft/world/effect/MobEffectInstance;Lnet/minecraft/world/entity/Entity;)Z", at = @At("HEAD"), cancellable = true)
    private void astralDice$abyssSignDarknessGateSourced(MobEffectInstance instance, Entity source,
                                                         CallbackInfoReturnable<Boolean> cir) {
        LivingEntity self = (LivingEntity) (Object) this;
        if (SykesSignItem.blocksDarkness(self, instance)) {
            cir.setReturnValue(false);
        }
    }
}
