package com.merlinkitsune.astral_dice.screen;

import com.merlinkitsune.astral_dice.combat.CardRegistry;
import com.merlinkitsune.astral_dice.combat.DiceCombatModifiers;
import com.merlinkitsune.astral_dice.component.AppliedStone;
import com.merlinkitsune.starenginelib.component.GameplayConstants;
import com.merlinkitsune.astral_dice.component.ModAttachments;
import com.merlinkitsune.astral_dice.component.ModDataComponents;
import com.merlinkitsune.astral_dice.effect.ModEffects;
import com.merlinkitsune.astral_dice.component.WeaponEnhancement;
import com.merlinkitsune.astral_dice.item.dice.DiceCurioItem;
import com.merlinkitsune.astral_dice.item.ModItems;
import com.merlinkitsune.astral_dice.item.card.ExclusiveCardUtil;
import com.merlinkitsune.astral_dice.item.card.TemporaryCardPermissiveSlot;
import com.merlinkitsune.astral_dice.item.card.TemporaryCardUtil;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import com.merlinkitsune.starenginelib.item.CuriosCompat;
import top.theillusivec4.curios.api.CuriosApi;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.inventory.ClickType;

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
    // 打开界面时骰子内是否已装有蓄力(用于区分"赐福进行中新放入蓄力")
    private int displayAttackMin;
    private int displayAttackMax;
    private int displayDefenseMin;
    private int displayDefenseMax;

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
            refreshDisplayStats();
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
                    int uses = ModDataComponents.CARD_USES.getOrDefault(stack,  AppliedStone.defaultUses(type));
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
        // 卡牌配置星级:下界之星骰子(T4)恒为最高档 3★(卡牌槽 12/费用上限各 6)
        this.starLevel = DiceCurioItem.configStarLevel(equippedDice);
        this.maxAttackCost = GameplayConstants.cardCostForStar(starLevel);
        this.maxDefenseCost = GameplayConstants.cardCostForStar(starLevel);
    }
    private ItemStack findEquippedDice() {
        var curios = CuriosCompat.getCuriosInventory(player);
        if (curios.isEmpty()) return ItemStack.EMPTY;
        var result = curios.get().findFirstCurio(DiceCurioItem::isDiceItem);
        return result.isPresent() ? result.get().stack() : ItemStack.EMPTY;
    }

    private void loadFromDice() {
        if (player.level().isClientSide()) return;
        if (equippedDice.isEmpty()) return;
        WeaponEnhancement enh = ModDataComponents.WEAPON_ENHANCEMENT.getOrDefault(equippedDice,  WeaponEnhancement.EMPTY);
        // 卡牌配置星级:下界之星骰子(T4)恒为最高档 3★
        this.starLevel = DiceCurioItem.configStarLevel(equippedDice);
        this.maxAttackCost = GameplayConstants.cardCostForStar(starLevel);
        this.maxDefenseCost = GameplayConstants.cardCostForStar(starLevel);
        List<AppliedStone> stones = enh.appliedStones();
        int attIdx = 0;
        int defIdx = attackSlots;
        for (AppliedStone stone : stones) {
            if (isDefenseType(stone.type())) {
                if (defIdx < cardSlots) {
                    ItemStack itemStack = stoneToItem(stone);
                    ModDataComponents.CARD_USES.set(itemStack,  stone.uses());
                    // 临时牌(绿洲女王 nardis):装配状态下的临时性只存在 AppliedStone 里(装配会销毁
                    // 物品栈),这里把它**还原**到重建出来的栈上 ⇒ 卡牌栏 UI 里也一眼可辨(isFoil 会亮)。
                    if (stone.temporary()) {
                        TemporaryCardUtil.mark(itemStack);
                    }
                    cardContainer.setItem(defIdx, itemStack);
                    defIdx++;
                }
            } else {
                if (attIdx < attackSlots) {
                    ItemStack itemStack = stoneToItem(stone);
                    ModDataComponents.CARD_USES.set(itemStack,  stone.uses());
                    if (stone.temporary()) {
                        TemporaryCardUtil.mark(itemStack);
                    }
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
                    int uses = ModDataComponents.CARD_USES.getOrDefault(stack,  AppliedStone.defaultUses(type));
                    // 临时性从物品栈**读回**写进记录(装配后物品栈会被销毁,记录是唯一载体;
                    // 反向还原见 loadFromDice)。
                    stones.add(new AppliedStone(type, uses, TemporaryCardUtil.isTemporary(stack)));
                    if (isDefenseType(type)) {
                        totalDefenseCost += cost;
                    } else {
                        totalAttackCost += cost;
                    }
                }
            }
        }
        // 星级持久化为实际星级(下界之星骰子 GUI 显示 3★ 档配置,但不得把实际星级改写为 3)
        int actualStar = ModDataComponents.WEAPON_ENHANCEMENT.getOrDefault(dice,  WeaponEnhancement.EMPTY).starLevel();
        ModDataComponents.WEAPON_ENHANCEMENT.set(dice, 
                new WeaponEnhancement(totalAttackCost, maxAttackCost, totalDefenseCost, maxDefenseCost, actualStar, stones));

        // 教主立牌「狐光」装备计层(**守卫 ②:历史同时装备水位去重**)。
        // 只传"本次实际装备的攻击牌 类型→张数";是否计层由 TeruSignItem 按该玩家的历史水位判定
        // ⇒ 同一批牌「插入 → 卸除 → 再插入」一层都刷不到(详见 TeruSignItem#onAttackCardsEquipped)。
        // 注意必须落在 stones 计算之后:装备会**销毁卡牌物品栈**,卸除时由 loadFromDice 重建全新栈,
        // 任何物品级标记都不可能在"插入→卸除→再插入"之间存活。
        java.util.Map<String, Integer> equippedAttackCards = new java.util.LinkedHashMap<>();
        for (AppliedStone stone : stones) {
            if (stone == null || stone.type() == null || isDefenseType(stone.type())) continue;
            equippedAttackCards.merge(stone.type(), 1, Integer::sum);
        }
        com.merlinkitsune.astral_dice.item.sign.TeruSignItem.onAttackCardsEquipped(player, equippedAttackCards);
    }

    /**
     * 清空卡牌栏里所有带临时标记的牌,返回移除张数(幂等;判据 =
     * {@link TemporaryCardUtil#isTemporary})。
     *
     * <p>为什么需要这个方法:牌一旦放进卡牌栏就**离开了物品栏**,而本菜单的
     * {@code cardContainer} 在这段时间里才是「骰子卡牌栏」的真值 —— 菜单关闭时
     * {@link #saveToDice()} 会把它写回骰子。所以死亡清牌({@code event/PlayerLifecycleHandler})
     * 与效果到期清牌({@code TemporaryCardUtil#tick})若不覆盖这里,这些牌会借「关闭菜单时写回骰子」
     * 跨过清理。{@code TemporaryCardUtil#purgeAll} 已接入本方法(扫「当前打开的容器」那一段)。
     */
    public int purgeTemporaryCards() {
        int removed = 0;
        for (int i = 0; i < cardSlots; i++) {
            ItemStack stack = cardContainer.getItem(i);
            if (!TemporaryCardUtil.isTemporary(stack)) continue;
            removed += stack.getCount();
            cardContainer.setItem(i, ItemStack.EMPTY);
        }
        return removed;
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
        if (player.hasEffect(ModEffects.DICE_BLESSING.get())) {
            return;
        }
        super.clicked(slotId, button, clickType, player);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        // 骰神赐福期间禁止快捷移动卡牌(纵深防御;正常经 clicked 的 QUICK_MOVE 路由拦截)
        if (player.hasEffect(ModEffects.DICE_BLESSING.get())) {
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

    // 卡牌栏两个槽类实现 TemporaryCardPermissiveSlot:临时牌**允许**放进卡牌栏(那就是"装备"),
    // 是需求允许的两条去路之一。两个 mixin 的容器拦截据此放行;不用容器类型猜(它们是 SimpleContainer)。
    class AttackCardSlot extends Slot implements TemporaryCardPermissiveSlot {
        AttackCardSlot(int index, int x, int y) {
            super(cardContainer, index, x, y);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            String type = itemToStoneType(stack);
            if (type == null || isDefenseType(type)) return false;
            // 专属牌守门(撕咬/龙之咆哮等):非获得者不得装备(无主时放行并首次绑定,与效果牌同语义)
            if (ExclusiveCardUtil.isExclusive(stack) && !ExclusiveCardUtil.canUse(player, stack)) return false;
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

    class DefenseCardSlot extends Slot implements TemporaryCardPermissiveSlot {
        DefenseCardSlot(int index, int x, int y) {
            super(cardContainer, index, x, y);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            String type = itemToStoneType(stack);
            if (type == null || !isDefenseType(type)) return false;
            // 专属牌守门(同攻击牌槽;当前专属战斗牌均为攻击牌,防御槽此行为纵深防御)
            if (ExclusiveCardUtil.isExclusive(stack) && !ExclusiveCardUtil.canUse(player, stack)) return false;
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
