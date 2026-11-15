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
 * 星愿铁砧界面：{@code textures/gui/shop/astral_anvil.png}（176x166），
 * 标题金色居中、经验消耗右对齐、输入无效时画原版铁砧的红叉。
 */
public class AstralAnvilScreen extends AbstractContainerScreen<AstralAnvilMenu> {

    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath(AstralDiceMod.MODID, "textures/gui/shop/astral_anvil.png");
    private static final ResourceLocation VANILLA_ANVIL_TEXTURE =
            ResourceLocation.fromNamespaceAndPath("minecraft", "textures/gui/container/anvil.png");

    private static final int TITLE_COLOR = 0xFFD700;
    private static final int TITLE_Y = 6;
    private static final int LABEL_COLOR = 4210752;

    private static final int COST_Y = 69;
    private static final int COST_RIGHT = 166;
    private static final int COST_COLOR_NORMAL = 0x80FF20;
    private static final int COST_COLOR_TOO_EXPENSIVE = 0xFF6060;
    private static final int COST_BOX_COLOR = 0x4F000000;
    private static final int COST_BOX_TOP = 67;
    private static final int COST_BOX_BOTTOM = 79;
    private static final int TOO_EXPENSIVE_LEVEL = 40;

    private static final int ERROR_X = 103;
    private static final int ERROR_Y = 36;
    private static final int ERROR_WIDTH = 28;
    private static final int ERROR_HEIGHT = 21;

    /** 箭头可点击区（避开材料槽 2 的 89..107）。 */
    private static final int RECIPE_X = 108;
    private static final int RECIPE_Y = 36;
    private static final int RECIPE_WIDTH = 23;
    private static final int RECIPE_HEIGHT = 21;
    private static final Component RECIPE_TOOLTIP =
            Component.translatable("gui.astral_dice.astral_anvil.show_recipes");
    private static final int ERROR_U = 176;
    private static final int ERROR_V = 0;
    private static final int VANILLA_TEXTURE_SIZE = 256;

    public AstralAnvilScreen(AstralAnvilMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.imageWidth = 176;
        this.imageHeight = 166;
        this.inventoryLabelY = 72;
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        this.renderTooltip(guiGraphics, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
        int x = this.leftPos;
        int y = this.topPos;
        guiGraphics.blit(TEXTURE, x, y, 0, 0.0F, 0.0F, this.imageWidth, this.imageHeight, this.imageWidth, this.imageHeight);
        if (this.hasInvalidInput()) {
            guiGraphics.blit(VANILLA_ANVIL_TEXTURE, x + ERROR_X, y + ERROR_Y, 0, (float) ERROR_U, (float) ERROR_V,
                    ERROR_WIDTH, ERROR_HEIGHT, VANILLA_TEXTURE_SIZE, VANILLA_TEXTURE_SIZE);
        }
    }

    @Override
    protected void renderLabels(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        guiGraphics.drawString(this.font, this.title, (this.imageWidth - this.font.width(this.title)) / 2, TITLE_Y,
                TITLE_COLOR, false);
        guiGraphics.drawString(this.font, this.playerInventoryTitle, this.inventoryLabelX, this.inventoryLabelY,
                LABEL_COLOR, false);
        this.renderExperienceCost(guiGraphics);
    }

    private void renderExperienceCost(GuiGraphics guiGraphics) {
        int cost = this.menu.getCost();
        if (cost <= 0 || this.minecraft == null || this.minecraft.player == null) {
            return;
        }
        boolean creative = this.minecraft.player.getAbilities().instabuild;
        Component text = Component.translatable("container.repair.cost", cost);
        int color = COST_COLOR_NORMAL;
        if (cost >= TOO_EXPENSIVE_LEVEL && !creative) {
            text = Component.translatable("container.repair.expensive");
            color = COST_COLOR_TOO_EXPENSIVE;
        } else if (!this.menu.getSlot(AstralAnvilMenu.RESULT_SLOT).mayPickup(this.minecraft.player)) {
            color = COST_COLOR_TOO_EXPENSIVE;
        }
        int costX = COST_RIGHT - this.font.width(text);
        guiGraphics.fill(costX - 2, COST_BOX_TOP, COST_RIGHT, COST_BOX_BOTTOM, COST_BOX_COLOR);
        guiGraphics.drawString(this.font, text, costX, COST_Y, color, false);
    }

    /**
     * 只要有一个输入槽有物品、而结果槽空着，就说明这组输入做不出东西。
     */
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
        return this.menu.getSlot(AstralAnvilMenu.RESULT_SLOT).getItem().isEmpty() ? AstralAnvilJeiBridge.showRecipes(ItemStack.EMPTY) : AstralAnvilJeiBridge.showRecipes(this.menu.getSlot(AstralAnvilMenu.RESULT_SLOT).getItem());
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
        if (AstralAnvilJeiBridge.isAvailable()
                && this.isOverRecipeArrow(mouseX, mouseY)) {
            guiGraphics.renderTooltip(this.font, RECIPE_TOOLTIP, mouseX, mouseY);
        }
    }

    private boolean hasInvalidInput() {
        boolean hasInput = false;
        for (int slot = AstralAnvilMenu.INPUT_SLOT; slot <= AstralAnvilMenu.MATERIAL_SLOT_2; slot++) {
            if (!this.menu.getSlot(slot).getItem().isEmpty()) {
                hasInput = true;
                break;
            }
        }
        return hasInput && this.menu.getSlot(AstralAnvilMenu.RESULT_SLOT).getItem().isEmpty();
    }
}
