package com.merlinkitsune.astral_dice.screen;

import com.merlinkitsune.astral_dice.combat.CardRegistry;
import com.merlinkitsune.astral_dice.component.AppliedStone;
import com.merlinkitsune.astral_dice.component.ModDataComponents;
import com.merlinkitsune.astral_dice.component.WeaponEnhancement;
import com.merlinkitsune.astral_dice.item.dice.DiceCurioItem;
import com.merlinkitsune.astral_dice.item.ModItems;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import top.theillusivec4.curios.api.CuriosApi;

import java.util.ArrayList;
import java.util.List;

public class CardInventoryMenu extends AbstractContainerMenu {
    private static final int ATTACK_X_START = 32;
    private static final int ATTACK_Y_START = 18;
    private static final int DEFENSE_X_START = 32;
    private static final int DEFENSE_Y_START = 40;

    final Player player;
    final Inventory playerInventory;
    final SimpleContainer cardContainer;
    // 攻防卡牌放置栏数量由当前佩戴的骰子决定(基础骰子 2+2=4,黄金骰子 3+3=6)
    private final int cardSlots;
    private final int attackSlots;
    private final int defenseSlots;
    private final ItemStack equippedDice;
    private int maxAttackCost = 3;
    private int maxDefenseCost = 3;
    private int starLevel = 0;

    public CardInventoryMenu(int containerId, Inventory playerInventory) {
        super(ModMenuTypes.CARD_INVENTORY.get(), containerId);
        this.player = playerInventory.player;
        this.playerInventory = playerInventory;
        this.equippedDice = findEquippedDice();
        this.cardSlots = DiceCurioItem.getCardSlots(equippedDice);
        this.attackSlots = cardSlots / 2;
        this.defenseSlots = cardSlots - attackSlots;
        this.cardContainer = new SimpleContainer(cardSlots);

        for (int i = 0; i < attackSlots; i++) {
            addSlot(new AttackCardSlot(i, ATTACK_X_START + i * 20, ATTACK_Y_START));
        }
        for (int i = 0; i < defenseSlots; i++) {
            addSlot(new DefenseCardSlot(attackSlots + i, DEFENSE_X_START + i * 20, DEFENSE_Y_START));
        }

        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(playerInventory, col + row * 9 + 9, 8 + col * 18, 70 + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(playerInventory, col, 8 + col * 18, 128));
        }

        addDataSlot(new DataSlot() {
            @Override
            public int get() { return maxAttackCost; }
            @Override
            public void set(int value) { maxAttackCost = value; }
        });
        addDataSlot(new DataSlot() {
            @Override
            public int get() { return maxDefenseCost; }
            @Override
            public void set(int value) { maxDefenseCost = value; }
        });

        if (!player.level().isClientSide()) {
            loadFromDice();
        }
    }

    private ItemStack findEquippedDice() {
        var curios = CuriosApi.getCuriosInventory(player);
        if (curios.isEmpty()) return ItemStack.EMPTY;
        var result = curios.get().findFirstCurio(DiceCurioItem::isDiceItem);
        return result.isPresent() ? result.get().stack() : ItemStack.EMPTY;
    }

    private void loadFromDice() {
        if (player.level().isClientSide()) return;
        if (equippedDice.isEmpty()) return;
        WeaponEnhancement enh = equippedDice.getOrDefault(ModDataComponents.WEAPON_ENHANCEMENT.get(), WeaponEnhancement.EMPTY);
        this.maxAttackCost = enh.maxCost();
        this.maxDefenseCost = enh.maxDefenseCost();
        this.starLevel = enh.starLevel();
        List<AppliedStone> stones = enh.appliedStones();
        int attIdx = 0;
        int defIdx = attackSlots;
        for (AppliedStone stone : stones) {
            if (isDefenseType(stone.type())) {
                if (defIdx < cardSlots) {
                    ItemStack itemStack = stoneToItem(stone);
                    itemStack.set(ModDataComponents.CARD_USES.get(), stone.uses());
                    cardContainer.setItem(defIdx, itemStack);
                    defIdx++;
                }
            } else {
                if (attIdx < attackSlots) {
                    ItemStack itemStack = stoneToItem(stone);
                    itemStack.set(ModDataComponents.CARD_USES.get(), stone.uses());
                    cardContainer.setItem(attIdx, itemStack);
                    attIdx++;
                }
            }
        }
    }

    private void saveToDice() {
        if (player.level().isClientSide()) return;
        if (equippedDice.isEmpty()) return;
        ItemStack dice = equippedDice;

        List<AppliedStone> stones = new ArrayList<>();
        int totalAttackCost = 0;
        int totalDefenseCost = 0;
        for (int i = 0; i < cardSlots; i++) {
            ItemStack stack = cardContainer.getItem(i);
            if (!stack.isEmpty()) {
                String type = itemToStoneType(stack);
                if (type != null) {
                    int cost = stoneCost(type);
                    int uses = stack.getOrDefault(ModDataComponents.CARD_USES.get(), AppliedStone.defaultUses(type));
                    stones.add(new AppliedStone(type, uses));
                    if (isDefenseType(type)) {
                        totalDefenseCost += cost;
                    } else {
                        totalAttackCost += cost;
                    }
                }
            }
        }
        dice.set(ModDataComponents.WEAPON_ENHANCEMENT.get(),
                new WeaponEnhancement(totalAttackCost, maxAttackCost, totalDefenseCost, maxDefenseCost, starLevel, stones));
    }

    public int getMaxAttackCost() {
        return maxAttackCost;
    }

    public int getMaxDefenseCost() {
        return maxDefenseCost;
    }

    public int getCardSlots() {
        return cardSlots;
    }

    public int getAttackSlots() {
        return attackSlots;
    }

    public int getDefenseSlots() {
        return defenseSlots;
    }

    public int getUsedAttackCost() {
        int used = 0;
        for (int i = 0; i < attackSlots; i++) {
            ItemStack stack = cardContainer.getItem(i);
            if (!stack.isEmpty()) {
                String type = itemToStoneType(stack);
                if (type != null && !isDefenseType(type)) used += stoneCost(type);
            }
        }
        return used;
    }

    public int getUsedDefenseCost() {
        int used = 0;
        for (int i = attackSlots; i < cardSlots; i++) {
            ItemStack stack = cardContainer.getItem(i);
            if (!stack.isEmpty()) {
                String type = itemToStoneType(stack);
                if (type != null && isDefenseType(type)) used += stoneCost(type);
            }
        }
        return used;
    }

    // 卡牌费用(考虑护法立牌后的折扣):统一由 CardRegistry 提供
    private int stoneCost(String type) {
        return CardRegistry.cost(type, player);
    }

    @Override
    public boolean stillValid(Player player) {
        return true;
    }

    @Override
    public void removed(Player player) {
        if (!player.level().isClientSide()) {
            saveToDice();
        }
        cardContainer.clearContent();
        super.removed(player);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        ItemStack stack = ItemStack.EMPTY;
        Slot slot = this.slots.get(index);
        if (slot != null && slot.hasItem()) {
            ItemStack slotStack = slot.getItem();
            stack = slotStack.copy();
            if (index < cardSlots) {
                if (!this.moveItemStackTo(slotStack, cardSlots, this.slots.size(), true)) {
                    return ItemStack.EMPTY;
                }
            } else {
                if (!this.moveItemStackTo(slotStack, 0, cardSlots, false)) {
                    return ItemStack.EMPTY;
                }
            }
            if (slotStack.isEmpty()) {
                slot.setByPlayer(ItemStack.EMPTY);
            } else {
                slot.setChanged();
            }
        }
        return stack;
    }

    class AttackCardSlot extends Slot {
        AttackCardSlot(int index, int x, int y) {
            super(cardContainer, index, x, y);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            String type = itemToStoneType(stack);
            if (type == null || isDefenseType(type)) return false;
            int slotCost = stoneCost(type);
            int usedWithoutThis = 0;
            for (int i = 0; i < attackSlots; i++) {
                if (i == this.getSlotIndex()) continue;
                ItemStack s = cardContainer.getItem(i);
                if (!s.isEmpty()) {
                    String t = itemToStoneType(s);
                    if (t != null && !isDefenseType(t)) usedWithoutThis += stoneCost(t);
                }
            }
            return (usedWithoutThis + slotCost) <= maxAttackCost;
        }

        @Override
        public int getMaxStackSize() {
            return 1;
        }
    }

    class DefenseCardSlot extends Slot {
        DefenseCardSlot(int index, int x, int y) {
            super(cardContainer, index, x, y);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            String type = itemToStoneType(stack);
            if (type == null || !isDefenseType(type)) return false;
            int slotCost = stoneCost(type);
            int usedWithoutThis = 0;
            for (int i = attackSlots; i < cardSlots; i++) {
                if (i == this.getSlotIndex()) continue;
                ItemStack s = cardContainer.getItem(i);
                if (!s.isEmpty()) {
                    String t = itemToStoneType(s);
                    if (t != null && isDefenseType(t)) usedWithoutThis += stoneCost(t);
                }
            }
            return (usedWithoutThis + slotCost) <= maxDefenseCost;
        }

        @Override
        public int getMaxStackSize() {
            return 1;
        }
    }

    static boolean isDefenseType(String type) {
        return CardRegistry.isDefense(type);
    }

    static String itemToStoneType(ItemStack stack) {
        return CardRegistry.itemToType(stack);
    }

    static ItemStack stoneToItem(AppliedStone stone) {
        return CardRegistry.typeToItem(stone.type());
    }
}
