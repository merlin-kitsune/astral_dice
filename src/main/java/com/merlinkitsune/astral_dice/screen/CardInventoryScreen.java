package com.merlinkitsune.astral_dice.screen;

import com.merlinkitsune.astral_dice.AstralDiceMod;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

public class CardInventoryScreen extends AbstractContainerScreen<CardInventoryMenu> {
    private static final ResourceLocation GUI_TEXTURE =
            ResourceLocation.fromNamespaceAndPath(AstralDiceMod.MODID, "textures/gui/card_inventory.png");
    private static final ResourceLocation ATK_DOT_TEXTURE =
            ResourceLocation.fromNamespaceAndPath(AstralDiceMod.MODID, "textures/gui/attack_dot.png");
    private static final ResourceLocation DEF_DOT_TEXTURE =
            ResourceLocation.fromNamespaceAndPath(AstralDiceMod.MODID, "textures/gui/defense_dot.png");
    private static final ResourceLocation EMPTY_DOT_TEXTURE =
            ResourceLocation.fromNamespaceAndPath(AstralDiceMod.MODID, "textures/gui/empty_dot.png");
    private static final int GUI_WIDTH = 176;
    private static final int GUI_HEIGHT = 166;
    private static final int DOT_SIZE = 16;

    public CardInventoryScreen(CardInventoryMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = GUI_WIDTH;
        this.imageHeight = GUI_HEIGHT;
        this.inventoryLabelY = this.imageHeight - 94;
    }

    @Override
    protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
        int x = this.leftPos;
        int y = this.topPos;

        guiGraphics.blit(GUI_TEXTURE, x, y, 0, 0, this.imageWidth, this.imageHeight);

        int atkSlots = this.menu.getAttackSlots();
        int defSlots = this.menu.getDefenseSlots();
        for (int i = 0; i < atkSlots; i++) {
            int slotX = x + 31 + i * 20;
            guiGraphics.blitSprite(ResourceLocation.withDefaultNamespace("container/slot"), slotX, y + 17, 18, 18);
        }
        for (int i = 0; i < defSlots; i++) {
            int slotX = x + 31 + i * 20;
            guiGraphics.blitSprite(ResourceLocation.withDefaultNamespace("container/slot"), slotX, y + 39, 18, 18);
        }

        int atkUsed = this.menu.getUsedAttackCost();
        int atkMax = this.menu.getMaxAttackCost();
        int atkStartX = x + (GUI_WIDTH - (atkMax * DOT_SIZE + (atkMax - 1) * 2)) / 2;
        for (int i = 0; i < atkMax; i++) {
            int dotX = atkStartX + i * (DOT_SIZE + 2);
            int dotY = y + 2;
            if (i >= atkMax - atkUsed) {
                guiGraphics.blit(EMPTY_DOT_TEXTURE, dotX, dotY, 0, 0.0f, 0.0f, DOT_SIZE, DOT_SIZE, DOT_SIZE, DOT_SIZE);
            } else {
                guiGraphics.blit(ATK_DOT_TEXTURE, dotX, dotY, 0, 0.0f, 0.0f, DOT_SIZE, DOT_SIZE, DOT_SIZE, DOT_SIZE);
            }
        }

        int defUsed = this.menu.getUsedDefenseCost();
        int defMax = this.menu.getMaxDefenseCost();
        int defStartX = x + (GUI_WIDTH - (defMax * DOT_SIZE + (defMax - 1) * 2)) / 2;
        for (int i = 0; i < defMax; i++) {
            int dotX = defStartX + i * (DOT_SIZE + 2);
            int dotY = y + 56;
            if (i >= defMax - defUsed) {
                guiGraphics.blit(EMPTY_DOT_TEXTURE, dotX, dotY, 0, 0.0f, 0.0f, DOT_SIZE, DOT_SIZE, DOT_SIZE, DOT_SIZE);
            } else {
                guiGraphics.blit(DEF_DOT_TEXTURE, dotX, dotY, 0, 0.0f, 0.0f, DOT_SIZE, DOT_SIZE, DOT_SIZE, DOT_SIZE);
            }
        }
    }

    @Override
    protected void renderLabels(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        super.renderLabels(guiGraphics, mouseX, mouseY);
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        this.renderTooltip(guiGraphics, mouseX, mouseY);
    }
}
