package com.merlinkitsune.astral_dice.screen;

import com.merlinkitsune.astral_dice.block.ModBlocks;
import com.merlinkitsune.astral_dice.component.ModDataComponents;
import com.merlinkitsune.astral_dice.component.WeaponEnhancement;
import com.merlinkitsune.astral_dice.item.CardReturnHelper;
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
 * 星愿铁砧菜单:槽 0 = 骰子,槽 1/2 = 升星材料(星币),槽 3 = 结果。
 *
 * <p><b>为什么继承 {@link ItemCombinerMenu} 而不是 {@code AnvilMenu}</b>:铁砧的消耗/命名/耐久逻辑
 * 全部嵌在 AnvilMenu.createResult 里,星愿只需「骰子 + 星币 → 升星骰子」这一条,直接用 ItemCombinerMenu
 * 的三输入骨架更干净,也天然不会走 {@code AnvilBlock.damage}。
 *
 * <p><b>数值唯一事实源</b>:星阶上限、星币成本、经验成本、产出形态(含实验性「免费还原」)全部委托给
 * {@link DiceStarUpgrade} —— 与原版铁砧的 {@code AnvilUpgradeHandler} 预览/授予共用同一份实现。
 *
 * <p><b>取走语义</b>(照本线 AnvilMenu 的 onTake):扣经验 → 扣材料(先槽 1 后槽 2,只扣所需数量,
 * 多余的留在槽里)→ 若输出形态是「0★ + 空 appliedStones」则把输入骰子的已装配卡牌归还玩家
 * ({@link CardReturnHelper#returnCards})→ 授予 1 次筹码商店刷新({@link ChipShopState#withRefreshGranted})。
 */
public class AstralAnvilMenu extends ItemCombinerMenu {
    /** 输入槽:骰子。 */
    public static final int INPUT_SLOT = 0;
    /** 材料槽 1/2:升星材料(默认只有星币)。 */
    public static final int MATERIAL_SLOT_1 = 1;
    public static final int MATERIAL_SLOT_2 = 2;
    /** 结果槽。 */
    public static final int RESULT_SLOT = 3;

    /** 玩家背包区间(ItemCombinerMenu 内部用的是私有方法,这里固化成本线常量)。 */
    private static final int INVENTORY_SLOT_START = 4;
    private static final int INVENTORY_SLOT_END = 31;
    private static final int USE_ROW_SLOT_START = 31;
    private static final int USE_ROW_SLOT_END = 40;

    /** 经验成本:服务端 createResult 写入,客户端经 {@link DataSlot} 同步(与 {@link #mayPickup} 同源)。 */
    private final DataSlot cost = DataSlot.standalone();

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
                .withSlot(MATERIAL_SLOT_1, 71, 38, stack -> stack.is(ModTags.Items.ASTRAL_ANVIL_MATERIALS))
                .withSlot(MATERIAL_SLOT_2, 89, 38, stack -> stack.is(ModTags.Items.ASTRAL_ANVIL_MATERIALS))
                .withResultSlot(RESULT_SLOT, 134, 38)
                .build();
    }

    @Override
    public void createResult() {
        ItemStack input = this.inputSlots.getItem(INPUT_SLOT);
        ItemStack output = ItemStack.EMPTY;
        if (DiceStarUpgrade.isDice(input)) {
            int available = 0;
            boolean starCoinsOnly = true;
            for (int slotIndex = MATERIAL_SLOT_1; slotIndex <= MATERIAL_SLOT_2; slotIndex++) {
                ItemStack material = this.inputSlots.getItem(slotIndex);
                if (material.isEmpty()) {
                    continue;
                }
                if (!material.is(ModItems.STAR_COIN.get())) {
                    starCoinsOnly = false;
                    break;
                }
                available += material.getCount();
            }
            int required = DiceStarUpgrade.starCoinCost(getStarLevel(input));
            if (starCoinsOnly && required >= 0 && available >= required) {
                output = DiceStarUpgrade.resultFor(input);
            }
        }
        this.resultSlots.setItem(0, output);
        if (!this.player.level().isClientSide()) {
            this.cost.set(output.isEmpty() ? 0 : DiceStarUpgrade.xpCostFor(input));
        }
    }

    /** 经验成本:服务端 createResult 写入 {@link DataSlot},客户端读同步值(与 {@link #mayPickup} 同源)。 */
    public int getCost() {
        return this.cost.get();
    }

    @Override
    protected boolean mayPickup(Player player, boolean hasStack) {
        int required = this.cost.get();
        return (player.getAbilities().instabuild || player.experienceLevel >= required) && required > 0;
    }

    @Override
    protected void onTake(Player player, ItemStack stack) {
        ItemStack input = this.inputSlots.getItem(INPUT_SLOT).copy();
        int xpCost = this.cost.get();
        if (!player.getAbilities().instabuild) {
            player.giveExperienceLevels(-xpCost);
        }

        // 扣材料:先槽 1 后槽 2,只扣所需数量,多余的留在槽里。
        int remaining = Math.max(DiceStarUpgrade.starCoinCost(getStarLevel(input)), 0);
        for (int slotIndex = MATERIAL_SLOT_1; slotIndex <= MATERIAL_SLOT_2 && remaining > 0; slotIndex++) {
            ItemStack material = this.inputSlots.getItem(slotIndex);
            if (material.isEmpty() || !material.is(ModItems.STAR_COIN.get())) {
                continue;
            }
            int taken = Math.min(remaining, material.getCount());
            material.shrink(taken);
            remaining -= taken;
            this.inputSlots.setItem(slotIndex, material.isEmpty() ? ItemStack.EMPTY : material);
        }
        this.inputSlots.setItem(INPUT_SLOT, ItemStack.EMPTY);

        if (player instanceof ServerPlayer serverPlayer && !input.isEmpty()) {
            // 快捷移动会先把结果槽搬空再回调 onTake,所以 stack 可能是空栈;空时按输入重新算一次产出形态。
            ItemStack takenResult = stack.isEmpty() ? DiceStarUpgrade.resultFor(input) : stack;
            WeaponEnhancement outputEnhancement =
                    ModDataComponents.WEAPON_ENHANCEMENT.getOrDefault(takenResult, WeaponEnhancement.EMPTY);
            if (outputEnhancement.starLevel() == 0 && outputEnhancement.appliedStones().isEmpty()) {
                CardReturnHelper.returnCards(serverPlayer, getEnhancement(input).appliedStones());
            }
            ChipShopState.store(serverPlayer, ChipShopState.get(serverPlayer).withRefreshGranted());
        }
        if (!player.level().isClientSide()) {
            this.cost.set(0);
        }
        this.broadcastChanges();
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = this.slots.get(index);
        if (slot == null || !slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack moved = stack.copy();
        if (index == RESULT_SLOT) {
            if (!this.moveItemStackTo(stack, INVENTORY_SLOT_START, USE_ROW_SLOT_END, true)) {
                return ItemStack.EMPTY;
            }
            slot.onQuickCraft(stack, moved);
        } else if (index < RESULT_SLOT) {
            if (!this.moveItemStackTo(stack, INVENTORY_SLOT_START, USE_ROW_SLOT_END, false)) {
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
        } else if (index >= INVENTORY_SLOT_START && index < INVENTORY_SLOT_END) {
            if (!this.moveItemStackTo(stack, USE_ROW_SLOT_START, USE_ROW_SLOT_END, false)) {
                return ItemStack.EMPTY;
            }
        } else if (index >= USE_ROW_SLOT_START && index < USE_ROW_SLOT_END
                && !this.moveItemStackTo(stack, INVENTORY_SLOT_START, INVENTORY_SLOT_END, false)) {
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

    @Override
    public boolean stillValid(Player player) {
        return this.access.evaluate((level, pos) -> level.getBlockState(pos).is(ModBlocks.ASTRAL_ANVIL.get())
                && player.distanceToSqr((double) pos.getX() + 0.5D, (double) pos.getY() + 0.5D, (double) pos.getZ() + 0.5D) <= 64.0D, true);
    }

    @Override
    protected boolean isValidBlock(BlockState state) {
        return state.is(ModBlocks.ASTRAL_ANVIL.get());
    }

    private static WeaponEnhancement getEnhancement(ItemStack stack) {
        return ModDataComponents.WEAPON_ENHANCEMENT.getOrDefault(stack, WeaponEnhancement.EMPTY);
    }

    private static int getStarLevel(ItemStack stack) {
        return getEnhancement(stack).starLevel();
    }
}
