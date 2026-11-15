package com.merlinkitsune.astral_dice.screen;

import com.merlinkitsune.astral_dice.block.ModBlocks;
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
 * 星愿铁砧菜单：槽 0 = 骰子，槽 1/2 = 材料（{@link ModTags.Items#ASTRAL_ANVIL_MATERIALS}），槽 3 = 结果。
 * <p>本轮只实现内置升星：材料必须是星币，两槽合计达到当前星级所需枚数即可产出高一星的骰子；
 * 具体数值与判定统一由 {@link DiceStarUpgrade} 提供（唯一事实源）。
 */
public class AstralAnvilMenu extends ItemCombinerMenu {

    public static final int INPUT_SLOT = 0;
    public static final int MATERIAL_SLOT_1 = 1;
    public static final int MATERIAL_SLOT_2 = 2;
    public static final int RESULT_SLOT = 3;

    /** 经验成本：服务端 createResult 写入，客户端经 {@link DataSlot} 同步（与 {@link #mayPickup} 同源）。 */
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
                .withSlot(INPUT_SLOT, 26, 38, this::mayPlaceDice)
                .withSlot(MATERIAL_SLOT_1, 71, 38, this::mayPlaceMaterial)
                .withSlot(MATERIAL_SLOT_2, 89, 38, this::mayPlaceMaterial)
                .withResultSlot(RESULT_SLOT, 134, 38)
                .build();
    }

    private boolean mayPlaceDice(ItemStack stack) {
        return DiceStarUpgrade.isDice(stack);
    }

    private boolean mayPlaceMaterial(ItemStack stack) {
        return stack.is(ModTags.Items.ASTRAL_ANVIL_MATERIALS);
    }

    @Override
    public void createResult() {
        ItemStack input = this.inputSlots.getItem(INPUT_SLOT);
        ItemStack output = ItemStack.EMPTY;
        if (!input.isEmpty() && DiceStarUpgrade.isDice(input)) {
            int coins = 0;
            boolean starCoinsOnly = true;
            for (int slot = MATERIAL_SLOT_1; slot <= MATERIAL_SLOT_2; slot++) {
                ItemStack material = this.inputSlots.getItem(slot);
                if (material.isEmpty()) {
                    continue;
                }
                if (!material.is(ModItems.STAR_COIN.get())) {
                    starCoinsOnly = false;
                    break;
                }
                coins += material.getCount();
            }
            if (starCoinsOnly && coins >= DiceStarUpgrade.starCoinCost(input)) {
                ItemStack result = DiceStarUpgrade.resultFor(input);
                if (result != null) {
                    output = result;
                }
            }
        }
        this.resultSlots.setItem(0, output);
        if (!this.player.level().isClientSide()) {
            this.cost.set(output.isEmpty() ? 0 : DiceStarUpgrade.xpCostFor(input));
        }
    }

    /** 本次操作要扣的经验等级：服务端 createResult 写入 {@link DataSlot}，客户端读同步值。 */
    public int getCost() {
        return this.cost.get();
    }

    @Override
    protected boolean mayPickup(Player player, boolean hasStack) {
        int required = this.cost.get();
        return required > 0 && (player.getAbilities().instabuild || player.experienceLevel >= required);
    }

    @Override
    protected void onTake(Player player, ItemStack stack) {
        ItemStack input = this.inputSlots.getItem(INPUT_SLOT);
        int xpCost = this.cost.get();
        int coinCost = input.isEmpty() ? 0 : DiceStarUpgrade.starCoinCost(input);

        // 1) 扣经验等级（创造模式不扣）
        if (!player.getAbilities().instabuild && xpCost > 0) {
            player.giveExperienceLevels(-xpCost);
        }

        // 2) 扣材料：先槽 1 后槽 2
        int remaining = coinCost;
        for (int slot = MATERIAL_SLOT_1; slot <= MATERIAL_SLOT_2 && remaining > 0; slot++) {
            ItemStack material = this.inputSlots.getItem(slot);
            if (material.isEmpty() || !material.is(ModItems.STAR_COIN.get())) {
                continue;
            }
            int used = Math.min(remaining, material.getCount());
            material.shrink(used);
            remaining -= used;
            this.inputSlots.setItem(slot, material.isEmpty() ? ItemStack.EMPTY : material);
        }

        // 3) 授予筹码商店 1 次刷新（仅服务端写附件）
        if (player instanceof ServerPlayer serverPlayer) {
            ChipShopState.store(serverPlayer, ChipShopState.get(serverPlayer).withRefreshGranted());
        }


        // 5) 清空输入骰子并播放铁砧音效
        this.inputSlots.setItem(INPUT_SLOT, ItemStack.EMPTY);
        this.access.execute((level, pos) -> level.levelEvent(1030, pos, 0));
        if (!player.level().isClientSide()) {
            this.cost.set(0);
        }
        this.broadcastChanges();
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        if (index < 0 || index >= this.slots.size()) {
            return ItemStack.EMPTY;
        }
        Slot slot = this.slots.get(index);
        if (slot == null || !slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack copy = stack.copy();
        int playerStart = RESULT_SLOT + 1;
        int playerEnd = this.slots.size();

        if (index == RESULT_SLOT) {
            // 结果槽：先过 mayPickup（经验够不够），再把产物塞进物品栏，最后手动走一次 onTake 语义
            if (!this.mayPickup(player, true) || !this.moveItemStackTo(stack, playerStart, playerEnd, true)) {
                return ItemStack.EMPTY;
            }
            this.onTake(player, copy);
            if (stack.isEmpty()) {
                slot.setByPlayer(ItemStack.EMPTY);
            } else {
                slot.setChanged();
            }
            return copy;
        }

        if (index >= playerStart) {
            // 物品栏 → 输入槽：骰子进槽 0，材料进槽 1/2，其余在快捷栏与背包之间挪
            if (DiceStarUpgrade.isDice(stack)) {
                if (!this.moveItemStackTo(stack, INPUT_SLOT, INPUT_SLOT + 1, false)) {
                    return ItemStack.EMPTY;
                }
            } else if (stack.is(ModTags.Items.ASTRAL_ANVIL_MATERIALS)) {
                if (!this.moveItemStackTo(stack, MATERIAL_SLOT_1, MATERIAL_SLOT_2 + 1, false)) {
                    return ItemStack.EMPTY;
                }
            } else {
                boolean fromHotbar = index >= playerEnd - 9;
                int start = fromHotbar ? playerStart : playerEnd - 9;
                int end = fromHotbar ? playerEnd - 9 : playerEnd;
                if (!this.moveItemStackTo(stack, start, end, false)) {
                    return ItemStack.EMPTY;
                }
            }
        } else if (!this.moveItemStackTo(stack, playerStart, playerEnd, false)) {
            // 输入槽 → 物品栏
            return ItemStack.EMPTY;
        }

        if (stack.isEmpty()) {
            slot.setByPlayer(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        if (stack.getCount() == copy.getCount()) {
            return ItemStack.EMPTY;
        }
        slot.onTake(player, stack);
        return copy;
    }

    @Override
    protected boolean isValidBlock(BlockState state) {
        // stillValid 由 ItemCombinerMenu 基类用 isValidBlock + 距离判定实现，这里只认星愿铁砧
        return state.is(ModBlocks.ASTRAL_ANVIL.get());
    }
}
