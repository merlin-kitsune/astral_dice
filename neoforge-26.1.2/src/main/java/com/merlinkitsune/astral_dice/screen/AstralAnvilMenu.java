package com.merlinkitsune.astral_dice.screen;

import com.merlinkitsune.astral_dice.block.ModBlocks;
import com.merlinkitsune.astral_dice.component.AppliedStone;
import com.merlinkitsune.astral_dice.component.ModDataComponents;
import com.merlinkitsune.astral_dice.component.WeaponEnhancement;
import com.merlinkitsune.astral_dice.item.DiceStarUpgrade;
import com.merlinkitsune.astral_dice.item.ModItems;
import com.merlinkitsune.astral_dice.item.ModTags;
import com.merlinkitsune.astral_dice.item.CardReturnHelper;
import com.merlinkitsune.astral_dice.shop.ChipShopState;
import java.util.List;
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
 * 星愿铁砧菜单：槽 0 = 骰子，槽 1/2 = 材料（{@link ModTags.Items#ASTRAL_ANVIL_MATERIALS}），槽 3 = 结果。
 *
 * <p>本批只做内置升星：两槽材料都必须为空或星币（{@link ModItems#STAR_COIN}），且合计达到
 * {@link DiceStarUpgrade#starCoinCost(int)} 的 15/20/25；经验按输入骰子的分级标签，取
 * {@link DiceStarUpgrade#xpCostFor(ItemStack)}。数据包自定义配方的查询入口留给后续批次。
 *
 * <p>取走结果时按本线铁砧语义：非创造扣经验、先槽 1 后槽 2 扣材料、授予筹码商店刷新；
 * 实验性「0★ + 卡牌归还」形态（{@code starLevel == 0 && appliedStones.isEmpty()}）额外把
 * 输入骰子上应用的石头卡还给玩家。本方块无耐久，不调用 {@code AnvilBlock.damage}。
 */
public class AstralAnvilMenu extends ItemCombinerMenu {

    /** 输入骰子槽。 */
    public static final int DICE_SLOT = 0;
    /** 第一材料槽（扣材料时先扣此槽）。 */
    public static final int MATERIAL_SLOT_1 = 1;
    /** 第二材料槽（扣材料时后扣此槽）。 */
    public static final int MATERIAL_SLOT_2 = 2;
    /** 结果槽。 */
    public static final int RESULT_SLOT = 3;

    /** 本次结果要扣的星币数；0 表示无结果。 */
    private int materialCost;
    /** 本次结果的经验消耗；服务端 createResult 写入，客户端经 {@link DataSlot} 同步；0 表示无结果。 */
    private final DataSlot cost = DataSlot.standalone();

    public AstralAnvilMenu(int containerId, Inventory playerInventory) {
        this(containerId, playerInventory, ContainerLevelAccess.NULL);
    }

    public AstralAnvilMenu(int containerId, Inventory playerInventory, ContainerLevelAccess access) {
        super(ModMenuTypes.ASTRAL_ANVIL.get(), containerId, playerInventory, access, createSlotDefinition());
        this.addDataSlot(this.cost);
    }

    private static ItemCombinerMenuSlotDefinition createSlotDefinition() {
        return ItemCombinerMenuSlotDefinition.create()
                .withSlot(DICE_SLOT, 26, 38, DiceStarUpgrade::isDice)
                .withSlot(MATERIAL_SLOT_1, 71, 38, stack -> stack.is(ModTags.Items.ASTRAL_ANVIL_MATERIALS))
                .withSlot(MATERIAL_SLOT_2, 89, 38, stack -> stack.is(ModTags.Items.ASTRAL_ANVIL_MATERIALS))
                .withResultSlot(RESULT_SLOT, 134, 38)
                .build();
    }

    @Override
    protected boolean isValidBlock(BlockState state) {
        return state.is(ModBlocks.ASTRAL_ANVIL.get());
    }

    /** 当前结果要扣的星币数（结果为空时 0）。 */
    public int getMaterialCost() {
        return this.materialCost;
    }

    /** 当前结果的经验消耗（结果为空时 0）；读服务端同步值。 */
    public int getCost() {
        return this.cost.get();
    }

    @Override
    public void createResult() {
        ItemStack dice = this.inputSlots.getItem(DICE_SLOT);
        ItemStack material1 = this.inputSlots.getItem(MATERIAL_SLOT_1);
        ItemStack material2 = this.inputSlots.getItem(MATERIAL_SLOT_2);

        ItemStack output = ItemStack.EMPTY;
        int coins = 0;
        if (DiceStarUpgrade.isDice(dice) && isStarCoinOrEmpty(material1) && isStarCoinOrEmpty(material2)) {
            int required = DiceStarUpgrade.starCoinCost(dice);
            if (required > 0 && material1.getCount() + material2.getCount() >= required) {
                ItemStack upgraded = DiceStarUpgrade.resultFor(dice);
                if (!upgraded.isEmpty()) {
                    output = upgraded;
                    coins = required;
                }
            }
        }
        this.resultSlots.setItem(0, output);
        this.materialCost = coins;
        if (!this.player.level().isClientSide()) {
            this.cost.set(output.isEmpty() ? 0 : DiceStarUpgrade.xpCostFor(dice));
        }
    }

    private static boolean isStarCoinOrEmpty(ItemStack stack) {
        return stack.isEmpty() || stack.is(ModItems.STAR_COIN.get());
    }

    @Override
    protected boolean mayPickup(Player player, boolean hasItem) {
        int required = this.cost.get();
        return required > 0 && (player.hasInfiniteMaterials() || player.experienceLevel >= required);
    }

    @Override
    protected void onTake(Player player, ItemStack carried) {
        ItemStack dice = this.inputSlots.getItem(DICE_SLOT).copy();
        int xpCost = this.cost.get();
        int coins = this.materialCost;
        this.materialCost = 0;
        if (!player.hasInfiniteMaterials()) {
            player.giveExperienceLevels(-xpCost);
        }
        consumeMaterials(coins);
        this.inputSlots.setItem(DICE_SLOT, ItemStack.EMPTY);
        if (player instanceof ServerPlayer serverPlayer) {
            ChipShopState.store(serverPlayer, ChipShopState.get(serverPlayer).withRefreshGranted());
        }

        // 快捷移动会先把结果槽搬空再回调 onTake，carried 可能是空栈；按输入栈重算产物形态。
        ItemStack expectedOutput = DiceStarUpgrade.resultFor(dice);
        WeaponEnhancement result = expectedOutput.getOrDefault(ModDataComponents.WEAPON_ENHANCEMENT.get(),
                WeaponEnhancement.EMPTY);
        if (!expectedOutput.isEmpty() && result.starLevel() == 0 && result.appliedStones().isEmpty()) {
            List<AppliedStone> stones = dice.getOrDefault(ModDataComponents.WEAPON_ENHANCEMENT.get(),
                    WeaponEnhancement.EMPTY).appliedStones();
            if (!stones.isEmpty()) {
                CardReturnHelper.returnCards(player, stones);
            }
        }
        if (!player.level().isClientSide()) {
            this.cost.set(0);
        }
        this.broadcastChanges();
    }

    private void consumeMaterials(int amount) {
        int remaining = amount;
        for (int index = MATERIAL_SLOT_1; index <= MATERIAL_SLOT_2 && remaining > 0; index++) {
            ItemStack stack = this.inputSlots.getItem(index);
            if (stack.isEmpty()) {
                continue;
            }
            int taken = Math.min(remaining, stack.getCount());
            stack.shrink(taken);
            remaining -= taken;
            if (stack.isEmpty()) {
                this.inputSlots.setItem(index, ItemStack.EMPTY);
            }
        }
    }
}
