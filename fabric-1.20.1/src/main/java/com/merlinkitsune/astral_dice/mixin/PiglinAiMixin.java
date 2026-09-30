package com.merlinkitsune.astral_dice.mixin;

import com.merlinkitsune.astral_dice.event.NetherrackDiceHandler;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.piglin.PiglinAi;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 下界岩骰子:佩戴期间猪灵对玩家保持中立(视同身穿金甲)。
 *
 * 原版 {@code PiglinAi.isWearingGold} 仅检查盔甲栏;此处在其返回 false 时
 * 追加判定 Curios "dice" 栏是否佩戴下界岩骰子,是则改判为 true,
 * 使猪灵的全部敌意/扰动行为一致保持中立。
 *
 * 1.20.1 使用 Mixin Booster 运行时重映射,方法引用写 Mojmap 名。
 */
@Mixin(PiglinAi.class)
public abstract class PiglinAiMixin {

    @Inject(method = "isWearingGold", at = @At("RETURN"), cancellable = true)
    private static void astralDice$netherrackDiceNeutral(LivingEntity livingEntity,
                                                         CallbackInfoReturnable<Boolean> cir) {
        if (Boolean.TRUE.equals(cir.getReturnValue())) return;
        if (!(livingEntity instanceof Player player)) return;
        if (!player.level().isClientSide() && NetherrackDiceHandler.hasNetherrackDice(player)) {
            cir.setReturnValue(true);
        }
    }
}
