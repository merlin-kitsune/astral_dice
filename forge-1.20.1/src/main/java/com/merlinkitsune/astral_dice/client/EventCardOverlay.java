package com.merlinkitsune.astral_dice.client;

import com.merlinkitsune.astral_dice.AstralDiceMod;
import com.merlinkitsune.astral_dice.event.AstralEventRegistry;
import com.merlinkitsune.astral_dice.event.api.AstralEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;

import java.util.ArrayList;
import java.util.List;

/** 事件效果卡覆盖层（1.20.1 线，IGuiOverlay）。口径与 1.21.1 版一致：同一 pose 内画 卡框 44×67 +
 *  事件图 38×38 贴 (3,3) + MC 字体两行居中文字 ⇒ 框/图/文字随整卡同步缩放。 */
public final class EventCardOverlay {
    public static final ResourceLocation LAYER_ID =
            ResourceLocation.fromNamespaceAndPath(AstralDiceMod.MODID, "event_card");

    private static final int FRAME_W = 44;
    private static final int FRAME_H = 67;
    private static final int PANE_X = 2;      // 图片格实测（card_frame.png: x=2..41）
    private static final int PANE_Y = 2;      // y=2..40
    private static final int PANE_W = 40;
    private static final int PANE_H = 39;


    private static final int NAME_Y_OFF = 45;   // 文字格 y=42..64 内居中
    private static final int TIME_Y_OFF = 54;
    private static final long IN_MS = 200L;    // 进场（用户裁决 2026-10-10：渐入 0.2s）
    private static final long HOLD_MS = 1000L; // 停留（用户裁决：1.0s）
    private static final long OUT_MS = 200L;   // 收起（用户裁决：渐出 0.2s）
    /** 收起阶段的**终点缩放**：用户裁决「淡出 = 缩小淡出」（原实现写成放大淡出，方向错了）。 */
    private static final float OUT_END_SCALE = 0.8F;
    private static final ResourceLocation FRAME_TEX =
            ResourceLocation.fromNamespaceAndPath(AstralDiceMod.MODID, "textures/gui/event/card_frame.png");

    private static final List<Card> CARDS = new ArrayList<>();

    private EventCardOverlay() {
    }

    private record Card(ResourceLocation eventId, int durationTicks, long startMs) {
    }

    public static void show(ResourceLocation eventId, int durationTicks) {
        CARDS.add(new Card(eventId, durationTicks, System.currentTimeMillis()));
    }

    /** 供 ModClientEvents#registerGuiOverlays 注册。 */
    public static final IGuiOverlay OVERLAY = (gui, g, partialTick, width, height) -> render(g);

    private static void render(GuiGraphics g) {
        if (CARDS.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui) { CARDS.clear(); return; }
        long now = System.currentTimeMillis();
        CARDS.removeIf(c -> now - c.startMs() >= IN_MS + HOLD_MS + OUT_MS);
        int sw = g.guiWidth();
        int sh = g.guiHeight();
        for (int i = 0; i < CARDS.size(); i++) {
            Card card = CARDS.get(i);
            long t = now - card.startMs();
            float scale; float alpha;
            if (t < IN_MS) {
                float p = t / (float) IN_MS;
                scale = 0.775F + 0.225F * p; alpha = p;
            } else if (t < IN_MS + HOLD_MS) {
                scale = 1.0F; alpha = 1.0F;
            } else {
                float p = (t - IN_MS - HOLD_MS) / (float) OUT_MS;
                scale = 1.0F - (1.0F - OUT_END_SCALE) * p; alpha = 1.0F - p;
            }
            drawCard(g, mc, card, sw / 2, sh / 2 + i * 76, scale, alpha);
        }
    }

    private static void drawCard(GuiGraphics g, Minecraft mc, Card card, int centerX, int centerY,
                                 float scale, float alpha) {
        ResourceLocation icon = ResourceLocation.fromNamespaceAndPath(card.eventId().getNamespace(),
                "textures/gui/event/" + card.eventId().getPath() + ".png");
        g.pose().pushPose();
        // 垂直对齐：FRAME_H 为奇数时 quad 中心天然低 0.5 GUI px（=2 物理 px @guiScale4）⇒ 上移半像素
        float yOff = (FRAME_H % 2 == 0) ? 0.0F : -0.5F;
        g.pose().translate(centerX, centerY + yOff, 300.0F);
        g.pose().scale(scale, scale, 1.0F);
        int x = -FRAME_W / 2;
        int y = -FRAME_H / 2;
        int a = Mth.clamp((int) (alpha * 255.0F), 0, 255);
        // ⚠️ 实机连拍实测（2026-10-10）：GuiGraphics#setColor 在本版本的 blit 路径**不生效**
        // ⇒ 卡框与事件图完全不透明（看起来是「不透明弹出 + 只缩小 + 只文字渐隐」）。改为直接设 shader 颜色。
        com.mojang.blaze3d.systems.RenderSystem.enableBlend();
        // 先把**更早 layer 已排队但未 flush 的几何（含原版准星）以白色冲掉**，再给卡设 alpha；
        // 否则 g.flush() 会把它们一起用卡的 alpha 画掉（淡入淡出期间准星会同步变淡）。
        g.flush();
        com.mojang.blaze3d.systems.RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, a / 255.0F);
        g.blit(FRAME_TEX, x, y, 0, 0, FRAME_W, FRAME_H, FRAME_W, FRAME_H);
        drawFaceCentered(g, icon, x, y);
        // 1.21.1/1.20.1 的 GuiGraphics 是**批处理**：shader 颜色在 flush 时生效 ⇒
        // 必须在此处显式 flush，否则卡框/事件图会用后续文字的颜色（= 完全不透明）画出来。
        g.flush();
        AstralEvent ev = AstralEventRegistry.get(card.eventId()).orElse(null);
        int color = (ev == null ? 0xFFFFFF : ev.polarity().color().getColor()) | (a << 24);
        Component name = Component.translatable("event.astral_dice." + card.eventId().getPath() + ".name");
        g.drawString(mc.font, name, x + FRAME_W / 2 - mc.font.width(name) / 2, y + NAME_Y_OFF, color, false);
        if (card.durationTicks() > 0) {
            Component timer = Component.literal(formatTicks(card.durationTicks()));
            g.drawString(mc.font, timer, x + FRAME_W / 2 - mc.font.width(timer) / 2, y + TIME_Y_OFF, color, false);
        }
        com.mojang.blaze3d.systems.RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        g.setColor(1.0F, 1.0F, 1.0F, 1.0F);
        g.pose().popPose();
    }

    private static String formatTicks(int ticks) {
        int total = Math.max(0, ticks) / 20;
        return String.format("%d:%02d", total / 60, total % 60);
    }
    // ────────────────────── 事件图「按内容居中」绘制（2026-10-10 修正） ──────────────────────

    /** 事件图的内容矩形缓存（x,y,w,h；来自 alpha>8 的 bbox）。 */
    private static final java.util.Map<ResourceLocation, int[]> RECT_CACHE = new java.util.HashMap<>();

    /**
     * 把事件图**按可见内容**居中并等比适配卡框的图片格。
     *
     * <p>为什么不能整张 64×64 直接缩放：卡面美术四周的透明边距**不对称**（实测
     * `world_collapse` 上 16 / 下 10、`crowded_passage` 左 2 / 右 4），整张画会让画面看着偏心。
     */
    private static void drawFaceCentered(GuiGraphics g, ResourceLocation tex, int frameX, int frameY) {
        int[] r = RECT_CACHE.computeIfAbsent(tex, EventCardOverlay::measureContent);
        if (r == null || r[2] <= 0 || r[3] <= 0) {
            g.blit(tex, frameX + PANE_X, frameY + PANE_Y, 0.0F, 0.0F, PANE_W, PANE_H, 64, 64);
            return;
        }
        float s = Math.min(PANE_W / (float) r[2], PANE_H / (float) r[3]);
        int dw = Math.max(1, Math.round(r[2] * s));
        int dh = Math.max(1, Math.round(r[3] * s));
        int dx = frameX + PANE_X + (PANE_W - dw) / 2;
        int dy = frameY + PANE_Y + (PANE_H - dh) / 2;
        g.blit(tex, dx, dy, dw, dh, (float) r[0], (float) r[1], r[2], r[3], 64, 64);
    }

    /** 量测事件图的内容矩形（缓存一次）；失败返回 null。 */
    private static int[] measureContent(ResourceLocation tex) {
        var mc = Minecraft.getInstance();
        if (mc == null) return null;
        var res = mc.getResourceManager().getResource(tex);
        if (res.isEmpty()) return null;
        try (var in = res.get().open()) {
            var img = com.mojang.blaze3d.platform.NativeImage.read(in);
            int w = img.getWidth(), h = img.getHeight();
            int minX = w, maxX = -1, minY = h, maxY = -1;
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    if (((img.getPixelRGBA(x, y) >>> 24) & 0xFF) > 8) {
                        if (x < minX) minX = x;
                        if (x > maxX) maxX = x;
                        if (y < minY) minY = y;
                        if (y > maxY) maxY = y;
                    }
                }
            }
            img.close();
            if (maxX < 0) return null;
            return new int[] { minX, minY, maxX - minX + 1, maxY - minY + 1 };
        } catch (Exception ex) {
            return null;
        }
    }
}
