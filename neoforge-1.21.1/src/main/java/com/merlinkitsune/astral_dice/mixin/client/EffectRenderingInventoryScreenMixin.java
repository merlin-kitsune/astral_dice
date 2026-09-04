package com.merlinkitsune.astral_dice.mixin.client;

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
 * 原版 {@code EffectRenderingInventoryScreen#getEffectName} 使用
 * {@code translatable("enchantment.level." + (amplifier + 1))} 显示罗马数字(I~X),且仅支持到等级 10;
 * 这里改为追加阿拉伯数字角标(如「治愈 3」「治愈 32」),并支持到等级 100。
 * 该方法同时是物品栏效果面板标签与悬浮 tooltip 的等级角标来源,注入此点即可覆盖两处。
 */
@Mixin(EffectRenderingInventoryScreen.class)
public abstract class EffectRenderingInventoryScreenMixin {

    @Inject(method = "getEffectName", at = @At("RETURN"), cancellable = true)
    private void astralDice$numericEffectLevelBadge(MobEffectInstance effect, CallbackInfoReturnable<Component> cir) {
        MutableComponent name = effect.getEffect().value().getDisplayName().copy();
        int amplifier = effect.getAmplifier();
        if (amplifier >= 1 && amplifier <= 99) {
            name.append(CommonComponents.SPACE).append(Component.literal(String.valueOf(amplifier + 1)));
        }
        cir.setReturnValue(name);
    }
}
