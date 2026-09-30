package com.merlinkitsune.astral_dice.mixin.bridge;

import com.merlinkitsune.astral_dice.platform.client.event.ScreenEvent;
import com.merlinkitsune.astral_dice.platform.event.LoaderBus;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * {@code ScreenEvent.Opening} 桥 —— 「界面即将被打开」。
 *
 * <p>Forge 在 {@code Minecraft#setScreen} 里派发该事件，允许监听者**替换**即将打开的界面
 * （{@code event.setNewScreen(...)}）。本模组的消费方（{@code TargetSelectionClient}）
 * 只用它来「一开菜单就取消目标选择会话」，不替换界面，但仍按完整语义实现替换能力。
 *
 * <p>⚠️ 注意 {@code setScreen} 自身会被 `setScreen(null)` 之类的收尾调用反复触发，
 * 因此事件在「开/关」两种情况下都会派发 —— 消费方自己按 {@code getScreen()} 判定。
 */
@Mixin(Minecraft.class)
public abstract class ClientScreenBridgeMixin {

    @Shadow
    public Screen screen;

    /**
     * 防止「替换界面后再次调用 setScreen」无限递归:替换只在**一次派发**内生效。
     */
    @Unique
    private boolean astralDice$replacingScreen = false;

    @Inject(method = "setScreen", at = @At("HEAD"))
    private void astralDice$bridgeScreenOpening(Screen newScreen, CallbackInfo ci) {
        if (this.astralDice$replacingScreen) {
            return;
        }
        // ⚠️ newScreen **可以为 null**:MC 内部大量用 `setScreen(null)` 来「关闭当前屏幕、回到游戏画面」,
        //    最典型的是 ReceivingLevelScreen.onClose(地形加载完成时)。
        //    Forge 侧同一路径**不派发** Opening —— 其 ScreenEvent 基类构造器同样是
        //    `Objects.requireNonNull(screen)`(javap 实证),而 Forge 客户端能正常进世界,
        //    说明它的 setScreen 补丁只在非 null 时构造事件。本类必须照做:
        //    否则 `Objects.requireNonNull` 抛 NPE → "Ticking screen" → 客户端崩溃。
        //    (2026-09-29 实测:该 NPE 导致**每次进入世界必崩**,服务端空跑完全测不出。)
        if (newScreen == null) {
            return;
        }
        Screen current = this.screen;
        if (current == newScreen) {
            return;
        }
        ScreenEvent.Opening event = new ScreenEvent.Opening(current, newScreen);
        LoaderBus.INSTANCE.post(event);
        Screen replacement = event.getNewScreen();
        if (replacement != null && replacement != newScreen) {
            // 监听者替换了目标界面 ⇒ 用替换值重跑一次,并把本次调用吞掉。
            // 标志位保证重跑那次直接放行(不再派发),避免自递归。
            this.astralDice$replacingScreen = true;
            try {
                ((Minecraft) (Object) this).setScreen(replacement);
            } finally {
                this.astralDice$replacingScreen = false;
            }
            ci.cancel();
        }
    }
}
