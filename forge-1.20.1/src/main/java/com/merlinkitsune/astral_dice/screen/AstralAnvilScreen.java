package com.merlinkitsune.astral_dice.screen;

import com.merlinkitsune.astral_dice.AstralDiceMod;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

/**
 * 星愿铁砧界面（2026-11-15）。
 *
 * <p>布局：标题金色居中（y=6）、物品栏标签 y=72、经验消耗右对齐到 x=166（y=69）；
 * 任一输入有物但结果为空时，在右侧箭头 (103,36) 画原版 28×21 红叉。
 */
public class AstralAnvilScreen extends AbstractContainerScreen<AstralAnvilMenu> {

    private static final ResourceLocation TEXTURE = new ResourceLocation(AstralDiceMod.MODID,
            "textures/gui/shop/astral_anvil.png");
    private static final ResourceLocation VANILLA_ANVIL = new ResourceLocation("minecraft",
            "textures/gui/container/anvil.png");

    private static final int TITLE_Y = 6;
    private static final int TITLE_COLOR = 0xFFD700;
    private static final int INVENTORY_LABEL_COLOR = 4210752;
    private static final int COST_Y = 69;
    private static final int COST_RIGHT = 166;
    private static final int COST_BACKGROUND_TOP = 67;
    private static final int COST_BACKGROUND_BOTTOM = 79;
    private static final int COST_BACKGROUND = 0x4F000000;
    private static final int COST_COLOR = 0x80FF20;
    private static final int COST_EXPENSIVE_COLOR = 0xFF6060;
    private static final int EXPENSIVE_THRESHOLD = 40;

    private static final int ERROR_X = 103;
    private static final int ERROR_Y = 36;
    private static final int ERROR_WIDTH = 28;
    private static final int ERROR_HEIGHT = 21;
    private static final int ERROR_U = 176;
    private static final int ERROR_V = 0;

    public AstralAnvilScreen(AstralAnvilMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = 176;
        this.imageHeight = 166;
        this.inventoryLabelY = 72;
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(guiGraphics);
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        this.renderTooltip(guiGraphics, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
        // 本线贴图已裁成 176×166，纹理尺寸按 imageWidth/imageHeight 取（同 CardInventoryScreen）。
        guiGraphics.blit(TEXTURE, this.leftPos, this.topPos, 0, 0.0F, 0.0F,
                this.imageWidth, this.imageHeight, this.imageWidth, this.imageHeight);
        if (this.hasInvalidInput()) {
            guiGraphics.blit(VANILLA_ANVIL, this.leftPos + ERROR_X, this.topPos + ERROR_Y,
                    ERROR_U, ERROR_V, ERROR_WIDTH, ERROR_HEIGHT);
        }
    }

    @Override
    protected void renderLabels(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        guiGraphics.drawString(this.font, this.playerInventoryTitle,
                this.inventoryLabelX, this.inventoryLabelY, INVENTORY_LABEL_COLOR);
        guiGraphics.drawCenteredString(this.font, this.title, this.imageWidth / 2, TITLE_Y, TITLE_COLOR);

        int cost = this.menu.getCost();
        if (cost <= 0) {
            return;
        }
        boolean creative = this.minecraft.player != null && this.minecraft.player.getAbilities().instabuild;
        boolean tooExpensive = cost >= EXPENSIVE_THRESHOLD && !creative;
        Component text = tooExpensive
                ? Component.translatable("container.repair.expensive")
                : Component.translatable("container.repair.cost", cost);
        int color = tooExpensive ? COST_EXPENSIVE_COLOR : COST_COLOR;
        int x = COST_RIGHT - this.font.width(text);
        guiGraphics.fill(x - 2, COST_BACKGROUND_TOP, COST_RIGHT, COST_BACKGROUND_BOTTOM, COST_BACKGROUND);
        guiGraphics.drawString(this.font, text, x, COST_Y, color);
    }

    /** 无效输入：任一输入槽有物，但结果槽为空（原版铁砧红叉的触发条件）。 */
    private boolean hasInvalidInput() {
        if (this.menu.getSlot(AstralAnvilMenu.RESULT_SLOT).hasItem()) {
            return false;
        }
        return this.menu.getSlot(AstralAnvilMenu.INPUT_SLOT).hasItem()
                || this.menu.getSlot(AstralAnvilMenu.MATERIAL_SLOT_FIRST).hasItem()
                || this.menu.getSlot(AstralAnvilMenu.MATERIAL_SLOT_SECOND).hasItem();
    }
}
