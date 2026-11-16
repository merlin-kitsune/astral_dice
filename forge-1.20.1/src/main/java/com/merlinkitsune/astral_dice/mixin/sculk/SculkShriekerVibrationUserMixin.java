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
 * 幽匿尖啸体：同 {@link SculkSensorVibrationUserMixin}，佩戴魔渊幼体立牌的玩家的振动不接收
 * ⇒ 不会触发尖啸（自然也不会进入黑暗 / 召唤监守者的链路）。
 */
@Mixin(targets = "net.minecraft.world.level.block.entity.SculkShriekerBlockEntity$VibrationUser")
public abstract class SculkShriekerVibrationUserMixin {

    @Inject(method = "canReceiveVibration", at = @At("HEAD"), cancellable = true)
    private void astral$abyssSignSilence(ServerLevel level, BlockPos pos, Holder<GameEvent> event,
                                         GameEvent.Context context, CallbackInfoReturnable<Boolean> cir) {
        if (SykesSignItem.isSilencedVibrationSource(context.sourceEntity())) {
            cir.setReturnValue(false);
        }
    }
}
