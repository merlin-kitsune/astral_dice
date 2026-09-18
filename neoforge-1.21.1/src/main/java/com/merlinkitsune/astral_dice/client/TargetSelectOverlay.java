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
        // F1（隐藏 HUD）守卫:1.20.1 原版 GameRenderer 在 hideGui 时整块跳过 gui.render
        // （forge 源 GameRenderer#render:949-953），该线的 overlay 天然不画;1.21.1 原版无此整块跳过
        // （neoforge 源 GameRenderer#render:1075-1079 只包住 renderItemActivationAnimation）
        // ⇒ 必须自行守,否则两发布线在 F1 下行为不一致（2026-09-18 t46,用户裁决「加守卫对齐 1.20.1」）。
        // 同一守卫在 1.20.1 侧冗余,但两线保持同形。
        if (mc.options.hideGui) return;
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
