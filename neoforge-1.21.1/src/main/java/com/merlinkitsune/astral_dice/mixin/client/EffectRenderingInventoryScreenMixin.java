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
 * 将原版效果等级角标上限由 amplifier ≤ 9(等级 ≤ 10,即「X」)扩展到 amplifier ≤ 99(等级 ≤ 100,即「C」)。
 *
 * 原版 {@code EffectRenderingInventoryScreen#getEffectName} 使用
 * {@code translatable("enchantment.level." + (amplifier + 1))},而 vanilla 仅有 1~10 的翻译键,
 * 因此这里改为自行生成罗马数字角标,2~10 级输出与原版完全一致(II~X),超出后继续按罗马数字渲染。
 * 该方法同时是物品栏效果面板标签与悬浮 tooltip 的等级角标来源,注入此点即可覆盖两处。
 */
@Mixin(EffectRenderingInventoryScreen.class)
public abstract class EffectRenderingInventoryScreenMixin {

    private static final String[] ROMAN_THOUSANDS = {"", "M", "MM", "MMM"};
    private static final String[] ROMAN_HUNDREDS = {"", "C", "CC", "CCC", "CD", "D", "DC", "DCC", "DCCC", "CM"};
    private static final String[] ROMAN_TENS = {"", "X", "XX", "XXX", "XL", "L", "LX", "LXX", "LXXX", "XC"};
    private static final String[] ROMAN_ONES = {"", "I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX"};

    @Inject(method = "getEffectName", at = @At("RETURN"), cancellable = true)
    private void astralDice$extendEffectLevelBadge(MobEffectInstance effect, CallbackInfoReturnable<Component> cir) {
        MutableComponent name = effect.getEffect().value().getDisplayName().copy();
        int amplifier = effect.getAmplifier();
        if (amplifier >= 1 && amplifier <= 99) {
            name.append(CommonComponents.SPACE).append(Component.literal(toRoman(amplifier + 1)));
        }
        cir.setReturnValue(name);
    }

    private static String toRoman(int number) {
        if (number <= 0 || number >= 4000) {
            return String.valueOf(number);
        }
        return ROMAN_THOUSANDS[number / 1000]
                + ROMAN_HUNDREDS[(number % 1000) / 100]
                + ROMAN_TENS[(number % 100) / 10]
                + ROMAN_ONES[number % 10];
    }
}
