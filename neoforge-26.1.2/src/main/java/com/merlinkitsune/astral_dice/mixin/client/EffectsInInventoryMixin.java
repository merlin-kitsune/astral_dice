package com.merlinkitsune.astral_dice.mixin.client;

import com.merlinkitsune.astral_dice.effect.ModEffects;
import net.minecraft.client.gui.screens.inventory.EffectsInInventory;
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
 * <p><b>26.1.2 迁移说明</b>:该逻辑在 1.21.1 位于 {@code EffectRenderingInventoryScreen#getEffectName};
 * MC 26.1.2(Mojang 2026 界面重组)把效果面板抽成独立类 {@code EffectsInInventory},方法签名保持不变 ——
 * {@code private Component getEffectName(MobEffectInstance effect)}
 * (目标版本反编译源码 {@code net/minecraft/client/gui/screens/inventory/EffectsInInventory.java:134}),
 * 故本 mixin 改为注入该类同名私有方法(私有方法可注入)。
 *
 * 该方法同时是物品栏效果面板标签与悬浮 tooltip 的等级角标来源,注入此点即可覆盖两处。
 */
@Mixin(EffectsInInventory.class)
public abstract class EffectsInInventoryMixin {

    @Inject(method = "getEffectName", at = @At("RETURN"), cancellable = true)
    private void astralDice$numericEffectLevelBadge(MobEffectInstance effect, CallbackInfoReturnable<Component> cir) {
        MutableComponent name = effect.getEffect().value().getDisplayName().copy();
        int amplifier = effect.getAmplifier();
        boolean isAlwaysNumeric = effect.getEffect().value() == ModEffects.CHARGE.get()
                || effect.getEffect().value() == ModEffects.HEALING.get()
                || effect.getEffect().value() == ModEffects.MARKED.get();
        if (amplifier >= 0 && amplifier <= 99 && (isAlwaysNumeric || amplifier >= 1)) {
            name.append(CommonComponents.SPACE).append(Component.literal(String.valueOf(amplifier + 1)));
        }
        cir.setReturnValue(name);
    }
}
