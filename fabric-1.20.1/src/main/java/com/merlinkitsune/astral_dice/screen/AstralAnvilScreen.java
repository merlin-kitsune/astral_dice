package com.merlinkitsune.astral_dice.screen;

import com.merlinkitsune.astral_dice.AstralDiceMod;
import com.merlinkitsune.astral_dice.compat.jei.AstralAnvilJeiBridge;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

/**
 * 星愿铁砧界面:176x166,槽位布局由 {@link AstralAnvilMenu} 决定;背景图是本线资源
 * {@code textures/gui/shop/astral_anvil.png}(资源批负责出图)。
 *
 * <p>1.20.1 的 {@link AbstractContainerScreen#render} 不会自己铺背景(与原版 ItemCombinerScreen 同形),
 * 所以这里覆写 render(),先 renderBackground 再 super.render + renderTooltip。
 *
 * <p>文案口径照原版铁砧:经验文本 container.repair.cost(够付 0x80FF20 / 不够 0xFF6060),
 * 代价 >= 40 且非创造时改显示 container.repair.expensive;无效输入在箭头右侧画原版
 * {@code textures/gui/container/anvil.png} 的 28x21 红叉。
 */
public class AstralAnvilScreen extends AbstractContainerScreen<AstralAnvilMenu> {
    private static final ResourceLocation TEXTURE =
            new ResourceLocation(AstralDiceMod.MODID, "textures/gui/shop/astral_anvil.png");
    private static final ResourceLocation VANILLA_ANVIL_LOCATION =
            new ResourceLocation("textures/gui/container/anvil.png");
    private static final Component TOO_EXPENSIVE_TEXT = Component.translatable("container.repair.expensive");

    private static final int TITLE_COLOR = 0xFFD700;
    private static final int TITLE_Y = 6;
    private static final int INVENTORY_LABEL_Y = 72;
    private static final int COST_Y = 69;
    private static final int COST_RIGHT = 166;
    private static final int COST_BG_TOP = 67;
    private static final int COST_BG_BOTTOM = 79;
    private static final int COST_BG_COLOR = 0x4F000000;
    private static final int COST_COLOR_OK = 0x80FF20;
    private static final int COST_COLOR_BAD = 0xFF6060;
    private static final int ERROR_X = 103;
    private static final int ERROR_Y = 36;
    private static final int ERROR_U = 176;
    private static final int ERROR_WIDTH = 28;
    private static final int ERROR_HEIGHT = 21;

    /** 箭头可点击区（避开材料槽 2 的 89..107）。 */
    private static final int RECIPE_X = 108;
    private static final int RECIPE_Y = 36;
    private static final int RECIPE_WIDTH = 23;
    private static final int RECIPE_HEIGHT = 21;
    private static final Component RECIPE_TOOLTIP =
            Component.translatable("gui.astral_dice.astral_anvil.show_recipes");
    private static final int LABEL_COLOR = 4210752;

    public AstralAnvilScreen(AstralAnvilMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.imageWidth = 176;
        this.imageHeight = 166;
        this.inventoryLabelY = INVENTORY_LABEL_Y;
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(guiGraphics);
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        this.renderTooltip(guiGraphics, mouseX, mouseY);

    }

    @Override
    protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
        guiGraphics.blit(TEXTURE, this.leftPos, this.topPos, 0, 0.0F, 0.0F,
                this.imageWidth, this.imageHeight, this.imageWidth, this.imageHeight);
        if (hasInvalidInput()) {
            guiGraphics.blit(VANILLA_ANVIL_LOCATION, this.leftPos + ERROR_X, this.topPos + ERROR_Y,
                    ERROR_U, 0, ERROR_WIDTH, ERROR_HEIGHT);
        }
    }

    @Override
    protected void renderLabels(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        int titleX = (this.imageWidth - this.font.width(this.title)) / 2;
        guiGraphics.drawString(this.font, this.title, titleX, TITLE_Y, TITLE_COLOR, false);
        guiGraphics.drawString(this.font, this.playerInventoryTitle, this.inventoryLabelX, this.inventoryLabelY,
                LABEL_COLOR, false);
        renderCost(guiGraphics);
    }

    private void renderCost(GuiGraphics guiGraphics) {
        if (!this.menu.getSlot(AstralAnvilMenu.INPUT_SLOT).hasItem()) {
            return;
        }
        int cost = this.menu.getCost();
        if (cost <= 0) {
            return;
        }

        boolean creative = this.minecraft != null && this.minecraft.player != null
                && this.minecraft.player.getAbilities().instabuild;
        Component text;
        int color;
        if (cost >= 40 && !creative) {
            text = TOO_EXPENSIVE_TEXT;
            color = COST_COLOR_BAD;
        } else if (!this.menu.getSlot(AstralAnvilMenu.RESULT_SLOT).hasItem()) {
            return;
        } else {
            text = Component.translatable("container.repair.cost", cost);
            boolean canPay = creative
                    || (this.minecraft.player != null && this.minecraft.player.experienceLevel >= cost);
            color = canPay ? COST_COLOR_OK : COST_COLOR_BAD;
        }

        int costX = COST_RIGHT - 2 - this.font.width(text);
        guiGraphics.fill(costX - 2, COST_BG_TOP, COST_RIGHT, COST_BG_BOTTOM, COST_BG_COLOR);
        guiGraphics.drawString(this.font, text, costX, COST_Y, color, false);
    }

    // ── JEI 配方箭头 ────────────────────────────────────────────────────────
    private boolean isOverRecipeArrow(double mouseX, double mouseY) {
        int localX = (int) mouseX - this.leftPos;
        int localY = (int) mouseY - this.topPos;
        return localX >= RECIPE_X && localX < RECIPE_X + RECIPE_WIDTH
                && localY >= RECIPE_Y && localY < RECIPE_Y + RECIPE_HEIGHT;
    }

    private boolean hasRecipeTarget() {
        return !this.menu.getSlot(AstralAnvilMenu.RESULT_SLOT).getItem().isEmpty()
                || !this.menu.getSlot(AstralAnvilMenu.INPUT_SLOT).getItem().isEmpty();
    }

    private boolean showRecipe() {
        ItemStack input = this.menu.getSlot(AstralAnvilMenu.INPUT_SLOT).getItem();
        if (!input.isEmpty()) {
            return AstralAnvilJeiBridge.showRecipes(input);
        }
        return AstralAnvilJeiBridge.showRecipes(this.menu.getSlot(AstralAnvilMenu.RESULT_SLOT).getItem());
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && this.isOverRecipeArrow(mouseX, mouseY) && this.showRecipe()) {
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    protected void renderTooltip(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        super.renderTooltip(guiGraphics, mouseX, mouseY);
        if (AstralAnvilJeiBridge.isAvailable() && this.hasRecipeTarget()
                && this.isOverRecipeArrow(mouseX, mouseY)) {
            guiGraphics.renderTooltip(this.font, RECIPE_TOOLTIP, mouseX, mouseY);
        }
    }

    private boolean hasInvalidInput() {
        for (int slotIndex = AstralAnvilMenu.INPUT_SLOT; slotIndex <= AstralAnvilMenu.MATERIAL_SLOT_2; slotIndex++) {
            if (this.menu.getSlot(slotIndex).hasItem()) {
                return !this.menu.getSlot(AstralAnvilMenu.RESULT_SLOT).hasItem();
            }
        }
        return false;
    }
}
