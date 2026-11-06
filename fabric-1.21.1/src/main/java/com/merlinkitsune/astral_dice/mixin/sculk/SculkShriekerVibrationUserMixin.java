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
 *
 * <p><b>平台差异</b>：{@code SculkShriekerBlockEntity$VibrationUser} 在 MC 1.21.1 里是
 * <b>package-private</b> 内部类 ⇒ 只能写 {@code targets} 字符串（不能 import 也不能写
 * {@code Foo.class}）。方法签名与感测体那条逐字相同。
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
