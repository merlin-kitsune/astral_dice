package com.merlinkitsune.astral_dice.screen;

import com.merlinkitsune.astral_dice.AstralDiceMod;
import com.merlinkitsune.astral_dice.item.ModItems;
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
 *   <li><b>类型过滤</b>：物品栏里**类型不符的卡**按下时（左键 / 右键 / 创意模式拾取键 = 原版
 *       {@code PICKUP} 与 {@code CLONE} 三条「拿到光标上」的路径）一律不交给原版 ⇒
 *       牌**原地不动**（= spec §9-3「原地不动」，不进入拖动），双击也不会发 QUICK_MOVE；
 *       非卡物品、以及光标已有携带物时的正常放置仍走原版；</li>
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
        // 右键（button == 1）与创意模式「拾取」（默认中键、可改绑）= 原版另外两条「把物品拿到光标上」的路径：
        //   1.20.1 AbstractContainerScreen#mouseClicked：空光标 + button 0/1 ⇒ PICKUP；
        //   空光标 + keyPickItem ⇒ ClickType.CLONE（创意判定在服务端 doClick：getAbilities().instabuild）。
        // 两条都要过与左键同一套类型过滤，否则类型不符的卡仍会被拿到光标上。
        boolean pickItemClick = isPickItemClick(button);
        if (button != 0 && button != 1 && !pickItemClick) {
            return super.mouseClicked(mouseX, mouseY, button);
        }
        if (button == 0) {
            leftDragHeld = true;
        }
        // 确认按钮优先（仅放满后存在；只有左键响应）
        if (button == 0 && this.menu.isComplete()
                && isHovering(CONFIRM_X, CONFIRM_Y, CONFIRM_W, CONFIRM_H, mouseX, mouseY)) {
            ModNetwork.sendToServer(new CardChoiceConfirmMessage(this.menu.token()));
            resetGesture();
            lastClickSlot = -1;
            lastClickMillis = 0L;
            pressIntercepted = true;
            return true;
        }
        // 卡框是隐藏坐标（HIDDEN_X/Y = -10000）⇒ 原版命中不到，必须回落到屏幕自绘的卡框命中区
        Slot slot = slotOrChoiceAt(mouseX, mouseY);
        // 类型过滤拒绝（spec §9-3「原地不动」）：物品栏里**类型不符的卡**按下时不要交给原版 ——
        // 原版的 PICKUP（左键 / 右键）与 CLONE（创意模式拾取键）都会把牌拿到光标上
        // （实测 neo 线点击路径、forge 线拖动路径），
        // 拒绝入槽后牌就停在光标上而不是回到原槽。
        // 口径：吞掉这次按下（左键连随后的抬起一起吞），牌**原地不动**，不发任何点击包、也不进入拖动；
        //      双击同样被吞 ⇒ 不会发 QUICK_MOVE、不会进错槽、不会复制、不会丢失。
        //      只针对「卡牌」，石头 / 钻石等普通物品仍交给原版，保留常规物品栏操作；
        //      空光标是三条路径的共同前提（光标有携带物时右键是「放一个」，必须放行）。
        if (slot != null && this.menu.getCarried().isEmpty()
                && slot.index >= this.menu.slotCount()
                && !this.menu.type().matches(slot.getItem())
                && ModItems.isCardItem(slot.getItem())) {
            resetGesture();
            lastClickSlot = -1;
            lastClickMillis = 0L;
            // 只有左键的按下由 mouseReleased 的 button == 0 分支吞并；右键 / 中键不发包即可，
            // 在此置 true 会残留到下一次左键抬起、误吞一次正常 release。
            pressIntercepted = button == 0;
            return true;
        }
        // 右键 / 中键未命中类型过滤 ⇒ 原样交给原版（不触碰左键的双击 / 拖拽状态）
        if (button != 0) {
            return super.mouseClicked(mouseX, mouseY, button);
        }
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

    /**
     * 本次按下是否会被原版当成「创意模式拾取」（{@code ClickType.CLONE}，默认中键、可在按键设置里改绑）。
     *
     * <p>1.20.1 的判定在 {@code AbstractContainerScreen#mouseClicked}（{@code keyPickItem} 命中鼠标键）
     * + 服务端 {@code AbstractContainerMenu#doClick}（{@code player.getAbilities().instabuild}）；
     * 这里两半一起判，保证「只有原版真会发 CLONE 的按下」才走本屏幕的过滤分支，非创意模式不受影响。
     */
    private boolean isPickItemClick(int button) {
        return this.minecraft != null
                && this.minecraft.player != null
                && this.minecraft.player.getAbilities().instabuild
                && this.minecraft.options.keyPickItem.matchesMouse(button);
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
        Slot target = slotOrChoiceAt(mouseX, mouseY);
        boolean placed = false;
        if (pickedUp && !this.menu.getCarried().isEmpty() && target != null && canSendClicks()) {
            if (target.index < this.menu.slotCount()) {
                // 卡槽：只在「空槽 + 类型匹配」时发包（spec §9-3 拒绝类型不符）。
                // 类型不符的牌正常根本到不了光标上 —— 见 mouseClicked 的拒绝分支；此处保留判据作为防御，
                // 保证任何来源的携带物都不会进错槽。
                if (!target.hasItem() && this.menu.type().matches(this.menu.getCarried())) {
                    this.slotClicked(target, target.index, 0, ClickType.PICKUP);
                    placed = target.hasItem();
                }
            } else if (canAcceptCarried(target)) {
                // 拖回物品栏：**发包前**先预判目标格能否安全接收。被不同物品占用时原版
                // {@code PICKUP} 会落到「交换」分支把两件物品换位，违反 spec §5 第 3 条「已占用 ⇒ 原路还原」
                // ⇒ 不发包，直接走下方还原分支（落点为空槽 / 同种可叠时仍按原版合并）。
                // 携带数减少即视为成功；余量留在光标上由原版处理。
                int before = this.menu.getCarried().getCount();
                this.slotClicked(target, target.index, 0, ClickType.PICKUP);
                placed = this.menu.getCarried().getCount() < before;
            }
        }
        // 目标没收下（落空 / 类型不符 / 已占用）⇒ 放回原槽；已放下则不再动，余量留在光标上由原版处理
        if (!placed) {
            returnCarriedToOrigin();
        }
        resetGesture();
    }

    /**
     * 物品栏槽能否**安全接收**光标上的卡（发 {@code PICKUP} 前的预判，spec §5 第 3 条「已占用 ⇒ 原路还原」）。
     *
     * <p>原版 {@code AbstractContainerMenu#doClick} 的 {@code PICKUP} 分支里：
     * <ul>
     *   <li>槽为空 ⇒ {@code safeInsert} 放入（不会换位）；</li>
     *   <li>槽内**同种且仍有余量** ⇒ {@code safeInsert} 合并（不会换位）；</li>
     *   <li>槽内是**不同物品**（且携带数不超过格子上限）⇒ 走到末尾的**交换**分支，
     *       两件物品直接换位 —— 这正是要避免的情形。</li>
     * </ul>
     * 因此「不同物品」与「同种已满叠 / 不可放置」一律判为不可接收，交给调用方原路还原。
     */
    private boolean canAcceptCarried(Slot slot) {
        ItemStack carried = this.menu.getCarried();
        if (carried.isEmpty() || !slot.mayPlace(carried)) {
            return false;
        }
        if (!slot.hasItem()) {
            return true;
        }
        ItemStack existing = slot.getItem();
        return ItemStack.isSameItemSameTags(existing, carried)
                && existing.getCount() < slot.getMaxStackSize(existing);
    }

    /** 事件丢失 / 失焦：把携带物放回原槽并清状态。 */
    private void cancelDrag() {
        returnCarriedToOrigin();
        resetGesture();
    }

    /**
     * 把光标上的卡原路放回 {@link #dragOrigin}（spec §5 第 3 条「落空 / 类型不符 / 已占用 ⇒ 原路还原」）。
     *
     * <p><b>边界口径</b>：
     * <ul>
     *   <li>原槽为空（正常情形 —— 升格时的 {@code PICKUP} 取走的是**整栈**，原槽必空）⇒ 发包放回；</li>
     *   <li>原槽已被占用 / 索引失效（理论上不会发生）⇒ **不发包**：发到被不同物品占用的格子会走原版
     *       {@code AbstractContainerMenu#doClick} 的交换分支（两件物品换位），发到卡槽则可能进错槽 ⇒
     *       保留在光标上由玩家自行放下 —— 不丢物品、不复制、不进错槽。</li>
     * </ul>
     */
    private void returnCarriedToOrigin() {
        if (!pickedUp || this.menu.getCarried().isEmpty() || !canSendClicks()) {
            return;
        }
        if (dragOrigin < 0 || dragOrigin >= this.menu.slots.size()) {
            return;
        }
        Slot origin = this.menu.slots.get(dragOrigin);
        if (origin != null && !origin.hasItem()) {
            this.slotClicked(origin, origin.index, 0, ClickType.PICKUP);
        }
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

    /**
     * 槽位命中：先按原版槽位坐标找（物品栏），找不到再回落到屏幕自绘的卡框。
     *
     * <p>虚拟卡槽坐标是 {@link CardChoiceMenu#HIDDEN_X}/{@link CardChoiceMenu#HIDDEN_Y}（-10000），
     * {@link Slot#isActive()} 虽为 true，但 {@code slotAt} 按原版槽位坐标命中永远打不中 ⇒
     * 卡框上的单击 / 双击 / 拖动升格 / 拖动落点全靠这条回落（卡槽先 addSlot，索引 0..slotCount-1）。
     */
    private Slot slotOrChoiceAt(double mouseX, double mouseY) {
        Slot slot = slotAt(mouseX, mouseY);
        if (slot != null) {
            return slot;
        }
        int ci = choiceSlotAt(mouseX, mouseY);
        return ci >= 0 ? this.menu.getSlot(ci) : null;
    }

    /** 会话 token（客户端只在关闭包 token 一致时关界面）。 */
    public int token() {
        return this.menu.token();
    }
}
