package com.merlinkitsune.astral_dice.screen;

import com.merlinkitsune.astral_dice.block.ModBlocks;
import com.merlinkitsune.astral_dice.item.CardReturnHelper;
import com.merlinkitsune.astral_dice.component.ModDataComponents;
import com.merlinkitsune.astral_dice.component.WeaponEnhancement;
import com.merlinkitsune.astral_dice.item.DiceStarUpgrade;
import com.merlinkitsune.astral_dice.item.ModItems;
import com.merlinkitsune.astral_dice.item.ModTags;
import com.merlinkitsune.astral_dice.shop.ChipShopState;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.inventory.ItemCombinerMenu;
import net.minecraft.world.inventory.ItemCombinerMenuSlotDefinition;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 星愿铁砧菜单（2026-11-15）。
 *
 * <p>三输入 + 结果：槽 0 = 骰子 (26,38)、槽 1/2 = 材料 (71,38)/(89,38)、槽 3 = 结果 (134,38)。
 * 材料槽统一由 {@link ModTags.Items#ASTRAL_ANVIL_MATERIALS} 控制（数据包可扩充），但本轮的内置升星
 * 只认星币：所有非空材料槽必须全是 {@link ModItems#STAR_COIN}，两槽合计达到该星阶成本才出结果。
 *
 * <p><b>不调 {@code AnvilBlock.damage}</b>：星愿铁砧无耐久，取走成品不会裂。
 */
public class AstralAnvilMenu extends ItemCombinerMenu {

    public static final int INPUT_SLOT = 0;
    public static final int MATERIAL_SLOT_1 = 1;
    public static final int MATERIAL_SLOT_2 = 2;
    public static final int RESULT_SLOT = 3;

    private static final int INVENTORY_SLOT_START = RESULT_SLOT + 1;
    private static final int INVENTORY_SLOT_END = INVENTORY_SLOT_START + 27;
    private static final int USE_ROW_START = INVENTORY_SLOT_END;
    private static final int USE_ROW_END = USE_ROW_START + 9;

    /** 经验成本：服务端算、经 DataSlot 同步给客户端画字（与 {@link #mayPickup} 判据同源）。 */
    private final DataSlot cost = DataSlot.standalone();
    /** createResult 时算出的星币总消耗，onTake 按同一数值扣料（预览=实扣）。 */
    private int materialCost;

    public AstralAnvilMenu(int containerId, Inventory playerInventory) {
        this(containerId, playerInventory, ContainerLevelAccess.NULL);
    }

    public AstralAnvilMenu(int containerId, Inventory playerInventory, ContainerLevelAccess access) {
        super(ModMenuTypes.ASTRAL_ANVIL.get(), containerId, playerInventory, access);
        this.addDataSlot(this.cost);
    }

    @Override
    protected ItemCombinerMenuSlotDefinition createInputSlotDefinitions() {
        return ItemCombinerMenuSlotDefinition.create()
                .withSlot(INPUT_SLOT, 26, 38, DiceStarUpgrade::isDice)
                .withSlot(MATERIAL_SLOT_1, 71, 38, AstralAnvilMenu::isMaterial)
                .withSlot(MATERIAL_SLOT_2, 89, 38, AstralAnvilMenu::isMaterial)
                .withResultSlot(RESULT_SLOT, 134, 38)
                .build();
    }

    private static boolean isMaterial(ItemStack stack) {
        return !stack.isEmpty() && stack.is(ModTags.Items.ASTRAL_ANVIL_MATERIALS);
    }

    /** 内置升星：骰子 + 全星币 + 两槽合计够成本 ⇒ 产出 {@link DiceStarUpgrade#resultFor}。 */
    @Override
    public void createResult() {
        ItemStack input = this.inputSlots.getItem(INPUT_SLOT);
        ItemStack material1 = this.inputSlots.getItem(MATERIAL_SLOT_1);
        ItemStack material2 = this.inputSlots.getItem(MATERIAL_SLOT_2);
        ItemStack output = ItemStack.EMPTY;
        this.materialCost = 0;
        if (DiceStarUpgrade.isDice(input) && isAllStarCoins(material1, material2)) {
            int required = DiceStarUpgrade.starCoinCost(starLevelOf(input));
            if (required > 0 && material1.getCount() + material2.getCount() >= required) {
                ItemStack candidate = DiceStarUpgrade.resultFor(input);
                if (!candidate.isEmpty()) {
                    output = candidate;
                    this.materialCost = required;
                }
            }
        }
        if (!this.player.level().isClientSide()) {
            this.cost.set(output.isEmpty() ? 0 : DiceStarUpgrade.xpCostFor(input));
        }
        this.resultSlots.setItem(0, output);
    }

    private static boolean isAllStarCoins(ItemStack material1, ItemStack material2) {
        return (material1.isEmpty() || material1.is(ModItems.STAR_COIN.get()))
                && (material2.isEmpty() || material2.is(ModItems.STAR_COIN.get()));
    }

    private static int starLevelOf(ItemStack stack) {
        return stack.getOrDefault(ModDataComponents.WEAPON_ENHANCEMENT.get(), WeaponEnhancement.EMPTY).starLevel();
    }

    /** 经验成本（原版铁砧同款语义：按等级扣，不是按点数）。 */
    public int getCost() {
        return this.cost.get();
    }

    @Override
    protected boolean mayPickup(Player player, boolean hasStack) {
        int required = this.cost.get();
        return required > 0 && (player.hasInfiniteMaterials() || player.experienceLevel >= required);
    }

    /**
     * 取走结果：扣经验（创造免）、扣材料（先槽 1 后槽 2）、授予筹码商店刷新；
     * 实验模式下产出形态为「0★ + 空卡槽」时，把**输入骰子**上挂过的卡牌归还。
     */
    @Override
    protected void onTake(Player player, ItemStack stack) {
        int xpCost = this.cost.get();
        int consumed = this.materialCost;
        ItemStack inputStack = this.inputSlots.getItem(INPUT_SLOT);
        WeaponEnhancement inputEnhancement = inputStack
                .getOrDefault(ModDataComponents.WEAPON_ENHANCEMENT.get(), WeaponEnhancement.EMPTY);
        if (!player.getAbilities().instabuild) {
            player.giveExperienceLevels(-xpCost);
        }
        // 先清输入骰子：会再触发一次 createResult（输入为空 ⇒ 结果清空、成本归零）
        this.inputSlots.setItem(INPUT_SLOT, ItemStack.EMPTY);
        consumeMaterials(consumed);
        if (player instanceof ServerPlayer serverPlayer) {
            ChipShopState.store(serverPlayer, ChipShopState.get(serverPlayer).withRefreshGranted());
            // 快捷移动会先把结果槽搬空再回调 onTake，stack 可能是空栈；按输入栈重算产物形态。
            ItemStack expectedOutput = DiceStarUpgrade.resultFor(inputStack);
            WeaponEnhancement output = expectedOutput
                    .getOrDefault(ModDataComponents.WEAPON_ENHANCEMENT.get(), WeaponEnhancement.EMPTY);
            if (!expectedOutput.isEmpty() && output.starLevel() == 0 && output.appliedStones().isEmpty()) {
                CardReturnHelper.returnCards(serverPlayer, inputEnhancement.appliedStones());
            }
        }
        if (!player.level().isClientSide()) {
            this.cost.set(0);
        }
        this.broadcastChanges();
    }

    /** 扣料：先槽 1 后槽 2，按 createResult 算出的总量扣，不按槽平均。 */
    private void consumeMaterials(int total) {
        int remaining = total;
        for (int slotIndex : new int[]{MATERIAL_SLOT_1, MATERIAL_SLOT_2}) {
            if (remaining <= 0) {
                return;
            }
            ItemStack material = this.inputSlots.getItem(slotIndex);
            if (material.isEmpty()) {
                continue;
            }
            int taken = Math.min(remaining, material.getCount());
            material.shrink(taken);
            this.inputSlots.setItem(slotIndex, material.isEmpty() ? ItemStack.EMPTY : material);
            remaining -= taken;
        }
    }

    @Override
    protected boolean isValidBlock(BlockState state) {
        return state.is(ModBlocks.ASTRAL_ANVIL.get());
    }

    /** 三输入 + 结果 + 玩家背包的互搬；材料槽优先槽 1 再槽 2。 */
    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        ItemStack moved = ItemStack.EMPTY;
        Slot slot = this.slots.get(index);
        if (slot == null || !slot.hasItem()) {
            return moved;
        }
        ItemStack stack = slot.getItem();
        moved = stack.copy();
        if (index == RESULT_SLOT) {
            if (!this.moveItemStackTo(stack, INVENTORY_SLOT_START, USE_ROW_END, true)) {
                return ItemStack.EMPTY;
            }
            slot.onQuickCraft(stack, moved);
        } else if (index < RESULT_SLOT) {
            if (!this.moveItemStackTo(stack, INVENTORY_SLOT_START, USE_ROW_END, false)) {
                return ItemStack.EMPTY;
            }
        } else if (DiceStarUpgrade.isDice(stack)) {
            if (!this.moveItemStackTo(stack, INPUT_SLOT, INPUT_SLOT + 1, false)) {
                return ItemStack.EMPTY;
            }
        } else if (stack.is(ModTags.Items.ASTRAL_ANVIL_MATERIALS)) {
            if (!this.moveItemStackTo(stack, MATERIAL_SLOT_1, MATERIAL_SLOT_2 + 1, false)) {
                return ItemStack.EMPTY;
            }
        } else if (index < USE_ROW_START) {
            if (!this.moveItemStackTo(stack, USE_ROW_START, USE_ROW_END, false)) {
                return ItemStack.EMPTY;
            }
        } else if (!this.moveItemStackTo(stack, INVENTORY_SLOT_START, USE_ROW_START, false)) {
            return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) {
            slot.setByPlayer(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        if (stack.getCount() == moved.getCount()) {
            return ItemStack.EMPTY;
        }
        slot.onTake(player, stack);
        return moved;
    }
}
