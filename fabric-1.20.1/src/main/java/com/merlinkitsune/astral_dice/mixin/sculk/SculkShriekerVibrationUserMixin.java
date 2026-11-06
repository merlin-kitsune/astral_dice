package com.merlinkitsune.astral_dice.mixin.sculk;

import com.merlinkitsune.astral_dice.item.sign.SykesSignItem;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.gameevent.GameEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 幽匿尖啸体：佩戴魔渊幼体立牌的玩家**不产生**可接收的振动（进而不触发尖啸 / 黑暗 / 监守者）。
 *
 * <p>移植自 neoforge-1.21.1 同路径；注入点与语义同 {@link SculkSensorVibrationUserMixin}。
 *
 * <p><b>平台差异（fabric 1.20.1）</b>：
 * <ul>
 *   <li>① {@code SculkShriekerBlockEntity.VibrationUser} 是**包私有**内部类 ⇒ 只能用 {@code targets} 字符串，
 *       无法用类字面量（本线既有同款写法见 {@code mixin/jade/*}）；</li>
 *   <li>② 第三参是裸 {@code GameEvent}（1.20.1），不是 1.21 的 {@code Holder&lt;GameEvent&gt;}。</li>
 * </ul>
 */
@Mixin(targets = "net.minecraft.world.level.block.entity.SculkShriekerBlockEntity$VibrationUser")
public class SculkShriekerVibrationUserMixin {

    @Inject(method = "canReceiveVibration", at = @At("HEAD"), cancellable = true)
    private void astralDice$silenceSykesWearer(ServerLevel level, BlockPos pos, GameEvent gameEvent,
                                               GameEvent.Context context, CallbackInfoReturnable<Boolean> cir) {
        if (SykesSignItem.isSilencedVibrationSource(context.sourceEntity())) {
            cir.setReturnValue(false);
        }
    }
}
