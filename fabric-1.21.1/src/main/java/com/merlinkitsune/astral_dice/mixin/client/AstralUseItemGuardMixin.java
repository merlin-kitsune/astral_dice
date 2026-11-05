package com.merlinkitsune.astral_dice.mixin.client;

import com.merlinkitsune.astral_dice.client.EffectCardUseGuard;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 效果牌「一次按下只出一张」:在 {@code Minecraft#startUseItem} 的 HEAD 取消长按产生的自动重复。
 *
 * <p>定点依据:{@code startUseItem} 是「按下右键」与「长按自动重复」的<b>共同唯一入口</b>
 * (1.21.1 反编译源码 {@code Minecraft.java:1712},方法体第一件事就是
 * {@code this.rightClickDelay = 4},随后走到 {@code gameMode.useItem(...)});
 * 两个版本签名一致(已用 {@code javap} 核对:`private void startUseItem()`),故两侧同文。
 *
 * <p>为什么不能改 {@code Item#use}:见 {@link EffectCardUseGuard} 的类注释——
 * 客户端无论如何都会发包,在 {@code use} 里返回 fail 拦不住服务端。
 */
@Mixin(Minecraft.class)
public abstract class AstralUseItemGuardMixin {

    @Inject(method = "startUseItem()V", at = @At("HEAD"), cancellable = true)
    private void astral$oneEffectCardPerPress(CallbackInfo ci) {
        if (EffectCardUseGuard.beginOrSuppressUse()) {
            ci.cancel();
        }
    }
}
