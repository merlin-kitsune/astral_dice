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
 * 目标选择模式的键盘输入锁定（Forge 1.20.1 的 {@code InputEvent.Key} 不可取消，只能 Mixin 拦截）。
 *
 * 规则（选择激活时）：
 * - Esc：PRESS 取消选择；RELEASE/REPEAT 也一律取消——注入环境下 Esc 的 KEYUP 会被再次送进
 *   keyPress（表现如同二次 PRESS），若不拦截会打开暂停菜单（1.20.1 实测，见 TC7）；
 * - 按键释放（RELEASE，Esc 除外）放行，避免进入选择前已按住的键卡死状态；
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

    // 注入环境实测(1.20.1):PostMessage 投递的 Esc KEYUP 会被 GLFW/游戏再次以 PRESS 送进 keyPress。
    // Esc PRESS 取消选择(active→false)后,紧随的这个"二次 PRESS"必须一并吞掉,否则会打开暂停菜单(TC7)。
    // 用时间窗代替布尔标志:真实键盘 RELEASE 或注入 KEYUP 都在毫秒级到达;若该事件被注入队列丢弃,
    // 时间窗过期后不会误吞下一次真正的 Esc(暂停菜单)按键。
    private static long swallowEscUntil = 0L;

    @Inject(method = "keyPress", at = @At("HEAD"), cancellable = true)
    private void astralDice$lockInput(long windowPointer, int key, int scanCode, int action, int modifiers,
                                      CallbackInfo ci) {
        if (key == GLFW.GLFW_KEY_ESCAPE) {
            if (TargetSelectionClient.isActive()) {
                // 选择激活:取消选择并吞掉本次事件
                TargetSelectionClient.cancelByEscape();
                swallowEscUntil = System.currentTimeMillis() + 5000L;
                ci.cancel();
                return;
            }
            if (System.currentTimeMillis() < swallowEscUntil) {
                // 选择刚被 Esc 取消,紧随的"释放"(注入环境以 PRESS 送达)吞掉,防暂停菜单
                swallowEscUntil = 0L;
                ci.cancel();
                return;
            }
            // 选择未激活且无待吞 Esc:放行(打开暂停菜单等原版行为)
            return;
        }

        // 释放事件必须放行(除 Esc 已在上方处理)：被拦截键进入选择前若已按下，其释放需正常送达，否则键状态卡死
        if (action == GLFW.GLFW_RELEASE) return;

        if (!TargetSelectionClient.isActive()) return;

        if (TargetSelectionClient.isAllowedInputKey(key, scanCode)) return;

        LOGGER.debug("[Astral Dice][TargetSelectionMixin] blocked key={} scancode={}", key, scanCode);
        ci.cancel();
    }
}
