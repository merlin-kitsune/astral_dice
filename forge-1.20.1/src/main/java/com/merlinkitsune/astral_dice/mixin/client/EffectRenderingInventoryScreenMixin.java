package com.merlinkitsune.astral_dice.mixin.client;

import com.merlinkitsune.astral_dice.effect.ModEffects;
import net.minecraft.client.gui.screens.inventory.EffectRenderingInventoryScreen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.effect.MobEffectInstance;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 将原版效果等级角标由「罗马数字、amplifier ≤ 9(等级 ≤ 10)」改为「阿拉伯数字、amplifier ≤ 99(等级 ≤ 100)」。
 *
 * 与 neoforge-1.21.1 的同名 Mixin 保持一致:覆盖物品栏效果面板标签与悬浮 tooltip 两处的等级角标。
 * 1.20.1 差异:{@code MobEffectInstance#getEffect()} 直接返回 {@code MobEffect}(无 Holder 包装)。
 * 方法引用使用 Mojmap 名,由 Mixin Booster 在运行时重映射为 SRG。
 */
@Mixin(EffectRenderingInventoryScreen.class)
public abstract class EffectRenderingInventoryScreenMixin {

    @Inject(method = "getEffectName", at = @At("RETURN"), cancellable = true)
    private void astralDice$numericEffectLevelBadge(MobEffectInstance effect, CallbackInfoReturnable<Component> cir) {
        MutableComponent name = effect.getEffect().getDisplayName().copy();
        int amplifier = effect.getAmplifier();
        boolean isCharge = effect.getEffect() == ModEffects.CHARGE.get();
        if (amplifier >= 0 && amplifier <= 99 && (isCharge || amplifier >= 1)) {
            name.append(CommonComponents.SPACE).append(Component.literal(String.valueOf(amplifier + 1)));
        }
        cir.setReturnValue(name);
    }
}
