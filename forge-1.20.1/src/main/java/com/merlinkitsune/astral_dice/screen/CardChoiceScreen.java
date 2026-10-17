package com.merlinkitsune.astral_dice.screen;

import com.merlinkitsune.astral_dice.AstralDiceMod;
import com.merlinkitsune.astral_dice.network.CardChoiceConfirmMessage;
import com.merlinkitsune.astral_dice.network.ModNetwork;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.HashMap;
import java.util.Map;

/**
 * 「选卡界面」屏幕（用户裁决 2026-10-17）。
 *
 * <p><b>视觉</b>：无整幅背景贴图、无标题带；标题为白字居中（lang 6 键，按 槽数×类型 选择）。
 * 卡槽 = {@code shop_card.png}(44x61)，三卡 x 步进 46；卡面 64x64 等比缩到 38x38 @ 框内 (+3,+3)，
 * 卡名在名称框内区（38x16）居中、超宽左右滚动（marquee，与卡牌商店同口径但本类独立实现）；
 * 物品栏底图 {@code shop_item_inventory.png}(166x80) @ (5,116)；确认按钮 51x22 仅在**放满后**渲染与响应。
 *
 * <p><b>交互</b>（口径对齐 {@code CardShopScreen} 的 2026-10-09 §3 手势，但槽位是真实菜单槽）：
 * <ul>
 *   <li><b>单击</b> = 只记录按下相位，**不**交给原版（避免「按下即拿起」）；</li>
 *   <li><b>双击</b>（同一槽 {@value #DOUBLE_CLICK_MS}ms 内第二次按下）= 发原版
 *       {@code ClickType.QUICK_MOVE} ⇒ 服务端 {@link CardChoiceMenu#quickMoveStack} 双向搬牌；</li>
 *   <li><b>拖动</b>：位移超过 {@value #DRAG_THRESHOLD}px 才升格，升格时才真正向服务端发
 *       {@code PICKUP} 把卡拿到光标上（跟手幽灵 = 原版携带物）；松手落点为空卡槽 ⇒ 放入，
 *       落空 / 类型不符 / 槽已占用 ⇒ 原路还原（再发一次 PICKUP 放回原槽）；</li>
 *   <li><b>清理</b>：左键已抬起却没收 {@code mouseReleased} / 窗口失焦 ⇒ 取消拖拽并把携带物放回原槽
 *       （{@link #containerTick()}）。</li>
 * </ul>
 *
 * <p>确认按钮只在 {@link CardChoiceMenu#isComplete()} 为真时出现；点击发 {@code CardChoiceConfirmPayload}，
 * 服务端权威校验、逐张消耗并把结果回传给调用者（见 {@code choice/CardChoiceManager}）。
 */
public class CardChoiceScreen extends AbstractContainerScreen<CardChoiceMenu> {

    private static final int GUI_WIDTH = 176;
    private static final int GUI_HEIGHT = 200;

    /** 标题白字顶边（无标题带，直接画在面板顶部）。 */
    private static final int TITLE_Y = 8;

    private static final int CARD_Y = 24;
    private static final int FRAME_W = 44;
    private static final int FRAME_H = 61;
    /** 三卡 x 步进（与卡牌商店货架同值）。 */
    private static final int FRAME_PITCH = 46;
    private static final int FACE_SIZE = 38;
    private static final int FACE_X_OFF = 3;
    private static final int FACE_Y_OFF = 3;

    /** 名称框内区：x∈[3,40]（38px）、y∈[43,58]（16px）；文本超宽左右滚动，不缩放不截断。 */
    private static final int NAME_BOX_X0 = 3;
    private static final int NAME_BOX_W = 38;
    private static final int NAME_BOX_Y0 = 43;
    private static final int NAME_BOX_H = 16;
    private static final int NAME_CENTER_X_OFF = 22;
    private static final int NAME_Y_OFF = 47;
    private static final int NAME_HOLD_TICKS = 20;
    private static final int NAME_STEP_TICKS = 2;

    /** 物品栏底图（与菜单槽位坐标同源：图 @ (5,116)，内格起点 = +(3,3)）。 */
    private static final int GRID_X = 5;
    private static final int GRID_Y = 116;
    private static final int GRID_W = 166;
    private static final int GRID_H = 80;

    /** 确认按钮 51x22（fanny_skill_confirm.png / _highlight.png），居中于 176 宽面板。 */
    private static final int CONFIRM_X = (GUI_WIDTH - 51) / 2;
    private static final int CONFIRM_Y = 90;
    private static final int CONFIRM_W = 51;
    private static final int CONFIRM_H = 22;

    /** 双击窗口（与卡牌商店一致：250–300ms 取中值 280ms）。 */
    private static final long DOUBLE_CLICK_MS = 280L;
    /** 拖拽升格阈值（像素）。 */
    private static final double DRAG_THRESHOLD = 4.0D;

    private static final int COLOR_WHITE = 0xFFFFFFFF;
    private static final int COLOR_GRAY = 0xFFB0B0B0;

    private int nameMarqueeTick = 0;

    // ── 手势状态（纯客户端） ──
    private int pressedSlot = -1;
    private double pressX = 0.0D;
    private double pressY = 0.0D;
    private boolean dragging = false;
    private boolean pickedUp = false;
    private int dragOrigin = -1;
    /** 本次左键按下是否被本屏幕吞掉（吞掉的按下必须连 release 一起吞，避免原版 doubleclick 残留逻辑误触）。 */
    private boolean pressIntercepted = false;
    /** 本屏幕自己跟踪的左键是否仍按住（挖矿/窗口失焦兜底用）。 */
    private boolean leftDragHeld = false;

    private int lastClickSlot = -1;
    private long lastClickMillis = 0L;

    private final Map<ResourceLocation, Boolean> faceCache = new HashMap<>();

    public CardChoiceScreen(CardChoiceMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = GUI_WIDTH;
        this.imageHeight = GUI_HEIGHT;
        this.titleLabelX = -10000;
        this.titleLabelY = -10000;
        this.inventoryLabelX = -10000;
        this.inventoryLabelY = -10000;
    }

    @Override
    protected void init() {
        super.init();
        faceCache.clear();
        resetGesture();
    }

    private static ResourceLocation tex(String name) {
        return new ResourceLocation(AstralDiceMod.MODID, "textures/gui/shop/" + name);
    }

    // ────────────────────────────── 绘制 ──────────────────────────────

    @Override
    protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
        // 卡面/卡名先画（在槽位物品与光标携带物之下），避免拖拽幽灵被卡面盖住
        for (int i = 0; i < this.menu.slotCount(); i++) {
            int x = cardX(i);
            blit(guiGraphics, tex("shop_card.png"), x, CARD_Y, FRAME_W, FRAME_H);
            ItemStack stack = this.menu.getSlot(i).getItem();
            if (!stack.isEmpty()) {
                drawCardFace(guiGraphics, stack, this.leftPos + x + FACE_X_OFF, this.topPos + CARD_Y + FACE_Y_OFF);
                drawCardName(guiGraphics, stack, this.leftPos + x, this.topPos + CARD_Y);
            }
        }
        blit(guiGraphics, tex("shop_item_inventory.png"), GRID_X, GRID_Y, GRID_W, GRID_H);
        // 确认按钮：只有放满后才出现（也未放满时连命中区都不存在）
        if (this.menu.isComplete()) {
            boolean hovered = isHovering(CONFIRM_X, CONFIRM_Y, CONFIRM_W, CONFIRM_H, mouseX, mouseY);
            blit(guiGraphics, tex(hovered ? "fanny_skill_confirm_highlight.png" : "fanny_skill_confirm.png"),
                    CONFIRM_X, CONFIRM_Y, CONFIRM_W, CONFIRM_H);
        }
    }

    @Override
    protected void renderLabels(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        guiGraphics.drawCenteredString(this.font,
                Component.translatable(this.menu.type().titleKey(this.menu.slotCount())),
                this.imageWidth / 2, TITLE_Y, COLOR_WHITE);
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        // 卡槽是隐藏坐标 ⇒ 原版不会给它出 tooltip，这里补一份（放满/未放满都可查看卡牌信息）
        int hovered = choiceSlotAt(mouseX, mouseY);
        if (hovered >= 0) {
            ItemStack stack = this.menu.getSlot(hovered).getItem();
            if (!stack.isEmpty()) {
                guiGraphics.renderTooltip(this.font, stack, mouseX, mouseY);
            }
        }
    }

    private int cardX(int index) {
        int total = FRAME_W + FRAME_PITCH * (this.menu.slotCount() - 1);
        int start = (GUI_WIDTH - total) / 2;
        return start + FRAME_PITCH * index;
    }

    private void blit(GuiGraphics guiGraphics, ResourceLocation texture, int x, int y, int w, int h) {
        guiGraphics.blit(texture, this.leftPos + x, this.topPos + y, 0, 0.0F, 0.0F, w, h, w, h);
    }

    /** 卡面：astral_dice:textures/gui/shop/card/(物品注册名).png，64x64 整体等比缩放到 38x38。 */
    private void drawCardFace(GuiGraphics guiGraphics, ItemStack stack, int x, int y) {
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        ResourceLocation face = new ResourceLocation(id.getNamespace(),
                "textures/gui/shop/card/" + id.getPath() + ".png");
        if (!faceExists(face)) {
            guiGraphics.fill(x, y, x + FACE_SIZE, y + FACE_SIZE, 0x66000000);
            guiGraphics.drawCenteredString(this.font, "?", x + FACE_SIZE / 2, y + FACE_SIZE / 2 - 4, COLOR_GRAY);
            return;
        }
        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(x, y, 0.0F);
        float scale = FACE_SIZE / 64.0F;
        guiGraphics.pose().scale(scale, scale, 1.0F);
        guiGraphics.blit(face, 0, 0, 0, 0.0F, 0.0F, 64, 64, 64, 64);
        guiGraphics.pose().popPose();
    }

    private boolean faceExists(ResourceLocation face) {
        return faceCache.computeIfAbsent(face, key ->
                this.minecraft != null && this.minecraft.getResourceManager().getResource(key).isPresent());
    }

    /** 卡名（白字、无阴影）：≤38px 居中，>38px 左对齐 + 共享相位左右滚动，scissor 裁到名称框内区。 */
    private void drawCardName(GuiGraphics guiGraphics, ItemStack stack, int frameX, int frameY) {
        String name = cardDisplayName(stack);
        int textWidth = this.font.width(name);
        int overflow = textWidth - NAME_BOX_W;
        guiGraphics.enableScissor(frameX + NAME_BOX_X0, frameY + NAME_BOX_Y0,
                frameX + NAME_BOX_X0 + NAME_BOX_W, frameY + NAME_BOX_Y0 + NAME_BOX_H);
        int drawX = overflow > 0
                ? frameX + NAME_BOX_X0 - marqueeOffset(overflow)
                : frameX + NAME_CENTER_X_OFF - textWidth / 2;
        guiGraphics.drawString(this.font, name, drawX, frameY + NAME_Y_OFF, COLOR_WHITE, false);
        guiGraphics.disableScissor();
    }

    private String cardDisplayName(ItemStack stack) {
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        String shortKey = "item." + id.getNamespace() + "." + id.getPath() + ".short";
        Language language = Language.getInstance();
        if (language.has(shortKey)) {
            String shortName = language.getOrDefault(shortKey);
            if (!shortName.isEmpty()) {
                return shortName;
            }
        }
        return stack.getHoverName().getString();
    }

    private int marqueeOffset(int overflow) {
        if (overflow <= 0) {
            return 0;
        }
        int travel = overflow * NAME_STEP_TICKS;
        int cycle = 2 * (NAME_HOLD_TICKS + travel);
        int t = this.nameMarqueeTick % cycle;
        if (t < NAME_HOLD_TICKS) {
            return 0;
        }
        t -= NAME_HOLD_TICKS;
        if (t < travel) {
            return t / NAME_STEP_TICKS;
        }
        t -= travel;
        if (t < NAME_HOLD_TICKS) {
            return overflow;
        }
        t -= NAME_HOLD_TICKS;
        return overflow - t / NAME_STEP_TICKS;
    }

    @Override
    public void containerTick() {
        super.containerTick();
        this.nameMarqueeTick++;
        // 左键已抬起却没收到 mouseReleased（事件丢失 / 光标出窗）/ 窗口失焦 ⇒ 取消拖拽、卡牌归位
        if (dragging && (!leftDragHeld || (this.minecraft != null && !this.minecraft.isWindowActive()))) {
            cancelDrag();
        }
    }

    // ────────────────────────────── 输入 ──────────────────────────────

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) {
            return super.mouseClicked(mouseX, mouseY, button);
        }
        leftDragHeld = true;
        // 确认按钮优先（仅放满后存在）
        if (this.menu.isComplete()
                && isHovering(CONFIRM_X, CONFIRM_Y, CONFIRM_W, CONFIRM_H, mouseX, mouseY)) {
            ModNetwork.sendToServer(new CardChoiceConfirmMessage(this.menu.token()));
            resetGesture();
            lastClickSlot = -1;
            lastClickMillis = 0L;
            pressIntercepted = true;
            return true;
        }
        Slot slot = slotAt(mouseX, mouseY);
        if (slot != null && this.menu.getCarried().isEmpty() && canStartGesture(slot)) {
            // Shift+点击 = 原版 QUICK_MOVE（与双击同一条服务端路径）
            if (hasShiftDown()) {
                resetGesture();
                lastClickSlot = -1;
                lastClickMillis = 0L;
                if (canSendClicks()) {
                    this.slotClicked(slot, slot.index, 0, ClickType.QUICK_MOVE);
                }
                pressIntercepted = true;
                return true;
            }
            long now = Util.getMillis();
            if (slot.index == lastClickSlot && now - lastClickMillis <= DOUBLE_CLICK_MS) {
                // 双击：QUICK_MOVE（服务端 quickMoveStack 负责双向搬牌）
                lastClickSlot = -1;
                lastClickMillis = 0L;
                resetGesture();
                if (canSendClicks()) {
                    this.slotClicked(slot, slot.index, 0, ClickType.QUICK_MOVE);
                }
                pressIntercepted = true;
                return true;
            }
            // 单击：只记相位，按下不交给原版（拖动超过阈值才升格拿牌）
            lastClickSlot = slot.index;
            lastClickMillis = now;
            pressedSlot = slot.index;
            pressX = mouseX;
            pressY = mouseY;
            dragging = false;
            pickedUp = false;
            dragOrigin = -1;
            pressIntercepted = true;
            return true;
        }
        lastClickSlot = -1;
        lastClickMillis = 0L;
        pressIntercepted = false;
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (button == 0 && pressedSlot >= 0) {
            if (!dragging) {
                double dx = mouseX - pressX;
                double dy = mouseY - pressY;
                if (dx * dx + dy * dy >= DRAG_THRESHOLD * DRAG_THRESHOLD) {
                    elevateToDrag();
                }
            }
            if (dragging) {
                return true;
            }
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0) {
            leftDragHeld = false;
            boolean intercepted = pressIntercepted;
            pressIntercepted = false;
            if (dragging) {
                finishDrag(mouseX, mouseY);
                return true;
            }
            if (intercepted) {
                resetGesture();
                return true;
            }
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public void removed() {
        // 关闭时不再发点击包：服务端 AbstractContainerMenu#removed 会把光标携带物退回物品栏，
        // 这里只清本地手势状态（否则会出现「关屏瞬间再发一次 PICKUP」的竞态）。
        resetGesture();
        super.removed();
    }

    /** 按下升格：此刻才真正向服务端发 PICKUP，把卡拿到光标上（原版携带物即跟手幽灵）。 */
    private void elevateToDrag() {
        if (pressedSlot < 0 || pressedSlot >= this.menu.slots.size()) {
            resetGesture();
            return;
        }
        Slot origin = this.menu.slots.get(pressedSlot);
        if (origin == null || !origin.hasItem()) {
            resetGesture();
            return;
        }
        dragging = true;
        dragOrigin = pressedSlot;
        lastClickSlot = -1;
        lastClickMillis = 0L;
        if (!canSendClicks()) {
            resetGesture();
            return;
        }
        this.slotClicked(origin, origin.index, 0, ClickType.PICKUP);
        pickedUp = !this.menu.getCarried().isEmpty();
        if (!pickedUp) {
            resetGesture();
        }
    }

    /** 松手：落在空卡槽 ⇒ 放入；其余（落空 / 占用 / 类型不符）⇒ 原路还原。 */
    private void finishDrag(double mouseX, double mouseY) {
        Slot target = slotAt(mouseX, mouseY);
        boolean placed = false;
        if (pickedUp && !this.menu.getCarried().isEmpty() && target != null && canSendClicks()) {
            if (target.index < this.menu.slotCount()) {
                if (!target.hasItem() && this.menu.type().matches(this.menu.getCarried())) {
                    this.slotClicked(target, target.index, 0, ClickType.PICKUP);
                    placed = target.hasItem();
                }
            } else {
                // 拖回物品栏：按原版规则落格（可合并），携带数减少即视为成功
                int before = this.menu.getCarried().getCount();
                this.slotClicked(target, target.index, 0, ClickType.PICKUP);
                placed = this.menu.getCarried().getCount() < before;
            }
        }
        // 目标没收下（或落空）⇒ 放回原槽；已放下则不再动，余量留在光标上由原版处理
        if (!placed && pickedUp && !this.menu.getCarried().isEmpty()
                && dragOrigin >= 0 && dragOrigin < this.menu.slots.size() && canSendClicks()) {
            Slot origin = this.menu.slots.get(dragOrigin);
            if (origin != null && !origin.hasItem()) {
                this.slotClicked(origin, origin.index, 0, ClickType.PICKUP);
            }
        }
        resetGesture();
    }

    /** 事件丢失 / 失焦：把携带物放回原槽并清状态。 */
    private void cancelDrag() {
        if (pickedUp && !this.menu.getCarried().isEmpty()
                && dragOrigin >= 0 && dragOrigin < this.menu.slots.size() && canSendClicks()) {
            Slot origin = this.menu.slots.get(dragOrigin);
            if (origin != null && !origin.hasItem()) {
                this.slotClicked(origin, origin.index, 0, ClickType.PICKUP);
            }
        }
        resetGesture();
    }

    private void resetGesture() {
        pressedSlot = -1;
        dragging = false;
        pickedUp = false;
        dragOrigin = -1;
        pressIntercepted = false;
    }

    private boolean canSendClicks() {
        return this.minecraft != null && this.minecraft.gameMode != null && this.minecraft.player != null;
    }

    /** 可做「单击/双击/拖拽」手势的槽：空卡槽不行；物品栏只认类型匹配的卡。 */
    private boolean canStartGesture(Slot slot) {
        if (slot.index < this.menu.slotCount()) {
            return slot.hasItem();
        }
        return this.menu.type().matches(slot.getItem());
    }

    private Slot slotAt(double mouseX, double mouseY) {
        for (Slot slot : this.menu.slots) {
            if (slot != null && slot.isActive()
                    && isHovering(slot.x, slot.y, 16, 16, mouseX, mouseY)) {
                return slot;
            }
        }
        return null;
    }

    private int choiceSlotAt(double mouseX, double mouseY) {
        for (int i = 0; i < this.menu.slotCount(); i++) {
            if (isHovering(cardX(i), CARD_Y, FRAME_W, FRAME_H, mouseX, mouseY)) {
                return i;
            }
        }
        return -1;
    }

    /** 会话 token（客户端只在关闭包 token 一致时关界面）。 */
    public int token() {
        return this.menu.token();
    }
}
