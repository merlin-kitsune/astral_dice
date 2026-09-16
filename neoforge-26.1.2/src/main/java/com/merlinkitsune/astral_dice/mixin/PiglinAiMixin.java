package com.merlinkitsune.astral_dice.mixin;

import com.merlinkitsune.astral_dice.event.NetherrackDiceHandler;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.monster.piglin.PiglinAi;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 下界岩骰子:佩戴期间猪灵对玩家保持中立(视同身穿金甲)。
 *
 * 原版 {@code PiglinAi.isWearingGold} 仅检查盔甲栏;此处在其返回 false 时
 * 原版 {@code PiglinAi.isWearingSafeArmor}(26.1.2 定名前叫 {@code isWearingGold})仅检查盔甲栏;
 * 此处在其返回 false 时追加判定 Curios "dice" 栏是否佩戴下界岩骰子,是则改判为 true,
 * 使猪灵的全部敌意/扰动行为(邻近愤怒、袭击、不袭击金甲玩家等)一致保持中立。
 * 目标签名(26.1.2 源码 {@code PiglinAi.java:653}):{@code public static boolean isWearingSafeArmor(LivingEntity)}。
 */
@Mixin(PiglinAi.class)
public abstract class PiglinAiMixin {

    @Inject(method = "isWearingSafeArmor", at = @At("RETURN"), cancellable = true)
    private static void astralDice$netherrackDiceNeutral(LivingEntity livingEntity,
                                                         CallbackInfoReturnable<Boolean> cir) {
        if (Boolean.TRUE.equals(cir.getReturnValue())) return;
        // 原版签名参数为 LivingEntity:仅对玩家执行骰子判定
        if (!(livingEntity instanceof Player player)) return;
        if (!player.level().isClientSide() && NetherrackDiceHandler.hasNetherrackDice(player)) {
            cir.setReturnValue(true);
        }
    }
}
