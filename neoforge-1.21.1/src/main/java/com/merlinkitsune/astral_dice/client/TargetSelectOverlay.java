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
 * 目标选择模式常驻 HUD 层（注册于 {@code VanillaGuiLayers.CROSSHAIR} 之上）。
 *
 * <p>**只有一行**（2026-09-17 用户裁决）：当前准星目标的「目标名 + 距离」，按敌我着色；
 * 无有效目标时**不画任何东西**（状态行与操作提示行已全部移到 actionbar，见
 * {@link TargetSelectionClient} 的每 tick 提示刷新）—— 中央 HUD 不再与 actionbar 争夺同一片
 * 视线区域。仅在选择激活时渲染；状态切换记录调试日志（禁止每帧日志）。
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

        LivingEntity target = TargetSelectionClient.currentTarget();
        if (target == null) return;

        int centerX = guiGraphics.guiWidth() / 2;
        int y = guiGraphics.guiHeight() / 2 + 14;
        int dist = (int) Math.round(mc.player.distanceTo(target));
        int color = TargetSelectionClient.highlightColor(target);
        Component line = Component.translatable("hud.astral_dice.target_select.target",
                target.getDisplayName(), dist);
        guiGraphics.drawString(mc.font, line, centerX - mc.font.width(line) / 2, y, color, true);
    }
}
