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
 * <h3>为什么必须是 mixin，而不是事件</h3>
 * <p>P0（neoforge-1.21.1）用 {@code MobEffectEvent.Applicable} 一句 {@code DO_NOT_APPLY} 就够；本线
 * <b>不派发 Applicable</b>（{@code platform/PuzzlesBridges} 只桥 Apply / Remove / Expire，见
 * {@code event/NewSignEvents.java} 的同一结论），而 {@code LivingEntity#addEffect(MobEffectInstance)}
 * 在 1.21.1 是 <b>final</b>（javap 实测），也无法用子类覆写 ⇒ 只能在字节码层拦。
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
