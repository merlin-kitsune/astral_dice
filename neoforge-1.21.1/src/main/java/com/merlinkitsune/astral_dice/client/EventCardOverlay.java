package com.merlinkitsune.astral_dice.client;

import com.merlinkitsune.astral_dice.AstralDiceMod;
import com.merlinkitsune.astral_dice.event.api.AstralEvent;
import com.merlinkitsune.astral_dice.event.AstralEventRegistry;
import com.merlinkitsune.astral_dice.event.EventCardService;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.LayeredDraw;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.List;

/**
 * 事件效果卡覆盖层（客户端）。
 *
 * <p><b>口径（用户裁决 2026-10-10，完全照商店卡牌 `screen/CardShopScreen`）</b>：
 * 卡框 `textures/gui/event/card_frame.png`（44×67，= `fanny_skill_event.png`）先画，事件图
 * `textures/gui/event/<path>.png`（64×64）等比缩到 **38×38 贴 (3,3)** —— 与商店的
 * {@code FACE_SIZE=38 / FACE_X_OFF=3 / FACE_Y_OFF=3} 完全一致；两者**在同一 pose 内**绘制
 * ⇒ 缩放时框与图永不失配。卡名 y=47、计时器 y=56（均在框内），**MC 自带字体**、居中、随卡缩放。
 *
 * <p>动画：单时间源 + 单进度 —— 进场 0.2s（scale 0.775→1.0、alpha 0→255）｜停留｜收起 0.2s
 * （**scale 1.0→0.8 缩小**、alpha 255→0）。
 *
 * <p><b>与服务端结算刻同源（2026-10-11 修复「还没等展示完就触发 / 没有同步」）</b>：可见窗口
 * = {@link EventCardService#CARD_ANIM_TICKS} 刻（= 1.4s，本类 {@link #ANIM_MS}），服务端唯一结算点
 * = {@link EventCardService#DELAY_TICKS} 刻（= 1.9s），两者之间隔着
 * {@link EventCardService#SETTLE_BUFFER_TICKS} 刻（0.5s）的**静默缓冲** ⇒
 * 卡在屏幕上（alpha > 0）的整段时间里，服务端**零效果 / 零伤害 / 零事件音效**。
 * 修复前的写法是「动画写死 1.4s + 服务端 38t 结算」，两套时钟各算一套、无法保证恒等关系。
 */
public final class EventCardOverlay implements LayeredDraw.Layer {
    public static final ResourceLocation LAYER_ID =
            ResourceLocation.fromNamespaceAndPath(AstralDiceMod.MODID, "event_card");
    public static final EventCardOverlay LAYER = new EventCardOverlay();

    /** 事件卡框宽（公开：事件选择界面按同口径命中判定/排布）。 */
    public static final int FRAME_W = 44;
    /** 事件卡框高（公开：同上）。 */
    public static final int FRAME_H = 67;
    private static final int PANE_X = 2;      // 图片格实测（card_frame.png: x=2..41）
    private static final int PANE_Y = 2;      // y=2..40
    private static final int PANE_W = 40;
    private static final int PANE_H = 39;


    private static final int NAME_Y_OFF = 45;   // 文字格 y=42..64 内居中
    private static final int TIME_Y_OFF = 54;
    private static final long IN_MS = 200L;    // 进场（用户裁决 2026-10-10：渐入 0.2s）
    private static final long OUT_MS = 200L;   // 收起（用户裁决：渐出 0.2s）
    /**
     * **卡片可见窗口** = 服务端 {@link EventCardService#CARD_ANIM_TICKS} 刻 × 50ms = 28t = 1.4s。
     *
     * <p>与服务端结算刻同源（都从 {@link EventCardService#DELAY_TICKS} 推导），不再写死数字。
     */
    private static final long ANIM_MS = EventCardService.CARD_ANIM_TICKS * 50L;
    /** 停留 = 可见窗口 - 进场 - 收起（随 {@link #ANIM_MS} 自动推导，不再写死 1.0s）。 */
    private static final long HOLD_MS = ANIM_MS - IN_MS - OUT_MS;
    /**
     * 卡实例的存活上限 = <b>服务端唯一结算点</b> {@link EventCardService#DELAY_TICKS} 刻 × 50ms（1.9s）。
     *
     * <p>动画在 {@link #ANIM_MS}（1.4s）就播完（alpha 已归零），这里只是把**已不可见**的实例丢掉；
     * 之所以留到结算刻才丢，是为了让「卡还在列表里」这件事恰好不超过结算刻 —— 即
     * {@link EventCardService#SETTLE_BUFFER_TICKS} 刻（0.5s）的静默窗口对玩家与服务端是同一个。
     */
    private static final long TOTAL_MS = EventCardService.DELAY_TICKS * 50L;
    /** 收起阶段的**终点缩放**：用户裁决「淡出 = 缩小淡出」（原实现写成放大淡出，方向错了）。 */
    private static final float OUT_END_SCALE = 0.8F;
    private static final ResourceLocation FRAME_TEX =
            ResourceLocation.fromNamespaceAndPath(AstralDiceMod.MODID, "textures/gui/event/card_frame.png");

    private static final org.slf4j.Logger LOGGER = com.mojang.logging.LogUtils.getLogger();

    private static final List<Card> CARDS = new ArrayList<>();

    private EventCardOverlay() {
    }

    /** 收到 S2C 弹卡包（主线程）。 */
    public static void show(ResourceLocation eventId, int durationTicks, int tier) {
        long startMs = System.currentTimeMillis();
        CARDS.add(new Card(eventId, durationTicks, tier, startMs));
        // 机器行（客户端时间轴取证）:与服务端 AP_EVENT_CARD: shown:<id>:exec_at=<tick> 配对，
        // 用于断言「卡可见窗口（show → hide）严格短于服务端 38t 结算」。
        LOGGER.info("AP_EVENT_CARD_CLIENT: show:{}:tier={}:anim_ms={}:total_ms={}",
                eventId, tier, ANIM_MS, TOTAL_MS);
    }

    private record Card(ResourceLocation eventId, int durationTicks, int tier, long startMs) {
    }

    @Override
    public void render(GuiGraphics guiGraphics, DeltaTracker deltaTracker) {
        if (CARDS.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui) { CARDS.clear(); return; }
        long now = System.currentTimeMillis();
        CARDS.removeIf(c -> {
            if (now - c.startMs() < TOTAL_MS) return false;
            LOGGER.info("AP_EVENT_CARD_CLIENT: hide:{}:alive_ms={}", c.eventId(), now - c.startMs());
            return true;
        });
        if (CARDS.isEmpty()) return;
        int sw = guiGraphics.guiWidth();
        int sh = guiGraphics.guiHeight();
        for (int i = 0; i < CARDS.size(); i++) {
            Card card = CARDS.get(i);
            long t = now - card.startMs();
            float scale; float alpha;
            if (t < IN_MS) {
                float p = t / (float) IN_MS;
                scale = 0.775F + 0.225F * p;
                alpha = p;
            } else if (t < IN_MS + HOLD_MS) {
                scale = 1.0F; alpha = 1.0F;
            } else {
                // 收起阶段（t ∈ [IN+HOLD, ANIM_MS]）；此后 p 夹到 1 ⇒ alpha=0、scale=OUT_END_SCALE，
                // 卡在 [ANIM_MS, TOTAL_MS) 这段缓冲里已经不可见（不夹会让 alpha 变成负数）。
                float p = Mth.clamp((t - IN_MS - HOLD_MS) / (float) OUT_MS, 0.0F, 1.0F);
                scale = 1.0F - (1.0F - OUT_END_SCALE) * p;
                alpha = 1.0F - p;
            }
            drawCard(guiGraphics, mc, card, sw / 2, sh / 2 + i * 76, scale, alpha);
        }
    }

    private void drawCard(GuiGraphics g, Minecraft mc, Card card, int centerX, int centerY,
                          float scale, float alpha) {
        ResourceLocation icon = ResourceLocation.fromNamespaceAndPath(card.eventId().getNamespace(),
                "textures/gui/event/" + iconPath(card) + ".png");
        g.pose().pushPose();
        // 垂直对齐：FRAME_H 为奇数时 quad 中心天然低 0.5 GUI px（=2 物理 px @guiScale4）⇒ 上移半像素
        float yOff = (FRAME_H % 2 == 0) ? 0.0F : -0.5F;
        g.pose().translate(centerX, centerY + yOff, 300.0F);
        g.pose().scale(scale, scale, 1.0F);
        int x = -FRAME_W / 2;
        int y = -FRAME_H / 2;
        int a = Mth.clamp((int) (alpha * 255.0F), 0, 255);
        // ① 卡框（44×67）
        // ⚠️ 实机连拍实测（2026-10-10）：GuiGraphics#setColor 在本版本的 blit 路径**不生效**
        // ⇒ 卡框与事件图完全不透明（看起来是「不透明弹出 + 只缩小 + 只文字渐隐」）。改为直接设 shader 颜色。
        com.mojang.blaze3d.systems.RenderSystem.enableBlend();
        // 先把**更早 layer 已排队但未 flush 的几何（含原版准星）以白色冲掉**，再给卡设 alpha；
        // 否则 g.flush() 会把它们一起用卡的 alpha 画掉（淡入淡出期间准星会同步变淡）。
        g.flush();
        com.mojang.blaze3d.systems.RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, a / 255.0F);
        g.blit(FRAME_TEX, x, y, 0.0F, 0.0F, FRAME_W, FRAME_H, FRAME_W, FRAME_H);
        // ② 事件图 64×64 → 38×38 贴 (3,3)（与商店卡牌同口径）
        drawFaceCentered(g, icon, x, y);
        // 1.21.1/1.20.1 的 GuiGraphics 是**批处理**：shader 颜色在 flush 时生效 ⇒
        // 必须在此处显式 flush，否则卡框/事件图会用后续文字的颜色（= 完全不透明）画出来。
        g.flush();
        // ③ 文字（MC 字体、居中、随卡缩放）——名称按类型染色，计时器同色
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

    /**
     * 把一张事件卡画在**屏幕绝对坐标** (frameX, frameY)（左上角）——供事件选择界面复用。
     *
     * <p>与 {@link #drawCard} 逐字同源（同一套「框架图 + 事件图标 + MC 字体文本」口径），
     * 只去掉「居中 + 缩放 + 淡入淡出」那层变换（选择界面里的卡是静止、不缩放的）。
     *
     * @param frameTex 卡框底图：默认 {@code textures/gui/shop/fanny_skill_event.png}；
     *                 鼠标指向 / 单击选中后传 {@code ..._select.png}（用户需求：指向高亮、单击后底图替换）
     */
    public static void drawCardAt(GuiGraphics g, Minecraft mc, ResourceLocation frameTex,
                                  ResourceLocation eventId, int durationTicks, int tier,
                                  int frameX, int frameY) {
        ResourceLocation icon = ResourceLocation.fromNamespaceAndPath(eventId.getNamespace(),
                "textures/gui/event/" + iconPath(eventId, tier) + ".png");
        int a = 255;
        com.mojang.blaze3d.systems.RenderSystem.enableBlend();
        g.flush();
        com.mojang.blaze3d.systems.RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        g.blit(frameTex, frameX, frameY, 0.0F, 0.0F, FRAME_W, FRAME_H, FRAME_W, FRAME_H);
        drawFaceCentered(g, icon, frameX, frameY);
        g.flush();
        AstralEvent ev = AstralEventRegistry.get(eventId).orElse(null);
        int color = (ev == null ? 0xFFFFFF : ev.polarity().color().getColor()) | (a << 24);
        Component name = Component.translatable("event.astral_dice." + eventId.getPath() + ".name");
        g.drawString(mc.font, name, frameX + FRAME_W / 2 - mc.font.width(name) / 2, frameY + NAME_Y_OFF, color, false);
        if (durationTicks > 0) {
            Component timer = Component.literal(formatTicks(durationTicks));
            g.drawString(mc.font, timer, frameX + FRAME_W / 2 - mc.font.width(timer) / 2, frameY + TIME_Y_OFF, color, false);
        }
        com.mojang.blaze3d.systems.RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        g.setColor(1.0F, 1.0F, 1.0F, 1.0F);
    }

    /**
     * 图标路径（公开版：供事件选择界面在**没有 Card 实例**时复用）：
     * **阶级 > 0 时取 `<id>_<suffix>`**（「调查阶段」4 张分阶图 `_1/_2/_3/_revelation`），否则取 `<id>`。
     */
    public static String iconPath(ResourceLocation eventId, int tier) {
        String base = eventId.getPath();
        if (tier > 0) {
            String suffix = com.merlinkitsune.astral_dice.event.api.AstralEventTier.of(tier).iconSuffix();
            if (suffix != null && !suffix.isEmpty()) return base + "_" + suffix;
        }
        return base;
    }
    /** 刻 → `m:ss`。 */
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
    /**
     * 图标路径：**阶级 > 0 时取 `<id>_<suffix>`**（「调查阶段」一条事件 4 张分阶图：
     * `_1 / _2 / _3 / _revelation`），否则取 `<id>`（用户裁决 2026-10-10：它与其他事件触发方式一致，
     * 仅图标随阶级切换）。
     */
    private static String iconPath(Card card) {
        return iconPath(card.eventId(), card.tier());
    }
}
