package com.merlinkitsune.astral_dice.screen;

import com.merlinkitsune.astral_dice.AstralDiceMod;
import com.merlinkitsune.astral_dice.client.CardShopClientState;
import com.merlinkitsune.astral_dice.item.sign.ParunanSignItem;
import com.merlinkitsune.astral_dice.network.CardShopBuyPayload;
import com.merlinkitsune.astral_dice.network.CardShopTransferListPayload;
import com.merlinkitsune.astral_dice.network.CardShopTransferPayload;
import com.merlinkitsune.astral_dice.shop.CardShopMenu;
import com.merlinkitsune.starenginelib.economy.StarCoinWalletState;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * 卡牌商店界面（2026-10-08 v4 定稿：坐标以 temp/t80/UI-DESIGN.md 的 bg 口径为准）。
 *
 * <p><b>主视图</b> = 贴图 {@code textures/gui/shop/card_shop_inventory_bg.png}(288x187) 整幅背景；
 * 所有像素坐标都取自该背景贴图坐标系（左上为原点）：
 * <ul>
 *   <li>标题带 {@code shop_title.png}(274x19) @ (7,3)，品红区内居中显示「卡牌商店」；
 *       大酬宾窗口内改播 {@code shop_title_light_1..3.png}（8 tick/帧循环）；</li>
 *   <li>6 个卡框 {@code shop_card(.select/.soldout).png}(44x61) @ (7+46i, 28)；卡面 38x38 @ 框内 (+3,+3)；
 *       卡名在名称框（实测内区 40×17）内居中且**按需缩放字号保证完整显示**（禁止截断）；折扣角标 17x14 贴卡面右下角；</li>
 *   <li>左按钮 (11,109) = 星光兑换星币；中按钮 (48,109) = 进入二级转账界面（星币 &lt; 5 或冷却中 ⇒ 灰化）；
 *       沙漏 (86,109) = 转账冷却倒计时；</li>
 *   <li>余额条 {@code shop_coin_bar.png}(96x24) @ (11,145) + {@code shop_coin_icon.png}(12x12) @ (17,151)；
 *       数字锚点/颜色/阴影与玩家物品栏余额栏（{@code StarCoinWalletButtons#drawBalance}）一致；</li>
 *   <li>物品栏底图 {@code shop_item_inventory.png}(166x80) @ (114,99)，槽位坐标与
 *       {@link CardShopMenu#INVENTORY_X}/{@link CardShopMenu#INVENTORY_Y}/{@link CardShopMenu#INVENTORY_HOTBAR_Y} 同口径。</li>
 * </ul>
 *
 * <p><b>购买 = 拖拽</b>（核心交互，替换旧的「点卡即买」）：{@link #mouseClicked} 在卡面上按下起拖
 * （余额不足则拒绝起拖 + actionbar 提示）→ {@link #mouseDragged} 更新跟手幽灵 → {@link #mouseReleased}
 * 判定落点：命中玩家物品栏空格 ⇒ 发 {@link CardShopBuyPayload}（服务端权威结算）；落点无效或该格被占用 ⇒
 * 仅清本地拖动态（卡牌原地移回，服务端全程无感知）。
 *
 * <p><b>二级「转账」视图</b> = 126x135 子面板，锚在主面板 (81,26)（2026-10-08 v4 定稿，
 * 权威口径 = temp/t81/UI-TRANSFER-DESIGN.md）：搜索框与金额框是**原版 {@link EditBox}**（输入即本地过滤 /
 * 纯数字 5..20），滚动条是**原版 {@link AbstractSliderButton} 子类**（{@link TransferScrollBar}）——
 * 三者都不进 {@code renderables}，一律在 {@link #renderTransferPanel} 内手工 render、在鼠标/键盘回调里手工转发
 * （原因：{@link AbstractContainerScreen#render} 先画 renderables，而本面板在 render 末尾画，进去会被面板底图盖住）。
 *
 * <p><b>下标口径</b>：界面上的「行号」是**搜索过滤后**的行号，而载荷要发**完整候选名单**的下标 ⇒ 二者之间由
 * {@link #transferFiltered}（过滤后行号 → 完整名单下标）与 {@link #transferOriginalIndex(int)} 做映射，
 * 发送前一律过一遍映射，**绝不下发过滤后的行号**。
 */
public class CardShopScreen extends AbstractContainerScreen<CardShopMenu> {

    // ── 主面板 ────────────────────────────────────────────────
    private static final int GUI_WIDTH = 288;
    private static final int GUI_HEIGHT = 187;

    private static final int FRAME_X0 = 7;
    private static final int FRAME_PITCH = 46;
    private static final int FRAME_Y = 28;
    private static final int FRAME_W = 44;
    private static final int FRAME_H = 61;
    /** 卡面安全区 38x38（框内偏移 +3,+3 ⇒ bg (10+46i,31)-(47+46i,68)） */
    private static final int FACE_SIZE = 38;
    private static final int FACE_X_OFF = 3;
    private static final int FACE_Y_OFF = 3;
    /**
     * 名称框内区宽 **40**：实测 {@code shop_card.png} 名称框内区 x∈[2,41]（列 1/42 是边框）
     * ⇒ 允许的最大文本宽度；超出即**缩放字号**（禁止截断，2026-10-08 用户裁决）。
     */
    private static final int NAME_BOX_W = 40;
    /** 名称水平锚点 = 名称框内区中心：内区 x∈[2,41] ⇒ 中心 21.5，取 22 传入可得到 21.5 的真实居中 */
    private static final int NAME_CENTER_X_OFF = 22;
    /** 名称绘制顶边：名称框内区 y∈[42,58]（17 行）⇒ 垂直中心 50，MC 行高 9 ⇒ 顶边 50-4 = 46 */
    private static final int NAME_Y_OFF = 46;
    private static final int PROMO_W = 17;
    private static final int PROMO_H = 14;

    private static final int EXCHANGE_X = 11;
    private static final int EXCHANGE_Y = 109;
    private static final int BUTTON_SIZE = 32;
    private static final int TRANSFER_X = 48;
    private static final int TRANSFER_Y = 109;
    private static final int HOURGLASS_X = 86;
    private static final int HOURGLASS_Y = 109;
    private static final int HOURGLASS_W = 20;
    private static final int HOURGLASS_H = 32;

    private static final int BALANCE_X = 11;
    private static final int BALANCE_Y = 145;
    private static final int BALANCE_W = 96;
    private static final int BALANCE_H = 24;
    private static final int BALANCE_ICON_X = 17;
    private static final int BALANCE_ICON_Y = 151;
    private static final int BALANCE_ICON_SIZE = 12;
    /**
     * 余额数字锚点：与玩家物品栏余额栏**同口径**（{@code StarCoinWalletButtons} 的
     * {@code BALANCE_TEXT_RIGHT = 88} / {@code BALANCE_TEXT_DY = 7}）—— 右对齐 + 白字无阴影。
     */
    private static final int BALANCE_TEXT_RIGHT = 88;
    private static final int BALANCE_TEXT_DY = 7;

    private static final int GRID_X = 114;
    private static final int GRID_Y = 99;
    private static final int GRID_W = 166;
    private static final int GRID_H = 80;
    /** 物品格 pitch / 格内边长（槽位坐标一律由 CardShopMenu 的常量派生，保证「物品落在实测内格」） */
    private static final int GRID_PITCH = 18;
    private static final int CELL_SIZE = 16;

    private static final int TITLE_X = 7;
    private static final int TITLE_Y = 3;
    private static final int TITLE_W = 274;
    private static final int TITLE_H = 19;
    /** 标题文字锚点 = 整张面板中心 x=144（品红区中心 143.5，差 0.5px 可忽略） */
    private static final int TITLE_CENTER_X = GUI_WIDTH / 2;
    /** 标题文字顶边 = 品红区垂直中心 12(bg) 上移 4px ⇒ TITLE_Y+6 */
    private static final int TITLE_TEXT_Y = TITLE_Y + 6;
    /** 大酬宾流光换帧速率：8 tick/帧（用户口径「按照 8t 的速率循环播放」） */
    private static final int TITLE_FRAME_TICKS = 8;

    // ── 二级转账面板（v4 定稿；坐标为**面板内相对坐标**，面板原点 = (TP_X, TP_Y)） ──
    private static final int TP_X = 81;
    private static final int TP_Y = 26;
    private static final int TP_BG_W = 92;
    private static final int TP_BG_H = 135;
    // 搜索框：素材自带放大镜 ⇒ 右侧 12px 是「刷新名单」热区，左侧才是输入框本体（原版 EditBox）。
    // ⚠️ TP_SEARCH_FIELD_* / TP_SEARCH_REFRESH_X 都是**相对 TP_SEARCH_X/Y 的素材内偏移**，
    //    控件构造时必须写成 panelX + TP_SEARCH_X + TP_SEARCH_FIELD_X（漏掉素材原点会把框画到面板左上角）。
    private static final int TP_SEARCH_X = 5;
    private static final int TP_SEARCH_Y = 4;
    private static final int TP_SEARCH_W = 82;
    private static final int TP_SEARCH_H = 12;
    private static final int TP_SEARCH_FIELD_X = 3;
    private static final int TP_SEARCH_FIELD_Y = 1;
    private static final int TP_SEARCH_FIELD_W = 65;
    private static final int TP_SEARCH_FIELD_H = 10;
    /** 放大镜热区左缘（面板内 x）：点击 x ≥ 此值 ⇒ 刷新名单，否则聚焦输入框 */
    private static final int TP_SEARCH_REFRESH_X = TP_SEARCH_X + TP_SEARCH_FIELD_X + TP_SEARCH_FIELD_W;
    private static final int TP_BACK_X = 94;
    private static final int TP_BACK_Y = 0;
    private static final int TP_BACK_W = 16;
    private static final int TP_BACK_H = 16;
    private static final int TP_CONFIRM_X = 94;
    private static final int TP_CONFIRM_Y = 24;
    private static final int TP_CONFIRM_W = 32;
    private static final int TP_CONFIRM_H = 22;
    // 金额框：素材底图 32x14，输入框本体（原版 EditBox）居中内缩
    // ⚠️ TP_AMOUNT_FIELD_* 是**相对 TP_AMOUNT_X/Y 的素材内偏移**（构造时必须加上 TP_AMOUNT_X/Y）
    private static final int TP_AMOUNT_X = 94;
    private static final int TP_AMOUNT_Y = 46;
    private static final int TP_AMOUNT_W = 32;
    private static final int TP_AMOUNT_H = 14;
    private static final int TP_AMOUNT_FIELD_X = 6;
    private static final int TP_AMOUNT_FIELD_Y = 3;
    private static final int TP_AMOUNT_FIELD_W = 20;
    private static final int TP_AMOUNT_FIELD_H = 9;
    // 四档 5/10/15/20
    private static final int TP_LIMIT_X = 94;
    private static final int TP_LIMIT_Y0 = 60;
    private static final int TP_LIMIT_PITCH = 15;
    private static final int TP_LIMIT_W = 24;
    private static final int TP_LIMIT_H = 15;
    /**
     * 滚动条：滑轨素材只有 6px 宽 ⇒ **控件矩形放宽到 12px**（原版命中判定用的就是控件矩形，
     * 见 {@link TransferScrollBar}），贴图仍按「滑轨居中 6px、滑块 12x16」原位绘制。
     */
    private static final int TP_RAIL_X = 81;
    private static final int TP_RAIL_Y = 20;
    private static final int TP_RAIL_W = 6;
    private static final int TP_RAIL_H = 111;
    private static final int TP_SLIDE_X = 78;
    private static final int TP_SLIDE_W = 12;
    private static final int TP_SLIDE_H = 16;
    // 列表区：y 从 24 起、行高 18、可见 6 行
    private static final int TP_ROW_X = 2;
    private static final int TP_ROW_RIGHT = 78;
    private static final int TP_ROW_Y0 = 24;
    private static final int TP_ROW_H = 18;
    private static final int TP_ROWS = 6;
    // 行内布局：头像 / 玩家名 / 星币标 / 余额 / 选中指示
    private static final int TP_FACE_X = 6;
    private static final int TP_FACE_Y_OFF = 1;
    private static final int TP_FACE_SIZE = 16;
    private static final int TP_NAME_X = 24;
    private static final int TP_NAME_MAX_W = 42;
    private static final int TP_COIN_X = 24;
    /** 星币标 Y 偏移：与余额文本**顶边对齐**（增补二 §2 口径 coinY = balanceTextY + 0） */
    private static final int TP_COIN_Y_OFF = 9;
    private static final int TP_COIN_SIZE = 7;
    /** star_coin_small.png 是 13x13，取 (3,3) 处的 7x7 子区 */
    private static final int TP_COIN_U = 3;
    private static final int TP_COIN_SRC = 13;
    private static final int TP_BALANCE_X = 33;
    private static final int TP_BALANCE_Y_OFF = 9;
    /**
     * 选中指示（粉色箭头）= shop_transfer_select.png **原生 13x12、不缩放**，画在**玩家名那一行**上：
     * x = 66 固定（紧贴名称列右界 x24..66 ⇒ 永不与名字重叠），y = rowY + 1。
     * 2026-10-08 增补二 §1 覆盖旧口径（旧口径：画在金额行、用已删除的 shop_transfer_select_small.png）。
     */
    private static final int TP_SELECT_X = 66;
    private static final int TP_SELECT_Y_OFF = 1;
    private static final int TP_SELECT_W = 13;
    private static final int TP_SELECT_H = 12;
    /** 玩家名 marquee（增补 §1）：起始停 20 tick → 每 2 tick 移 1px → 到尾反向（纯视觉，不影响命中区） */
    private static final int TP_NAME_HOLD_TICKS = 20;
    private static final int TP_NAME_STEP_TICKS = 2;
    /** 悬停行的半透明灰底（设计文档 (170,170,170,α≈0x66)） */
    private static final int COLOR_ROW_HOVER = 0x66AAAAAA;
    /** 四档固定金额 */
    private static final int[] LIMITS = {5, 10, 15, 20};

    private static final int COLOR_GOLD = 0xFFFFD700;
    private static final int COLOR_GREEN = 0xFF55FF55;
    private static final int COLOR_GRAY = 0xFFB0B0B0;
    private static final int COLOR_WHITE = 0xFFFFFFFF;

    private boolean transferView = false;
    /** 已选中的目标在**完整候选名单**中的下标（-1 = 未选中）——载荷发的就是这个下标 */
    private int transferSelectedOriginal = -1;
    /** 列表窗口顶端行号（口径 = **过滤后**的行号；映射见 {@link #transferOriginalIndex(int)}） */
    private int transferScroll = 0;
    /** 金额权威值（恒 5..20；金额框只是它的一个视图） */
    private int transferAmount = LIMITS[0];
    /** 过滤结果：元素 = 该行在**完整名单**中的下标（过滤后行号 → 完整名单下标） */
    private final List<Integer> transferFiltered = new ArrayList<>();
    /** 生成 transferFiltered 时的名单引用：**引用变了才重建**（服务端每次重发都是新 List） */
    private List<CardShopTransferListPayload.Entry> transferFilteredFrom = List.of();
    /** 搜索框（原版 EditBox；手工 render，不进 renderables） */
    private EditBox transferSearchBox;
    /** 金额框（原版 EditBox；纯数字，非法自动回落到 5） */
    private EditBox transferAmountBox;
    /** 滚动条（原版 AbstractSliderButton 子类；滑轨 6px ⇒ 控件矩形 12px 宽） */
    private TransferScrollBar transferScrollBar;
    /** 正在由代码回写金额框文本（避免 responder 递归） */
    private boolean syncingAmountBox = false;
    /** 玩家名 marquee 的共享相位（containerTick 里 +1；所有行共用同一相位，避免各行各自计时） */
    private int transferNameMarqueeTick = 0;
    /** 确认转账按钮按下反馈剩余 tick（画 shop_transfer_export_push.png） */
    private int transferConfirmPressTicks = 0;
    /** 四档金额按钮按下反馈剩余 tick 与其下标（-1 = 无；画 shop_transfer_button_limit_{i+1}_1.png） */
    private int transferLimitPressTicks = 0;
    private int transferLimitPressIndex = -1;
    /** 左按钮（星光兑换）按下反馈剩余 tick */
    private int exchangePressTicks = 0;
    /** 中按钮（进入转账界面）按下反馈剩余 tick（不可用时也置位 ⇒ 画 _push_1） */
    private int transferPressTicks = 0;

    // ── 拖拽购买状态（纯客户端；服务端无感知，故「未拖到物品栏 ⇒ 卡牌移回」天然成立） ──
    /** 正在被拖动的货架格索引（-1 = 未拖动） */
    private int dragIndex = -1;
    /** 跟手幽灵中心（界面坐标系，未加 leftPos/topPos） */
    private double dragCenterX = 0.0D;
    private double dragCenterY = 0.0D;

    /** 卡面素材存在性缓存（避免每帧对资源管理器做最多 7 次查询；界面重建时清空） */
    private final Map<ResourceLocation, Boolean> faceCache = new HashMap<>();

    public CardShopScreen(CardShopMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = GUI_WIDTH;
        this.imageHeight = GUI_HEIGHT;
        this.titleLabelX = -10000;
        this.titleLabelY = -10000;
        this.inventoryLabelX = -10000;
        this.inventoryLabelY = -10000;
        CardShopClientState.clear();
    }

    @Override
    protected void init() {
        super.init();
        faceCache.clear();
        // 控件坐标依赖 leftPos/topPos ⇒ 必须在 super.init() 之后建
        buildTransferWidgets();
    }

    private static ResourceLocation tex(String name) {
        return ResourceLocation.fromNamespaceAndPath(AstralDiceMod.MODID, "textures/gui/shop/" + name);
    }

    @Override
    protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
        blit(guiGraphics, tex("card_shop_inventory_bg.png"), 0, 0, GUI_WIDTH, GUI_HEIGHT);
        // 物品格底纹（槽位坐标 = 实测内格起点，见 CardShopMenu.INVENTORY_X/Y/HOTBAR_Y）
        blit(guiGraphics, tex("shop_item_inventory.png"), GRID_X, GRID_Y, GRID_W, GRID_H);
    }

    @Override
    protected void renderLabels(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        // 原版标签一律不画（标题与物品栏标签坐标已挪到屏幕外）
    }

    @Override
    public void containerTick() {
        super.containerTick();
        if (exchangePressTicks > 0) {
            exchangePressTicks--;
        }
        if (transferPressTicks > 0) {
            transferPressTicks--;
        }
        if (transferConfirmPressTicks > 0) {
            transferConfirmPressTicks--;
        }
        if (transferLimitPressTicks > 0) {
            transferLimitPressTicks--;
        }
        if (transferView) {
            // 二级视图存活期间：推进玩家名 marquee 共享相位；服务端把「本店会话已用过转账」置位后
            // （DataSlot[8] 同步）自动退回商店主界面（增补二 §4）
            transferNameMarqueeTick++;
            if (this.menu.isTransferUsedThisSession()) {
                closeTransferView();
            }
        }
        // 拖拽兜底：极少数情况下松开事件没送到界面（光标出窗 / 焦点丢失）⇒ 本地解锁，卡牌移回
        if (dragIndex >= 0 && this.minecraft != null && this.minecraft.mouseHandler != null
                && !this.minecraft.mouseHandler.isLeftPressed()) {
            dragIndex = -1;
        }
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        renderTitle(guiGraphics);
        renderShelf(guiGraphics, mouseX, mouseY);
        renderBalance(guiGraphics);
        renderMainButtons(guiGraphics, mouseX, mouseY);
        if (transferView) {
            renderTransferPanel(guiGraphics, mouseX, mouseY);
        }
        // 幽灵卡牌画在最上层（在物品栏、余额条、按钮之上；tooltip 的 z 更高）
        renderDragGhost(guiGraphics);
        this.renderTooltip(guiGraphics, mouseX, mouseY);
        renderShopTooltips(guiGraphics, mouseX, mouseY);
    }

    @Override
    public void removed() {
        super.removed();
        dragIndex = -1;
        CardShopClientState.clear();
    }

    // ────────────────────────────── 主视图绘制 ──────────────────────────────

    private void renderTitle(GuiGraphics guiGraphics) {
        if (this.menu.isPromotionActive()) {
            int frame = titleLightFrame() + 1;
            blit(guiGraphics, tex("shop_title_light_" + frame + ".png"), TITLE_X, TITLE_Y, TITLE_W, TITLE_H);
        } else {
            blit(guiGraphics, tex("shop_title.png"), TITLE_X, TITLE_Y, TITLE_W, TITLE_H);
        }
        guiGraphics.drawCenteredString(this.font, Component.translatable("gui.astral_dice.card_shop"),
                this.leftPos + TITLE_CENTER_X, this.topPos + TITLE_TEXT_Y, COLOR_WHITE);
    }

    /** 流光帧 = 全局 gameTick / 8 mod 3（全局刻 ⇒ 同服两人看到的配色一致）；播不播由 DataSlot[7] 决定 */
    private int titleLightFrame() {
        long tick = this.minecraft != null && this.minecraft.level != null ? this.minecraft.level.getGameTime() : 0L;
        return (int) ((tick / TITLE_FRAME_TICKS) % 3L);
    }

    private void renderShelf(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        for (int i = 0; i < CardShopMenu.SHELF_SIZE; i++) {
            int x = this.leftPos + FRAME_X0 + FRAME_PITCH * i;
            int y = this.topPos + FRAME_Y;
            // 已售出 / 正被拖动（卡已离开货架）⇒ 售罄底图，且不叠卡面与名称（UI-DESIGN 3.4）
            if (this.menu.isSold(i) || i == dragIndex) {
                blitAbs(guiGraphics, tex("shop_card_soldout.png"), x, y, FRAME_W, FRAME_H);
                continue;
            }
            ItemStack stack = this.menu.getSlot(i).getItem();
            if (stack.isEmpty()) {
                // 空槽：卡框恒为 6 个，不随上架数量增减（UI-DESIGN 5.1）
                blitAbs(guiGraphics, tex("shop_card.png"), x, y, FRAME_W, FRAME_H);
                continue;
            }
            boolean hovered = isHoveringAbs(mouseX, mouseY, x, y, FRAME_W, FRAME_H);
            blitAbs(guiGraphics, tex(hovered ? "shop_card_select.png" : "shop_card.png"), x, y, FRAME_W, FRAME_H);
            drawCardFace(guiGraphics, stack, x + FACE_X_OFF, y + FACE_Y_OFF);
            drawCardName(guiGraphics, stack, x, y);
            if (this.menu.isDiscounted(i)) {
                // 折扣角标贴卡面图标右下角
                blitAbs(guiGraphics, tex("shop_promotion.png"),
                        x + FACE_X_OFF + FACE_SIZE - PROMO_W, y + FACE_Y_OFF + FACE_SIZE - PROMO_H, PROMO_W, PROMO_H);
            }
        }
    }

    /** 跟手幽灵：原尺寸卡框 + 卡面 + 名称，85% 不透明；边框用 shop_card_select.png（被拖动的卡） */
    private void renderDragGhost(GuiGraphics guiGraphics) {
        if (dragIndex < 0 || dragIndex >= CardShopMenu.SHELF_SIZE) {
            return;
        }
        ItemStack stack = this.menu.getSlot(dragIndex).getItem();
        if (stack.isEmpty()) {
            return;
        }
        int x = (int) Math.round(dragCenterX) - FRAME_W / 2;
        int y = (int) Math.round(dragCenterY) - FRAME_H / 2;
        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(0.0F, 0.0F, 300.0F);
        guiGraphics.setColor(1.0F, 1.0F, 1.0F, 0.85F);
        blitAbs(guiGraphics, tex("shop_card_select.png"), x, y, FRAME_W, FRAME_H);
        drawCardFace(guiGraphics, stack, x + FACE_X_OFF, y + FACE_Y_OFF);
        drawCardName(guiGraphics, stack, x, y);
        guiGraphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);
        guiGraphics.pose().popPose();
    }

    /** 卡面图标：astral_dice:textures/gui/shop/card/(物品注册名).png（64x64 整体等比缩放到 38x38） */
    private void drawCardFace(GuiGraphics guiGraphics, ItemStack stack, int x, int y) {
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        ResourceLocation face = ResourceLocation.fromNamespaceAndPath(id.getNamespace(),
                "textures/gui/shop/card/" + id.getPath() + ".png");
        if (!faceExists(face)) {
            // 缺素材 ⇒ 画占位（半透明底 + 问号），不崩
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


    /**
     * 卡名绘制（2026-10-08 用户裁决：**禁止截断**，改为全局缩小字号）：
     * <ul>
       <li>宽度超过 {@link #NAME_BOX_W} ⇒ 按 {@code scale = NAME_BOX_W / 文本宽} 缩放 pose 后绘制，保证完整显示；</li>
       <li>水平居中于名称框内区中心（{@link #NAME_CENTER_X_OFF}），垂直锚点 {@link #NAME_Y_OFF}；</li>
       <li>文本样式对齐玩家物品栏余额栏（白字、**无阴影**，见 {@code StarCoinWalletButtons#drawBalance}）。</li>
     * </ul>
     */
    private void drawCardName(GuiGraphics guiGraphics, ItemStack stack, int frameX, int frameY) {
        String name = cardDisplayName(stack);
        int textWidth = this.font.width(name);
        float scale = textWidth <= NAME_BOX_W ? 1.0F : (float) NAME_BOX_W / (float) textWidth;
        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(frameX + NAME_CENTER_X_OFF, frameY + NAME_Y_OFF, 0.0F);
        guiGraphics.pose().scale(scale, scale, 1.0F);
        guiGraphics.drawString(this.font, name, -textWidth / 2, 0, COLOR_WHITE, false);
        guiGraphics.pose().popPose();
    }

    /**
     * 卡名文案：优先取可选的商店专用短名 {@code item.<命名空间>.<注册名>.short}（有键才用，当前 lang 未提供）；
     * **否则直接返回完整名**（禁止截断 —— 2026-10-08 用户裁决；宽度问题由 {@link #drawCardName} 缩放解决）。
     */
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


    private void renderBalance(GuiGraphics guiGraphics) {
        blit(guiGraphics, tex("shop_coin_bar.png"), BALANCE_X, BALANCE_Y, BALANCE_W, BALANCE_H);
        blit(guiGraphics, tex("shop_coin_icon.png"), BALANCE_ICON_X, BALANCE_ICON_Y,
                BALANCE_ICON_SIZE, BALANCE_ICON_SIZE);
        String text = String.valueOf(StarCoinWalletState.balance());
        // 与玩家物品栏余额栏同口径：右对齐锚点 +88、top +7、白字、无阴影（StarCoinWalletButtons#drawBalance）
        guiGraphics.drawString(this.font, text,
                this.leftPos + BALANCE_X + BALANCE_TEXT_RIGHT - this.font.width(text),
                this.topPos + BALANCE_Y + BALANCE_TEXT_DY, COLOR_WHITE, false);
    }

    /**
     * 中按钮可用条件 = 星币 >= CardShopMenu.MIN_TRANSFER 且 转账冷却已结束 且 **本店会话还没用过转账**
     * （2026-10-08 增补二 §4 追加第 3 条；会话位由服务端 DataSlot[8] 同步）。
     */
    private boolean canOpenTransfer() {
        return StarCoinWalletState.balance() >= CardShopMenu.MIN_TRANSFER
                && this.menu.transferCooldownSeconds() <= 0
                && !this.menu.isTransferUsedThisSession();
    }

    private void renderMainButtons(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        boolean exchangeHover = isHovering(mouseX, mouseY, EXCHANGE_X, EXCHANGE_Y, BUTTON_SIZE, BUTTON_SIZE);
        boolean transferHover = isHovering(mouseX, mouseY, TRANSFER_X, TRANSFER_Y, BUTTON_SIZE, BUTTON_SIZE);
        // 左（11,109）= 星光兑换星币：无冷却，故只有 normal / push 两态
        String exchangeTex = exchangeHover && exchangePressTicks > 0
                ? "shop_transfer_button_push.png"
                : "shop_transfer_button.png";
        blit(guiGraphics, tex(exchangeTex), EXCHANGE_X, EXCHANGE_Y, BUTTON_SIZE, BUTTON_SIZE);
        // 中（48,109）= 进入二级转账界面：可用/不可用 × 按下，共 5 态
        //   2026-10-08 用户裁决：starlight_exchange_push_2 = **经商立牌玩家按下**的专属效果
        boolean ready = canOpenTransfer();
        boolean pressed = transferHover && transferPressTicks > 0;
        boolean parunan = this.minecraft != null && this.minecraft.player != null
                && ParunanSignItem.isEquipped(this.minecraft.player);
        String transferTex;
        if (ready) {
            transferTex = pressed
                    ? (parunan ? "starlight_exchange_push_2.png" : "starlight_exchange_push.png")
                    : "starlight_exchange.png";
        } else {
            transferTex = pressed ? "starlight_exchange_push_1.png" : "starlight_exchange_1.png";
        }
        blit(guiGraphics, tex(transferTex), TRANSFER_X, TRANSFER_Y, BUTTON_SIZE, BUTTON_SIZE);

        // 沙漏：剩余 0 ⇒ 第 5 帧（可用）；冷却中 ⇒ floor((1-剩余/总时长)*5) 夹到 0..4（从低到高推进）
        int remain = this.menu.transferCooldownSeconds();
        int frame = 5;
        if (remain > 0) {
            double progress = 1.0D - remain / (double) CardShopMenu.TRANSFER_COOLDOWN_SECONDS;
            frame = Math.max(0, Math.min(4, (int) Math.floor(progress * 5.0D)));
        }
        blit(guiGraphics, tex("transfer_cooling_" + frame + ".png"),
                HOURGLASS_X, HOURGLASS_Y, HOURGLASS_W, HOURGLASS_H);
        if (remain > 0) {
            String text = String.valueOf(remain);
            guiGraphics.drawString(this.font, text,
                    this.leftPos + HOURGLASS_X + (HOURGLASS_W - this.font.width(text)) / 2,
                    this.topPos + HOURGLASS_Y + HOURGLASS_H - 9, COLOR_GOLD, true);
        }
    }

    // ────────────────── 二级转账面板（v4 定稿；控件手工 render/转发） ──────────────────

    /**
     * 转账面板整体绘制。**绘制顺序 = 控件必须手工 render 的原因**：
     * {@link AbstractContainerScreen#render} 的顺序是「底图 → renderables（原版控件都在这）→ renderLabels →
     * 本类 render 的其余部分」，而本面板在主面板之后才画 ⇒ 控件若进 renderables 会被面板底图盖住。
     */
    private void renderTransferPanel(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        // 名单可能刚被服务端重发（打开视图 / 点放大镜 / 转账落定）⇒ 先按最新名单重建过滤结果
        refreshTransferFilterIfNeeded();
        List<CardShopTransferListPayload.Entry> candidates = CardShopClientState.transferCandidates();
        blit(guiGraphics, tex("shop_transfer_bg.png"), TP_X, TP_Y, TP_BG_W, TP_BG_H);
        // 搜索框 = 素材底图（右侧放大镜是刷新热区）+ 原版 EditBox 本体（无边框，只画文本与光标）
        blit(guiGraphics, tex("shop_transfer_search.png"), TP_X + TP_SEARCH_X, TP_Y + TP_SEARCH_Y, TP_SEARCH_W, TP_SEARCH_H);
        if (transferSearchBox != null) {
            transferSearchBox.render(guiGraphics, mouseX, mouseY, 0.0F);
        }
        // 返回：只切视图，不发包
        boolean backHover = isHovering(mouseX, mouseY, TP_X + TP_BACK_X, TP_Y + TP_BACK_Y, TP_BACK_W, TP_BACK_H);
        blit(guiGraphics, tex("shop_transfer_button_back.png"),
                TP_X + TP_BACK_X, TP_Y + TP_BACK_Y, TP_BACK_W, TP_BACK_H);
        guiGraphics.drawCenteredString(this.font, "<", this.leftPos + TP_X + TP_BACK_X + 8,
                this.topPos + TP_Y + TP_BACK_Y + 4, backHover ? COLOR_GOLD : COLOR_GRAY);
        // 名单：空 ⇒ 列表区**居中**提示（整份名单为空 与 搜索无匹配 用不同键）
        if (transferFiltered.isEmpty()) {
            Component empty = Component.translatable(candidates.isEmpty()
                    ? "gui.astral_dice.card_shop.transfer_empty"
                    : "gui.astral_dice.card_shop.transfer_no_match");
            guiGraphics.drawCenteredString(this.font, empty, this.leftPos + TP_X + TP_BG_W / 2,
                    this.topPos + TP_Y + TP_ROW_Y0 + (TP_ROWS * TP_ROW_H) / 2 - 4, COLOR_GRAY);
        }
        for (int row = 0; row < TP_ROWS; row++) {
            int filteredRow = transferScroll + row;
            if (filteredRow >= transferFiltered.size()) {
                break;
            }
            int original = transferFiltered.get(filteredRow);
            renderTransferRow(guiGraphics, row, original, candidates.get(original), mouseX, mouseY);
        }
        // 滚动条（AbstractSliderButton 子类：拖拽手柄 / 点轨道翻页 / 滚轮逐行）
        if (transferScrollBar != null) {
            transferScrollBar.render(guiGraphics, mouseX, mouseY, 0.0F);
        }
        // 确认转账：不可用（未选目标 / 金额 > 余额 / 冷却中）⇒ 压暗 + 灰字，点击只给 actionbar 说明
        boolean confirmable = canConfirmTransfer();
        boolean confirmHover = isHovering(mouseX, mouseY, TP_X + TP_CONFIRM_X, TP_Y + TP_CONFIRM_Y,
                TP_CONFIRM_W, TP_CONFIRM_H);
        // 按下态素材 shop_transfer_export_push.png（增补 §3）：命中 + 刚点过 ⇒ 画按下图
        String confirmTex = confirmHover && transferConfirmPressTicks > 0
                ? "shop_transfer_export_push.png"
                : "shop_transfer_export.png";
        blit(guiGraphics, tex(confirmTex), TP_X + TP_CONFIRM_X, TP_Y + TP_CONFIRM_Y,
                TP_CONFIRM_W, TP_CONFIRM_H);
        if (!confirmable) {
            guiGraphics.fill(this.leftPos + TP_X + TP_CONFIRM_X, this.topPos + TP_Y + TP_CONFIRM_Y,
                    this.leftPos + TP_X + TP_CONFIRM_X + TP_CONFIRM_W,
                    this.topPos + TP_Y + TP_CONFIRM_Y + TP_CONFIRM_H, 0x99000000);
        }
        guiGraphics.drawCenteredString(this.font, Component.translatable("gui.astral_dice.card_shop.transfer_confirm"),
                this.leftPos + TP_X + TP_CONFIRM_X + TP_CONFIRM_W / 2, this.topPos + TP_Y + TP_CONFIRM_Y + 7,
                confirmable ? (confirmHover ? COLOR_GOLD : COLOR_WHITE) : COLOR_GRAY);
        int remain = this.menu.transferCooldownSeconds();
        if (remain > 0) {
            guiGraphics.drawCenteredString(this.font, Component.translatable("gui.astral_dice.card_shop.cooldown", remain),
                    this.leftPos + TP_X + TP_CONFIRM_X + TP_CONFIRM_W / 2, this.topPos + TP_Y + TP_CONFIRM_Y + 24,
                    0xFFFF5555);
        }
        // 金额框 = 素材底图 + 原版 EditBox 本体
        blit(guiGraphics, tex("shop_transfer_limit.png"), TP_X + TP_AMOUNT_X, TP_Y + TP_AMOUNT_Y, TP_AMOUNT_W, TP_AMOUNT_H);
        if (transferAmountBox != null) {
            transferAmountBox.render(guiGraphics, mouseX, mouseY, 0.0F);
        }
        // 四档 5/10/15/20：点击回填金额框（当前档位金色）
        for (int i = 0; i < LIMITS.length; i++) {
            int y = TP_Y + TP_LIMIT_Y0 + i * TP_LIMIT_PITCH;
            boolean hover = isHovering(mouseX, mouseY, TP_X + TP_LIMIT_X, y, TP_LIMIT_W, TP_LIMIT_H);
            // 按下态素材 shop_transfer_button_limit_{i+1}_1.png（增补 §3；_1 后缀 = 按下，**不是**禁用态）
            boolean pressed = hover && transferLimitPressTicks > 0 && transferLimitPressIndex == i;
            blit(guiGraphics, tex("shop_transfer_button_limit_" + (i + 1) + (pressed ? "_1" : "") + ".png"),
                    TP_X + TP_LIMIT_X, y, TP_LIMIT_W, TP_LIMIT_H);
            guiGraphics.drawCenteredString(this.font, String.valueOf(LIMITS[i]),
                    this.leftPos + TP_X + TP_LIMIT_X + TP_LIMIT_W / 2, this.topPos + y + 4,
                    transferAmount == LIMITS[i] || hover ? COLOR_GOLD : COLOR_GRAY);
        }
    }

    /**
     * 名单单行（设计文档「行内布局」）：
     * 皮肤头像 16x16 @ (+6,+1)（脸 (8,8,8,8) + 帽子层 (40,8,8,8) 合成 — 即原版
     * {@link PlayerFaceRenderer#draw}）· 玩家名白色 @ (+24,+0)（宽 42px：**禁止缩小字号**，超宽时左右滚动
     * marquee，见 {@link #transferNameOffset(int)}）· 星币标 7x7 @ (+24,+9)（{@code star_coin_small.png} 的
     * (3,3) 子区，与余额文本顶边对齐）· 余额金色 @ (+33,+9)（千分位）。
     * 悬停行画半透明灰底 (2,+0)-(78,+18)；选中行画 {@code shop_transfer_select.png}(13x12) @ (+66,+1)
     * ——箭头指向**玩家名那一行**（增补二 §1）。
     *
     * @param originalIndex 该行在**完整名单**中的下标
     */
    private void renderTransferRow(GuiGraphics guiGraphics, int row, int originalIndex,
            CardShopTransferListPayload.Entry entry, int mouseX, int mouseY) {
        int rowY = TP_Y + TP_ROW_Y0 + row * TP_ROW_H;
        boolean selected = originalIndex == transferSelectedOriginal;
        if (!selected && isHovering(mouseX, mouseY, TP_X + TP_ROW_X, rowY, TP_ROW_RIGHT - TP_ROW_X, TP_ROW_H)) {
            guiGraphics.fill(this.leftPos + TP_X + TP_ROW_X, this.topPos + rowY,
                    this.leftPos + TP_X + TP_ROW_RIGHT, this.topPos + rowY + TP_ROW_H, COLOR_ROW_HOVER);
        }
        PlayerFaceRenderer.draw(guiGraphics, transferSkin(entry.id()),
                this.leftPos + TP_X + TP_FACE_X, this.topPos + rowY + TP_FACE_Y_OFF, TP_FACE_SIZE);
        // 玩家名：**不缩字号、不截断**，超 42px ⇒ 按共享相位左右滚动（marquee，增补 §1）；用 scissor
        // 裁到名称列（面板内 x 24..66）⇒ 文字不会溢出到箭头/金额列，命中区与点击不受影响
        Component name = Component.literal(entry.name());
        int nameWidth = this.font.width(name);
        int nameOffset = transferNameOffset(Math.max(0, nameWidth - TP_NAME_MAX_W));
        guiGraphics.enableScissor(this.leftPos + TP_X + TP_NAME_X, this.topPos + rowY,
                this.leftPos + TP_X + TP_NAME_X + TP_NAME_MAX_W, this.topPos + rowY + TP_ROW_H);
        guiGraphics.drawString(this.font, name, this.leftPos + TP_X + TP_NAME_X - nameOffset,
                this.topPos + rowY, COLOR_WHITE, false);
        guiGraphics.disableScissor();
        blitSub(guiGraphics, tex("star_coin_small.png"), TP_X + TP_COIN_X, rowY + TP_COIN_Y_OFF,
                TP_COIN_U, TP_COIN_U, TP_COIN_SIZE, TP_COIN_SIZE, TP_COIN_SRC);
        guiGraphics.drawString(this.font, String.format(Locale.ROOT, "%,d", entry.balance()),
                this.leftPos + TP_X + TP_BALANCE_X, this.topPos + rowY + TP_BALANCE_Y_OFF, COLOR_GOLD, false);
        if (selected) {
            // 选中指示 = shop_transfer_select.png **原生 13x12** @ (66, rowY+1)：指向玩家名那一行
            // （增补二 §1；已删除的 shop_transfer_select_small.png 与旧 16x14 回落逻辑一并作废）
            blit(guiGraphics, tex("shop_transfer_select.png"), TP_X + TP_SELECT_X, rowY + TP_SELECT_Y_OFF,
                    TP_SELECT_W, TP_SELECT_H);
        }
    }

    /**
     * 行头像用的皮肤：优先**真实皮肤**（{@code Minecraft#getConnection()#getPlayerInfo(uuid)#getSkin()}），
     * 离线 / 查无此人（PlayerInfo 为 null，例如刚进服的玩家）⇒ 原版 {@link DefaultPlayerSkin#get(UUID)}。
     */
    private PlayerSkin transferSkin(UUID id) {
        if (this.minecraft != null && this.minecraft.getConnection() != null) {
            PlayerInfo info = this.minecraft.getConnection().getPlayerInfo(id);
            if (info != null) {
                return info.getSkin();
            }
        }
        return DefaultPlayerSkin.get(id);
    }

    // ────────────────────────────── 提示 ──────────────────────────────

    private void renderShopTooltips(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        if (!transferView) {
            for (int i = 0; i < CardShopMenu.SHELF_SIZE; i++) {
                ItemStack stack = this.menu.getSlot(i).getItem();
                int x = this.leftPos + FRAME_X0 + FRAME_PITCH * i;
                int y = this.topPos + FRAME_Y;
                if (!isHoveringAbs(mouseX, mouseY, x, y, FRAME_W, FRAME_H)) {
                    continue;
                }
                if (this.menu.isSold(i)) {
                    guiGraphics.renderTooltip(this.font,
                            Component.translatable("gui.astral_dice.card_shop.sold_out"), mouseX, mouseY);
                } else if (!stack.isEmpty()) {
                    // 原有物品信息全保留，另补「折扣 / 售价 / 拖拽购买」三行
                    // （完整卡名 = 物品名，不受名称框截断影响）
                    List<Component> lines = new ArrayList<>(this.getTooltipFromContainerItem(stack));
                    boolean discounted = this.menu.isDiscounted(i);
                    if (discounted) {
                        lines.add(Component.translatable("gui.astral_dice.card_shop.discount")
                                .withStyle(ChatFormatting.GOLD));
                    }
                    lines.add(Component.translatable("gui.astral_dice.card_shop.price", this.menu.priceOf(i))
                            .withStyle(discounted ? ChatFormatting.GREEN : ChatFormatting.GOLD));
                    lines.add(Component.translatable("gui.astral_dice.card_shop.drag_hint")
                            .withStyle(ChatFormatting.GRAY));
                    guiGraphics.renderComponentTooltip(this.font, lines, mouseX, mouseY);
                }
                return;
            }
            if (isHovering(mouseX, mouseY, EXCHANGE_X, EXCHANGE_Y, BUTTON_SIZE, BUTTON_SIZE)) {
                guiGraphics.renderTooltip(this.font,
                        Component.translatable("gui.astral_dice.card_shop.exchange_hint"), mouseX, mouseY);
            } else if (isHovering(mouseX, mouseY, TRANSFER_X, TRANSFER_Y, BUTTON_SIZE, BUTTON_SIZE)) {
                guiGraphics.renderTooltip(this.font,
                        Component.translatable("gui.astral_dice.card_shop.transfer_hint"), mouseX, mouseY);
            } else if (isHovering(mouseX, mouseY, HOURGLASS_X, HOURGLASS_Y, HOURGLASS_W, HOURGLASS_H)) {
                guiGraphics.renderTooltip(this.font,
                        Component.translatable("gui.astral_dice.card_shop.hourglass_hint"), mouseX, mouseY);
            }
            return;
        }
        if (isHovering(mouseX, mouseY, TP_X + TP_AMOUNT_X, TP_Y + TP_AMOUNT_Y, TP_AMOUNT_W, TP_AMOUNT_H)) {
            guiGraphics.renderTooltip(this.font,
                    Component.translatable("gui.astral_dice.card_shop.transfer_amount_field_hint"), mouseX, mouseY);
        } else if (isHovering(mouseX, mouseY, TP_X + TP_SEARCH_X, TP_Y + TP_SEARCH_Y, TP_SEARCH_W, TP_SEARCH_H)) {
            // 搜索框素材右侧自带的放大镜 = 刷新名单热区；左侧才是搜索输入区
            boolean refreshZone = (mouseX - this.leftPos - TP_X) >= TP_SEARCH_REFRESH_X;
            guiGraphics.renderTooltip(this.font, Component.translatable(refreshZone
                    ? "gui.astral_dice.card_shop.transfer_refresh"
                    : "gui.astral_dice.card_shop.transfer_search_hint"), mouseX, mouseY);
        } else if (isHovering(mouseX, mouseY, TP_X + TP_BACK_X, TP_Y + TP_BACK_Y, TP_BACK_W, TP_BACK_H)) {
            guiGraphics.renderTooltip(this.font,
                    Component.translatable("gui.astral_dice.card_shop.transfer_back"), mouseX, mouseY);
        } else if (isHovering(mouseX, mouseY, TP_X + TP_CONFIRM_X, TP_Y + TP_CONFIRM_Y, TP_CONFIRM_W, TP_CONFIRM_H)) {
            guiGraphics.renderTooltip(this.font,
                    Component.translatable("gui.astral_dice.card_shop.transfer_confirm_hint"), mouseX, mouseY);
        }
    }

    // ────────────────────────────── 输入 ──────────────────────────────

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        final int mx = (int) mouseX;
        final int my = (int) mouseY;
        if (button != 0) {
            return super.mouseClicked(mouseX, mouseY, button);
        }
        if (transferView) {
            // ⚠️ 本类覆写了 mouseClicked/Dragged/Released/Scrolled ⇒ 默认的子控件派发**不会**发生，
            // 两个 EditBox 与滚动条都得在这里手工转发（keyPressed/charTyped 例外，见 keyPressed）。
            // 服务端包在两次渲染之间到达过 ⇒ 点击前先按最新名单对齐一次过滤/选中（保证行的下标一定有效）
            refreshTransferFilterIfNeeded();
            if (isHovering(mx, my, TP_X + TP_BACK_X, TP_Y + TP_BACK_Y, TP_BACK_W, TP_BACK_H)) {
                closeTransferView();
                return true;
            }
            if (isHovering(mx, my, TP_X + TP_SEARCH_X, TP_Y + TP_SEARCH_Y, TP_SEARCH_W, TP_SEARCH_H)) {
                if (mx >= this.leftPos + TP_X + TP_SEARCH_REFRESH_X) {
                    this.sendButton(CardShopMenu.BUTTON_REFRESH_TRANSFER);
                } else if (transferSearchBox != null) {
                    if (transferAmountBox != null) {
                        transferAmountBox.setFocused(false);
                        normalizeTransferAmountText();
                    }
                    this.setFocused(transferSearchBox);
                    transferSearchBox.mouseClicked(mouseX, mouseY, button);
                }
                return true;
            }
            if (isHovering(mx, my, TP_X + TP_AMOUNT_X, TP_Y + TP_AMOUNT_Y, TP_AMOUNT_W, TP_AMOUNT_H)) {
                if (transferAmountBox != null) {
                    if (transferSearchBox != null) {
                        transferSearchBox.setFocused(false);
                    }
                    this.setFocused(transferAmountBox);
                    transferAmountBox.mouseClicked(mouseX, mouseY, button);
                }
                return true;
            }
            if (transferScrollBar != null && transferScrollBar.mouseClicked(mouseX, mouseY, button)) {
                // 滚动条不抢输入焦点（否则 EditBox 的光标会消失）
                this.setFocused(null);
                return true;
            }
            if (isHovering(mx, my, TP_X + TP_CONFIRM_X, TP_Y + TP_CONFIRM_Y, TP_CONFIRM_W, TP_CONFIRM_H)) {
                transferConfirmPressTicks = 6;
                confirmTransfer();
                return true;
            }
            for (int i = 0; i < LIMITS.length; i++) {
                if (isHovering(mx, my, TP_X + TP_LIMIT_X, TP_Y + TP_LIMIT_Y0 + i * TP_LIMIT_PITCH,
                        TP_LIMIT_W, TP_LIMIT_H)) {
                    setTransferAmount(LIMITS[i]);
                    transferLimitPressIndex = i;
                    transferLimitPressTicks = 6;
                    return true;
                }
            }
            for (int row = 0; row < TP_ROWS; row++) {
                int filteredRow = transferScroll + row;
                if (filteredRow >= transferFiltered.size()) {
                    break;
                }
                if (isHovering(mx, my, TP_X + TP_ROW_X, TP_Y + TP_ROW_Y0 + row * TP_ROW_H,
                        TP_ROW_RIGHT - TP_ROW_X, TP_ROW_H)) {
                    transferSelectedOriginal = transferFiltered.get(filteredRow);
                    return true;
                }
            }
            // 点空白：收起输入焦点（金额框文本在此落定），不改选中项
            blurTransferWidgets();
            return true;
        }
        // 卡框优先：按下即尝试起拖（不触发任何原版槽位交互）
        int cardIndex = cardFrameIndexAt(mx, my);
        if (cardIndex >= 0) {
            tryStartDrag(cardIndex, mx, my);
            return true;
        }
        if (isHovering(mx, my, EXCHANGE_X, EXCHANGE_Y, BUTTON_SIZE, BUTTON_SIZE)) {
            this.sendButton(CardShopMenu.BUTTON_EXCHANGE);
            exchangePressTicks = 6;
            return true;
        }
        if (isHovering(mx, my, TRANSFER_X, TRANSFER_Y, BUTTON_SIZE, BUTTON_SIZE)) {
            if (!canOpenTransfer()) {
                // 不可用：不进入转账视图，只给按压反馈 + actionbar 说明原因
                transferPressTicks = 6;
                showTransferDisabledHint();
                return true;
            }
            openTransferView();
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    /** 起拖：该槽有货、未售出、且余额 >= 售价；余额不足 ⇒ 拒绝起拖 + actionbar 提示（UI-DESIGN 4.2.1） */
    private void tryStartDrag(int index, int mouseX, int mouseY) {
        if (this.menu.isSold(index)) {
            return;
        }
        if (this.menu.getSlot(index).getItem().isEmpty()) {
            return;
        }
        int price = this.menu.priceOf(index);
        if (StarCoinWalletState.balance() < price) {
            overlayMessage(Component.translatable("msg.astral_dice.card_shop_no_coin", price)
                    .withStyle(ChatFormatting.RED));
            return;
        }
        dragIndex = index;
        dragCenterX = mouseX;
        dragCenterY = mouseY;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (transferView && transferScrollBar != null && transferScrollBar.isHandleDragging()) {
            return transferScrollBar.mouseDragged(mouseX, mouseY, button, dragX, dragY);
        }
        if (dragIndex >= 0 && button == 0) {
            dragCenterX = mouseX;
            dragCenterY = mouseY;
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (transferView && transferScrollBar != null && transferScrollBar.isHandleDragging()) {
            return transferScrollBar.mouseReleased(mouseX, mouseY, button);
        }
        if (button == 0 && dragIndex >= 0) {
            int index = dragIndex;
            dragIndex = -1;
            if (!transferView) {
                int invIndex = inventoryCellIndexAt((int) mouseX, (int) mouseY);
                if (invIndex >= 0) {
                    if (this.menu.getSlot(CardShopMenu.SHELF_SIZE + CardShopMenu.playerSlotOffset(invIndex))
                            .getItem().isEmpty()) {
                        // 合法落点：只发购买意图，扣款与发货由服务端 handleBuy 决定
                        net.neoforged.neoforge.network.PacketDistributor.sendToServer(
                                new CardShopBuyPayload(index, invIndex));
                    } else {
                        overlayMessage(Component.translatable("msg.astral_dice.card_shop_slot_occupied")
                                .withStyle(ChatFormatting.RED));
                    }
                }
                // 落点不在玩家物品栏格内 ⇒ 什么都不发，卡牌原地移回（服务端全程无感知）
            }
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        final int mx = (int) mouseX;
        final int my = (int) mouseY;
        if (transferView) {
            // 金额框上滚轮 ⇒ 金额 ±1（夹在 5..20）；其余位置 ⇒ 列表逐行滚动（与原版滑条滚轮一致）
            if (isHovering(mx, my, TP_X + TP_AMOUNT_X, TP_Y + TP_AMOUNT_Y, TP_AMOUNT_W, TP_AMOUNT_H)) {
                setTransferAmount(transferAmount + (verticalAmount > 0 ? 1 : -1));
                return true;
            }
            scrollTransferBy(verticalAmount > 0 ? -1 : 1);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    /**
     * 确认转账（设计文档 ⑨ 的**下标口径**）：这里发出的必须是选中项在**完整候选名单**中的下标，
     * 而不是界面上的过滤后行号 —— 二者由 {@link #transferOriginalIndex(int)} 转换。
     * 不可用时的点击全部转成 actionbar 说明（未选目标 / 冷却 / 余额不足）。
     */
    private void confirmTransfer() {
        // 发送前与金额框对账：文本合法（5..20）⇒ 以文本为准；非法/越界/空 ⇒ 回落 5 并把文本重写为 5
        // （这样「框中显示的数字」与「实际发出的数字」永远一致；呼应设计文档的三处兜底）
        if (this.transferAmountBox != null) {
            Integer parsed = parseAmount(this.transferAmountBox.getValue());
            this.transferAmount = parsed != null
                    && parsed >= CardShopMenu.MIN_TRANSFER && parsed <= CardShopMenu.MAX_TRANSFER
                            ? parsed : CardShopMenu.MIN_TRANSFER;
            normalizeTransferAmountText();
        }
        int original = transferOriginalIndex(transferSelectedRow());
        if (original < 0) {
            overlayMessage(Component.translatable("gui.astral_dice.card_shop.transfer_select_hint")
                    .withStyle(ChatFormatting.RED));
            return;
        }
        if (!canConfirmTransfer()) {
            if (this.menu.transferCooldownSeconds() <= 0 && transferAmount > StarCoinWalletState.balance()) {
                overlayMessage(Component.translatable("msg.astral_dice.card_shop_no_coin", transferAmount)
                        .withStyle(ChatFormatting.RED));
            } else {
                showTransferDisabledHint();
            }
            return;
        }
        net.neoforged.neoforge.network.PacketDistributor.sendToServer(
                new CardShopTransferPayload(original, transferAmount));
    }

    private void sendButton(int id) {
        if (this.minecraft != null && this.minecraft.gameMode != null) {
            this.minecraft.gameMode.handleInventoryButtonClick(this.menu.containerId, id);
        }
    }

    /**
     * 中按钮不可用时的 actionbar 说明（按真实原因分档，避免误报「星币不足」）：
     * ① 冷却中（自带倒计时）② **本店会话已用过转账**（code_review P2：此时冷却可能已走完、余额也够，
     * 若仍报「星币不足」会让玩家以为钱包出问题）③ 否则报星币门槛。
     */
    private void showTransferDisabledHint() {
        int remain = this.menu.transferCooldownSeconds();
        Component message;
        if (remain > 0) {
            message = Component.translatable("gui.astral_dice.card_shop.cooldown", remain);
        } else if (this.menu.isTransferUsedThisSession()) {
            message = Component.translatable("msg.astral_dice.card_shop_transfer_used");
        } else {
            message = Component.translatable("msg.astral_dice.card_shop_no_coin", CardShopMenu.MIN_TRANSFER);
        }
        overlayMessage(message.copy().withStyle(ChatFormatting.RED));
    }

    private void overlayMessage(Component message) {
        if (this.minecraft != null && this.minecraft.gui != null) {
            this.minecraft.gui.setOverlayMessage(message, false);
        }
    }

    // ────────────────────────────── 坐标工具 ──────────────────────────────

    private boolean isHovering(int mouseX, int mouseY, int x, int y, int w, int h) {
        return isHoveringAbs(mouseX, mouseY, this.leftPos + x, this.topPos + y, w, h);
    }

    private boolean isHoveringAbs(int mouseX, int mouseY, int x, int y, int w, int h) {
        return mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
    }

    /** 命中的卡框索引（-1 = 没命中）；判定区 = 整个卡框 44x61（UI-DESIGN U4 建议口径） */
    private int cardFrameIndexAt(int mouseX, int mouseY) {
        for (int i = 0; i < CardShopMenu.SHELF_SIZE; i++) {
            if (isHovering(mouseX, mouseY, FRAME_X0 + FRAME_PITCH * i, FRAME_Y, FRAME_W, FRAME_H)) {
                return i;
            }
        }
        return -1;
    }

    /**
     * 命中的玩家物品栏格 ⇒ 物品栏索引（0..8 快捷栏、9..35 主物品栏）；没命中返回 -1。
     * 格内矩形口径 = 槽位坐标的 16x16 内格（x = INVENTORY_X+18j；主物品栏 y = INVENTORY_Y+18r、
     * 快捷栏 y = INVENTORY_HOTBAR_Y），与 CardShopMenu 的槽位坐标严格同源。
     */
    private int inventoryCellIndexAt(int mouseX, int mouseY) {
        int guiX = mouseX - this.leftPos;
        int guiY = mouseY - this.topPos;
        for (int row = 0; row < 4; row++) {
            int cellY = row == 3
                    ? CardShopMenu.INVENTORY_HOTBAR_Y
                    : CardShopMenu.INVENTORY_Y + row * GRID_PITCH;
            if (guiY < cellY || guiY > cellY + CELL_SIZE - 1) {
                continue;
            }
            for (int col = 0; col < 9; col++) {
                int cellX = CardShopMenu.INVENTORY_X + col * GRID_PITCH;
                if (guiX >= cellX && guiX <= cellX + CELL_SIZE - 1) {
                    return row == 3 ? col : 9 + row * 9 + col;
                }
            }
        }
        return -1;
    }

    // ───────────────── 二级转账面板：控件构建 / 过滤 / 状态机 ─────────────────

    /**
     * 建三个**原版控件**（坐标依赖 leftPos/topPos ⇒ 只在 {@link #init()} 里调一次）：
     * 搜索框（{@link EditBox}）· 金额框（{@link EditBox}）· 滚动条（{@link TransferScrollBar}）。
     * 它们都**不进 renderables**（会被本面板底图盖住），改由 {@link #renderTransferPanel} 手工 render。
     */
    private void buildTransferWidgets() {
        int panelX = this.leftPos + TP_X;
        int panelY = this.topPos + TP_Y;
        // 搜索框：无边框（底图已给框），输入即本地过滤（setResponder 才是输入回调，setFilter 只是闸门）
        this.transferSearchBox = new EditBox(this.font, panelX + TP_SEARCH_X + TP_SEARCH_FIELD_X,
                panelY + TP_SEARCH_Y + TP_SEARCH_FIELD_Y,
                TP_SEARCH_FIELD_W, TP_SEARCH_FIELD_H,
                Component.translatable("gui.astral_dice.card_shop.transfer_search_hint"));
        this.transferSearchBox.setBordered(false);
        this.transferSearchBox.setMaxLength(16);
        this.transferSearchBox.setHint(Component.translatable("gui.astral_dice.card_shop.transfer_search_hint"));
        this.transferSearchBox.setTextColor(COLOR_WHITE);
        this.transferSearchBox.setResponder(text -> refreshTransferFilter());
        // 金额框：无边框，纯数字，每次输入都校验（三处兜底之一，另两处 = 回车与失焦）
        this.transferAmountBox = new EditBox(this.font, panelX + TP_AMOUNT_X + TP_AMOUNT_FIELD_X,
                panelY + TP_AMOUNT_Y + TP_AMOUNT_FIELD_Y,
                TP_AMOUNT_FIELD_W, TP_AMOUNT_FIELD_H,
                Component.translatable("gui.astral_dice.card_shop.transfer_amount_field_hint"));
        this.transferAmountBox.setBordered(false);
        this.transferAmountBox.setMaxLength(2);
        this.transferAmountBox.setTextColor(COLOR_GOLD);
        this.transferAmountBox.setResponder(this::onTransferAmountTextChanged);
        // 滚动条：控件矩形 12x111（滑轨素材只有 6px 宽 ⇒ 命中区刻意放宽到 12px）
        this.transferScrollBar = new TransferScrollBar(this, panelX + TP_SLIDE_X, panelY + TP_RAIL_Y);
        setTransferAmount(this.transferAmount);
        this.transferSearchBox.setValue("");
        refreshTransferFilter();
        updateTransferWidgets();
    }

    /** 打开二级视图：只在本地切状态 + 请求服务端下发候选名单，不发转账包 */
    private void openTransferView() {
        this.transferView = true;
        // 增补二 §3：进入视图时**不预选任何行**（transferSelectedOriginal = -1 ⇒ 无箭头、确认不可用）
        this.transferSelectedOriginal = -1;
        this.transferScroll = 0;
        this.transferNameMarqueeTick = 0;
        this.setFocused(null);
        if (this.transferAmountBox != null) {
            this.transferAmountBox.setFocused(false);
        }
        if (this.transferSearchBox != null) {
            this.transferSearchBox.setValue("");
            this.transferSearchBox.setFocused(false);
        }
        setTransferAmount(LIMITS[0]);
        refreshTransferFilter();
        updateTransferWidgets();
        this.sendButton(CardShopMenu.BUTTON_TRANSFER);
    }

    /** 返回商店主界面：只切视图（面板内不留任何服务端副作用） */
    private void closeTransferView() {
        this.transferView = false;
        blurTransferWidgets();
        updateTransferWidgets();
    }

    /** 三个控件的 visible/active 随 transferView 走；滚动条额外要求「确实有可滚动的行」 */
    private void updateTransferWidgets() {
        if (this.transferSearchBox == null || this.transferAmountBox == null || this.transferScrollBar == null) {
            return;
        }
        // 1.21.1 的 AbstractWidget 把 active/visible 暴露为 public 字段（没有 setter）⇒ 直接赋值
        this.transferSearchBox.visible = this.transferView;
        this.transferSearchBox.active = this.transferView;
        this.transferAmountBox.visible = this.transferView;
        this.transferAmountBox.active = this.transferView;
        this.transferScrollBar.visible = this.transferView;
        this.transferScrollBar.active = this.transferView && transferMaxScroll() > 0;
        if (!this.transferView) {
            this.transferSearchBox.setFocused(false);
            this.transferAmountBox.setFocused(false);
            this.transferScrollBar.setFocused(false);
        }
        syncTransferScrollBar();
    }

    /** 名单被服务端重发时（每次都是新 List 实例）才重建过滤结果，避免每帧重复过滤 */
    private void refreshTransferFilterIfNeeded() {
        if (CardShopClientState.transferCandidates() != this.transferFilteredFrom) {
            refreshTransferFilter();
        }
    }

    /**
     * 本地过滤（大小写不敏感子串匹配玩家名），并重建 {@link #transferFiltered}
     * （过滤后行号 → **完整名单**下标）。过滤只影响可见窗口，不影响下发索引。
     * 选中项若被过滤掉 ⇒ 置为 -1（**取消「默认选中第一个」**，增补二 §3：只有玩家点了某一行，
     * 箭头才出现；列表空 / 搜索无匹配 ⇒ 无选中、确认按钮不可用）。
     */
    private void refreshTransferFilter() {
        List<CardShopTransferListPayload.Entry> candidates = CardShopClientState.transferCandidates();
        String query = this.transferSearchBox == null
                ? "" : this.transferSearchBox.getValue().trim().toLowerCase(Locale.ROOT);
        this.transferFiltered.clear();
        for (int i = 0; i < candidates.size(); i++) {
            if (query.isEmpty()
                    || candidates.get(i).name().toLowerCase(Locale.ROOT).contains(query)) {
                this.transferFiltered.add(i);
            }
        }
        this.transferFilteredFrom = candidates;
        if (this.transferSelectedOriginal >= 0
                && !this.transferFiltered.contains(this.transferSelectedOriginal)) {
            this.transferSelectedOriginal = -1;
            this.transferScroll = 0;
        }
        setTransferScroll(this.transferScroll);
        updateTransferWidgets();
    }

    /**
     * 玩家名 marquee 的横向滚动偏移（增补 §1）：名称列 42px，超宽部分 overflow = 文本宽 - 42。
     * 轨迹 = 停 {@link #TP_NAME_HOLD_TICKS} tick → 每 {@link #TP_NAME_STEP_TICKS} tick 移 1px 到尾
     * → 再停同样 tick → 反向移回 → 循环；相位取 {@link #transferNameMarqueeTick}（**所有行共享**，
     * 各行按自身 overflow 映射，故同时起步、不会各自错乱）。
     */
    private int transferNameOffset(int overflow) {
        if (overflow <= 0) {
            return 0;
        }
        int travel = overflow * TP_NAME_STEP_TICKS;
        int cycle = 2 * (TP_NAME_HOLD_TICKS + travel);
        int t = this.transferNameMarqueeTick % cycle;
        if (t < TP_NAME_HOLD_TICKS) {
            return 0;
        }
        t -= TP_NAME_HOLD_TICKS;
        if (t < travel) {
            return t / TP_NAME_STEP_TICKS;
        }
        t -= travel;
        if (t < TP_NAME_HOLD_TICKS) {
            return overflow;
        }
        t -= TP_NAME_HOLD_TICKS;
        return overflow - t / TP_NAME_STEP_TICKS;
    }

    /** 已选中项在**过滤后**列表中的行号（-1 = 未选中 / 已被过滤掉） */
    private int transferSelectedRow() {
        return this.transferSelectedOriginal < 0 ? -1 : this.transferFiltered.indexOf(this.transferSelectedOriginal);
    }

    /**
     * **过滤后行号 → 完整候选名单下标**（载荷口径的唯一转换点；越界/未选中 ⇒ -1）。
     * 这是本面板最容易错的一处：界面上看到的是过滤后行号，服务端要的是完整名单下标。
     */
    private int transferOriginalIndex(int filteredRow) {
        if (filteredRow < 0 || filteredRow >= this.transferFiltered.size()) {
            return -1;
        }
        return this.transferFiltered.get(filteredRow);
    }

    private int transferMaxScroll() {
        return Math.max(0, this.transferFiltered.size() - TP_ROWS);
    }

    private void setTransferScroll(int row) {
        this.transferScroll = Math.max(0, Math.min(transferMaxScroll(), row));
        syncTransferScrollBar();
    }

    private void scrollTransferBy(int delta) {
        setTransferScroll(this.transferScroll + delta);
    }

    private void syncTransferScrollBar() {
        if (this.transferScrollBar != null) {
            this.transferScrollBar.syncFromScreen(this.transferScroll, transferMaxScroll());
        }
    }

    /** 写金额（四档按钮 / 滚轮 / 打开视图都走这里）：先夹到 5..20，再把文本同步进金额框 */
    private void setTransferAmount(int amount) {
        this.transferAmount = Math.max(CardShopMenu.MIN_TRANSFER, Math.min(CardShopMenu.MAX_TRANSFER, amount));
        if (this.transferAmountBox != null) {
            this.syncingAmountBox = true;
            this.transferAmountBox.setValue(String.valueOf(this.transferAmount));
            this.syncingAmountBox = false;
        }
    }

    /**
     * 金额框输入回调（三处兜底之「每次输入」）：合法 5..20 ⇒ 接受；纯数字但越界 ⇒ 权威值回落 5，
     * 文本先留着（这样用户还能把 "1" 补成 "15"），回车/失焦时再重写；非数字/空 ⇒ 立即重置为 5。
     */
    private void onTransferAmountTextChanged(String text) {
        if (this.syncingAmountBox) {
            return;
        }
        Integer parsed = parseAmount(text);
        if (parsed == null) {
            setTransferAmount(CardShopMenu.MIN_TRANSFER);
        } else if (parsed >= CardShopMenu.MIN_TRANSFER && parsed <= CardShopMenu.MAX_TRANSFER) {
            this.transferAmount = parsed;
        } else {
            this.transferAmount = CardShopMenu.MIN_TRANSFER;
        }
    }

    /** 把金额框文本重写为权威值（回车 / 失焦兜底） */
    private void normalizeTransferAmountText() {
        if (this.transferAmountBox != null) {
            setTransferAmount(this.transferAmount);
        }
    }

    private static Integer parseAmount(String text) {
        if (text == null || text.isEmpty()) {
            return null;
        }
        try {
            return Integer.valueOf(text);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** 收起输入焦点（金额框文本就此落定） */
    private void blurTransferWidgets() {
        normalizeTransferAmountText();
        if (this.transferSearchBox != null) {
            this.transferSearchBox.setFocused(false);
        }
        if (this.transferAmountBox != null) {
            this.transferAmountBox.setFocused(false);
        }
        this.setFocused(null);
    }

    /** 确认可用条件：已选中合法目标 且 金额 5..20 且 ≤ 持有余额 且 冷却结束（设计文档 ⑥） */
    private boolean canConfirmTransfer() {
        if (transferSelectedRow() < 0) {
            return false;
        }
        if (this.menu.transferCooldownSeconds() > 0) {
            return false;
        }
        return this.transferAmount >= CardShopMenu.MIN_TRANSFER
                && this.transferAmount <= CardShopMenu.MAX_TRANSFER
                && this.transferAmount <= StarCoinWalletState.balance();
    }

    /**
     * 键盘：金额框聚焦时回车（257）/小键盘回车（335）把文本落定；其余一律交给 super
     * ⇒ {@code Screen.keyPressed → ContainerEventHandler 默认}把按键派发给 {@link #getFocused()}，
     * EditBox 的退格/左右/Ctrl+A/C/V 与 ESC 关屏、Tab 焦点导航都保持原版行为。
     * （charTyped 同理：本类不覆写 ⇒ 默认派发给聚焦控件，输入不会被容器吞掉。）
     */
    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (this.transferView && this.transferAmountBox != null && this.transferAmountBox.isFocused()
                && (keyCode == 257 || keyCode == 335)) {
            normalizeTransferAmountText();
            return true;
        }
        boolean textFocused = this.transferView
                && ((this.transferSearchBox != null && this.transferSearchBox.isFocused())
                        || (this.transferAmountBox != null && this.transferAmountBox.isFocused()));
        // 输入框聚焦时按键**只交给 EditBox**、不再冒泡：AbstractContainerScreen.keyPressed 会在 super 返回 false
        // 之后把同一个按键当容器热键处理 —— 于是输入 "e" 会直接关掉商店、输入 1..9 会给鼠标下的槽位换快捷栏、
        // 输入 Q 会丢物品。ESC(256) 与 Tab(258) 例外，保持原版「关屏 / 焦点导航」。charTyped 本类不覆写，
        // 走 ContainerEventHandler 默认派发给焦点控件，所以字符照常输入。
        if (textFocused && keyCode != 256 && keyCode != 258) {
            if (this.getFocused() != null) {
                this.getFocused().keyPressed(keyCode, scanCode, modifiers);
            }
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void blit(GuiGraphics guiGraphics, ResourceLocation texture, int x, int y, int w, int h) {
        blitAbs(guiGraphics, texture, this.leftPos + x, this.topPos + y, w, h);
    }

    /** 贴图子区绘制（u/v/w/h + 源图边长 src；坐标为面板内相对坐标） */
    private void blitSub(GuiGraphics guiGraphics, ResourceLocation texture, int x, int y,
            int u, int v, int w, int h, int src) {
        guiGraphics.blit(texture, this.leftPos + x, this.topPos + y, 0, (float) u, (float) v, w, h, src, src);
    }

    private static void blitAbs(GuiGraphics guiGraphics, ResourceLocation texture, int x, int y, int w, int h) {
        guiGraphics.blit(texture, x, y, 0, 0.0F, 0.0F, w, h, w, h);
    }
    /**
     * 转账列表的滚动条 —— 用**原版 {@link AbstractSliderButton} 逻辑**（拖拽手柄 / 点轨道翻页 / 滚轮逐行），
     * 只把外观换成素材，并把命中区放宽：
     *
     * <ul>
     *   <li>滑轨素材 {@code shop_transfer_slide_rail.png} 只有 6px 宽，太细按不中 ⇒ **控件矩形做成 12x111**
     *       （原版命中判定用的就是控件矩形，见 {@link net.minecraft.client.gui.components.AbstractWidget#isMouseOver}），
     *       贴图仍按滑轨居中 6px 绘制。</li>
     *   <li>贴图是自有素材 ⇒ 整体覆写 {@link #renderWidget}，不用原版 slider sprite。</li>
     *   <li>原版 {@code setValue} 是 private ⇒ 子类只能直接写 {@link #value} 再自行
     *       {@link #applyValue()} / {@link #updateMessage()}（本类封装成 {@link #syncFromScreen}）。</li>
     *   <li>{@code CardShopScreen} 自己吞掉了鼠标事件 ⇒ 拖拽/释放由宿主界面在
     *       {@code mouseDragged/mouseReleased} 里转发，{@link #isHandleDragging()} 是转发开关。</li>
     * </ul>
     */
    private static final class TransferScrollBar extends AbstractSliderButton {

        /** 宿主界面：applyValue / 点轨道翻页都要回调它 */
        private final CardShopScreen screen;
        /** 可滚动的行数（= 过滤后名单条数 − TP_ROWS；0 ⇒ 不可滚动） */
        private int maxRow;
        /** 是否正拖着手柄（宿主据此转发拖动/释放事件） */
        private boolean handleDragging;

        private TransferScrollBar(CardShopScreen screen, int x, int y) {
            super(x, y, TP_SLIDE_W, TP_RAIL_H, Component.empty(), 0.0D);
            this.screen = screen;
        }

        /** 由宿主同步显示状态：直接写 value（绕开原版 setValue）⇒ 不会回调 applyValue，避免回环 */
        private void syncFromScreen(int row, int maxRow) {
            this.maxRow = maxRow;
            this.value = maxRow <= 0 ? 0.0D : Math.max(0.0D, Math.min(1.0D, row / (double) maxRow));
            this.active = maxRow > 0;
            this.visible = screen.transferView;
        }

        private boolean isHandleDragging() {
            return this.handleDragging;
        }

        @Override
        protected void updateMessage() {
            // 列表滚动条不显示数值
        }

        @Override
        protected void applyValue() {
            if (this.maxRow > 0) {
                this.screen.setTransferScroll((int) Math.round(this.value * this.maxRow));
            }
        }

        // AbstractSliderButton 在 1.21.1 把 renderWidget 提升为 public ⇒ 覆写也必须 public
        @Override
        public void renderWidget(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
            if (!this.visible) {
                return;
            }
            blitAbs(guiGraphics, tex("shop_transfer_slide_rail.png"),
                    this.getX() + (TP_SLIDE_W - TP_RAIL_W) / 2, this.getY(), TP_RAIL_W, TP_RAIL_H);
            int travel = TP_RAIL_H - TP_SLIDE_H;
            blitAbs(guiGraphics, tex("shop_transfer_slide_button.png"), this.getX(),
                    this.getY() + (int) Math.round(travel * this.value), TP_SLIDE_W, TP_SLIDE_H);
        }

        /** 按在手柄上 ⇒ 进入拖拽；按在轨道上 ⇒ 与 MC 默认滑条同款「按页翻」（一次一屏 = TP_ROWS 行） */
        @Override
        public void onClick(double mouseX, double mouseY) {
            int handleY = this.getY() + (int) Math.round((TP_RAIL_H - TP_SLIDE_H) * this.value);
            if (mouseY >= handleY && mouseY < handleY + TP_SLIDE_H) {
                this.handleDragging = true;
                return;
            }
            this.screen.scrollTransferBy(mouseY < handleY ? -TP_ROWS : TP_ROWS);
        }

        @Override
        protected void onDrag(double mouseX, double mouseY, double dragX, double dragY) {
            if (!this.handleDragging) {
                return;
            }
            double ratio = (mouseY - (this.getY() + TP_SLIDE_H / 2.0D)) / (TP_RAIL_H - TP_SLIDE_H);
            this.value = Math.max(0.0D, Math.min(1.0D, ratio));
            applyValue();
            updateMessage();
        }

        @Override
        public void onRelease(double mouseX, double mouseY) {
            this.handleDragging = false;
        }
    }
}

