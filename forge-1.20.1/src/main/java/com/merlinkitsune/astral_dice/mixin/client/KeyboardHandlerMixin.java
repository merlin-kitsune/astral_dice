package com.merlinkitsune.astral_dice.mixin.client;

import com.merlinkitsune.astral_dice.client.TargetSelectionClient;
import net.minecraft.client.KeyboardHandler;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 目标选择模式的键盘输入锁定（NeoForge 的 {@code InputEvent.Key} 不可取消，只能 Mixin 拦截）。
 *
 * 规则（选择激活时）：
 * - 按键释放（RELEASE）一律放行，避免进入选择前已按住的键卡死状态；
 * - Esc：取消选择并阻止后续处理（同时阻止暂停界面打开）；
 * - 移动键 / 确认键 Enter / 主动技能键 J：白名单放行（见
 *   {@link TargetSelectionClient#isAllowedInputKey}）；
 * - 其余键盘按键（含 F3/E/T/H 与第三方模组按键：Xaero 地图、FTB 区块等）全部取消，
 *   使对应 KeyMapping 状态不更新，从根源阻断其他模组按键能力。
 *
 * 兼容性：开发/测试环境(userdev)为 Mojmap（编译/运行同名），无需 refmap；生产重混淆环境需补充 refmap(后续)。
 */
@Mixin(KeyboardHandler.class)
public abstract class KeyboardHandlerMixin {
    private static final Logger LOGGER = LoggerFactory.getLogger(KeyboardHandlerMixin.class);

    @Inject(method = "keyPress", at = @At("HEAD"), cancellable = true)
    private void astralDice$lockInput(long windowPointer, int key, int scanCode, int action, int modifiers,
                                      CallbackInfo ci) {
        if (!TargetSelectionClient.isActive()) return;
        // 释放事件必须放行：被拦截键进入选择前若已按下，其释放需正常送达，否则键状态卡死
        if (action == GLFW.GLFW_RELEASE) return;

        if (key == GLFW.GLFW_KEY_ESCAPE) {
            TargetSelectionClient.cancelByEscape();
            ci.cancel();
            return;
        }

        if (TargetSelectionClient.isAllowedInputKey(key, scanCode)) return;

        LOGGER.debug("[Astral Dice][TargetSelectionMixin] blocked key={} scancode={}", key, scanCode);
        ci.cancel();
    }
}
