package com.merlinkitsune.astral_dice.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 目标选择模式常驻 HUD 层(1.20.1 Forge 版,注册于 {@code VanillaGuiOverlay.CROSSHAIR} 之上,
 * 与 1.21.1 的 {@code LayeredDraw.Layer} 对应,此处实现 {@link IGuiOverlay})。
 *
 * <p>**只有一行**（2026-09-17 用户裁决，2026-09-18 追加类型标签，与 1.21.1 逐条对等）：
 * 当前准星目标的「目标名 + 距离 + 类型标签」，按敌我着色；无有效目标时**不画任何东西**
 * （旧版的状态行「目标选择中」、无目标闪烁行与操作提示行已全部移到 actionbar，见
 * {@link TargetSelectionClient} 的每 tick 提示刷新）—— 中央 HUD 不再与 actionbar 争夺同一片
 * 视线区域。仅在选择激活时渲染；状态切换记录调试日志（禁止每帧日志）。
 *
 * <p>渲染组成（整行仍用 {@code highlightColor} 的颜色绘制）：
 * {@code hud.astral_dice.target_select.target}（{@code 目标：%s（%s 格）}，参数个数不变）
 * + 字面量 {@code " · "} + {@code hud.astral_dice.target_select.tag.<后缀>}，
 * 后缀由 {@link TargetSelectionClient#targetTagKey(net.minecraft.world.entity.LivingEntity)}
 * 按敌我口径推导（hostile / teammate / pet / neutral / player）。
 */
public final class TargetSelectOverlay implements IGuiOverlay {
    private static final Logger LOGGER = LoggerFactory.getLogger(TargetSelectOverlay.class);

    public static final TargetSelectOverlay INSTANCE = new TargetSelectOverlay();

    private boolean wasActive;

    private TargetSelectOverlay() {
    }

    @Override
    public void render(ForgeGui gui, GuiGraphics guiGraphics, float partialTick, int width, int height) {
        Minecraft mc = Minecraft.getInstance();
        // F1（隐藏 HUD）守卫:本线原版门槛是 `!hideGui || screen != null`（forge 源 GameRenderer#render:949-953）
        // ⇒ 本 overlay 仅在 `hideGui && screen == null` 时天然不画,与之同条件的守卫是**逐例 no-op**;
        // 写成裸 `hideGui` 会在「F1 + 界面打开」时多隐藏一层、与本线原行为不符（R2-04）。此处的意义
        // 只是与 1.21.1 保持同形（1.21.1 原版无该整块跳过、模组层不被 `!hideGui` 包裹,必须自行守）。
        if (mc.options.hideGui && mc.screen == null) return;
        boolean active = TargetSelectionClient.isActive();
        if (active != wasActive) {
            LOGGER.debug("[Astral Dice][TargetSelectOverlay] overlay {}", active ? "active" : "inactive");
            wasActive = active;
        }
        if (!active || mc.player == null) return;

        LivingEntity target = TargetSelectionClient.currentTarget();
        if (target == null) return;

        int centerX = width / 2;
        int y = height / 2 + 14;
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
