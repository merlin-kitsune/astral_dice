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
    /** 本次结果要扣的材料面额总量；0 表示无结果。 */
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

    @Override
    public void createResult() {
        ItemStack input = this.inputSlots.getItem(INPUT_SLOT);
        ItemStack material1 = this.inputSlots.getItem(MATERIAL_SLOT_1);
        ItemStack material2 = this.inputSlots.getItem(MATERIAL_SLOT_2);
        ItemStack output = ItemStack.EMPTY;
        int coins = 0;
        if (DiceStarUpgrade.isDice(input) && areValidMaterials(material1, material2)) {
            int required = DiceStarUpgrade.starCoinCost(DiceStarUpgrade.starLevel(input));
            if (required > 0 && totalDenomination(material1) + totalDenomination(material2) >= required) {
                ItemStack upgraded = DiceStarUpgrade.resultFor(input);
                if (!upgraded.isEmpty()) {
                    output = upgraded;
                    coins = required;
                }
            }
        }
        this.resultSlots.setItem(0, output);
        this.materialCost = coins;
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

        // 扣材料:先槽 1 后槽 2,按面额扣;面额不可拆时可能略超扣。
        int remaining = this.materialCost;
        this.materialCost = 0;
        for (int slotIndex = MATERIAL_SLOT_1; slotIndex <= MATERIAL_SLOT_2 && remaining > 0; slotIndex++) {
            ItemStack material = this.inputSlots.getItem(slotIndex);
            while (remaining > 0 && !material.isEmpty()) {
                int value = Math.max(1, denominationOf(material));
                material.shrink(1);
                remaining -= value;
                this.inputSlots.setItem(slotIndex, material.isEmpty() ? ItemStack.EMPTY : material);
            }
        }
        this.inputSlots.setItem(INPUT_SLOT, ItemStack.EMPTY);

        if (player instanceof ServerPlayer serverPlayer && !input.isEmpty()) {
            ChipShopState.store(serverPlayer, ChipShopState.get(serverPlayer).withRefreshGranted());
        }
        if (!player.level().isClientSide()) {
            this.cost.set(0);
        }
        // 播放原版铁砧使用音效（与 AnvilMenu#onTake 一致）
        this.access.execute((level, pos) -> level.levelEvent(1030, pos, 0));
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
        } else if (isMaterial(stack)) {
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
}
