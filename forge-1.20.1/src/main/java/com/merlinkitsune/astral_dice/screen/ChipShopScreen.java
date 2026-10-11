package com.merlinkitsune.astral_dice.screen;

import com.merlinkitsune.astral_dice.AstralDiceMod;
import com.merlinkitsune.astral_dice.client.ChipShopClientState;
import com.merlinkitsune.astral_dice.shop.ChipShopMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

/**
 * 筹码商店界面（本批 = **仅界面骨架 + 素材接线**；判据正本
 * {@code docs/chip-shop-ui-spec.md}，机器判据 {@code scripts/verify/verify_chip_shop_ui.ps1}）。
 *
 * <p><b>整屏 284x183</b>，全部坐标为 GUI 内绝对像素；条带内的素材坐标另用「条带内相对坐标」
 * （{@link #stripX}/{@link #stripY} 换算），常量一律照规范 §3 的实测值逐字定义。
 *
 * <p><b>绘制顺序（规范 §4 的 10 步，逐步号对应代码）</b>：本类把 ①..⑨ 全部落在 {@link #renderBg}
 * 里 —— 原版 {@code AbstractContainerScreen#render} 的次序恰好是
 * {@code renderBg → 槽位循环（含悬停高亮与物品）→ renderLabels → tooltip}，
 * 所以「⑨ 面板底图 → 槽位物品/高亮」与「⑩ tooltip 最后」天然成立，**无需覆写 {@code render}**；
 * 条带（y 6..84）与面板（y 89..175）在纵向完全不重叠 ⇒ ⑧ 按钮不会被 ⑨ 的槽位绘制干扰。
 * 标题/物品栏标签的原版绘制已由构造器挪到屏幕外。
 *
 * <p><b>交互口径（规范 §5，硬约束）</b>：
 * <ul>
 *   <li>单击筹码格 = **只选中**（改 {@link ChipShopClientState#select}），不产生任何请求；
 *       再次单击同一格仍只是选中（本类**没有**双击判定）；</li>
 *   <li>购买**只能**点确认按钮；刷新**只能**点刷新按钮 —— 两个按钮各自独占一个分支；</li>
 *   <li>没有拖拽路径：本类**不覆写**鼠标拖动回调，也不持有任何拖拽/双击状态字段
 *       （守门 G4 会拒绝这类标识符）；</li>
 *   <li>点击空白**不清除**已选：本类没有「清选」分支，未命中任何格/按钮时一律交回原版处理
 *       （物品栏槽位照常可点）。</li>
 * </ul>
 *
 * <p><b>本批不发包、不联网</b>：两个按钮回调只做计数 + TODO（见
 * {@code onConfirmPressed}/{@code onRefreshPressed} 的注释），网络与结算属逻辑批（规范 §9）。
 *
 * <p><b>平台差异（forge-1.20.1，本文件是 1.21.1 蓝本的按差异改写版）</b>：整份代码只有**一处** API 差异 ——
 * 贴图定位用 {@code new ResourceLocation(MODID, path)}：1.20.1 没有
 * {@code ResourceLocation.fromNamespaceAndPath}（同名写法见本线 {@code screen/CardShopScreen.java} 的
 * {@code tex(...)}）。其余注解与调用两线同名同形：{@code AbstractContainerScreen} + {@code GuiGraphics}、
 * {@code GuiGraphics#blit(ResourceLocation,int,int,int,float,float,int,int,int,int)}、
 * {@code enableScissor}/{@code disableScissor}、{@code renderItem}、
 * {@code drawCenteredString(Font,Component,int,int,int)}、{@code containerTick}、标签坐标屏外化。
 */
public class ChipShopScreen extends AbstractContainerScreen<ChipShopMenu> {

    // ────────────────────────────── 布局常量（规范 §3 实测值，逐字对齐守门脚本） ──────────────────────────────

    /** 整屏背景尺寸 */
    private static final int GUI_WIDTH = 284;
    private static final int GUI_HEIGHT = 183;

    /** 选择条带（五格区）在屏内的位置与尺寸 */
    private static final int STRIP_X = 34;
    private static final int STRIP_Y = 6;
    private static final int STRIP_W = 216;
    private static final int STRIP_H = 78;

    /** 条带内五格的相对 x（格距 43、格宽 40）：0 确认按钮、1/2/3 筹码位、4 刷新按钮 */
    private static final int[] CELL_X = {3, 45, 88, 131, 174};
    private static final int CELL_W = 40;

    /** 条带内两个 24x24 按钮 */
    private static final int BUTTON_SIZE = 24;
    private static final int CONFIRM_X = 11;
    private static final int CONFIRM_Y = 36;
    private static final int REFRESH_X = 181;
    private static final int REFRESH_Y = 36;

    /** 条带内品质外框 28x28（三个筹码位） */
    private static final int QUALITY_SIZE = 28;
    private static final int[] QUALITY_X = {51, 94, 137};
    private static final int QUALITY_Y = 31;

    /** 筹码物品图标 16x16，在 28x28 外框内每边内缩 6 px（28 = 16 + 2x6 ⇒ 正好居中） */
    private static final int CHIP_ICON_SIZE = 16;
    private static final int CHIP_ICON_OFF = 6;

    /** 条带内标签框 42x15（五格各一个） */
    private static final int LABEL_W = 42;
    private static final int LABEL_H = 15;
    private static final int LABEL_Y = 61;
    private static final int[] LABEL_X = {1, 44, 87, 130, 172};

    /** 物品栏面板（底纹在贴图内，槽位物品由原版绘制） */
    private static final int PANEL_X = 56;
    private static final int PANEL_Y = 89;
    private static final int PANEL_W = 172;
    private static final int PANEL_H = 86;

    /**
     * 玩家物品栏**格网**（18x18 格）：主栏 9x3 @ (61, 94)、槽距 18、快捷栏行 y = 152。
     * <p>本界面**不逐格绘制**（格纹在 {@code chipshop_inventory.png} 里），这组常量是格网的**权威定义**：
     * 菜单侧槽位坐标 =「格网起点 + 1 px 内缩」（规范 §3 末条，见 {@link ChipShopMenu}），
     * 二者的对齐关系由下方静态自检兜底。
     */
    private static final int SLOT_X0 = 61;
    private static final int SLOT_Y0 = 94;
    private static final int SLOT_PITCH = 18;
    private static final int SLOT_COLS = 9;
    private static final int SLOT_ROWS = 3;
    private static final int HOTBAR_Y = 152;

    /**
     * 格网自检（类加载时执行一次）：格网必须落在面板贴图范围内。
     * <p>为什么要有它：这组常量与面板贴图是**两套彼此独立**的数字（前者在代码里、后者在 png 里），
     * 一旦日后被单方面改动，物品会静默画到面板外 —— 这里让它立刻失败，而不是等实机肉眼发现。
     */
    static {
        int gridRight = SLOT_X0 + SLOT_COLS * SLOT_PITCH;
        int gridBottom = Math.max(SLOT_Y0 + SLOT_ROWS * SLOT_PITCH, HOTBAR_Y + SLOT_PITCH);
        if (gridRight > PANEL_X + PANEL_W || gridBottom > PANEL_Y + PANEL_H) {
            throw new IllegalStateException("筹码商店物品栏格网超出面板贴图: grid=" + gridRight + "," + gridBottom
                    + " panel=" + (PANEL_X + PANEL_W) + "," + (PANEL_Y + PANEL_H));
        }
    }

    /** 条带内格序号（规范 §3 的语义口径） */
    private static final int CELL_CONFIRM = 0;
    private static final int CELL_CHIP_FIRST = 1;
    private static final int CELL_REFRESH = 4;
    /** 筹码位数（条带 cell1..cell3） */
    private static final int CHIP_COUNT = ChipShopClientState.OFFER_COUNT;

    /** 筹码格命中区（条带内）：格宽 x 整条带高；按钮格 0/4 由更早的分支截获，永不落到筹码格判定 */
    private static final int CELL_Y = 0;
    private static final int CELL_H = STRIP_H;

    /** 按下态贴图保持 tick 数（规范 §4 第 8 步：hover 或**按下 2 tick 内**用 *_push） */
    private static final int PRESS_TICKS = 2;

    /** 标签框内文本的纵向内缩：框高 15、MC 墨迹行高 8 ⇒ +4 使墨迹（4..11）中心落在框中心 7.5 */
    private static final int LABEL_TEXT_DY = 4;

    private static final int COLOR_TEXT = 0xFFFFFF;
    private static final int COLOR_EMPTY = 0x808080;

    // ────────────────────────────── 贴图名（规范 §2；一律经 tex(...) 拼成 textures/gui/shop/<名>） ──────────────────────────────

    private static final String TEX_BG = "chipshop_bg.png";
    /**
     * 条带外层候选 A（§2「选择位外框（深底 + 顶部装饰）」）：与 {@link #TEX_STRIP_FRAME_2} 同为 216x78、
     * 全图仅 1260 px 差异。§3 明确本批**取 frame_2 作外层** ⇒ 本名只是素材登记项，不在绘制路径上
     * （逻辑批若要换外层，只改 {@link #renderBg} 里的一个常量）。
     */
    private static final String TEX_STRIP_FRAME = "chipshop_frame.png";
    /** 条带内部框架 A：5 格轮廓（透明底） */
    private static final String TEX_STRIP_FRAME_1 = "chipshop_frame_1.png";
    /** 条带内部框架 B：深底外框（本批的外层） */
    private static final String TEX_STRIP_FRAME_2 = "chipshop_frame_2.png";
    private static final String TEX_INVENTORY = "chipshop_inventory.png";
    private static final String TEX_CONFIRM = "chipshop_confirm.png";
    private static final String TEX_CONFIRM_PUSH = "chipshop_confirm_push.png";
    private static final String TEX_REFRESH = "chipshop_refresh.png";
    private static final String TEX_REFRESH_PUSH = "chipshop_refresh_push.png";
    private static final String TEX_QUALITY_1 = "chipshop_quality_1.png";
    private static final String TEX_QUALITY_2 = "chipshop_quality_2.png";
    private static final String TEX_QUALITY_3 = "chipshop_quality_3.png";
    private static final String TEX_SELECT = "chipshop_select.png";

    // ────────────────────────────── 界面态（本批全部是本地计数器/反馈态，无业务状态） ──────────────────────────────

    /** 确认按钮按下态剩余 tick（探针 confirmPressTicks 读的就是它；0 = 常态） */
    private int confirmPressTicks = 0;
    /** 刷新按钮按下态剩余 tick */
    private int refreshPressTicks = 0;
    /** 界面生命周期内「确认按钮」发出的购买请求数（探针；本批只计数，不发包） */
    private int buyRequestCount = 0;
    /** 界面生命周期内「刷新按钮」发出的刷新请求数（探针；本批只计数，不发包） */
    private int refreshRequestCount = 0;

    public ChipShopScreen(ChipShopMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = GUI_WIDTH;
        this.imageHeight = GUI_HEIGHT;
        // 原版标题与物品栏标签一律挪到屏幕外（本屏标题由背景素材承担）
        this.titleLabelX = -10000;
        this.titleLabelY = -10000;
        this.inventoryLabelX = -10000;
        this.inventoryLabelY = -10000;
        ChipShopClientState.clear();
    }

    private static ResourceLocation tex(String name) {
        return new ResourceLocation(AstralDiceMod.MODID, "textures/gui/shop/" + name);
    }

    // ────────────────────────────── 绘制（规范 §4） ──────────────────────────────

    @Override
    protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
        // ① 背景
        blit(guiGraphics, tex(TEX_BG), 0, 0, GUI_WIDTH, GUI_HEIGHT);
        // ② 条带深底（frame_2）→ ③ 格轮廓（frame_1）
        blitStrip(guiGraphics, tex(TEX_STRIP_FRAME_2), 0, 0, STRIP_W, STRIP_H);
        blitStrip(guiGraphics, tex(TEX_STRIP_FRAME_1), 0, 0, STRIP_W, STRIP_H);
        // ④ 品质外框 → ⑤ 筹码物品图标（无筹码则不画，二者共用同一循环以免顺序错位）
        renderOffers(guiGraphics);
        // ⑥ 三处文本：花费（cell0）/ 筹码名（cell1..3）/ 刷新计数（cell4）
        renderStripText(guiGraphics);
        // ⑦ 选中标记（仅所选筹码格）
        renderSelection(guiGraphics);
        // ⑧ 两个按钮（常态 / hover 或按下 2 tick 内用 *_push）
        renderButtons(guiGraphics, mouseX, mouseY);
        // ⑨ 物品栏面板底纹；槽位物品与悬停高亮由原版在本方法之后绘制（天然叠在面板之上）
        blit(guiGraphics, tex(TEX_INVENTORY), PANEL_X, PANEL_Y, PANEL_W, PANEL_H);
        // ⑩ tooltip 由原版 AbstractContainerScreen#render 收尾绘制（本批无自定义 tooltip 内容）
    }

    /** ④ + ⑤：三个筹码位的品质外框与 16x16 物品图标（空筹码位两者都不画） */
    private void renderOffers(GuiGraphics guiGraphics) {
        for (int chip = 0; chip < CHIP_COUNT; chip++) {
            ItemStack stack = ChipShopClientState.offer(chip);
            if (stack.isEmpty()) {
                continue;
            }
            int frame = ChipShopClientState.qualityFrameIndexOf(stack);
            if (frame != ChipShopClientState.QUALITY_FRAME_NONE) {
                blitStrip(guiGraphics, tex(qualityTex(frame)),
                        QUALITY_X[chip], QUALITY_Y, QUALITY_SIZE, QUALITY_SIZE);
            }
            // 图标区裁到 16x16：物品模型自带外溢（3D 模型 / 数量角标）不得越过内缩区
            int iconX = stripX(QUALITY_X[chip] + CHIP_ICON_OFF);
            int iconY = stripY(QUALITY_Y + CHIP_ICON_OFF);
            guiGraphics.enableScissor(iconX, iconY, iconX + CHIP_ICON_SIZE, iconY + CHIP_ICON_SIZE);
            guiGraphics.renderItem(stack, iconX, iconY);
            guiGraphics.disableScissor();
        }
    }

    /** ⑥：标签框文本（花费 / 筹码名 / 刷新计数） */
    private void renderStripText(GuiGraphics guiGraphics) {
        drawLabel(guiGraphics, CELL_CONFIRM,
                Component.translatable("gui.astral_dice.chip_shop.cost", ChipShopClientState.cost()),
                COLOR_TEXT);
        for (int chip = 0; chip < CHIP_COUNT; chip++) {
            ItemStack stack = ChipShopClientState.offer(chip);
            drawLabel(guiGraphics, chip + CELL_CHIP_FIRST,
                    stack.isEmpty() ? Component.translatable("gui.astral_dice.chip_shop.empty")
                            : stack.getHoverName(),
                    stack.isEmpty() ? COLOR_EMPTY : COLOR_TEXT);
        }
        drawLabel(guiGraphics, CELL_REFRESH,
                Component.translatable("gui.astral_dice.chip_shop.refresh_counter",
                        ChipShopClientState.remainingRefreshes(), ChipShopClientState.totalRefreshes()),
                COLOR_TEXT);
    }

    /** ⑦：选中标记 chipshop_select.png（42x15，与标签框同坐标，只画在所选筹码格上） */
    private void renderSelection(GuiGraphics guiGraphics) {
        int chip = ChipShopClientState.selectedIndex();
        if (chip < 0) {
            return;
        }
        blitStrip(guiGraphics, tex(TEX_SELECT), LABEL_X[chip + CELL_CHIP_FIRST], LABEL_Y, LABEL_W, LABEL_H);
    }

    /** ⑧：两个按钮（hover 或按下反馈期内换成 *_push） */
    private void renderButtons(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        boolean confirmPush = this.confirmPressTicks > 0
                || isHoveringStrip(mouseX, mouseY, CONFIRM_X, CONFIRM_Y, BUTTON_SIZE, BUTTON_SIZE);
        blitStrip(guiGraphics, tex(confirmPush ? TEX_CONFIRM_PUSH : TEX_CONFIRM),
                CONFIRM_X, CONFIRM_Y, BUTTON_SIZE, BUTTON_SIZE);
        boolean refreshPush = this.refreshPressTicks > 0
                || isHoveringStrip(mouseX, mouseY, REFRESH_X, REFRESH_Y, BUTTON_SIZE, BUTTON_SIZE);
        blitStrip(guiGraphics, tex(refreshPush ? TEX_REFRESH_PUSH : TEX_REFRESH),
                REFRESH_X, REFRESH_Y, BUTTON_SIZE, BUTTON_SIZE);
    }

    /** 在某个格的标签框内居中画一行文本；scissor 到 42x15 框 ⇒ 超宽文本不会溢出到相邻格 */
    private void drawLabel(GuiGraphics guiGraphics, int cell, Component text, int color) {
        int x = stripX(LABEL_X[cell]);
        int y = stripY(LABEL_Y);
        guiGraphics.enableScissor(x, y, x + LABEL_W, y + LABEL_H);
        guiGraphics.drawCenteredString(this.font, text, x + LABEL_W / 2, y + LABEL_TEXT_DY, color);
        guiGraphics.disableScissor();
    }

    /** 品质外框序号（1/2/3）→ 贴图名；其余值按 3 处理（与 {@link ChipShopClientState} 的兜底同口径） */
    private static String qualityTex(int frame) {
        if (frame == 1) {
            return TEX_QUALITY_1;
        }
        if (frame == 2) {
            return TEX_QUALITY_2;
        }
        return TEX_QUALITY_3;
    }

    // ────────────────────────────── 输入（规范 §5） ──────────────────────────────

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) {
            return super.mouseClicked(mouseX, mouseY, button);
        }
        final int mx = (int) mouseX;
        final int my = (int) mouseY;
        // ── ① 确认按钮：**全类唯一**触发购买请求的分支（规范 §5 第 2 条） ──
        if (isHoveringStrip(mx, my, CONFIRM_X, CONFIRM_Y, BUTTON_SIZE, BUTTON_SIZE)) {
            this.confirmPressTicks = PRESS_TICKS;
            onConfirmPressed();
            return true;
        }
        // ── ② 刷新按钮：刷新请求的唯一来源（规范 §5 第 3 条） ──
        if (isHoveringStrip(mx, my, REFRESH_X, REFRESH_Y, BUTTON_SIZE, BUTTON_SIZE)) {
            this.refreshPressTicks = PRESS_TICKS;
            onRefreshPressed();
            return true;
        }
        // ── ③ 三个筹码格：单击只改选中，绝不产生请求（再次单击同格仍是选中，本类无双击判定） ──
        int chip = chipIndexAt(mx, my);
        if (chip >= 0) {
            ChipShopClientState.select(chip);
            return true;
        }
        // ── ④ 其余一律交回原版：物品栏槽位照常交互；**点空白不清选**（本类没有清选分支） ──
        return super.mouseClicked(mouseX, mouseY, button);
    }

    /**
     * 确认按钮（条带 cell0）点击回调 —— **本批只计数 + TODO：不联网、不发包**（规范 §5 / §9）。
     * <p>未选中任何筹码位、或所选筹码位为空 ⇒ 直接返回，不产生请求（规范 §5 第 5 条）。
     */
    private void onConfirmPressed() {
        int chip = ChipShopClientState.selectedIndex();
        if (chip < 0 || ChipShopClientState.offer(chip).isEmpty()) {
            return;
        }
        this.buyRequestCount++;
        // TODO(逻辑批)：在此发 C2S 购买包（选中筹码位 + 服务端权威校验余额/扣款/发货，见规范 §9 输入清单）
    }

    /** 刷新按钮（条带 cell4）点击回调 —— 本批只计数 + TODO：不联网、不发包。 */
    private void onRefreshPressed() {
        this.refreshRequestCount++;
        // TODO(逻辑批)：在此发 C2S 刷新请求（刷新次数的来源与上限见规范 §9 输入清单）
    }

    /** 筹码格命中（返回筹码位序号 0..2；非筹码格返回 -1）—— cell0/cell4 是按钮格，不在此列 */
    private int chipIndexAt(int mouseX, int mouseY) {
        for (int chip = 0; chip < CHIP_COUNT; chip++) {
            if (isHoveringStrip(mouseX, mouseY, CELL_X[chip + CELL_CHIP_FIRST], CELL_Y, CELL_W, CELL_H)) {
                return chip;
            }
        }
        return -1;
    }

    @Override
    public void containerTick() {
        super.containerTick();
        if (this.confirmPressTicks > 0) {
            this.confirmPressTicks--;
        }
        if (this.refreshPressTicks > 0) {
            this.refreshPressTicks--;
        }
        ChipShopClientState.tick();
    }

    @Override
    public void removed() {
        super.removed();
        this.confirmPressTicks = 0;
        this.refreshPressTicks = 0;
        ChipShopClientState.clear();
    }

    // ────────────────────────────── 坐标工具 ──────────────────────────────

    /** 条带内相对 x → 屏幕绝对 x */
    private int stripX(int stripRelX) {
        return this.leftPos + STRIP_X + stripRelX;
    }

    /** 条带内相对 y → 屏幕绝对 y */
    private int stripY(int stripRelY) {
        return this.topPos + STRIP_Y + stripRelY;
    }

    /** 条带内相对矩形的命中判定（用屏幕绝对坐标比较，与绘制同一套换算） */
    private boolean isHoveringStrip(int mouseX, int mouseY, int x, int y, int w, int h) {
        int absX = stripX(x);
        int absY = stripY(y);
        return mouseX >= absX && mouseX < absX + w && mouseY >= absY && mouseY < absY + h;
    }

    /** GUI 内相对坐标绘制整幅贴图 */
    private void blit(GuiGraphics guiGraphics, ResourceLocation texture, int x, int y, int w, int h) {
        guiGraphics.blit(texture, this.leftPos + x, this.topPos + y, 0, 0.0F, 0.0F, w, h, w, h);
    }

    /** 条带内相对坐标绘制整幅贴图 */
    private void blitStrip(GuiGraphics guiGraphics, ResourceLocation texture, int x, int y, int w, int h) {
        blit(guiGraphics, texture, STRIP_X + x, STRIP_Y + y, w, h);
    }

    // ────────────────────────────── 只读探针（规范 §7；一律只读，绝不改状态） ──────────────────────────────

    /** 探针：当前选中筹码位（0..2；-1 = 未选中） */
    public int selectedChipIndex() {
        return ChipShopClientState.selectedIndex();
    }

    /**
     * 探针：实测绘制坐标串，格式（规范 §7）
     * {@code cells=3,45,88,131,174:labelY=61:panel=56,89,172,86:hotbarY=152:selected=<n>:offers=<n>}。
     * <p>{@code offers=<n>} = 当前**非空**筹码位数（0..3，见 {@link ChipShopClientState#offerCount()}）。
     */
    public String chipShopLayoutProbe() {
        return "cells=" + CELL_X[0] + "," + CELL_X[1] + "," + CELL_X[2] + "," + CELL_X[3] + "," + CELL_X[4]
                + ":labelY=" + LABEL_Y
                + ":panel=" + PANEL_X + "," + PANEL_Y + "," + PANEL_W + "," + PANEL_H
                + ":hotbarY=" + HOTBAR_Y
                + ":selected=" + selectedChipIndex()
                + ":offers=" + ChipShopClientState.offerCount();
    }

    /** 探针：界面生命周期内确认按钮发出的购买请求数（本批只有「已选中且非空」的点击才 +1） */
    public int buyRequestCount() {
        return this.buyRequestCount;
    }

    /** 探针：界面生命周期内刷新按钮发出的刷新请求数 */
    public int refreshRequestCount() {
        return this.refreshRequestCount;
    }

    /** 探针：确认按钮按下态剩余 tick（0 = 常态） */
    public int confirmPressTicks() {
        return this.confirmPressTicks;
    }
}
