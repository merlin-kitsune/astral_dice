package com.merlinkitsune.astral_dice.client;

import com.merlinkitsune.astral_dice.component.GameplayConstants;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * 客户端 actionbar 管理器:以指定时长显示消息,并在最后 1 秒(默认 20 tick)淡出。
 * 总时长上限取 GameplayConstants.ACTIONBAR_DURATION_TICKS(默认 5 秒,由配置文件控制),超出部分被截断,避免长时间显示。
 */
public final class ActionBarManager {
    private static Component message;
    private static long endTick;

    private ActionBarManager() {
    }

    // 显示消息:实际时长取 min(指定时长, 总时长上限),防止超时长时间显示
    public static void show(Component msg, int ticks) {
        message = msg;
        int capped = Math.max(1, Math.min(ticks, GameplayConstants.ACTIONBAR_DURATION_TICKS));
        endTick = currentTick() + capped;
    }

    public static void show(Component msg) {
        show(msg, GameplayConstants.ACTIONBAR_DURATION_TICKS);
    }

    public static void clear() {
        message = null;
    }

    // 当前是否正在显示指定消息
    public static boolean isShowing(Component msg) {
        return message != null && message.equals(msg);
    }

    private static long currentTick() {
        Minecraft mc = Minecraft.getInstance();
        return mc.level != null ? mc.level.getGameTime() : 0;
    }

    public static void render(GuiGraphics guiGraphics, DeltaTracker deltaTracker) {
        if (message == null) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.font == null) {
            message = null;
            return;
        }
        long remaining = endTick - mc.level.getGameTime();
        if (remaining <= 0) {
            message = null;
            return;
        }
        int fadeTicks = GameplayConstants.ACTIONBAR_FADE_TICKS;
        int alpha = remaining > fadeTicks ? 255 : (int) (remaining * 255 / (double) fadeTicks);
        int x = guiGraphics.guiWidth() / 2 - mc.font.width(message) / 2;
        int y = guiGraphics.guiHeight() - 58;
        int color = (alpha << 24) | 0xFFFFFF;
        guiGraphics.drawString(mc.font, message, x, y, color, true);
    }
}
