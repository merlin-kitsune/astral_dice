package com.merlinkitsune.astral_dice.screen;

import com.merlinkitsune.astral_dice.block.ModBlocks;
import com.merlinkitsune.astral_dice.data.AstralDiceDataStore;
import com.merlinkitsune.astral_dice.item.DiceStarUpgrade;
import com.merlinkitsune.astral_dice.shop.ChipShopState;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
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
 * 星愿铁砧菜单（2026-11-15）：骰子 + 两格材料的升星台。
 *
 * <p>槽位：0 = 骰子、1/2 = 材料、3 = 结果；玩家物品栏由 {@link ItemCombinerMenu} 基类补齐。
 *
 * <p>材料与费用由数据包 {@code data/<namespace>/astral_dice/anvil/*.json} 驱动：
 * {@code materials} 面额表优先，{@code material_tag}（默认
 * {@code astral_dice:astral_anvil_materials}）收录但未列面额的材料按 1 计。
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

    /** 本次 createResult 选中的材料面额总量，onTake 扣料时复用。 */
    private int materialCost;

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
                .withSlot(MATERIAL_SLOT_FIRST, 71, 38, AstralAnvilMenu::isMaterial)
                .withSlot(MATERIAL_SLOT_SECOND, 89, 38, AstralAnvilMenu::isMaterial)
                .withResultSlot(RESULT_SLOT, 134, 38)
                .build();
    }

    @Override
    public void createResult() {
        ItemStack input = this.inputSlots.getItem(INPUT_SLOT);
        ItemStack material1 = this.inputSlots.getItem(MATERIAL_SLOT_FIRST);
        ItemStack material2 = this.inputSlots.getItem(MATERIAL_SLOT_SECOND);
        this.materialCost = 0;

        ItemStack result = ItemStack.EMPTY;
        if (DiceStarUpgrade.isDice(input) && areValidMaterials(material1, material2)) {
            int required = DiceStarUpgrade.starCoinCost(DiceStarUpgrade.starLevel(input));
            if (required > 0 && totalDenomination(material1) + totalDenomination(material2) >= required) {
                ItemStack candidate = DiceStarUpgrade.resultFor(input);
                if (!candidate.isEmpty()) {
                    result = candidate;
                    this.materialCost = required;
                }
            }
        }
        this.resultSlots.setItem(0, result);
        if (!this.player.level().isClientSide()) {
            this.cost.set(result.isEmpty() ? 0 : DiceStarUpgrade.xpCostFor(input));
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
        ResourceLocation tagId = ResourceLocation.tryParse(AstralDiceDataStore.anvil().materialTag());
        if (tagId == null) {
            return 0;
        }
        TagKey<net.minecraft.world.item.Item> tag = TagKey.create(Registries.ITEM, tagId);
        return stack.is(tag) ? 1 : 0;
    }

    private static int totalDenomination(ItemStack stack) {
        return denominationOf(stack) * stack.getCount();
    }

    /** 当前经验消耗（客户端读同一个 {@link DataSlot}）。 */
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
        int xpCost = this.cost.get();
        int consumed = this.materialCost;

        // 与普通铁砧一致：创造模式不扣经验（AnvilMenu#onTake 的语义），但不做耐久损坏。
        if (!player.getAbilities().instabuild) {
            player.giveExperienceLevels(-xpCost);
        }

        if (player instanceof ServerPlayer serverPlayer) {
            ChipShopState.store(serverPlayer, ChipShopState.get(serverPlayer).withRefreshGranted());
        }

        this.inputSlots.setItem(INPUT_SLOT, ItemStack.EMPTY);
        this.consumeMaterials(consumed);
        if (!player.level().isClientSide()) {
            this.cost.set(0);
        }
        this.broadcastChanges();
    }

    /** 扣材料：先槽 1 后槽 2，按面额扣；面额不可拆时可能略超扣。 */
    private void consumeMaterials(int totalValue) {
        int remaining = totalValue;
        for (int index = MATERIAL_SLOT_FIRST; index <= MATERIAL_SLOT_SECOND && remaining > 0; index++) {
            ItemStack material = this.inputSlots.getItem(index);
            while (remaining > 0 && !material.isEmpty()) {
                int value = Math.max(1, denominationOf(material));
                material.shrink(1);
                if (material.isEmpty()) {
                    this.inputSlots.setItem(index, ItemStack.EMPTY);
                } else {
                    this.inputSlots.setItem(index, material);
                }
                remaining -= value;
            }
        }
    }

    @Override
    protected boolean isValidBlock(BlockState state) {
        return state.is(ModBlocks.ASTRAL_ANVIL.get());
    }

    @Override
    protected boolean canMoveIntoInputSlots(ItemStack stack) {
        return DiceStarUpgrade.isDice(stack) || isMaterial(stack);
    }

    @Override
    public int getSlotToQuickMoveTo(ItemStack stack) {
        return DiceStarUpgrade.isDice(stack) ? INPUT_SLOT : MATERIAL_SLOT_FIRST;
    }
}
