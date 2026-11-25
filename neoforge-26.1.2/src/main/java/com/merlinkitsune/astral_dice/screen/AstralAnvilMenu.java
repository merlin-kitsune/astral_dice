package com.merlinkitsune.astral_dice.screen;

import com.merlinkitsune.astral_dice.block.ModBlocks;
import com.merlinkitsune.astral_dice.data.AstralDiceDataStore;
import com.merlinkitsune.astral_dice.item.DiceStarUpgrade;
import com.merlinkitsune.astral_dice.shop.ChipShopState;
import java.util.List;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
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
                .withSlot(MATERIAL_SLOT_1, 71, 38, AstralAnvilMenu::isMaterial)
                .withSlot(MATERIAL_SLOT_2, 89, 38, AstralAnvilMenu::isMaterial)
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
        if (DiceStarUpgrade.isDice(dice) && areValidMaterials(material1, material2)) {
            int required = DiceStarUpgrade.starCoinCost(DiceStarUpgrade.starLevel(dice));
            if (required > 0 && totalDenomination(material1) + totalDenomination(material2) >= required) {
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

    private static boolean areValidMaterials(ItemStack material1, ItemStack material2) {
        return (material1.isEmpty() || isMaterial(material1)) && (material2.isEmpty() || isMaterial(material2));
    }

    /** 材料槽可放：数据包面额 > 0，或命中 {@code material_tag}（按 1 计）。 */
    private static boolean isMaterial(ItemStack stack) {
        return !stack.isEmpty() && denominationOf(stack) > 0;
    }

    private static int denominationOf(ItemStack stack) {
        String itemId = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
        int configured = AstralDiceDataStore.anvil().denomination(itemId);
        if (configured > 0) {
            return configured;
        }
        Identifier tagId = Identifier.tryParse(AstralDiceDataStore.anvil().materialTag());
        if (tagId == null) {
            return 0;
        }
        TagKey<net.minecraft.world.item.Item> tag = TagKey.create(Registries.ITEM, tagId);
        return stack.is(tag) ? 1 : 0;
    }

    private static int totalDenomination(ItemStack stack) {
        return denominationOf(stack) * stack.getCount();
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

        if (!player.level().isClientSide()) {
            this.cost.set(0);
        }
        // 播放原版铁砧使用音效（与 AnvilMenu#onTake 一致）
        this.access.execute((level, pos) -> level.levelEvent(1030, pos, 0));
        this.broadcastChanges();
    }

    private void consumeMaterials(int totalValue) {
        int remaining = totalValue;
        for (int index = MATERIAL_SLOT_1; index <= MATERIAL_SLOT_2 && remaining > 0; index++) {
            ItemStack stack = this.inputSlots.getItem(index);
            while (remaining > 0 && !stack.isEmpty()) {
                int value = Math.max(1, denominationOf(stack));
                stack.shrink(1);
                remaining -= value;
                if (stack.isEmpty()) {
                    this.inputSlots.setItem(index, ItemStack.EMPTY);
                } else {
                    this.inputSlots.setItem(index, stack);
                }
            }
        }
    }
}
