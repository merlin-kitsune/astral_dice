package com.merlinkitsune.astral_dice.screen;

import com.merlinkitsune.astral_dice.AstralDiceMod;
import com.merlinkitsune.astral_dice.client.ChipShopClientState;
import com.merlinkitsune.astral_dice.network.ChipShopBuyPayload;
import com.merlinkitsune.astral_dice.network.ChipShopRefreshPayload;
import com.merlinkitsune.astral_dice.shop.ChipShopCatalog;
import com.merlinkitsune.astral_dice.shop.ChipShopMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * 筹码商店界面（**界面骨架批 + 逻辑批已合流**：图标 blit / 品质外框 / 确认与刷新发包 / 拒绝红闪；判据正本
 * {@code docs/chip-shop-ui-spec.md}，机器判据 {@code scripts/verify/verify_chip_shop_ui.ps1}）。
 *
 * <p><b>整屏 284x183</b>，全部坐标为 GUI 内绝对像素；条带内的素材坐标另用「条带内相对坐标」
 * （{@link #stripX}/{@link #stripY} 换算），常量一律照规范 §3 的实测值逐字定义。
 *
 * <p><b>绘制顺序（规范 §4 的 10 步，逐步号对应代码）</b>：本类把 ①..⑨ 全部落在 {@link #renderBg}
 * 里 —— 原版 {@code AbstractContainerScreen#render} 的次序恰好是
 * {@code renderBg → 槽位循环（含悬停高亮与物品）→ renderLabels → tooltip}，
 * 所以「⑨ 面板底图 → 槽位物品/高亮」与「⑩ tooltip 最后」天然成立；筹码格（隐藏候选槽，坐标 -10000）
 * 原版不会弹 tooltip ⇒ 本类仅覆写 {@code render} 在最后补画筹码格 tooltip（需求 1，见 {@link #renderChipTooltips}）；
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
 * <p><b>两个按钮的职责（逻辑批）</b>：确认按钮 → 发 C2S {@link ChipShopBuyPayload}（**全类唯一的购买请求出口**）；
 * 刷新按钮 → 次数为 0 时**只红闪、不发包**，否则发 C2S {@link ChipShopRefreshPayload}。两者都只表达「意图」，
 * 价格 / 余额 / 扣款 / 发货一律由服务端 {@link ChipShopMenu} 结算（服务端权威）。
 *
 * <p><b>读数来源（每 tick 同步）</b>：候选与数量类读数全部经 {@link #containerTick()} 从菜单（隐藏候选槽 + DataSlot）
 * 抄进 {@link ChipShopClientState}；失败提示走 S2C {@code ChipShopDenyPayload} → {@link ChipShopClientState#notifyDeny}。
 * 本类**不持有**任何业务状态（两个 {@code *PressTicks} 只是按下态动画，两个 {@code *RequestCount} 只是只读探针）。
 *
 * <p><b>筹码图标 = 商店专用素材</b>：候选格画的是 {@link ChipShopCatalog.Entry#iconLocation()}
 * （32x32 的商店图标，按 id 入库），**不是**筹码的物品贴图（两者逐字节不同，见逻辑计划 §7 取证件）。
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

    /** 筹码物品图标 24x24，在 28x28 外框内每边内缩 2 px（28 = 24 + 2x2 ⇒ 正好居中） */
    private static final int CHIP_ICON_SIZE = 24;
    private static final int CHIP_ICON_OFF = 2;
    /** 商店筹码图标的**源**尺寸（32x32 素材缩到 {@link #CHIP_ICON_SIZE} 画；逐张实测确认） */
    private static final int CHIP_ICON_SRC_SIZE = 32;

    /** 条带内标签框 42x15（五格各一个） */
    private static final int LABEL_W = 42;
    private static final int LABEL_H = 15;
    private static final int LABEL_Y = 61;
    private static final int[] LABEL_X = {1, 44, 87, 130, 172};

    /** 筹码名 marquee 起始停顿 tick 数（只作用于 cell1..3 筹码名；2026-10-13 起用） */
    private static final int CHIP_NAME_HOLD_TICKS = 20;
    /** 筹码名 marquee 每移 1 px 所需的 tick 数 */
    private static final int CHIP_NAME_STEP_TICKS = 2;
    /** 标签框（42 宽）内文本内区左内边距：左右各留 2 px ⇒ 内区起点相对标签框左界 +2 */
    private static final int CHIP_NAME_INNER_X = 2;
    /** 标签框内文本内区宽度（42 − 2×2 = 38）；文本宽超过它才触发左右滚动 */
    private static final int CHIP_NAME_INNER_W = 38;

    /**
     * 花费格（cell0）的「星币图标 + 金额数字」组合：图标取 {@code star_coin_small.png}
     * （13x13 素材）的 (3,3) 起 7x7 子区，与 8px 墨迹同高对齐；图标与数字间隔 2 px。
     */
    private static final int COST_ICON_SIZE = 7;
    private static final int COST_ICON_U = 3;
    private static final int COST_ICON_V = 3;
    private static final int COST_ICON_SRC = 13;
    private static final int COST_ICON_GAP = 2;

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

    /** 按下态贴图保持 tick 数（规范 §4 第 8 步：**按下后 PRESS_TICKS tick 内**用 *_push；2026-10-13 用户裁决：悬停不再切换 push 贴图） */
    private static final int PRESS_TICKS = 2;

    /** 标签框内文本的纵向内缩：框高 15、MC 墨迹行高 8 ⇒ +4 使墨迹（4..11）中心落在框中心 7.5 */
    private static final int LABEL_TEXT_DY = 4;

    private static final int COLOR_TEXT = 0xFFFFFF;
    private static final int COLOR_EMPTY = 0x808080;
    /** 拒绝红闪的「红」相颜色（计划表 §4：余额不足闪花费文本、次数 0 闪剩余刷新次数文本） */
    private static final int COLOR_DENY = 0xFF5555;
    /** 选中筹码格（被高亮）下方筹码名的颜色：黑色（2026-10-13；选中标记画在文字下层 ⇒ 黑字更清晰） */
    private static final int COLOR_SELECTED_NAME = 0x000000;
    /**
     * 悬停筹码图标的灰色半透明覆盖层（与 {@code CardShopScreen.COLOR_ROW_HOVER} 同值 0x66AAAAAA、
     * 即 (170,170,170,α≈0x66)；2026-10-13 新增，对齐卡牌商店 / 转账界面的悬停指示）。
     */
    private static final int COLOR_HOVER_OVERLAY = 0x66AAAAAA;

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
    /**
     * 筹码名 marquee 相位（只作用于 cell1..3 筹码名）：{@code containerTick} 里无条件 +1，
     * 与同包 {@code CardShopScreen#nameMarqueeTick} 同形态（每界面一个共享时钟）。
     */
    private int chipNameMarqueeTick = 0;

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
        return ResourceLocation.fromNamespaceAndPath(AstralDiceMod.MODID, "textures/gui/shop/" + name);
    }

    // ────────────────────────────── 绘制（规范 §4） ──────────────────────────────

    /**
     * 覆写 {@code render} 的唯一目的 = 在原版整屏绘制**之后**补画筹码格 tooltip（需求 1）。
     * {@code super.render} 会依次走 背景 → {@link #renderBg}（①..⑨）→ 槽位物品/悬停高亮 → 标签 →
     * 容器槽位 tooltip；筹码格是**隐藏候选槽**（坐标 -10000）⇒ 原版不会为它们弹 tooltip，
     * 故这里在最后对**悬中的筹码格**调 {@code GuiGraphics#renderTooltip(Font, ItemStack, int, int)}。
     */
    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        renderChipTooltips(guiGraphics, mouseX, mouseY);
    }

    /**
     * 需求 1：鼠标指向三个售卖格的筹码图标时，显示该筹码物品的**标准物品 tooltip**
     * （名称 / 品质色 / 全部 tooltip 行，等价于对 {@link ItemStack} 调 {@code renderTooltip}）。
     *
     * <p>命中面 = {@link #chipIndexAt(int, int)}（筹码格 40x78 整列 ∪ 该格标签框）—— 与灰色悬停覆盖层
     * {@link #renderHoverOverlay} **同源**（光标落在哪一格，覆盖层与 tooltip 就出现在哪一格）；
     * 空格 / 目录外（无图标可画，与覆盖层早退同口径）⇒ 不弹。
     */
    private void renderChipTooltips(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        int chip = chipIndexAt(mouseX, mouseY);
        if (chip < 0) {
            return;
        }
        ItemStack stack = ChipShopClientState.offer(chip);
        if (stack.isEmpty()) {
            return;
        }
        if (ChipShopCatalog.byStack(stack).isEmpty()) {
            return;
        }
        guiGraphics.renderTooltip(this.font, stack, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
        // ① 背景
        blit(guiGraphics, tex(TEX_BG), 0, 0, GUI_WIDTH, GUI_HEIGHT);
        // ② 条带深底（frame_2）→ ③ 格轮廓（frame_1）
        blitStrip(guiGraphics, tex(TEX_STRIP_FRAME_2), 0, 0, STRIP_W, STRIP_H);
        blitStrip(guiGraphics, tex(TEX_STRIP_FRAME_1), 0, 0, STRIP_W, STRIP_H);
        // ④ 品质外框 → ⑤ 筹码物品图标（无筹码则不画，二者共用同一循环以免顺序错位）
        renderOffers(guiGraphics);
        // ⑤b 悬停格图标的灰色覆盖层（压在图标上、被 ⑥ 选中标记与 ⑦ 文字压在它上面；2026-10-13 新增）
        renderHoverOverlay(guiGraphics, mouseX, mouseY);
        // ⑥ 选中标记（仅所选筹码格；画在筹码名文字**下层** ⇒ 选中格黑字更清晰）
        renderSelection(guiGraphics);
        // ⑦ 三处文本：花费（cell0）/ 筹码名（cell1..3）/ 刷新计数（cell4）
        renderStripText(guiGraphics);
        // ⑧ 两个按钮（常态 / 按下后 PRESS_TICKS tick 内用 *_push；2026-10-13 用户裁决：悬停不再切换）
        renderButtons(guiGraphics, mouseX, mouseY);
        // ⑨ 物品栏面板底纹；槽位物品与悬停高亮由原版在本方法之后绘制（天然叠在面板之上）
        blit(guiGraphics, tex(TEX_INVENTORY), PANEL_X, PANEL_Y, PANEL_W, PANEL_H);
        // ⑩ 容器槽位 tooltip 由原版 AbstractContainerScreen#render 收尾绘制；筹码格 tooltip 由本类覆写的 render 补画
    }

    /** ④ + ⑤：三个筹码位的品质外框与 24x24 物品图标（空筹码位两者都不画） */
    private void renderOffers(GuiGraphics guiGraphics) {
        for (int chip = 0; chip < CHIP_COUNT; chip++) {
            ItemStack stack = ChipShopClientState.offer(chip);
            if (stack.isEmpty()) {
                continue;
            }
            // 品质外框：优先用服务端同步的品质码（DataSlot[4..6]）；码未到（0）时退回按稀有度现算
            int frame = ChipShopClientState.qualityCode(chip);
            if (frame <= 0) {
                frame = ChipShopClientState.qualityFrameIndexOf(stack);
            }
            if (frame != ChipShopClientState.QUALITY_FRAME_NONE) {
                blitStrip(guiGraphics, tex(qualityTex(frame)),
                        QUALITY_X[chip], QUALITY_Y, QUALITY_SIZE, QUALITY_SIZE);
            }
            // 筹码图标 = 商店专用 32x32 素材，缩到 24x24 画在内缩区中心（非物品模型，故无需 scissor）
            ChipShopCatalog.Entry entry = ChipShopCatalog.byStack(stack).orElse(null);
            if (entry == null) {
                continue;
            }
            int iconX = stripX(QUALITY_X[chip] + CHIP_ICON_OFF);
            int iconY = stripY(QUALITY_Y + CHIP_ICON_OFF);
            guiGraphics.blit(entry.iconLocation(), iconX, iconY, CHIP_ICON_SIZE, CHIP_ICON_SIZE,
                    0.0F, 0.0F, CHIP_ICON_SRC_SIZE, CHIP_ICON_SRC_SIZE,
                    CHIP_ICON_SRC_SIZE, CHIP_ICON_SRC_SIZE);
        }
    }

    /**
     * ⑤b：悬停筹码图标的灰色半透明覆盖层（2026-10-13 新增，对齐卡牌商店 / 转账界面的悬停指示）。
     *
     * <p>范围 = 悬停格的 **24x24 图标区**（iconX/iconY 计算与 {@link #renderOffers} 完全一致）；
     * 空格不画（{@link ChipShopClientState#offer(int)} 返回 empty ⇒ 直接返回）；
     * **目录外早退**（offer 非空但 {@link ChipShopCatalog#byStack} 为空 ⇒ {@link #renderOffers} 同样不画图标）
     * ⇒ 覆盖层也不画（2026-10-13：对齐 renderOffers 的目录外早退，覆盖层不画无图标格）。
     *
     * <p>命中口径 = {@link #chipIndexAt(int, int)}（筹码格（40x78 整列）∪ 该格标签框）—— 与点击选中**有意一致**：
     * 光标落在标签框上时该格图标也亮灰，因为标签框同样是「该筹码格」的选中面（规范 §5）。
     */
    private void renderHoverOverlay(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        int chip = chipIndexAt(mouseX, mouseY);
        if (chip < 0) {
            return;
        }
        ItemStack stack = ChipShopClientState.offer(chip);
        if (stack.isEmpty()) {
            return;
        }
        if (ChipShopCatalog.byStack(stack).isEmpty()) {
            return;
        }
        // 灰色覆盖层画在该格 24x24 图标区上（与 renderOffers 同坐标；压在图标上、被 ⑥/⑦ 压在它上面）
        int iconX = stripX(QUALITY_X[chip] + CHIP_ICON_OFF);
        int iconY = stripY(QUALITY_Y + CHIP_ICON_OFF);
        guiGraphics.fill(iconX, iconY, iconX + CHIP_ICON_SIZE, iconY + CHIP_ICON_SIZE, COLOR_HOVER_OVERLAY);
    }

    /**
     * ⑦：标签框内容（花费=星币图标+数字 / 筹码名 / 剩余刷新次数）。
     * <p>拒绝红闪（计划表 §4）：余额不足 ⇒ 闪**花费数字**；刷新次数为 0 ⇒ 闪**剩余刷新次数文本**；
     * ⚠️ 2026-10-14（需求 2）：花费文本仍按 0/2 两码判定 —— 码 2（物品栏已满）为**保留码**，购买路径
     * 已不再下发（满包改为照常成交 + 装不下的筹码掉落），保留字面是为守门 G23 与本类同相口径。
     * 相位由 {@link ChipShopClientState#denyBlinkRed()} 给出（30t 内 3 次红/白交替）。
     */
    private void renderStripText(GuiGraphics guiGraphics) {
        boolean red = ChipShopClientState.denyBlinkRed();
        int reason = ChipShopClientState.denyReason();
        int costColor = red && (reason == ChipShopMenu.DENY_NO_COIN || reason == ChipShopMenu.DENY_INVENTORY_FULL)
                ? COLOR_DENY : COLOR_TEXT;
        int refreshColor = red && reason == ChipShopMenu.DENY_NO_REFRESH ? COLOR_DENY : COLOR_TEXT;
        drawCost(guiGraphics, costColor);
        for (int chip = 0; chip < CHIP_COUNT; chip++) {
            drawChipName(guiGraphics, chip);
        }
        drawLabel(guiGraphics, CELL_REFRESH,
                Component.translatable("gui.astral_dice.chip_shop.refresh_counter",
                        ChipShopClientState.remainingRefreshes()),
                refreshColor);
    }

    /** ⑥：选中标记 chipshop_select.png（42x15，与标签框同坐标，只画在所选筹码格上；画在筹码名文字下层） */
    private void renderSelection(GuiGraphics guiGraphics) {
        int chip = ChipShopClientState.selectedIndex();
        if (chip < 0) {
            return;
        }
        blitStrip(guiGraphics, tex(TEX_SELECT), LABEL_X[chip + CELL_CHIP_FIRST], LABEL_Y, LABEL_W, LABEL_H);
    }

    /**
     * ⑧：两个按钮（**按下后 PRESS_TICKS tick 内**换成 *_push；2026-10-13 用户裁决：悬停不再切换 push 贴图）。
     *
     * <p>⚠️ mouseX/mouseY 参数保留原因：与 {@link #renderBg} 的绘制入口签名保持一致（绘制与命中同形），
     * 且后续若要加 hover 反馈（如高亮描边）可直接复用；本方法当前不使用这两个参数（javac 不报未使用参数错误）。
     */
    private void renderButtons(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        boolean confirmPush = this.confirmPressTicks > 0;
        blitStrip(guiGraphics, tex(confirmPush ? TEX_CONFIRM_PUSH : TEX_CONFIRM),
                CONFIRM_X, CONFIRM_Y, BUTTON_SIZE, BUTTON_SIZE);
        boolean refreshPush = this.refreshPressTicks > 0;
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

    /**
     * cell1..3 筹码名（2026-10-13）：
     * <ul>
     *   <li>空候选 ⇒ 维持 {@link #drawLabel} 的「无」灰字居中（不滚）；</li>
     *   <li>非空且文本宽 ≤ {@link #CHIP_NAME_INNER_W}(38) ⇒ 居中（现状口径，不滚）；</li>
     *   <li>文本宽 &gt; 38 ⇒ 左对齐到内区左界，再按 {@link #chipNameOffset(int)} 逐像素左右滚动；</li>
     *   <li>滚动/居中一律 scissor 到标签框内区 [INNER_X, INNER_X+W) × [0, H) ⇒ 文字不溢出到相邻格。</li>
     * </ul>
     * <p>颜色/阴影：选中格（{@code chip == selectedIndex}）⇒ {@link #COLOR_SELECTED_NAME} 黑字 + **无阴影**；
     * 否则 {@link #COLOR_TEXT} 白字 + 阴影（与 {@link #drawLabel} 观感一致）。
     */
    private void drawChipName(GuiGraphics guiGraphics, int chip) {
        int cell = chip + CELL_CHIP_FIRST;
        int labelX = stripX(LABEL_X[cell]);
        int labelY = stripY(LABEL_Y);
        ItemStack stack = ChipShopClientState.offer(chip);
        if (stack.isEmpty()) {
            drawLabel(guiGraphics, cell,
                    Component.translatable("gui.astral_dice.chip_shop.empty"), COLOR_EMPTY);
            return;
        }
        Component name = stack.getHoverName();
        int w = this.font.width(name);
        boolean selected = chip == ChipShopClientState.selectedIndex();
        int color = selected ? COLOR_SELECTED_NAME : COLOR_TEXT;
        int overflow = w - CHIP_NAME_INNER_W;
        guiGraphics.enableScissor(labelX + CHIP_NAME_INNER_X, labelY,
                labelX + CHIP_NAME_INNER_X + CHIP_NAME_INNER_W, labelY + LABEL_H);
        int drawX = overflow > 0
                ? labelX + CHIP_NAME_INNER_X - chipNameOffset(overflow)   // 超宽：左对齐 + 相位滚动
                : labelX + (LABEL_W - w) / 2;                             // 不超宽：居中（现状口径）
        guiGraphics.drawString(this.font, name, drawX, labelY + LABEL_TEXT_DY, color, !selected);
        guiGraphics.disableScissor();
    }

    /**
     * 筹码名 marquee 的横向滚动偏移：overflow = 文本宽 - {@link #CHIP_NAME_INNER_W}（≤0 ⇒ 0，不滚）。
     * 轨迹 = 停 {@link #CHIP_NAME_HOLD_TICKS} tick → 每 {@link #CHIP_NAME_STEP_TICKS} tick 移 1px 到尾
     * → 再停同样 tick → 反向移回 → 循环；相位取 {@link #chipNameMarqueeTick}。
     * <p>形态逐字照抄同包 {@code CardShopScreen#marqueeOffset(int)}（仅把常量换成本类新常量）。
     */
    private int chipNameOffset(int overflow) {
        if (overflow <= 0) {
            return 0;
        }
        int travel = overflow * CHIP_NAME_STEP_TICKS;
        int cycle = 2 * (CHIP_NAME_HOLD_TICKS + travel);
        int t = this.chipNameMarqueeTick % cycle;
        if (t < CHIP_NAME_HOLD_TICKS) {
            return 0;
        }
        t -= CHIP_NAME_HOLD_TICKS;
        if (t < travel) {
            return t / CHIP_NAME_STEP_TICKS;
        }
        t -= travel;
        if (t < CHIP_NAME_HOLD_TICKS) {
            return overflow;
        }
        t -= CHIP_NAME_HOLD_TICKS;
        return overflow - t / CHIP_NAME_STEP_TICKS;
    }

    /**
     * cell0 花费格：**星币图标 + 2px 间隔 + 金额数字**整体居中（不再走带前缀的纯文本）。
     * <p>图标 = {@code star_coin_small.png} 的 (3,3) 起 7x7 子区，y = 标签框顶 + {@link #LABEL_TEXT_DY}
     * （与 8px 墨迹同高）；数字 = {@code gui.astral_dice.chip_shop.cost}（值 {@code "%s"}）渲染 {@link ChipShopClientState#cost()}，保持键有线，红闪色由 {@code color} 给出（图标不参与红闪）。
     * 组宽 = 7 + 2 + {@code font.width(数字)}，组左 = 标签框左 + (42 − 组宽)/2；scissor 到 42x15 框防溢出。
     */
    private void drawCost(GuiGraphics guiGraphics, int color) {
        int x = stripX(LABEL_X[CELL_CONFIRM]);
        int y = stripY(LABEL_Y);
        Component amount = Component.translatable("gui.astral_dice.chip_shop.cost", ChipShopClientState.cost());
        int groupW = COST_ICON_SIZE + COST_ICON_GAP + this.font.width(amount);
        int groupX = x + (LABEL_W - groupW) / 2;
        guiGraphics.enableScissor(x, y, x + LABEL_W, y + LABEL_H);
        blitSubAbs(guiGraphics, tex("star_coin_small.png"), groupX, y + LABEL_TEXT_DY,
                COST_ICON_U, COST_ICON_V, COST_ICON_SIZE, COST_ICON_SIZE, COST_ICON_SRC);
        guiGraphics.drawString(this.font, amount, groupX + COST_ICON_SIZE + COST_ICON_GAP,
                y + LABEL_TEXT_DY, color, true);
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
     * 确认按钮（条带 cell0）点击回调 —— 发 C2S {@link ChipShopBuyPayload}（**全类唯一的购买出口**）。
     *
     * <p>未选中任何筹码位、或所选筹码位为空 ⇒ 直接返回，不发包（规范 §5 第 5 条）。
     * 余额 / 库存 / 价格一律不在这里判定 —— 服务端 {@code ChipShopMenu#handleBuy} 二次校验，
     * 失败时回 {@code ChipShopDenyPayload} 让花费文本红闪。
     */
    private void onConfirmPressed() {
        int chip = ChipShopClientState.selectedIndex();
        if (chip < 0 || ChipShopClientState.offer(chip).isEmpty()) {
            return;
        }
        this.buyRequestCount++;
        PacketDistributor.sendToServer(new ChipShopBuyPayload(chip));
    }

    /**
     * 刷新按钮（条带 cell4）点击回调 —— 发 C2S {@link ChipShopRefreshPayload}。
     *
     * <p>剩余次数为 0 时**只起本地红闪、不发包**（计划表 §6：服务端同样拒绝，这里省一个往返；
     * 客户端读数来自服务端 DataSlot，不存在「本地偏大」的窗口）。
     */
    private void onRefreshPressed() {
        if (ChipShopClientState.remainingRefreshes() <= 0) {
            ChipShopClientState.notifyDeny(ChipShopMenu.DENY_NO_REFRESH);
            return;
        }
        this.refreshRequestCount++;
        PacketDistributor.sendToServer(new ChipShopRefreshPayload());
    }

    /**
     * 筹码格命中（返回筹码位序号 0..2；非筹码格返回 -1）—— cell0/cell4 是按钮格，不在此列。
     *
     * <p>命中面 = **筹码格（40x78 整列）∪ 该格下方的标签框（42x15 @ y=61）**（计划表 §5）：
     * 玩家点名字那一行同样是「选中」，与点格子等价。两处命中都**只** {@code select()}，不产生任何请求。
     */
    private int chipIndexAt(int mouseX, int mouseY) {
        for (int chip = 0; chip < CHIP_COUNT; chip++) {
            int cell = chip + CELL_CHIP_FIRST;
            if (isHoveringStrip(mouseX, mouseY, CELL_X[cell], CELL_Y, CELL_W, CELL_H)
                    || isHoveringStrip(mouseX, mouseY, LABEL_X[cell], LABEL_Y, LABEL_W, LABEL_H)) {
                return chip;
            }
        }
        return -1;
    }

    /**
     * 每 tick：把菜单（= 服务端同步过来的隐藏候选槽 + DataSlot）抄进 {@link ChipShopClientState}，
     * 再推进按下态动画与存活计数。
     *
     * <p>⚠️ 拒绝红闪**不在这里**从 DataSlot[3] 同步：{@code ChipShopDenyPayload} 才是触发源 ——
     * 若每 tick 都重设，相位永远停在第一相、看起来根本不闪。
     */
    @Override
    public void containerTick() {
        super.containerTick();
        ChipShopMenu menu = this.getMenu();
        ChipShopClientState.setOffers(new ItemStack[]{
                menu.offerStack(0), menu.offerStack(1), menu.offerStack(2)});
        ChipShopClientState.setQualityCodes(new int[]{
                menu.offerTier(0), menu.offerTier(1), menu.offerTier(2)});
        ChipShopClientState.setCost(menu.price());
        ChipShopClientState.setRefreshes(menu.remainingRefreshes(), menu.totalRefreshes());
        if (this.confirmPressTicks > 0) {
            this.confirmPressTicks--;
        }
        if (this.refreshPressTicks > 0) {
            this.refreshPressTicks--;
        }
        // 筹码名 marquee 相位：无条件推进（只作用于 cell1..3 筹码名，见 drawChipName/chipNameOffset）
        this.chipNameMarqueeTick++;
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

    /**
     * 贴图子区绘制的**屏幕绝对坐标**版本（{@code u/v/w/h} + 源图边长 {@code src}；不加 leftPos/topPos）——
     * 供已换算到绝对屏幕坐标的标签框内绘制使用（先例：同包 {@code CardShopScreen#blitSubAbs}）。
     */
    private static void blitSubAbs(GuiGraphics guiGraphics, ResourceLocation texture, int x, int y,
            int u, int v, int w, int h, int src) {
        guiGraphics.blit(texture, x, y, 0, (float) u, (float) v, w, h, src, src);
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

    /**
     * 探针（只读）：逻辑批读数串 ——
     * {@code cost=<n>:refreshes=<n>/<n>:tiers=<a,b,c>:deny=<n>:red=<true|false>:buy=<n>:refresh=<n>}。
     * <p>{@code red} = 当前是否处于红闪的「红」相；{@code buy}/{@code refresh} = 本界面生命周期内实际**发出**的包数。
     */
    public String chipShopLogicProbe() {
        return "cost=" + ChipShopClientState.cost()
                + ":refreshes=" + ChipShopClientState.remainingRefreshes()
                + "/" + ChipShopClientState.totalRefreshes()
                + ":tiers=" + ChipShopClientState.qualityCode(0) + "," + ChipShopClientState.qualityCode(1)
                + "," + ChipShopClientState.qualityCode(2)
                + ":deny=" + ChipShopClientState.denyReason()
                + ":red=" + ChipShopClientState.denyBlinkRed()
                + ":buy=" + this.buyRequestCount
                + ":refresh=" + this.refreshRequestCount;
    }

    /** 探针：界面生命周期内确认按钮发出的购买请求数（只有「已选中且非空」的点击才 +1 并发包） */
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
