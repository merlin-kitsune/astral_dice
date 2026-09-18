package com.merlinkitsune.astral_dice.client;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.client.gui.GuiLayer;
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
 *
 * <h2>26.1.2 与 1.21.1 基准的差异（全部为平台 API，语义零差异）</h2>
 * <ol>
 *   <li><b>层接口</b>：{@code net.minecraft.client.gui.LayeredDraw.Layer} →
 *       {@code net.neoforged.neoforge.client.gui.GuiLayer}
 *       （26.1.2 的 {@code LayeredDraw} 整类已不存在；NeoForge 侧接口定义见
 *       {@code neoforge/client/gui/GuiLayer.java:12}，
 *       唯一抽象方法 {@code void render(GuiGraphicsExtractor, DeltaTracker)}）。</li>
 *   <li><b>绘制上下文</b>：{@code net.minecraft.client.gui.GuiGraphics} →
 *       {@code net.minecraft.client.gui.GuiGraphicsExtractor}
 *       （26.1.2 的 GUI 也是两相：{@code Gui#extractRenderState} 收集 → 由 GUI 渲染状态统一落盘；
 *       见 {@code Gui.java:215-222}）。{@code guiWidth()/guiHeight()} 同名同义
 *       （{@code GuiGraphicsExtractor.java:131/135}）。</li>
 *   <li><b>文字绘制</b>：{@code guiGraphics.drawString(font, component, x, y, color, dropShadow)}
 *       → {@code guiGraphics.text(font, component, x, y, color, dropShadow)}
 *       （{@code GuiGraphicsExtractor.java:261}，参数个数与含义逐项对应）。</li>
 *   <li><b>层注册</b>：{@link ModClientEvents#registerGuiLayers} 里
 *       {@code RegisterGuiLayersEvent#registerAbove(Identifier, Identifier, GuiLayer)}
 *       签名与 1.21.1 相同（{@code neoforge/client/event/RegisterGuiLayersEvent.java:71}），
 *       注册 id 与两发布线对齐为 {@code astral_dice:target_select}。</li>
 * </ol>
 *
 * <p><b>本类不使用 {@code pose()}</b>：1.21.1 与 26.1.2 的 {@code pose()} 返回类型不同
 * （{@code PoseStack} ↔ JOML {@code Matrix3x2fStack}），但基准实现本就只用绝对坐标写一行字，
 * 不推栈也不缩放，故该差异对本类**不可观测**。
 */
public final class TargetSelectOverlay implements GuiLayer {
    private static final Logger LOGGER = LoggerFactory.getLogger(TargetSelectOverlay.class);

    public static final TargetSelectOverlay INSTANCE = new TargetSelectOverlay();

    private boolean wasActive;

    private TargetSelectOverlay() {
    }

    @Override
    public void render(GuiGraphicsExtractor guiGraphics, DeltaTracker deltaTracker) {
        Minecraft mc = Minecraft.getInstance();
        // F1（隐藏 HUD）守卫:条件取 `hideGui && screen == null`,与两发布线逐字同形
        // （1.21.1 `client/TargetSelectOverlay.java:46`、`client/ModClientEvents.java:49`;
        // 1.20.1 对应两处同为该形式 —— 见 AGENTS.md「目标选择器规范 · 现行口径」第 13 条与 R2-04）。
        // 26.1.2 侧为何仍需自行守:原版 `Gui#registerVanillaLayers`（Gui.java:224-257）把**每个原版层**
        // 单独包进 `BooleanSupplier guiVisible = () -> !this.minecraft.options.hideGui`（:225）
        // 或 `survivalVisible`（:226）,而**模组层**经 `GuiLayerManager#add(Identifier, GuiLayer)`
        // （GuiLayerManager.java:36-39,无 shouldRender 参数）加入、**不被包裹**
        // ⇒ 不自行守时按 F1 本模组 overlay 仍会显示。
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
        guiGraphics.text(mc.font, line, centerX - mc.font.width(line) / 2, y, color, true);
    }
}
