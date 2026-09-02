package com.merlinkitsune.astral_dice.screen;

import com.merlinkitsune.astral_dice.combat.CardRegistry;
import com.merlinkitsune.astral_dice.combat.DiceCombatModifiers;
import com.merlinkitsune.astral_dice.component.AppliedStone;
import com.merlinkitsune.astral_dice.component.GameplayConstants;
import com.merlinkitsune.astral_dice.component.ModDataComponents;
import com.merlinkitsune.astral_dice.effect.ModEffects;
import com.merlinkitsune.astral_dice.component.WeaponEnhancement;
import com.merlinkitsune.astral_dice.item.dice.DiceCurioItem;
import com.merlinkitsune.astral_dice.item.ModItems;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import top.theillusivec4.curios.api.CuriosApi;

import java.util.ArrayList;
import java.util.List;

public class CardInventoryMenu extends AbstractContainerMenu {
    private static final int HIDDEN_X = -10000;
    private static final int HIDDEN_Y = -10000;
    private static final int CARD_SLOT_X_START = 26;
    private static final int CARD_SLOT_SPACING = 18;
    private static final int CARD_SLOT_ATTACK_Y = 3;
    private static final int CARD_SLOT_DEFENSE_Y = 21;
    public static final int SELECTOR_VISIBLE_ROWS = 3;
    private static final int SELECTOR_LEFT_X = 8;
    private static final int SELECTOR_RIGHT_X = 90;
    private static final int SELECTOR_ROW_Y = 56;
    private static final int SELECTOR_ROW_SPACING = 18;
    public static final int SELECTOR_COLUMNS = 3;

    final Player player;
    final Inventory playerInventory;
    final SimpleContainer cardContainer;
    private final List<Slot> inventorySlots = new ArrayList<>();
    private final int cardSlots;
    private final int attackSlots;
    private final int defenseSlots;
    private final ItemStack equippedDice;
    private int maxAttackCost = GameplayConstants.MAX_CARD_COST;
    private int maxDefenseCost = GameplayConstants.MAX_CARD_COST;
    private int starLevel = 0;
    private int selectorScrollOffset = 0;
    private int displayAttackMin;
    private int displayAttackMax;
    private int displayDefenseMin;
    private int displayDefenseMax;
    // 显示数值刷新节流:完整修饰器链(含 Curios 查询/属性读取)只需每秒刷新一次
    private long lastStatsRefreshTick = Long.MIN_VALUE;

    public CardInventoryMenu(int containerId, Inventory playerInventory) {
        super(ModMenuTypes.CARD_INVENTORY.get(), containerId);
        this.player = playerInventory.player;
        this.playerInventory = playerInventory;
        this.equippedDice = findEquippedDice();
        initMaxCostFromDice();
        int totalCardSlots = equippedDice.isEmpty()
                ? GameplayConstants.CARD_SLOTS_TOTAL
                : DiceCurioItem.getCardSlots(equippedDice);
        this.cardSlots = totalCardSlots;
        this.attackSlots = totalCardSlots / 2;
        this.defenseSlots = totalCardSlots / 2;
        this.cardContainer = new SimpleContainer(cardSlots);

        for (int i = 0; i < attackSlots; i++) {
            addSlot(new AttackCardSlot(i, CARD_SLOT_X_START + i * CARD_SLOT_SPACING, CARD_SLOT_ATTACK_Y));
        }
        for (int i = 0; i < defenseSlots; i++) {
            addSlot(new DefenseCardSlot(attackSlots + i, CARD_SLOT_X_START + i * CARD_SLOT_SPACING, CARD_SLOT_DEFENSE_Y));
        }

        for (int i = 0; i < playerInventory.items.size(); i++) {
            Slot slot = new Slot(playerInventory, i, HIDDEN_X, HIDDEN_Y);
            addSlot(slot);
        }

        addDataSlot(new DataSlot() {
            @Override
            public int get() { return maxAttackCost; }
            @Override
            public void set(int value) { maxAttackCost = value; }
        });
        addDataSlot(new DataSlot() {
            @Override
            public int get() { return starLevel; }
            @Override
            public void set(int value) { starLevel = value; }
        });
        addDataSlot(new DataSlot() {
            @Override
            public int get() { return maxDefenseCost; }
            @Override
            public void set(int value) { maxDefenseCost = value; }
        });
        addDataSlot(new DataSlot() {
            @Override
            public int get() { return displayAttackMin; }
            @Override
            public void set(int value) { displayAttackMin = value; }
        });
        addDataSlot(new DataSlot() {
            @Override
            public int get() { return displayAttackMax; }
            @Override
            public void set(int value) { displayAttackMax = value; }
        });
        addDataSlot(new DataSlot() {
            @Override
            public int get() { return displayDefenseMin; }
            @Override
            public void set(int value) { displayDefenseMin = value; }
        });
        addDataSlot(new DataSlot() {
            @Override
            public int get() { return displayDefenseMax; }
            @Override
            public void set(int value) { displayDefenseMax = value; }
        });


        if (!player.level().isClientSide()) {
            loadFromDice();
            refreshDisplayStats();
        }
    }

    // === 卡牌选择器:返回物品栏战斗牌对应的隐藏 Slot(按物品栏顺序) ===
    public List<Slot> getAttackSelectorSlots() {
        return getSelectorSlots(false);
    }

    public List<Slot> getDefenseSelectorSlots() {
        return getSelectorSlots(true);
    }

    public int getDisplayAttackMin() {
        return displayAttackMin;
    }

    public int getDisplayAttackMax() {
        return displayAttackMax;
    }

    public int getDisplayDefenseMin() {
        return displayDefenseMin;
    }

    public int getDisplayDefenseMax() {
        return displayDefenseMax;
    }

    public Slot getFirstEmptyInventorySlot() {
        for (int i = cardSlots; i < this.slots.size(); i++) {
            Slot slot = this.slots.get(i);
            if (!slot.hasItem()) return slot;
        }
        return null;
    }

    @Override
    public void broadcastChanges() {
        if (!player.level().isClientSide()) {
            // 完整攻击/防御修饰器链每 tick 重算代价高(Curios 查询/属性读取/卡牌范围);
            // 显示数值按 20 tick(1 秒)节流刷新,点击/放入卡牌后最迟 1 秒内更新
            long now = player.level().getGameTime();
            if (now - lastStatsRefreshTick >= 20) {
                refreshDisplayStats();
                lastStatsRefreshTick = now;
            }
        }
        super.broadcastChanges();
    }

    private void refreshDisplayStats() {
        if (player.level().isClientSide()) return;
        WeaponEnhancement enh = buildEnhancementFromContainer();
        DiceCombatModifiers.PowerRange atk = DiceCombatModifiers.getDisplayAttackRange(player, equippedDice, enh);
        DiceCombatModifiers.PowerRange def = DiceCombatModifiers.getDisplayDefenseRange(player, enh);
        this.displayAttackMin = atk.min();
        this.displayAttackMax = atk.max();
        this.displayDefenseMin = def.min();
        this.displayDefenseMax = def.max();
    }

    // 根据当前卡牌栏实时构建临时强化数据,确保放入/移除卡牌后数值立即刷新
    private WeaponEnhancement buildEnhancementFromContainer() {
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
        return new WeaponEnhancement(totalAttackCost, maxAttackCost, totalDefenseCost, maxDefenseCost, starLevel, stones);
    }


    public int getSelectorScrollOffset() {
        return selectorScrollOffset;
    }

    public int getMaxSelectorScrollOffset() {
        // 3 列网格:按行滚动(每行 SELECTOR_COLUMNS 张)
        int slots = Math.max(getAttackSelectorSlots().size(), getDefenseSelectorSlots().size());
        int rows = (slots + SELECTOR_COLUMNS - 1) / SELECTOR_COLUMNS;
        return Math.max(0, rows - SELECTOR_VISIBLE_ROWS);
    }

    public void scrollSelector(int amount) {
        this.selectorScrollOffset = Math.max(0, Math.min(getMaxSelectorScrollOffset(), selectorScrollOffset + amount));
    }

    private List<Slot> getSelectorSlots(boolean defense) {
        List<Slot> result = new ArrayList<>();
        for (int i = 0; i < playerInventory.items.size(); i++) {
            ItemStack stack = playerInventory.items.get(i);
            String type = CardRegistry.itemToType(stack);
            if (type == null) continue;
            if (CardRegistry.isDefense(type) == defense) {
                result.add(this.slots.get(cardSlots + i));
            }
        }
        return result;
    }
    private void initMaxCostFromDice() {
        if (equippedDice.isEmpty()) return;
        WeaponEnhancement enh = equippedDice.getOrDefault(ModDataComponents.WEAPON_ENHANCEMENT.get(), WeaponEnhancement.EMPTY);
        this.starLevel = enh.starLevel();
        this.maxAttackCost = GameplayConstants.cardCostForStar(starLevel);
        this.maxDefenseCost = GameplayConstants.cardCostForStar(starLevel);
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
        this.starLevel = enh.starLevel();
        this.maxAttackCost = GameplayConstants.cardCostForStar(starLevel);
        this.maxDefenseCost = GameplayConstants.cardCostForStar(starLevel);
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

    public ItemStack getCardItem(int slotIndex) {
        return cardContainer.getItem(slotIndex);
    }
    public int getMaxAttackCost() {
        return maxAttackCost;
    }

    public int getStarLevel() {
        return starLevel;
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

        // 骰神赐福期间卡牌栏锁定:禁止插入/移除卡牌(服务端权威;客户端同逻辑避免操作闪烁)
        @Override
        public void clicked(int slotId, int button, ClickType clickType, Player player) {
            if (player.hasEffect(ModEffects.DICE_BLESSING)) {
                return;
            }
            super.clicked(slotId, button, clickType, player);
        }

        @Override
        public ItemStack quickMoveStack(Player player, int index) {
            // 骰神赐福期间禁止快捷移动卡牌(纵深防御;正常经 clicked 的 QUICK_MOVE 路由拦截)
            if (player.hasEffect(ModEffects.DICE_BLESSING)) {
                return ItemStack.EMPTY;
            }
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
