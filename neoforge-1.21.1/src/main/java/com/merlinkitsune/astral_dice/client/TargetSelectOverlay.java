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
 * <p>**只有一行**（2026-09-17 用户裁决，2026-09-18 追加类型标签）：当前准星目标的
 * 「目标名 + 距离 + 类型标签」，按敌我着色；无有效目标时**不画任何东西**（状态行与操作提示行
 * 已全部移到 actionbar，见 {@link TargetSelectionClient} 的每 tick 提示刷新）——
 * 中央 HUD 不再与 actionbar 争夺同一片视线区域。仅在选择激活时渲染；状态切换记录调试日志
 * （禁止每帧日志）。
 *
 * <p>渲染组成（整行仍用 {@code highlightColor} 的颜色绘制）：
 * {@code hud.astral_dice.target_select.target}（{@code 目标：%s（%s 格）}，参数个数不变）
 * + 字面量 {@code " · "} + {@code hud.astral_dice.target_select.tag.<后缀>}，
 * 后缀由 {@link TargetSelectionClient#targetTagKey(net.minecraft.world.entity.LivingEntity)}
 * 按敌我口径推导（hostile / teammate / pet / neutral / player）。
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
        // F1（隐藏 HUD）守卫:条件取 `hideGui && screen == null`,精确镜像 1.20.1 原版的绘制门槛
        // `if (!hideGui || screen != null) { … gui.render(…) }`（forge 源 GameRenderer#render:949-953）
        // ⇒ 该线行为逐例不变（守卫对它是彻底的 no-op）;而 1.21.1 原版**不**整块跳过 gui.render
        // （neoforge 源 :1075-1079 只包住 renderItemActivationAnimation），且 GuiLayerManager 摊平时
        // 只把**原版层**包进 `!hideGui`、模组层不包裹 ⇒ 必须自行守,否则按 F1 时本模组 overlay 仍显示、
        // 两发布线不一致（2026-09-18 t46 用户裁决「加守卫对齐 1.20.1」;t46b 按 R2-04 补 `screen == null`）。
        if (mc.options.hideGui && mc.screen == null) return;
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
        Component head = Component.translatable("hud.astral_dice.target_select.target",
                target.getDisplayName(), dist);
        Component tag = Component.translatable("hud.astral_dice.target_select.tag."
                + TargetSelectionClient.targetTagKey(target));
        Component line = Component.empty().append(head).append(Component.literal(" · ")).append(tag);
        guiGraphics.drawString(mc.font, line, centerX - mc.font.width(line) / 2, y, color, true);
    }
}
