package com.merlinkitsune.astral_dice.mixin.bridge;

import com.merlinkitsune.astral_dice.platform.event.LoaderBus;
import com.merlinkitsune.astral_dice.platform.event.entity.living.LivingUseTotemEvent;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * {@code LivingUseTotemEvent} 桥 —— <b>Puzzles Lib 没有这一项，必须自写</b>。
 *
 * <p>Forge 在 {@code LivingEntity#checkTotemDeathProtection} 里派发该事件，语义是
 * 「即将消耗不死图腾救命」，**可取消**（取消 = 不使用图腾 ⇒ 死亡）。
 * 1.20.1 该方法签名为 {@code private boolean checkTotemDeathProtection(DamageSource)}
 * —— Mixin 可以注入 private 方法。
 *
 * <p>图腾与手：原版实现就是遍历双手找 {@code Items.TOTEM_OF_UNDYING}，这里用同样口径取，
 * 以便事件第 3/4 参（{@code totem} / {@code hand}）与 Forge 一致 —— 本模组的消费方
 * （{@code EnderDiceHandler}）只判 {@code getEntity()}，但保留完整语义以免将来误用。
 */
@Mixin(LivingEntity.class)
public abstract class LivingUseTotemBridgeMixin {

    @Inject(method = "checkTotemDeathProtection", at = @At("HEAD"), cancellable = true)
    private void astralDice$bridgeLivingUseTotem(DamageSource source, CallbackInfoReturnable<Boolean> cir) {
        LivingEntity self = (LivingEntity) (Object) this;
        for (InteractionHand hand : InteractionHand.values()) {
            ItemStack held = self.getItemInHand(hand);
            if (!held.is(Items.TOTEM_OF_UNDYING)) {
                continue;
            }
            LivingUseTotemEvent event = new LivingUseTotemEvent(self, source, held, hand);
            LoaderBus.INSTANCE.post(event);
            if (event.isCanceled()) {
                // 取消 = 不消耗图腾、不获得保护 ⇒ 直接判「未保护」
                cir.setReturnValue(false);
            }
            return;
        }
    }
}
