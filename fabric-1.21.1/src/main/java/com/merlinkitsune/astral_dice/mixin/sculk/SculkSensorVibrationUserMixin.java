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
 * 幽匿感测体：佩戴魔渊幼体立牌的玩家的振动一律不接收（用户裁决「无法触发幽匿感测体」）。
 *
 * <p>注入点是 {@code SculkSensorBlockEntity$VibrationUser#canReceiveVibration}：所有振动
 * （脚步 / 方块 / 物品）最终都要经过它，在这里返回 false 即可让感测体完全不响应，也不会向邻近的
 * 尖啸体 / 催化器转发。
 *
 * <p><b>平台差异</b>：目标类用 {@code targets} 字符串（内部类无法直接 import），方法签名用 MC 1.21.1 的
 * {@code (ServerLevel, BlockPos, Holder<GameEvent>, GameEvent.Context)}；fabric 的 mixin 在
 * {@code remapJar} 阶段自动把 {@code targets}/{@code method} 里的 Mojang 名重映射为 intermediary，
 * 源码侧保持官方名即可。{@code $} 在注解字符串内无需转义。
 */
@Mixin(targets = "net.minecraft.world.level.block.entity.SculkSensorBlockEntity$VibrationUser")
public abstract class SculkSensorVibrationUserMixin {

    @Inject(method = "canReceiveVibration", at = @At("HEAD"), cancellable = true)
    private void astral$abyssSignSilence(ServerLevel level, BlockPos pos, Holder<GameEvent> event,
                                         GameEvent.Context context, CallbackInfoReturnable<Boolean> cir) {
        if (SykesSignItem.isSilencedVibrationSource(context.sourceEntity())) {
            cir.setReturnValue(false);
        }
    }
}
