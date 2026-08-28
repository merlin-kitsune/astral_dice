package com.merlinkitsune.astral_dice.screen;

import com.merlinkitsune.astral_dice.AstralDiceMod;
import com.merlinkitsune.astral_dice.combat.CardRegistry;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Set;

public class CardInventoryScreen extends AbstractContainerScreen<CardInventoryMenu> {
    private static final ResourceLocation GUI_TEXTURE =
            ResourceLocation.fromNamespaceAndPath(AstralDiceMod.MODID, "textures/gui/card_inventory.png");
    private static final int GUI_WIDTH = 168;
    private static final int GUI_HEIGHT = 124;

    // 费用点数槽(亮线上方的两个深色槽,左对齐绘制)
    private static final int COST_ATTACK_X = 8;
    private static final int COST_DEFENSE_X = 45;
    private static final int COST_Y = 43;
    private static final int COST_HEIGHT = 6;



    // 总攻击/防御力显示(右侧,与格子水平居中)
    private static final int TOTAL_ATTACK_X = 136;
    private static final int TOTAL_ATTACK_Y = 7;
    private static final int TOTAL_DEFENSE_X = 136;
    private static final int TOTAL_DEFENSE_Y = 25;
    private static final int ATTACK_TEXT_COLOR = 0xFFFF5555;
    private static final int DEFENSE_TEXT_COLOR = 0xFF55AAFF;

    // 卡牌选择器(下方棋盘格区域)
    private static final int SELECTOR_LEFT_X = 8;
    private static final int SELECTOR_RIGHT_X = 90;
    private static final int SELECTOR_ROW_Y = 56;
    private static final int SELECTOR_ROW_SPACING = 18;
    private static final int SELECTOR_VISIBLE_ROWS = CardInventoryMenu.SELECTOR_VISIBLE_ROWS;
    private static final int SELECTOR_COLUMNS = CardInventoryMenu.SELECTOR_COLUMNS;
    private static final int SELECTOR_COL_SPACING = 18;

    // 这些卡牌图标偏大,在卡牌槽内渲染时缩小
    private static final Set<String> LARGE_CARD_TYPES = Set.of(
            "shadow_strike", "meito", "charge", "full_power"
    );
    private static final float LARGE_CARD_SCALE = 0.8F;

    public CardInventoryScreen(CardInventoryMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = GUI_WIDTH;
        this.imageHeight = GUI_HEIGHT;
        this.titleLabelX = -10000;
        this.titleLabelY = -10000;
        this.inventoryLabelY = -10000;
    }

    @Override
    protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
        int x = this.leftPos;
        int y = this.topPos;

        guiGraphics.blit(GUI_TEXTURE, x, y, 0, 0.0F, 0.0F, GUI_WIDTH, GUI_HEIGHT, GUI_WIDTH, GUI_HEIGHT);

        // 费用点数:按当前骰子星级对应的最大点数显示
        int atkMax = Math.max(0, this.menu.getMaxAttackCost());
        int atkUsed = Math.max(0, Math.min(this.menu.getUsedAttackCost(), atkMax));
        blitCost(guiGraphics, x + COST_ATTACK_X, y + COST_Y, costTexture("attack", atkMax, atkUsed), costWidth(atkMax));

        int defMax = Math.max(0, this.menu.getMaxDefenseCost());
        int defUsed = Math.max(0, Math.min(this.menu.getUsedDefenseCost(), defMax));
        blitCost(guiGraphics, x + COST_DEFENSE_X, y + COST_Y, costTexture("defense", defMax, defUsed), costWidth(defMax));

        // 总攻击/防御力范围:右侧,红/蓝区分,与格子水平居中
        guiGraphics.drawString(this.font, this.menu.getDisplayAttackMin() + "-" + this.menu.getDisplayAttackMax(),
                x + TOTAL_ATTACK_X, y + TOTAL_ATTACK_Y, ATTACK_TEXT_COLOR, true);
        guiGraphics.drawString(this.font, this.menu.getDisplayDefenseMin() + "-" + this.menu.getDisplayDefenseMax(),
                x + TOTAL_DEFENSE_X, y + TOTAL_DEFENSE_Y, DEFENSE_TEXT_COLOR, true);

        // 卡牌选择器:攻击左列 / 防御右列,按物品栏顺序,不堆叠,支持滚动
        int offset = this.menu.getSelectorScrollOffset();
        renderSelectorGrid(guiGraphics, x, y, this.menu.getAttackSelectorSlots(), offset, SELECTOR_LEFT_X);
        renderSelectorGrid(guiGraphics, x, y, this.menu.getDefenseSelectorSlots(), offset, SELECTOR_RIGHT_X);
    }

    @Override
    protected void renderLabels(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        // 新界面不绘制标题/物品栏文字,避免遮挡贴图
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        this.renderTooltip(guiGraphics, mouseX, mouseY);
    }

    @Override
    protected void renderSlot(GuiGraphics guiGraphics, Slot slot) {
        // 卡牌槽内的大图标适当缩小,避免超出格子
        if (slot.container == this.menu.cardContainer) {
            ItemStack stack = slot.getItem();
            if (!stack.isEmpty()) {
                String type = CardRegistry.itemToType(stack);
                if (type != null && LARGE_CARD_TYPES.contains(type)) {
                    renderScaledSlotItem(guiGraphics, slot, stack, LARGE_CARD_SCALE);
                    return;
                }
            }
        }
        super.renderSlot(guiGraphics, slot);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // 点击下方卡牌存放区域:若当前手持卡牌,则放回物品栏
        if (!this.menu.getCarried().isEmpty() && isInSelectorArea(mouseX, mouseY)) {
            Slot empty = this.menu.getFirstEmptyInventorySlot();
            if (empty != null) {
                this.slotClicked(empty, empty.index, 0, ClickType.PICKUP);
                return true;
            }
        }

        // 右键取消选择:若当前手上持有卡牌且未放入上方槽位,则放回物品栏
        if (button == 1 && !this.menu.getCarried().isEmpty()) {
            Slot empty = this.menu.getFirstEmptyInventorySlot();
            if (empty != null) {
                this.slotClicked(empty, empty.index, 0, ClickType.PICKUP);
                return true;
            }
        }

        if (button == 0 || button == 1) {
            Slot selectorSlot = getSelectorSlotAt(mouseX, mouseY);
            if (selectorSlot != null) {
                ClickType type = hasShiftDown() ? ClickType.QUICK_MOVE : ClickType.PICKUP;
                this.slotClicked(selectorSlot, selectorSlot.index, button, type);
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (isInSelectorArea(mouseX, mouseY)) {
            this.menu.scrollSelector(verticalAmount > 0 ? -1 : 1);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    private void renderScaledSlotItem(GuiGraphics guiGraphics, Slot slot, ItemStack stack, float scale) {
        // 向右下轻微偏移
        float offsetX = 2.0F;
        float offsetY = 2.0F;
        int drawX = slot.x + (int) offsetX;
        int drawY = slot.y + (int) offsetY;
        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(drawX, drawY, 0.0F);
        guiGraphics.pose().scale(scale, scale, 1.0F);
        guiGraphics.renderItem(stack, 0, 0);
        guiGraphics.pose().popPose();
        // 在未缩放坐标下绘制耐久条/装饰,确保耐久条可见
        guiGraphics.renderItemDecorations(this.font, stack, drawX, drawY);
    }

    // 3 列网格:同一侧(攻击/防御)的卡牌按 3 列排布,按行滚动
    private void renderSelectorGrid(GuiGraphics guiGraphics, int guiX, int guiY, List<Slot> slots, int offset, int colStartX) {
        for (int i = 0; i < slots.size(); i++) {
            int row = i / SELECTOR_COLUMNS - offset;
            if (row < 0 || row >= SELECTOR_VISIBLE_ROWS) continue;
            int col = i % SELECTOR_COLUMNS;
            int sx = guiX + colStartX + col * SELECTOR_COL_SPACING;
            int sy = guiY + SELECTOR_ROW_Y + row * SELECTOR_ROW_SPACING;
            ItemStack stack = slots.get(i).getItem();
            if (!stack.isEmpty()) {
                guiGraphics.renderItem(stack, sx, sy);
                guiGraphics.renderItemDecorations(this.font, stack, sx, sy);
            }
        }
    }

    private boolean isInSelectorArea(double mouseX, double mouseY) {
        return mouseX >= this.leftPos && mouseX < this.leftPos + GUI_WIDTH
                && mouseY >= this.topPos + SELECTOR_ROW_Y
                && mouseY < this.topPos + SELECTOR_ROW_Y + SELECTOR_VISIBLE_ROWS * SELECTOR_ROW_SPACING;
    }

    private Slot getSelectorSlotAt(double mouseX, double mouseY) {
        if (!isInSelectorArea(mouseX, mouseY)) return null;
        int localX = (int) (mouseX - this.leftPos);
        int localY = (int) (mouseY - this.topPos);
        boolean defense = localX >= SELECTOR_RIGHT_X;
        int colX = defense ? SELECTOR_RIGHT_X : SELECTOR_LEFT_X;
        if (localX < colX || localX >= colX + SELECTOR_COLUMNS * SELECTOR_COL_SPACING) return null;
        int col = (localX - colX) / SELECTOR_COL_SPACING;
        if (col >= SELECTOR_COLUMNS) return null;
        int row = (localY - SELECTOR_ROW_Y) / SELECTOR_ROW_SPACING;
        if (row < 0) return null;
        int index = (row + this.menu.getSelectorScrollOffset()) * SELECTOR_COLUMNS + col;
        List<Slot> slots = defense ? this.menu.getDefenseSelectorSlots() : this.menu.getAttackSelectorSlots();
        if (index >= 0 && index < slots.size()) {
            return slots.get(index);
        }
        return null;
    }

    private void blitCost(GuiGraphics guiGraphics, int x, int y, ResourceLocation texture, int width) {
        guiGraphics.blit(texture, x, y, 0, 0.0f, 0.0f, width, COST_HEIGHT, width, COST_HEIGHT);
    }

    private int costWidth(int maxCost) {
        return switch (maxCost) {
            case 3 -> 16;
            case 4 -> 21;
            case 5 -> 26;
            default -> 31;
        };
    }

    private ResourceLocation costTexture(String side, int maxCost, int used) {
        return ResourceLocation.fromNamespaceAndPath(AstralDiceMod.MODID,
                "textures/gui/cost/cost_" + side + "_" + maxCost + "_" + used + ".png");
    }
}
