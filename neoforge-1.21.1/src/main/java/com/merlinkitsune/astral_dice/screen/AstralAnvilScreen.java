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
 * 星愿铁砧界面（2026-11-15）：176×166，背景取自 textures/gui/shop/astral_anvil.png。
 *
 * <p>标题金色居中（y=6），经验行贴在右下（COST_Y=69 / 右缘 166），左上第三个输入槽的箭头位置
 * 在「有输入但无结果」时叠一张原版 28×21 红叉。
 */
public class AstralAnvilScreen extends AbstractContainerScreen<AstralAnvilMenu> {

    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(AstralDiceMod.MODID,
            "textures/gui/shop/astral_anvil.png");
    /** 红叉直接取原版铁砧图集（1.20.1 / 1.21.1 同款 u=176,v=0）。 */
    private static final ResourceLocation VANILLA_ANVIL_TEXTURE = ResourceLocation.withDefaultNamespace(
            "textures/gui/container/anvil.png");
    private static final Component TOO_EXPENSIVE_TEXT = Component.translatable("container.repair.expensive");
    private static final Component TITLE = Component.translatableWithFallback(
            "container.astral_dice.astral_anvil", "星愿铁砧");

    private static final int TITLE_COLOR = 0xFFD700;
    private static final int COST_Y = 69;
    private static final int COST_RIGHT = 166;
    private static final int COST_OK_COLOR = 0x80FF20;
    private static final int COST_BAD_COLOR = 0xFF6060;
    private static final int COST_BG_COLOR = 0x4F000000;
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
        super(menu, playerInventory, title);
        this.imageWidth = 176;
        this.imageHeight = 166;

    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        this.renderRecipeTooltip(guiGraphics, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
        guiGraphics.blit(TEXTURE, this.leftPos, this.topPos, 0.0F, 0.0F, this.imageWidth, this.imageHeight,
                this.imageWidth, this.imageHeight);
        if (hasInvalidInput()) {
            guiGraphics.blit(VANILLA_ANVIL_TEXTURE, this.leftPos + ERROR_X, this.topPos + ERROR_Y, 176, 0,
                    ERROR_WIDTH, ERROR_HEIGHT);
        }
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
        return this.menu.getSlot(AstralAnvilMenu.RESULT_SLOT).getItem().isEmpty() ? AstralAnvilJeiBridge.showRecipes(ItemStack.EMPTY) : AstralAnvilJeiBridge.showRecipes(this.menu.getSlot(AstralAnvilMenu.RESULT_SLOT).getItem());
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && this.isOverRecipeArrow(mouseX, mouseY) && this.showRecipe()) {
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private void renderRecipeTooltip(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        if (AstralAnvilJeiBridge.isAvailable()
                && this.isOverRecipeArrow(mouseX, mouseY)) {
            guiGraphics.renderTooltip(this.font, RECIPE_TOOLTIP, mouseX, mouseY);
        }
    }

    private boolean hasInvalidInput() {
        boolean hasInput = this.menu.getSlot(AstralAnvilMenu.INPUT_SLOT).hasItem()
                || this.menu.getSlot(AstralAnvilMenu.MATERIAL_SLOT_1).hasItem()
                || this.menu.getSlot(AstralAnvilMenu.MATERIAL_SLOT_2).hasItem();
        return hasInput && !this.menu.getSlot(AstralAnvilMenu.RESULT_SLOT).hasItem();
    }

    @Override
    protected void renderLabels(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        guiGraphics.drawString(this.font, TITLE, (this.imageWidth - this.font.width(TITLE)) / 2, 6,
                TITLE_COLOR, false);
        this.renderCost(guiGraphics);
    }

    /** 经验行：可做 0x80FF20 / 太贵或不可取 0xFF6060；≥40 级且非创造改显「过于昂贵」。 */
    private void renderCost(GuiGraphics guiGraphics) {
        int cost = this.menu.getCost();
        if (cost <= 0) {
            return;
        }
        int color = COST_OK_COLOR;
        Component line;
        if (cost >= 40 && !this.minecraft.player.hasInfiniteMaterials()) {
            line = TOO_EXPENSIVE_TEXT;
            color = COST_BAD_COLOR;
        } else if (!this.menu.getSlot(AstralAnvilMenu.RESULT_SLOT).hasItem()) {
            line = null;
        } else {
            line = Component.translatable("container.repair.cost", cost);
            if (!this.menu.getSlot(AstralAnvilMenu.RESULT_SLOT).mayPickup(this.minecraft.player)) {
                color = COST_BAD_COLOR;
            }
        }
        if (line == null) {
            return;
        }
        int costX = COST_RIGHT - this.font.width(line);
        guiGraphics.fill(costX - 2, 67, COST_RIGHT, 79, COST_BG_COLOR);
        guiGraphics.drawString(this.font, line, costX, COST_Y, color, false);
    }
}
