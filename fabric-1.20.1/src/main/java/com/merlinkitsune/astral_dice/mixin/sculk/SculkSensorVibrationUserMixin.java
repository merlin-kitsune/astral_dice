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
 * 幽匿感测体：佩戴魔渊幼体立牌的玩家**不产生**可接收的振动。
 *
 * <p>移植自 neoforge-1.21.1 同路径。注入点与语义：在 {@code canReceiveVibration} 头部，
 * 若振动源是佩戴者则直接返回 false（感测体不会激活、不会把信号传给尖啸体 / 校准感测体）。
 *
 * <p><b>平台差异（fabric 1.20.1）</b>：
 * <ul>
 *   <li>① 第三参是**裸 {@code GameEvent}**（1.20.1），不是 1.21 的 {@code Holder&lt;GameEvent&gt;} —— 照本线字节码写；</li>
 *   <li>② {@code SculkSensorBlockEntity.VibrationUser} 是 **public 内部类**，理论上可以用类字面量；
 *       但为了与尖啸体那支（内部类是包私有，只能用字符串）保持同一写法、并让 loom 的 mixin 注解处理器
 *       统一走 refmap 重映射，这里统一用 {@code targets} 字符串（@Mixin 的 target 字符串会被 remap）；</li>
 *   <li>③ {@code injectors.defaultRequire = 1}：注入点必须恰好命中 1 次，否则目标类加载即抛 InjectionError。</li>
 * </ul>
 */
@Mixin(targets = "net.minecraft.world.level.block.entity.SculkSensorBlockEntity$VibrationUser")
public class SculkSensorVibrationUserMixin {

    @Inject(method = "canReceiveVibration", at = @At("HEAD"), cancellable = true)
    private void astralDice$silenceSykesWearer(ServerLevel level, BlockPos pos, GameEvent gameEvent,
                                               GameEvent.Context context, CallbackInfoReturnable<Boolean> cir) {
        if (SykesSignItem.isSilencedVibrationSource(context.sourceEntity())) {
            cir.setReturnValue(false);
        }
    }
}
