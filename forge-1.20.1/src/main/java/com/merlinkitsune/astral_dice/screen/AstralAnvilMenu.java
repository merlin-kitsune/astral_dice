package com.merlinkitsune.astral_dice.screen;

import com.merlinkitsune.astral_dice.block.ModBlocks;
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
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 星愿铁砧菜单（2026-11-15）：骰子 + 两格材料的升星台。
 *
 * <p>槽位：0 = 骰子、1/2 = 材料（只收 {@link ModTags.Items#ASTRAL_ANVIL_MATERIALS}）、3 = 结果；
 * 玩家物品栏由 {@link ItemCombinerMenu} 基类按原版坐标 (8,84)/(8,102)/(8,120)/(8,142) 补齐。
 *
 * <p>本轮 createResult 只实现内置升星：输入必须是骰子、所有非空材料槽必须是星币、
 * 两槽合计不少于 {@link DiceStarUpgrade#starCoinCost(int)}；数据包自定义配方是后续批次。
 *
 * <p>星愿铁砧没有耐久：本类不调用 {@code AnvilBlock#damage}，{@link #isValidBlock(BlockState)}
 * 也只认 {@link ModBlocks#ASTRAL_ANVIL}。
 */
public class AstralAnvilMenu extends ItemCombinerMenu {

    /** 骰子输入槽。 */
    public static final int INPUT_SLOT = 0;
    /** 材料槽 A。 */
    public static final int MATERIAL_SLOT_FIRST = 1;
    /** 材料槽 B。 */
    public static final int MATERIAL_SLOT_SECOND = 2;
    /** 结果槽（3 个输入槽之后）。 */
    public static final int RESULT_SLOT = 3;

    /** 经验消耗：服务端 createResult 写入，客户端经 {@link DataSlot} 同步。 */
    private final DataSlot cost = DataSlot.standalone();

    public AstralAnvilMenu(int containerId, Inventory inventory) {
        this(containerId, inventory, ContainerLevelAccess.NULL);
    }

    public AstralAnvilMenu(int containerId, Inventory inventory, ContainerLevelAccess access) {
        super(ModMenuTypes.ASTRAL_ANVIL.get(), containerId, inventory, access);
        this.addDataSlot(this.cost);
    }

    @Override
    protected ItemCombinerMenuSlotDefinition createInputSlotDefinitions() {
        return ItemCombinerMenuSlotDefinition.create()
                .withSlot(INPUT_SLOT, 26, 38, DiceStarUpgrade::isDice)
                .withSlot(MATERIAL_SLOT_FIRST, 71, 38, stack -> stack.is(ModTags.Items.ASTRAL_ANVIL_MATERIALS))
                .withSlot(MATERIAL_SLOT_SECOND, 89, 38, stack -> stack.is(ModTags.Items.ASTRAL_ANVIL_MATERIALS))
                .withResultSlot(RESULT_SLOT, 134, 38)
                .build();
    }

    /**
     * 内置升星：骰子 + 全星币 + 数量足够 + 未到满星。数据包自定义配方接入前，只走这一条。
     */
    @Override
    public void createResult() {
        ItemStack input = this.inputSlots.getItem(INPUT_SLOT);
        ItemStack result = this.isValidUpgrade(input) ? DiceStarUpgrade.resultFor(input) : ItemStack.EMPTY;
        this.resultSlots.setItem(0, result);
        if (!this.player.level().isClientSide()) {
            this.cost.set(result.isEmpty() ? 0 : DiceStarUpgrade.xpCostFor(input));
        }
    }

    private boolean isValidUpgrade(ItemStack input) {
        if (input.isEmpty() || !DiceStarUpgrade.isDice(input)) {
            return false;
        }
        int required = DiceStarUpgrade.starCoinCost(starLevelOf(input));
        if (required <= 0) {
            return false;
        }
        return this.areMaterialSlotsStarCoins() && this.countMaterials() >= required;
    }

    private boolean areMaterialSlotsStarCoins() {
        for (int index = MATERIAL_SLOT_FIRST; index <= MATERIAL_SLOT_SECOND; index++) {
            ItemStack stack = this.inputSlots.getItem(index);
            if (!stack.isEmpty() && !stack.is(ModItems.STAR_COIN.get())) {
                return false;
            }
        }
        return true;
    }

    private int countMaterials() {
        int total = 0;
        for (int index = MATERIAL_SLOT_FIRST; index <= MATERIAL_SLOT_SECOND; index++) {
            total += this.inputSlots.getItem(index).getCount();
        }
        return total;
    }

    private static int starLevelOf(ItemStack stack) {
        return ModDataComponents.WEAPON_ENHANCEMENT.getOrDefault(stack, WeaponEnhancement.EMPTY).starLevel();
    }

    /** 当前经验消耗（客户端读同一个 {@link DataSlot}）。 */
    public int getCost() {
        return this.cost.get();
    }

    @Override
    protected boolean mayPickup(Player player, boolean hasStack) {
        if (!hasStack) {
            return false;
        }
        return player.getAbilities().instabuild || player.experienceLevel >= this.getCost();
    }

    @Override
    protected void onTake(Player player, ItemStack stack) {
        ItemStack input = this.inputSlots.getItem(INPUT_SLOT);
        int required = DiceStarUpgrade.starCoinCost(starLevelOf(input));
        int xpCost = this.getCost();

        // 与普通铁砧一致：创造模式不扣经验（AnvilMenu#onTake 的语义），但不做耐久损坏。
        if (!player.getAbilities().instabuild) {
            player.giveExperienceLevels(-xpCost);
        }

        if (player instanceof ServerPlayer serverPlayer) {
            ChipShopState.store(serverPlayer, ChipShopState.get(serverPlayer).withRefreshGranted());
        }

        this.inputSlots.setItem(INPUT_SLOT, ItemStack.EMPTY);
        this.consumeMaterials(required);
        if (!player.level().isClientSide()) {
            this.cost.set(0);
        }
        this.broadcastChanges();
    }

    /** 扣材料：先槽 1 后槽 2，按实际需求扣，不吞多余星币。 */
    private void consumeMaterials(int required) {
        int remaining = required;
        for (int index = MATERIAL_SLOT_FIRST; index <= MATERIAL_SLOT_SECOND && remaining > 0; index++) {
            int taken = Math.min(remaining, this.inputSlots.getItem(index).getCount());
            if (taken <= 0) {
                continue;
            }
            this.inputSlots.removeItem(index, taken);
            remaining -= taken;
        }
    }

    @Override
    protected boolean isValidBlock(BlockState state) {
        return state.is(ModBlocks.ASTRAL_ANVIL.get());
    }

    @Override
    protected boolean canMoveIntoInputSlots(ItemStack stack) {
        return DiceStarUpgrade.isDice(stack) || stack.is(ModTags.Items.ASTRAL_ANVIL_MATERIALS);
    }

    @Override
    public int getSlotToQuickMoveTo(ItemStack stack) {
        return DiceStarUpgrade.isDice(stack) ? INPUT_SLOT : MATERIAL_SLOT_FIRST;
    }
}
