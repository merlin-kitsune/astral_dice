package com.merlinkitsune.astral_dice.screen;

import com.merlinkitsune.astral_dice.AstralDiceMod;
import com.merlinkitsune.astral_dice.client.CardShopClientState;
import com.merlinkitsune.astral_dice.item.ModItems;
import com.merlinkitsune.astral_dice.network.CardShopTransferPayload;
import com.merlinkitsune.astral_dice.shop.CardShopMenu;
import com.merlinkitsune.starenginelib.economy.StarCoinWalletState;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * 卡牌商店界面（2026-10-08 v3 计划）。
 *
 * <p>主视图 = 贴图 {@code textures/gui/shop/card_shop_inventory_bg.png}(288x187) 整幅背景；
 * **所有像素坐标都取自背景贴图坐标系**（左上为原点）：卡框 {@code 7+46i / 28}（44x61）、
 * 卡面 36x36 画在框内 (4,4)、价格写在框内 (5,48)、大酬宾标记 17x14 画在框内 (24,47)；
 * 三个金框按钮 = 星光兑换 (11,109) / 转账 (48,109) / 冷却沙漏 (86,109)；
 * 余额条 (12,146) 起 94x17；物品格贴图 {@code shop_item_inventory.png}(166x80) 画在 (114,99)
 * ⇒ 槽位图标坐标与 {@link CardShopMenu#INVENTORY_X}/{@link CardShopMenu#INVENTORY_Y} 对齐；
 * 标题牌 {@code shop_title.png}(274x19) 画在 (7,3) 并叠 3 帧流光动画。
 *
 * <p>二级「转账」视图 = 126x135 子面板，锚在主面板 (81,26)；子面板内部坐标见本类常量
 * （左列玩家名单 92 宽 + 右侧返回/确认/金额/四档限额 + 滑轨）。
 *
 * <p>交互一律走服务端权威：购买/兑换/刷新名单用原版菜单按钮包
 * （{@code clickMenuButton}），转账因为要带「目标索引 + 金额」两个参数，走
 * {@code CardShopTransferPayload}。
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
    private static final int FACE_SIZE = 36;
    private static final int FACE_X_OFF = 4;
    private static final int FACE_Y_OFF = 4;
    private static final int PRICE_X_OFF = 5;
    private static final int PRICE_Y_OFF = 48;
    private static final int PROMO_X_OFF = 24;
    private static final int PROMO_Y_OFF = 47;
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

    private static final int BALANCE_X = 38;
    private static final int BALANCE_Y = 152;
    private static final int BALANCE_ICON_X = 18;
    private static final int BALANCE_ICON_Y = 152;

    private static final int GRID_X = 114;
    private static final int GRID_Y = 99;
    private static final int GRID_W = 166;
    private static final int GRID_H = 80;

    private static final int TITLE_X = 7;
    private static final int TITLE_Y = 3;
    private static final int TITLE_W = 274;
    private static final int TITLE_H = 19;

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

    private boolean transferView = false;
    private int transferSelected = -1;
    private int transferScroll = 0;
    private int transferAmount = LIMITS[0];
    private int pressTicks = 0;
    private int titleTick = 0;

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

    private static ResourceLocation tex(String name) {
        return ResourceLocation.fromNamespaceAndPath(AstralDiceMod.MODID, "textures/gui/shop/" + name);
    }

    @Override
    protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
        blit(guiGraphics, tex("card_shop_inventory_bg.png"), 0, 0, GUI_WIDTH, GUI_HEIGHT);
        // 物品格底纹（与原版槽位坐标严格对齐：格子内部起点 = 1+18i / 1+18r）
        blit(guiGraphics, tex("shop_item_inventory.png"), GRID_X, GRID_Y, GRID_W, GRID_H);
    }

    @Override
    protected void renderLabels(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        // 原版标签一律不画（标题与物品栏标签坐标已挪到屏幕外）
    }

    @Override
    public void containerTick() {
        super.containerTick();
        titleTick++;
        if (pressTicks > 0) {
            pressTicks--;
        }
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        renderTitle(guiGraphics);
        renderShelf(guiGraphics);
        renderBalance(guiGraphics);
        renderMainButtons(guiGraphics, mouseX, mouseY);
        if (transferView) {
            renderTransferPanel(guiGraphics, mouseX, mouseY);
        }
        this.renderTooltip(guiGraphics, mouseX, mouseY);
        renderShopTooltips(guiGraphics, mouseX, mouseY);
    }

    @Override
    public void removed() {
        super.removed();
        CardShopClientState.clear();
    }

    // ────────────────────────────── 主视图绘制 ──────────────────────────────

    private void renderTitle(GuiGraphics guiGraphics) {
        blit(guiGraphics, tex("shop_title.png"), TITLE_X, TITLE_Y, TITLE_W, TITLE_H);
        int frame = (titleTick / 4) % 3 + 1;
        blit(guiGraphics, tex("shop_title_light_" + frame + ".png"), TITLE_X, TITLE_Y, TITLE_W, TITLE_H);
        guiGraphics.drawCenteredString(this.font, this.title, this.leftPos + TITLE_X + TITLE_W / 2,
                this.topPos + TITLE_Y + 6, 0xFFFFFFFF);
    }

    private void renderShelf(GuiGraphics guiGraphics) {
        for (int i = 0; i < CardShopMenu.SHELF_SIZE; i++) {
            ItemStack stack = this.menu.getSlot(i).getItem();
            boolean sold = this.menu.isSold(i);
            if (stack.isEmpty() && !sold) {
                continue;
            }
            int x = this.leftPos + FRAME_X0 + FRAME_PITCH * i;
            int y = this.topPos + FRAME_Y;
            if (sold) {
                blitAbs(guiGraphics, tex("shop_card_soldout.png"), x, y, FRAME_W, FRAME_H);
                continue;
            }
            blitAbs(guiGraphics, tex("shop_card.png"), x, y, FRAME_W, FRAME_H);
            drawCardFace(guiGraphics, stack, x + FACE_X_OFF, y + FACE_Y_OFF);
            boolean discounted = this.menu.isDiscounted(i);
            guiGraphics.drawString(this.font, String.valueOf(this.menu.priceOf(i)),
                    x + PRICE_X_OFF, y + PRICE_Y_OFF, discounted ? COLOR_GREEN : COLOR_GOLD, true);
            if (discounted) {
                blitAbs(guiGraphics, tex("shop_promotion.png"), x + PROMO_X_OFF, y + PROMO_Y_OFF, PROMO_W, PROMO_H);
            }
        }
    }

    private void drawCardFace(GuiGraphics guiGraphics, ItemStack stack, int x, int y) {
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        ResourceLocation face = ResourceLocation.fromNamespaceAndPath(id.getNamespace(),
                "textures/gui/shop/card/" + id.getPath() + ".png");
        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(x, y, 0.0F);
        float scale = FACE_SIZE / 64.0F;
        guiGraphics.pose().scale(scale, scale, 1.0F);
        guiGraphics.blit(face, 0, 0, 0, 0.0F, 0.0F, 64, 64, 64, 64);
        guiGraphics.pose().popPose();
    }

    private void renderBalance(GuiGraphics guiGraphics) {
        guiGraphics.renderItem(new ItemStack(ModItems.STAR_COIN.get()),
                this.leftPos + BALANCE_ICON_X, this.topPos + BALANCE_ICON_Y);
        long balance = StarCoinWalletState.balance();
        guiGraphics.drawString(this.font, String.valueOf(balance),
                this.leftPos + BALANCE_X, this.topPos + BALANCE_Y, COLOR_GOLD, true);
    }

    private void renderMainButtons(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        boolean exchangeHover = isHovering(mouseX, mouseY, EXCHANGE_X, EXCHANGE_Y, BUTTON_SIZE, BUTTON_SIZE);
        boolean transferHover = isHovering(mouseX, mouseY, TRANSFER_X, TRANSFER_Y, BUTTON_SIZE, BUTTON_SIZE);
        String exchangeTex = exchangeHover ? (pressTicks > 0 ? "starlight_exchange_push_1.png" : "starlight_exchange_1.png")
                : "starlight_exchange.png";
        String transferTex = transferHover ? "shop_transfer_button_push.png" : "shop_transfer_button.png";
        blit(guiGraphics, tex(exchangeTex), EXCHANGE_X, EXCHANGE_Y, BUTTON_SIZE, BUTTON_SIZE);
        blit(guiGraphics, tex(transferTex), TRANSFER_X, TRANSFER_Y, BUTTON_SIZE, BUTTON_SIZE);

        int remain = this.menu.transferCooldownSeconds();
        int frame = remain <= 0
                ? 5
                : Math.max(0, Math.min(5, (int) ((1.0D - remain / (double) CardShopMenu.TRANSFER_COOLDOWN_SECONDS) * 6.0D)));
        blit(guiGraphics, tex("transfer_cooling_" + frame + ".png"), HOURGLASS_X, HOURGLASS_Y, HOURGLASS_W, HOURGLASS_H);
        if (remain > 0) {
            String text = String.valueOf(remain);
            guiGraphics.drawString(this.font, text,
                    this.leftPos + HOURGLASS_X + (HOURGLASS_W - this.font.width(text)) / 2,
                    this.topPos + HOURGLASS_Y + HOURGLASS_H - 9, COLOR_GOLD, true);
        }
    }

    // ────────────────────────────── 二级转账面板 ──────────────────────────────

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
        boolean confirmHover = isHovering(mouseX, mouseY, TP_X + TP_CONFIRM_X, TP_Y + TP_CONFIRM_Y, TP_CONFIRM_W, TP_CONFIRM_H);
        blit(guiGraphics, tex("shop_transfer_export.png"), TP_X + TP_CONFIRM_X, TP_Y + TP_CONFIRM_Y, TP_CONFIRM_W, TP_CONFIRM_H);
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
                    guiGraphics.renderTooltip(this.font, stack, mouseX, mouseY);
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
                if (isHovering(mx, my, TP_X + TP_ROW_X, TP_Y + TP_ROW_Y0 + row * TP_ROW_H, TP_BG_W - TP_ROW_X, TP_ROW_H)) {
                    transferSelected = index;
                    return true;
                }
            }
            return true;
        }
        if (isHovering(mx, my, EXCHANGE_X, EXCHANGE_Y, BUTTON_SIZE, BUTTON_SIZE)) {
            this.sendButton(CardShopMenu.BUTTON_EXCHANGE);
            pressTicks = 6;
            return true;
        }
        if (isHovering(mx, my, TRANSFER_X, TRANSFER_Y, BUTTON_SIZE, BUTTON_SIZE)) {
            transferView = true;
            transferSelected = -1;
            transferScroll = 0;
            this.sendButton(CardShopMenu.BUTTON_TRANSFER);
            return true;
        }
        for (int i = 0; i < CardShopMenu.SHELF_SIZE; i++) {
            if (isHovering(mx, my, FRAME_X0 + FRAME_PITCH * i, FRAME_Y, FRAME_W, FRAME_H)) {
                if (!this.menu.isSold(i) && !this.menu.getSlot(i).getItem().isEmpty()) {
                    this.sendButton(CardShopMenu.BUTTON_BUY_BASE + i);
                }
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
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
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    private void confirmTransfer() {
        List<String> names = CardShopClientState.transferCandidates();
        if (transferSelected < 0 || transferSelected >= names.size()) {
            return;
        }
        if (this.menu.transferCooldownSeconds() > 0) {
            return;
        }
        net.neoforged.neoforge.network.PacketDistributor.sendToServer(
                new CardShopTransferPayload(transferSelected, transferAmount));
    }

    private void sendButton(int id) {
        if (this.minecraft != null && this.minecraft.gameMode != null) {
            this.minecraft.gameMode.handleInventoryButtonClick(this.menu.containerId, id);
        }
    }

    // ────────────────────────────── 坐标工具 ──────────────────────────────

    private boolean isHovering(int mouseX, int mouseY, int x, int y, int w, int h) {
        return isHoveringAbs(mouseX, mouseY, this.leftPos + x, this.topPos + y, w, h);
    }

    private boolean isHoveringAbs(int mouseX, int mouseY, int x, int y, int w, int h) {
        return mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
    }

    private void blit(GuiGraphics guiGraphics, ResourceLocation texture, int x, int y, int w, int h) {
        blitAbs(guiGraphics, texture, this.leftPos + x, this.topPos + y, w, h);
    }

    private static void blitAbs(GuiGraphics guiGraphics, ResourceLocation texture, int x, int y, int w, int h) {
        guiGraphics.blit(texture, x, y, 0, 0.0F, 0.0F, w, h, w, h);
    }
}
