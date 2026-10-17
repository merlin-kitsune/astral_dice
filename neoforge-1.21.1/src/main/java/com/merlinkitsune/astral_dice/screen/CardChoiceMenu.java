package com.merlinkitsune.astral_dice.screen;

import com.merlinkitsune.astral_dice.choice.CardChoiceManager;
import com.merlinkitsune.astral_dice.choice.CardChoiceType;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * 「选卡界面」菜单（用户裁决 2026-10-17；蓝本 = {@code shop/CardShopMenu} 的 ShelfSlot 形态）。
 *
 * <p><b>布局</b>（屏幕层自持 176x200，菜单层只给槽位坐标）：
 * <ul>
 *   <li>N 个虚拟卡槽（N = 1 或 3）：{@link #HIDDEN_X}/{@link #HIDDEN_Y} 后备容器，屏幕层自绘卡框，
 *       每格至多 1 张；{@code mayPlace} 按 {@link CardChoiceType} 过滤；</li>
 *   <li>玩家物品栏 36 格 9x4：坐标与商店同源（{@code shop_item_inventory.png} 的内格，图内偏移 +3,+3），
 *       由原版渲染物品。</li>
 * </ul>
 *
 * <p><b>移动规则</b>：{@link #quickMoveStack} 双向（物品栏卡牌 → 第一个可放置的空卡槽；
 * 卡槽 → 物品栏）。屏幕层把「双击」翻译成原版 {@code ClickType.QUICK_MOVE}，
 * 因此本方法同时承担双击与 Shift+点击两条入口。
 *
 * <p><b>权威性</b>：卡牌由 {@code quickMoveStack}/原版点击**真实移入**本菜单的后备容器（服务端持有），
 * 确认时只对容器内的栈逐张消耗 1 张，余量随 {@link #removed} 退回物品栏 ⇒ 客户端无法凭空造牌。
 */
public class CardChoiceMenu extends AbstractContainerMenu {

    /** 隐藏坐标（屏幕层自绘；与商店 ShelfSlot 同口径）。 */
    public static final int HIDDEN_X = -10000;
    public static final int HIDDEN_Y = -10000;

    /** 物品栏第一格（主物品栏第 1 行第 1 列）= shop_item_inventory.png 内格起点（图 @ (5,116) + (3,3)）。 */
    public static final int INVENTORY_X = 8;
    public static final int INVENTORY_Y = 119;
    /** 快捷栏 y（图内 +61：三行主物品栏与快捷栏之间留 6px）。 */
    public static final int INVENTORY_HOTBAR_Y = 177;

    private final Inventory playerInventory;
    private final SimpleContainer cardContainer;
    private final int token;
    private final int slotCount;
    private final CardChoiceType type;
    /** 已确认：removed 时只退余量、不触发会话作废。 */
    private boolean confirmed;
    private boolean closed;

    /** 服务端构造（{@code ServerPlayer#openMenu} 的工厂）。 */
    public CardChoiceMenu(int containerId, Inventory playerInventory, int token, int slotCount, CardChoiceType type) {
        super(ModMenuTypes.CARD_CHOICE.get(), containerId);
        this.playerInventory = playerInventory;
        this.token = token;
        this.slotCount = Math.max(CardChoiceManager.MIN_SLOTS, Math.min(CardChoiceManager.MAX_SLOTS, slotCount));
        this.type = type == null ? CardChoiceType.ANY : type;
        this.cardContainer = new SimpleContainer(this.slotCount);
        initSlots();
    }

    /** 客户端构造（extra data：token + slotCount + type.ordinal()）。 */
    public CardChoiceMenu(int containerId, Inventory playerInventory, FriendlyByteBuf buf) {
        this(containerId, playerInventory, buf.readVarInt(), buf.readVarInt(),
                CardChoiceType.byId(buf.readVarInt()));
    }

    private void initSlots() {
        for (int i = 0; i < slotCount; i++) {
            addSlot(new ChoiceCardSlot(cardContainer, i, this.type, HIDDEN_X, HIDDEN_Y));
        }
        for (int row = 0; row < 4; row++) {
            for (int col = 0; col < 9; col++) {
                int x = INVENTORY_X + col * 18;
                int y = row == 3 ? INVENTORY_HOTBAR_Y : INVENTORY_Y + row * 18;
                int index = row == 3 ? col : 9 + row * 9 + col;
                addSlot(new Slot(playerInventory, index, x, y));
            }
        }
    }

    public int token() {
        return token;
    }

    public int slotCount() {
        return slotCount;
    }

    public CardChoiceType type() {
        return type;
    }

    /** 已放满（确认按钮的唯一显示/响应判据）。 */
    public boolean isComplete() {
        return filledSlots() == slotCount;
    }

    public int filledSlots() {
        int filled = 0;
        for (int i = 0; i < slotCount; i++) {
            ItemStack stack = cardContainer.getItem(i);
            if (!stack.isEmpty() && type.matches(stack)) {
                filled++;
            }
        }
        return filled;
    }

    /** 服务端确认：校验后逐张消耗 1 张（1.21.1 = {@code ItemStack#consume(1, player)}）。 */
    public List<ItemStack> consumeSelection(Player player) {
        // 先整体校验，再动任何一张（避免「消耗到一半发现不合法」）
        for (int i = 0; i < slotCount; i++) {
            ItemStack stack = cardContainer.getItem(i);
            if (stack.isEmpty() || !type.matches(stack)) {
                return List.of();
            }
        }
        List<ItemStack> picked = new ArrayList<>(slotCount);
        for (int i = 0; i < slotCount; i++) {
            ItemStack stack = cardContainer.getItem(i);
            ItemStack one = stack.copyWithCount(1);
            stack.consume(1, player);
            cardContainer.setItem(i, stack.isEmpty() ? ItemStack.EMPTY : stack);
            picked.add(one);
        }
        this.confirmed = true;
        return picked;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        if (index < 0 || index >= slots.size()) {
            return ItemStack.EMPTY;
        }
        Slot slot = slots.get(index);
        if (slot == null || !slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack copy = stack.copy();
        int playerStart = slotCount;
        int playerEnd = slotCount + 36;
        boolean moved;
        if (index < slotCount) {
            // 卡槽 → 物品栏
            moved = moveItemStackTo(stack, playerStart, playerEnd, true);
        } else {
            // 物品栏 → 卡槽（类型不符由 ChoiceCardSlot#mayPlace 拒绝）
            if (!type.matches(stack)) {
                return ItemStack.EMPTY;
            }
            moved = moveItemStackTo(stack, 0, slotCount, false);
        }
        if (!moved) {
            return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) {
            slot.setByPlayer(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        return copy;
    }

    /** 恒 true：无方块锚点（与卡牌商店同口径），合法性由 token + 菜单实例 + 类型三重校验保证。 */
    @Override
    public boolean stillValid(Player player) {
        return true;
    }

    @Override
    public void removed(Player player) {
        if (!player.level().isClientSide() && !closed) {
            closed = true;
            // 死亡 / 已断线一律**直接掉落**（避免写进不会再保存的背包）。
            // ⚠️ 不能只看 isAlive()：原版 {@code Entity#isAlive()} = {@code !isRemoved()}，死亡事件期间
            //    仍为 true（{@code PlayerList#respawn} 换实体之前不会 removed）⇒ 必须在死亡瞬间用
            //    isDeadOrDying() 判定，否则卡牌会写进旧玩家的背包并随旧实体一起消失。
            boolean dying = player.isDeadOrDying() || !player.isAlive()
                    || (player instanceof ServerPlayer serverPlayer && serverPlayer.hasDisconnected());
            // 余量（含「已确认但原栈还有多张」与「未确认的全部选择」）一律退回物品栏
            for (int i = 0; i < slotCount; i++) {
                ItemStack stack = cardContainer.getItem(i);
                if (!stack.isEmpty()) {
                    // 与 AbstractContainerMenu#removed 同口径：死亡 / 已断线时掉落，避免写进不会再保存的背包
                    if (dying) {
                        player.drop(stack, false);
                    } else {
                        player.getInventory().placeItemBackInInventory(stack);
                    }
                    cardContainer.setItem(i, ItemStack.EMPTY);
                }
            }
            // 光标上拖着的卡同理：父类 removed 只按 isAlive() 判定，会把它塞回（不会再保存的）背包
            if (dying) {
                ItemStack carried = getCarried();
                if (!carried.isEmpty()) {
                    player.drop(carried, false);
                    setCarried(ItemStack.EMPTY);
                }
            }
            if (!confirmed && player instanceof ServerPlayer serverPlayer) {
                CardChoiceManager.abandon(serverPlayer, token);
            }
        }
        cardContainer.clearContent();
        super.removed(player);
    }

    /** 虚拟卡槽：隐藏坐标、单格 1 张、按类型过滤。 */
    private static final class ChoiceCardSlot extends Slot {
        private final CardChoiceType type;

        ChoiceCardSlot(SimpleContainer container, int index, CardChoiceType type, int x, int y) {
            super(container, index, x, y);
            this.type = type;
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return type.matches(stack);
        }

        /**
         * ⚠️ 必须为 true：原版在 {@code ClickType.QUICK_MOVE}（双击/Shift+点击）之前会检查
         * {@code slot.mayPickup(player)}，恒 false 会让「双击取回」静默失效。
         * 本槽不涉及商店那种「白拿货」风险（槽里就是玩家自己的卡），允许光标直接取回同样合理。
         */
        @Override
        public boolean mayPickup(Player player) {
            return true;
        }

        @Override
        public int getMaxStackSize() {
            return 1;
        }
    }
}
