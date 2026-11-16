package com.merlinkitsune.astral_dice.mixin.bridge;

import com.merlinkitsune.astral_dice.platform.client.event.InputEvent;
import com.merlinkitsune.astral_dice.platform.event.LoaderBus;

import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * {@code InputEvent.MouseButton.Pre} / {@code InputEvent.MouseScrollingEvent} 桥。
 *
 * <p>Forge 在 {@code MouseHandler#onPress} / {@code #onScroll} 里调
 * {@code ForgeHooksClient.onMouseButtonPre(...)} / {@code onMouseScroll(...)}，
 * 取消后**提前 return** ⇒ 原版逻辑与其它模组的鼠标绑定都不生效。
 * 这里注入同样两个 private 方法，并把事件设为可取消。
 *
 * <p>⚠️ 两个目标方法都带 {@code long window} 首参（该参数本模组用不到，但必须列出以对齐描述符）。
 * <p>⚠️ `MouseScrollingEvent` 的 4 个按键状态与鼠标坐标由 {@code Minecraft} 现取 ——
 * Forge 也是这么填的（{@code onScroll} 自身只有 window/deltaX/deltaY）。
 */
@Mixin(MouseHandler.class)
public abstract class ClientMouseBridgeMixin {

    @Inject(method = "onPress", at = @At("HEAD"), cancellable = true)
    private void astralDice$bridgeMouseButton(long window, int button, int action, int modifiers,
                                              CallbackInfo ci) {
        InputEvent.MouseButton.Pre event = new InputEvent.MouseButton.Pre(button, action, modifiers);
        LoaderBus.INSTANCE.post(event);
        if (event.isCanceled()) {
            ci.cancel();
        }
    }

    @Inject(method = "onScroll", at = @At("HEAD"), cancellable = true)
    private void astralDice$bridgeMouseScroll(long window, double deltaX, double deltaY, CallbackInfo ci) {
        Minecraft minecraft = Minecraft.getInstance();
        double mouseX = minecraft.mouseHandler.xpos();
        double mouseY = minecraft.mouseHandler.ypos();
        InputEvent.MouseScrollingEvent event = new InputEvent.MouseScrollingEvent(
                deltaY,
                minecraft.options.keyUp.isDown() || minecraft.options.keyLeft.isDown(),
                minecraft.options.keyJump.isDown(),
                minecraft.options.keyDown.isDown() || minecraft.options.keyRight.isDown(),
                mouseX, mouseY);
        LoaderBus.INSTANCE.post(event);
        if (event.isCanceled()) {
            ci.cancel();
        }
    }
}
