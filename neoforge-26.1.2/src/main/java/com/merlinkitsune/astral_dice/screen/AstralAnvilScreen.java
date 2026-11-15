package com.merlinkitsune.astral_dice.screen;

import com.merlinkitsune.astral_dice.AstralDiceMod;
import com.merlinkitsune.astral_dice.compat.jei.AstralAnvilJeiBridge;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

/**
 * 星愿铁砧界面（176×166）。
 *
 * <p>标题金色居中，经验行照原版铁砧的右侧对齐；材料/骰子有货但无结果时在右侧箭头处画原版红叉
 * （26.1.2 是 sprite {@code minecraft:container/anvil/error}，不是 1.20.1 那张拼图）。
 */
public class AstralAnvilScreen extends AbstractContainerScreen<AstralAnvilMenu> {

    private static final Identifier BACKGROUND =
            Identifier.fromNamespaceAndPath(AstralDiceMod.MODID, "textures/gui/shop/astral_anvil.png");
    private static final Identifier ERROR_SPRITE = Identifier.withDefaultNamespace("container/anvil/error");

    private static final int GUI_WIDTH = 176;
    private static final int GUI_HEIGHT = 166;
    private static final int TITLE_Y = 6;
    private static final int TITLE_COLOR = 0xFFFFD700;
    private static final int LABEL_COLOR = 0xFF404040;
    private static final int INVENTORY_LABEL_Y = 72;

    private static final int COST_Y = 69;
    private static final int COST_RIGHT = 166;
    private static final int COST_BOX_TOP = 67;
    private static final int COST_BOX_BOTTOM = 79;
    private static final int COST_BOX_COLOR = 0x4F000000;
    private static final int COST_COLOR = 0xFF80FF20;
    private static final int COST_COLOR_TOO_EXPENSIVE = 0xFFFF6060;
    private static final int COST_TOO_EXPENSIVE_THRESHOLD = 40;

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

    public AstralAnvilScreen(AstralAnvilMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title, GUI_WIDTH, GUI_HEIGHT);
        this.titleLabelX = (GUI_WIDTH - this.font.width(this.title)) / 2;
        this.titleLabelY = TITLE_Y;
        this.inventoryLabelY = INVENTORY_LABEL_Y;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        this.extractTransparentBackground(graphics);
        graphics.blit(RenderPipelines.GUI_TEXTURED, BACKGROUND, this.leftPos, this.topPos, 0.0F, 0.0F,
                GUI_WIDTH, GUI_HEIGHT, GUI_WIDTH, GUI_HEIGHT);
        if (this.hasInvalidInput()) {
            graphics.blitSprite(RenderPipelines.GUI_TEXTURED, ERROR_SPRITE,
                    this.leftPos + ERROR_X, this.topPos + ERROR_Y, ERROR_WIDTH, ERROR_HEIGHT);
        }
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        graphics.text(this.font, this.title, this.titleLabelX, this.titleLabelY, TITLE_COLOR, false);
        graphics.text(this.font, this.playerInventoryTitle, this.inventoryLabelX, this.inventoryLabelY, LABEL_COLOR,
                false);
        this.extractCostLabel(graphics);
    }

    private void extractCostLabel(GuiGraphicsExtractor graphics) {
        int cost = this.menu.getCost();
        if (cost <= 0) {
            return;
        }
        Component line = Component.translatable("container.repair.cost", cost);
        int color = COST_COLOR;
        if (!this.isCreative() && cost >= COST_TOO_EXPENSIVE_THRESHOLD) {
            line = Component.translatable("container.repair.expensive");
            color = COST_COLOR_TOO_EXPENSIVE;
        } else if (!this.canPickupResult()) {
            color = COST_COLOR_TOO_EXPENSIVE;
        }
        int costX = COST_RIGHT - this.font.width(line);
        graphics.fill(costX - 2, COST_BOX_TOP, COST_RIGHT, COST_BOX_BOTTOM, COST_BOX_COLOR);
        graphics.text(this.font, line, costX, COST_Y, color);
    }

    private boolean isCreative() {
        return this.minecraft != null && this.minecraft.player != null
                && this.minecraft.player.hasInfiniteMaterials();
    }

    private boolean canPickupResult() {
        return this.minecraft == null || this.minecraft.player == null
                || this.menu.getSlot(AstralAnvilMenu.RESULT_SLOT).mayPickup(this.minecraft.player);
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
                || !this.menu.getSlot(AstralAnvilMenu.DICE_SLOT).getItem().isEmpty();
    }

    private boolean showRecipe() {
        ItemStack input = this.menu.getSlot(AstralAnvilMenu.DICE_SLOT).getItem();
        if (!input.isEmpty()) {
            return AstralAnvilJeiBridge.showRecipes(input);
        }
        return this.menu.getSlot(AstralAnvilMenu.RESULT_SLOT).getItem().isEmpty() ? AstralAnvilJeiBridge.showRecipes(ItemStack.EMPTY) : AstralAnvilJeiBridge.showRecipes(this.menu.getSlot(AstralAnvilMenu.RESULT_SLOT).getItem());
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        if (AstralAnvilJeiBridge.isAvailable()
                && this.isOverRecipeArrow(mouseX, mouseY)) {
            graphics.setTooltipForNextFrame(this.font, RECIPE_TOOLTIP, mouseX, mouseY);
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() == 0 && this.isOverRecipeArrow(event.x(), event.y()) && this.showRecipe()) {
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    private boolean hasInvalidInput() {
        boolean hasInput = this.menu.getSlot(AstralAnvilMenu.DICE_SLOT).hasItem()
                || this.menu.getSlot(AstralAnvilMenu.MATERIAL_SLOT_1).hasItem()
                || this.menu.getSlot(AstralAnvilMenu.MATERIAL_SLOT_2).hasItem();
        return hasInput && !this.menu.getSlot(AstralAnvilMenu.RESULT_SLOT).hasItem();
    }
}
