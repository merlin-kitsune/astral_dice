package com.merlinkitsune.astral_dice.mixin.sculk;

import com.merlinkitsune.astral_dice.item.sign.SykesSignItem;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.gameevent.GameEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 幽匿尖啸体：佩戴魔渊幼体立牌的玩家同样不触发尖啸（不会召唤监守者、也不会被施加黑暗）。
 *
 * <p>注入点 {@code SculkShriekerBlockEntity$VibrationUser#canReceiveVibration} 与感测体同构；
 * 「黑暗」另由 {@link SykesSignItem} 的 {@code MobEffectEvent.Applicable} 闸门拦截。
 */
@Mixin(targets = "net.minecraft.world.level.block.entity.SculkShriekerBlockEntity$VibrationUser")
public abstract class SculkShriekerVibrationUserMixin {

    @Inject(method = "canReceiveVibration", at = @At("HEAD"), cancellable = true)
    private void astral$abyssSignSilenceShrieker(ServerLevel level, BlockPos pos, Holder<GameEvent> event,
                                                 GameEvent.Context context, CallbackInfoReturnable<Boolean> cir) {
        if (SykesSignItem.isSilencedVibrationSource(context.sourceEntity())) {
            cir.setReturnValue(false);
        }
    }
}
