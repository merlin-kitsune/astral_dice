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
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 星愿铁砧菜单（2026-11-15）。
 *
 * <p>三输入 + 结果：槽 0 = 骰子 (26,38)、槽 1/2 = 材料 (71,38)/(89,38)、槽 3 = 结果 (134,38)。
 * 材料槽由数据包控制：{@code materials} 面额表里的物品按面额计，{@code material_tag}
 * （默认 {@code astral_dice:astral_anvil_materials}）收录但未列面额的物品按 1 计；两槽面额合计达到
 * 该星阶成本才出结果。默认数据文件只列星币，因此不装数据包时行为与旧版一致。
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

    /**
     * 材料槽过滤：命中数据包口径（{@code material_tag} 标签）**或** {@code materials} 面额表里的物品。
     * 默认数据文件两者都是星币，行为与 2026-11-15 之前一致。
     */
    private static boolean isMaterial(ItemStack stack) {
        return !stack.isEmpty() && denominationOf(stack) > 0;
    }

    /**
     * 单件材料的面额：{@code materials} 表优先；只被 {@code material_tag} 收录、未列入面额表的物品按 1 计；
     * 其它物品 0（不是材料槽可接受的物品）。
     */
    private static int denominationOf(ItemStack stack) {
        if (stack.isEmpty()) {
            return 0;
        }
        ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(stack.getItem());
        int fromData = AstralDiceDataStore.anvil().denomination(itemId.toString());
        if (fromData > 0) {
            return fromData;
        }
        ResourceLocation tagId = ResourceLocation.tryParse(AstralDiceDataStore.anvil().materialTag());
        if (tagId == null) {
            return 0;
        }
        TagKey<net.minecraft.world.item.Item> materialTag = TagKey.create(Registries.ITEM, tagId);
        return stack.is(materialTag) ? 1 : 0;
    }

    /** 内置升星：骰子 + 全星币 + 两槽合计够成本 ⇒ 产出 {@link DiceStarUpgrade#resultFor}。 */
    @Override
    public void createResult() {
        ItemStack input = this.inputSlots.getItem(INPUT_SLOT);
        ItemStack material1 = this.inputSlots.getItem(MATERIAL_SLOT_1);
        ItemStack material2 = this.inputSlots.getItem(MATERIAL_SLOT_2);
        ItemStack output = ItemStack.EMPTY;
        this.materialCost = 0;
        if (DiceStarUpgrade.isDice(input) && areValidMaterials(material1, material2)) {
            int required = DiceStarUpgrade.starCoinCost(starLevelOf(input));
            if (required > 0 && totalDenomination(material1) + totalDenomination(material2) >= required) {
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

    /** 两个材料槽都必须为空或可接受的材料（标签内物品或面额表物品）。 */
    private static boolean areValidMaterials(ItemStack material1, ItemStack material2) {
        return (material1.isEmpty() || isMaterial(material1))
                && (material2.isEmpty() || isMaterial(material2));
    }

    /** 一槽材料的**总面额**（单件面额 × 数量）——数据包可以定义面额 > 1 的材料，如「星币袋 = 10」。 */
    private static int totalDenomination(ItemStack stack) {
        return stack.isEmpty() ? 0 : denominationOf(stack) * stack.getCount();
    }

    private static int starLevelOf(ItemStack stack) {
        return DiceStarUpgrade.starLevel(stack);
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
     * 取走结果：扣经验（创造免）、扣材料（先槽 1 后槽 2）、授予筹码商店刷新。
     */
    @Override
    protected void onTake(Player player, ItemStack stack) {
        int xpCost = this.cost.get();
        int consumed = this.materialCost;
        if (!player.getAbilities().instabuild) {
            player.giveExperienceLevels(-xpCost);
        }
        // 先清输入骰子：会再触发一次 createResult（输入为空 ⇒ 结果清空、成本归零）
        this.inputSlots.setItem(INPUT_SLOT, ItemStack.EMPTY);
        consumeMaterials(consumed);
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

    /**
     * 扣料：先槽 1 后槽 2，按 createResult 算出的**面额总量**扣，不按槽平均。
     * 面额 > 1 的材料按整件扣，最后一次可能略超扣（面额不可拆分，超出部分不退，见交付说明）。
     */
    private void consumeMaterials(int totalValue) {
        int remaining = totalValue;
        for (int slotIndex : new int[]{MATERIAL_SLOT_1, MATERIAL_SLOT_2}) {
            while (remaining > 0) {
                ItemStack material = this.inputSlots.getItem(slotIndex);
                if (material.isEmpty()) {
                    break;
                }
                int value = Math.max(1, denominationOf(material));
                material.shrink(1);
                this.inputSlots.setItem(slotIndex, material.isEmpty() ? ItemStack.EMPTY : material);
                remaining -= value;
            }
            if (remaining <= 0) {
                return;
            }
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
        } else if (isMaterial(stack)) {
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
