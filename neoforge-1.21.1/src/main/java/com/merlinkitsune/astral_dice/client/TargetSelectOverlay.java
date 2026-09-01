package com.merlinkitsune.astral_dice.client;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.LayeredDraw;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 目标选择模式常驻 HUD 层（注册于 {@code VanillaGuiLayers.CROSSHAIR} 之上）：
 * - 状态行：目标选择中；
 * - 目标行：当前准星目标名 + 距离（按敌我着色，无目标且刚确认过时显示「没有可指定的目标」）；
 * - 操作提示行：右键 / Enter 确认 · Esc 取消。
 * 仅在选择激活时渲染；状态切换记录调试日志（禁止每帧日志）。
 */
public final class TargetSelectOverlay implements LayeredDraw.Layer {
    private static final Logger LOGGER = LoggerFactory.getLogger(TargetSelectOverlay.class);

    public static final TargetSelectOverlay INSTANCE = new TargetSelectOverlay();

    private boolean wasActive;

    private TargetSelectOverlay() {
    }

    @Override
    public void render(GuiGraphics guiGraphics, DeltaTracker deltaTracker) {
        Minecraft mc = Minecraft.getInstance();
        boolean active = TargetSelectionClient.isActive();
        if (active != wasActive) {
            LOGGER.debug("[Astral Dice][TargetSelectOverlay] overlay {}", active ? "active" : "inactive");
            wasActive = active;
        }
        if (!active || mc.player == null) return;

        int centerX = guiGraphics.guiWidth() / 2;
        int y = guiGraphics.guiHeight() / 2 + 14;

        // 状态行
        Component status = Component.translatable("hud.astral_dice.target_select.active");
        guiGraphics.drawString(mc.font, status, centerX - mc.font.width(status) / 2, y, 0xFFFFFF, true);

        // 目标行（着色）
        LivingEntity target = TargetSelectionClient.currentTarget();
        if (target != null) {
            int dist = (int) Math.round(mc.player.distanceTo(target));
            int color = TargetSelectionClient.highlightColor(target);
            Component line = Component.translatable("hud.astral_dice.target_select.target",
                    target.getDisplayName(), dist);
            guiGraphics.drawString(mc.font, line, centerX - mc.font.width(line) / 2, y + 10, color, true);
        } else if (TargetSelectionClient.noTargetFlash()) {
            Component noTarget = Component.translatable("hud.astral_dice.target_select.no_target");
            guiGraphics.drawString(mc.font, noTarget, centerX - mc.font.width(noTarget) / 2, y + 10,
                    TargetSelectionClient.COLOR_HOSTILE, true);
        }

        // 操作提示行
        Component hint = Component.translatable("hud.astral_dice.target_select.hint");
        guiGraphics.drawString(mc.font, hint, centerX - mc.font.width(hint) / 2, y + 22, 0xAAAAAA, true);
    }
}
