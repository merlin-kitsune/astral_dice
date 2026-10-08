package com.merlinkitsune.astral_dice.screen;

import com.merlinkitsune.astral_dice.AstralDiceMod;
import com.merlinkitsune.astral_dice.client.CardShopClientState;
import com.merlinkitsune.astral_dice.item.sign.ParunanSignItem;
import com.merlinkitsune.astral_dice.network.ModNetwork;
import com.merlinkitsune.astral_dice.shop.CardShopMenu;
import com.merlinkitsune.starenginelib.economy.StarCoinWalletState;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
 * 判定落点：命中玩家物品栏空格 ⇒ 发 {@code ModNetwork.CardShopBuyMessage}（服务端权威结算）；落点无效或该格被占用 ⇒
 * 仅清本地拖动态（卡牌原地移回，服务端全程无感知）。
 *
 * <p>二级「转账」视图 = 126x135 子面板，锚在主面板 (81,26)；子面板内部坐标见本类常量，本次未改动。
 *
 * <p><b>1.20.1 Forge 移植差异</b>（其余逻辑与 1.21.1 版逐字相同，差异仅限平台 API）：
 * ① {@code ResourceLocation.fromNamespaceAndPath(ns, path)} 在 1.20.1 不存在 ⇒ 一律改成
 *    {@code new ResourceLocation(ns, path)}；
 * ② {@code GuiGraphics#blit} 在 1.20.1 是 **10 参**（第 4 参是 int blitOffset，位于 x/y 之后、
 *    u/v 之前）：{@code blit(tex, x, y, blitOffset, uOffset, vOffset, w, h, texW, texH)}
 *    —— 1.21.1 无 blitOffset 参数；
 * ③ {@code containerTick()} 两线同名（1.20.1 的 {@code tick()} 是 final ⇒ 只能覆写 containerTick）；
 * ④ {@code mouseScrolled} 在 1.20.1 只有 3 参（无 horizontalAmount），故本线把滚轮微调金额/滚动名单
 *    的判定改为「非零即有效」，横向分量不存在；
 * ⑤ C2S 发包一律改走本线 {@code ModNetwork.sendToServer(Object)}（1.21.1 是
 *    {@code PacketDistributor.sendToServer}）：拖拽购买 = {@code ModNetwork.CardShopBuyMessage(id 14)}，
 *    转账 = {@code ModNetwork.CardShopTransferMessage(id 13)}。
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

    // ── 二级转账面板 ──────────────────────────────────────────
    private static final int TP_X = 81;
    private static final int TP_Y = 26;
    private static final int TP_BG_W = 92;
    private static final int TP_BG_H = 135;
    private static final int TP_SEARCH_X = 5;
    private static final int TP_SEARCH_Y = 4;
    private static final int TP_SEARCH_W = 82;
    private static final int TP_SEARCH_H = 12;
    private static final int TP_BACK_X = 94;
    private static final int TP_BACK_Y = 0;
    private static final int TP_BACK_W = 16;
    private static final int TP_BACK_H = 16;
    private static final int TP_CONFIRM_X = 94;
    private static final int TP_CONFIRM_Y = 24;
    private static final int TP_CONFIRM_W = 32;
    private static final int TP_CONFIRM_H = 22;
    private static final int TP_AMOUNT_X = 94;
    private static final int TP_AMOUNT_Y = 46;
    private static final int TP_AMOUNT_W = 32;
    private static final int TP_AMOUNT_H = 14;
    private static final int TP_LIMIT_X = 94;
    private static final int TP_LIMIT_Y0 = 60;
    private static final int TP_LIMIT_PITCH = 15;
    private static final int TP_LIMIT_W = 24;
    private static final int TP_LIMIT_H = 15;
    private static final int TP_RAIL_X = 81;
    private static final int TP_RAIL_Y = 22;
    private static final int TP_RAIL_W = 6;
    private static final int TP_RAIL_H = 111;
    private static final int TP_SLIDE_X = 78;
    private static final int TP_SLIDE_Y = 21;
    private static final int TP_SLIDE_W = 12;
    private static final int TP_SLIDE_H = 16;
    private static final int TP_ROW_X = 2;
    private static final int TP_ROW_Y0 = 20;
    private static final int TP_ROW_H = 12;
    private static final int TP_ROWS = 9;
    /** 四档固定金额（自定义金额 = 在 5..20 内用滚轮微调） */
    private static final int[] LIMITS = {5, 10, 15, 20};

    private static final int COLOR_GOLD = 0xFFFFD700;
    private static final int COLOR_GREEN = 0xFF55FF55;
    private static final int COLOR_GRAY = 0xFFB0B0B0;
    private static final int COLOR_WHITE = 0xFFFFFFFF;

    private boolean transferView = false;
    private int transferSelected = -1;
    private int transferScroll = 0;
    private int transferAmount = LIMITS[0];
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
    }

    private static ResourceLocation tex(String name) {
        return new ResourceLocation(AstralDiceMod.MODID, "textures/gui/shop/" + name);
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
        ResourceLocation face = new ResourceLocation(id.getNamespace(),
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

    /** 中按钮可用条件 = 星币 >= CardShopMenu.MIN_TRANSFER 且 转账冷却已结束（UI-DESIGN 3.1） */
    private boolean canOpenTransfer() {
        return StarCoinWalletState.balance() >= CardShopMenu.MIN_TRANSFER
                && this.menu.transferCooldownSeconds() <= 0;
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

    // ──────────────────────── 二级转账面板（功能与坐标未改动） ────────────────────────

    private void renderTransferPanel(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        blit(guiGraphics, tex("shop_transfer_bg.png"), TP_X, TP_Y, TP_BG_W, TP_BG_H);
        blit(guiGraphics, tex("shop_transfer_search.png"), TP_X + TP_SEARCH_X, TP_Y + TP_SEARCH_Y, TP_SEARCH_W, TP_SEARCH_H);
        boolean backHover = isHovering(mouseX, mouseY, TP_X + TP_BACK_X, TP_Y + TP_BACK_Y, TP_BACK_W, TP_BACK_H);
        blit(guiGraphics, tex("shop_transfer_button_back.png"),
                TP_X + TP_BACK_X, TP_Y + TP_BACK_Y, TP_BACK_W, TP_BACK_H);
        guiGraphics.drawCenteredString(this.font, "<", this.leftPos + TP_X + TP_BACK_X + 8,
                this.topPos + TP_Y + TP_BACK_Y + 4, backHover ? COLOR_GOLD : COLOR_GRAY);

        List<String> names = CardShopClientState.transferCandidates();
        if (names.isEmpty()) {
            guiGraphics.drawString(this.font, Component.translatable("gui.astral_dice.card_shop.transfer_empty"),
                    this.leftPos + TP_X + 4, this.topPos + TP_Y + TP_ROW_Y0 + 4, COLOR_GRAY, false);
        }
        for (int row = 0; row < TP_ROWS; row++) {
            int index = transferScroll + row;
            if (index >= names.size()) {
                break;
            }
            int rowY = TP_Y + TP_ROW_Y0 + row * TP_ROW_H;
            boolean selected = index == transferSelected;
            if (selected) {
                blit(guiGraphics, tex("shop_transfer_select.png"), TP_X + TP_ROW_X - 1, rowY - 1, 16, 14);
            }
            guiGraphics.drawString(this.font, this.font.plainSubstrByWidth(names.get(index), 68),
                    this.leftPos + TP_X + TP_ROW_X + 4, this.topPos + rowY + 2,
                    selected ? COLOR_GOLD : 0xFFE0E0E0, false);
        }
        // 滑轨 + 滑块
        blit(guiGraphics, tex("shop_transfer_slide_rail.png"), TP_X + TP_RAIL_X, TP_Y + TP_RAIL_Y, TP_RAIL_W, TP_RAIL_H);
        int maxScroll = Math.max(0, names.size() - TP_ROWS);
        int travel = TP_RAIL_H - TP_SLIDE_H;
        int slideOffset = maxScroll <= 0 ? 0 : (int) (travel * (transferScroll / (double) maxScroll));
        blit(guiGraphics, tex("shop_transfer_slide_button.png"), TP_X + TP_SLIDE_X, TP_Y + TP_SLIDE_Y + slideOffset,
                TP_SLIDE_W, TP_SLIDE_H);

        // 确认按钮 + 金额条 + 四档限额
        boolean confirmHover = isHovering(mouseX, mouseY, TP_X + TP_CONFIRM_X, TP_Y + TP_CONFIRM_Y,
                TP_CONFIRM_W, TP_CONFIRM_H);
        blit(guiGraphics, tex("shop_transfer_export.png"), TP_X + TP_CONFIRM_X, TP_Y + TP_CONFIRM_Y,
                TP_CONFIRM_W, TP_CONFIRM_H);
        guiGraphics.drawCenteredString(this.font, Component.translatable("gui.astral_dice.card_shop.transfer_confirm"),
                this.leftPos + TP_X + TP_CONFIRM_X + TP_CONFIRM_W / 2, this.topPos + TP_Y + TP_CONFIRM_Y + 7,
                confirmHover ? COLOR_GOLD : COLOR_GRAY);
        int remain = this.menu.transferCooldownSeconds();
        if (remain > 0) {
            guiGraphics.drawCenteredString(this.font, Component.translatable("gui.astral_dice.card_shop.cooldown", remain),
                    this.leftPos + TP_X + TP_CONFIRM_X + TP_CONFIRM_W / 2, this.topPos + TP_Y + TP_CONFIRM_Y + 24,
                    0xFFFF5555);
        }
        blit(guiGraphics, tex("shop_transfer_limit.png"), TP_X + TP_AMOUNT_X, TP_Y + TP_AMOUNT_Y, TP_AMOUNT_W, TP_AMOUNT_H);
        guiGraphics.drawCenteredString(this.font, String.valueOf(transferAmount),
                this.leftPos + TP_X + TP_AMOUNT_X + TP_AMOUNT_W / 2, this.topPos + TP_Y + TP_AMOUNT_Y + 3, COLOR_GOLD);
        for (int i = 0; i < LIMITS.length; i++) {
            int y = TP_Y + TP_LIMIT_Y0 + i * TP_LIMIT_PITCH;
            boolean hover = isHovering(mouseX, mouseY, TP_X + TP_LIMIT_X, y, TP_LIMIT_W, TP_LIMIT_H);
            if (transferAmount == LIMITS[i] || hover) {
                blit(guiGraphics, tex("shop_transfer_button_limit_" + (i + 1) + ".png"),
                        TP_X + TP_LIMIT_X, y, TP_LIMIT_W, TP_LIMIT_H);
            }
            guiGraphics.drawCenteredString(this.font, String.valueOf(LIMITS[i]),
                    this.leftPos + TP_X + TP_LIMIT_X + TP_LIMIT_W / 2, this.topPos + y + 4,
                    transferAmount == LIMITS[i] ? COLOR_GOLD : COLOR_GRAY);
        }
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
                    Component.translatable("gui.astral_dice.card_shop.transfer_amount_hint"), mouseX, mouseY);
        } else if (isHovering(mouseX, mouseY, TP_X + TP_SEARCH_X, TP_Y + TP_SEARCH_Y, TP_SEARCH_W, TP_SEARCH_H)) {
            guiGraphics.renderTooltip(this.font,
                    Component.translatable("gui.astral_dice.card_shop.transfer_refresh"), mouseX, mouseY);
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
            if (isHovering(mx, my, TP_X + TP_BACK_X, TP_Y + TP_BACK_Y, TP_BACK_W, TP_BACK_H)) {
                transferView = false;
                return true;
            }
            if (isHovering(mx, my, TP_X + TP_SEARCH_X, TP_Y + TP_SEARCH_Y, TP_SEARCH_W, TP_SEARCH_H)) {
                this.sendButton(CardShopMenu.BUTTON_REFRESH_TRANSFER);
                return true;
            }
            if (isHovering(mx, my, TP_X + TP_CONFIRM_X, TP_Y + TP_CONFIRM_Y, TP_CONFIRM_W, TP_CONFIRM_H)) {
                confirmTransfer();
                return true;
            }
            for (int i = 0; i < LIMITS.length; i++) {
                if (isHovering(mx, my, TP_X + TP_LIMIT_X, TP_Y + TP_LIMIT_Y0 + i * TP_LIMIT_PITCH,
                        TP_LIMIT_W, TP_LIMIT_H)) {
                    transferAmount = LIMITS[i];
                    return true;
                }
            }
            List<String> names = CardShopClientState.transferCandidates();
            for (int row = 0; row < TP_ROWS; row++) {
                int index = transferScroll + row;
                if (index >= names.size()) {
                    break;
                }
                if (isHovering(mx, my, TP_X + TP_ROW_X, TP_Y + TP_ROW_Y0 + row * TP_ROW_H,
                        TP_BG_W - TP_ROW_X, TP_ROW_H)) {
                    transferSelected = index;
                    return true;
                }
            }
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
            transferView = true;
            transferSelected = -1;
            transferScroll = 0;
            this.sendButton(CardShopMenu.BUTTON_TRANSFER);
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
        if (dragIndex >= 0 && button == 0) {
            dragCenterX = mouseX;
            dragCenterY = mouseY;
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0 && dragIndex >= 0) {
            int index = dragIndex;
            dragIndex = -1;
            if (!transferView) {
                int invIndex = inventoryCellIndexAt((int) mouseX, (int) mouseY);
                if (invIndex >= 0) {
                    if (this.menu.getSlot(CardShopMenu.SHELF_SIZE + CardShopMenu.playerSlotOffset(invIndex))
                            .getItem().isEmpty()) {
                        // 合法落点：只发购买意图，扣款与发货由服务端 handleBuy 决定
                        ModNetwork.sendToServer(
                                new ModNetwork.CardShopBuyMessage(index, invIndex));
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
    public boolean mouseScrolled(double mouseX, double mouseY, double verticalAmount) {
        final int mx = (int) mouseX;
        final int my = (int) mouseY;
        if (transferView) {
            if (isHovering(mx, my, TP_X + TP_AMOUNT_X, TP_Y + TP_AMOUNT_Y, TP_AMOUNT_W, TP_AMOUNT_H)) {
                int delta = verticalAmount > 0 ? 1 : -1;
                transferAmount = Math.max(CardShopMenu.MIN_TRANSFER,
                        Math.min(CardShopMenu.MAX_TRANSFER, transferAmount + delta));
                return true;
            }
            int maxScroll = Math.max(0, CardShopClientState.transferCandidates().size() - TP_ROWS);
            int delta = verticalAmount > 0 ? -1 : 1;
            transferScroll = Math.max(0, Math.min(maxScroll, transferScroll + delta));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, verticalAmount);
    }

    private void confirmTransfer() {
        List<String> names = CardShopClientState.transferCandidates();
        if (transferSelected < 0 || transferSelected >= names.size()) {
            return;
        }
        if (this.menu.transferCooldownSeconds() > 0) {
            return;
        }
        ModNetwork.sendToServer(
                new ModNetwork.CardShopTransferMessage(transferSelected, transferAmount));
    }

    private void sendButton(int id) {
        if (this.minecraft != null && this.minecraft.gameMode != null) {
            this.minecraft.gameMode.handleInventoryButtonClick(this.menu.containerId, id);
        }
    }

    /** 中按钮不可用时的 actionbar 说明：冷却优先（冷却本身也有倒计时），否则报星币门槛 */
    private void showTransferDisabledHint() {
        int remain = this.menu.transferCooldownSeconds();
        Component message = remain > 0
                ? Component.translatable("gui.astral_dice.card_shop.cooldown", remain)
                : Component.translatable("msg.astral_dice.card_shop_no_coin", CardShopMenu.MIN_TRANSFER);
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

    private void blit(GuiGraphics guiGraphics, ResourceLocation texture, int x, int y, int w, int h) {
        blitAbs(guiGraphics, texture, this.leftPos + x, this.topPos + y, w, h);
    }

    private static void blitAbs(GuiGraphics guiGraphics, ResourceLocation texture, int x, int y, int w, int h) {
        guiGraphics.blit(texture, x, y, 0, 0.0F, 0.0F, w, h, w, h);
    }
}
