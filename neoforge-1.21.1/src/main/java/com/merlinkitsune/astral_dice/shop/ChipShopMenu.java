package com.merlinkitsune.astral_dice.shop;

import com.merlinkitsune.astral_dice.screen.ModMenuTypes;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * 筹码商店菜单（本批 = **界面骨架**：只有玩家物品栏，没有任何商店逻辑槽；判据正本
 * {@code docs/chip-shop-ui-spec.md}）。
 *
 * <p><b>槽位构成</b>：仅玩家物品栏 36 格 —— 主栏 9x3（物品栏槽 9..35）+ 快捷栏 9x1（槽 0..8）。
 * 没有货架槽、没有 DataSlot、没有任何服务端逻辑（购买/刷新的载荷与结算属于逻辑批，见规范 §9）。
 *
 * <p><b>坐标口径</b>（规范 §3 末条）：界面侧面板格网原点 = (61, 94)、格距 18、快捷栏行 y = 152；
 * 「槽内物品图标内缩 1 px」⇒ 本类的槽位坐标 = 格网起点 **+1 px**（主栏 (62 + 18i, 95 + 18j)、
 * 快捷栏 (62 + 18i, 153)）。原版 {@code Slot} 的 x/y 同时是**物品绘制点**与**命中判定点**，
 * 故内缩位移放在槽位坐标里一次做对，屏幕层不再逐格修正。界面侧的同一套数字见
 * {@code ChipShopScreen.SLOT_X0 / SLOT_Y0 / SLOT_PITCH / SLOT_COLS / SLOT_ROWS / HOTBAR_Y}。
 *
 * <p><b>为什么 36 条槽位构造是逐条字面量</b>：守门脚本
 * {@code scripts/verify/verify_chip_shop_ui.ps1}（G6）按字面统计槽位数（应恰为 36），
 * 循环写法会让这条判据失去意义 —— 保持逐条可核对，改动时也不容易静默多/少一格。
 */
public class ChipShopMenu extends AbstractContainerMenu {

    /** 玩家槽总数（9x3 主栏 + 9x1 快捷栏） */
    public static final int PLAYER_SLOT_COUNT = 36;
    /** 主栏格数（物品栏槽 9..35；快捷栏 = 其后的 9 格） */
    public static final int MAIN_SIZE = 27;

    /** 物品栏面板格网原点（界面侧常量同口径；槽位坐标另加 {@link #ITEM_INSET}） */
    public static final int GRID_X = 61;
    public static final int GRID_Y = 94;
    /** 格距（18x18 格） */
    public static final int GRID_PITCH = 18;
    /** 快捷栏行的格网 y（与主栏第 3 行之间留 4 px 间隙：94 + 2*18 = 130，快捷栏 152） */
    public static final int HOTBAR_GRID_Y = 152;
    /** 槽内物品内缩（规范 §3：16x16 物品在 18x18 格内居中 ⇒ 每边 1 px） */
    public static final int ITEM_INSET = 1;

    /** 2 参构造 = {@link ModMenuTypes#CHIP_SHOP} 的工厂入口（客户端实例无任何服务端状态可初始化） */
    public ChipShopMenu(int containerId, Inventory playerInventory) {
        super(ModMenuTypes.CHIP_SHOP.get(), containerId);

        // ── 主栏 9x3（物品栏槽 9..35）：格网 (61 + 18i, 94 + 18j) + 1 px 内缩 ──
        // 第 0 行（物品栏槽 9..17）
        addSlot(new Slot(playerInventory, 9, GRID_X + ITEM_INSET, GRID_Y + ITEM_INSET));
        addSlot(new Slot(playerInventory, 10, GRID_X + GRID_PITCH + ITEM_INSET, GRID_Y + ITEM_INSET));
        addSlot(new Slot(playerInventory, 11, GRID_X + 2 * GRID_PITCH + ITEM_INSET, GRID_Y + ITEM_INSET));
        addSlot(new Slot(playerInventory, 12, GRID_X + 3 * GRID_PITCH + ITEM_INSET, GRID_Y + ITEM_INSET));
        addSlot(new Slot(playerInventory, 13, GRID_X + 4 * GRID_PITCH + ITEM_INSET, GRID_Y + ITEM_INSET));
        addSlot(new Slot(playerInventory, 14, GRID_X + 5 * GRID_PITCH + ITEM_INSET, GRID_Y + ITEM_INSET));
        addSlot(new Slot(playerInventory, 15, GRID_X + 6 * GRID_PITCH + ITEM_INSET, GRID_Y + ITEM_INSET));
        addSlot(new Slot(playerInventory, 16, GRID_X + 7 * GRID_PITCH + ITEM_INSET, GRID_Y + ITEM_INSET));
        addSlot(new Slot(playerInventory, 17, GRID_X + 8 * GRID_PITCH + ITEM_INSET, GRID_Y + ITEM_INSET));
        // 第 1 行（物品栏槽 18..26）
        addSlot(new Slot(playerInventory, 18, GRID_X + ITEM_INSET, GRID_Y + GRID_PITCH + ITEM_INSET));
        addSlot(new Slot(playerInventory, 19, GRID_X + GRID_PITCH + ITEM_INSET,
                GRID_Y + GRID_PITCH + ITEM_INSET));
        addSlot(new Slot(playerInventory, 20, GRID_X + 2 * GRID_PITCH + ITEM_INSET,
                GRID_Y + GRID_PITCH + ITEM_INSET));
        addSlot(new Slot(playerInventory, 21, GRID_X + 3 * GRID_PITCH + ITEM_INSET,
                GRID_Y + GRID_PITCH + ITEM_INSET));
        addSlot(new Slot(playerInventory, 22, GRID_X + 4 * GRID_PITCH + ITEM_INSET,
                GRID_Y + GRID_PITCH + ITEM_INSET));
        addSlot(new Slot(playerInventory, 23, GRID_X + 5 * GRID_PITCH + ITEM_INSET,
                GRID_Y + GRID_PITCH + ITEM_INSET));
        addSlot(new Slot(playerInventory, 24, GRID_X + 6 * GRID_PITCH + ITEM_INSET,
                GRID_Y + GRID_PITCH + ITEM_INSET));
        addSlot(new Slot(playerInventory, 25, GRID_X + 7 * GRID_PITCH + ITEM_INSET,
                GRID_Y + GRID_PITCH + ITEM_INSET));
        addSlot(new Slot(playerInventory, 26, GRID_X + 8 * GRID_PITCH + ITEM_INSET,
                GRID_Y + GRID_PITCH + ITEM_INSET));
        // 第 2 行（物品栏槽 27..35）
        addSlot(new Slot(playerInventory, 27, GRID_X + ITEM_INSET, GRID_Y + 2 * GRID_PITCH + ITEM_INSET));
        addSlot(new Slot(playerInventory, 28, GRID_X + GRID_PITCH + ITEM_INSET,
                GRID_Y + 2 * GRID_PITCH + ITEM_INSET));
        addSlot(new Slot(playerInventory, 29, GRID_X + 2 * GRID_PITCH + ITEM_INSET,
                GRID_Y + 2 * GRID_PITCH + ITEM_INSET));
        addSlot(new Slot(playerInventory, 30, GRID_X + 3 * GRID_PITCH + ITEM_INSET,
                GRID_Y + 2 * GRID_PITCH + ITEM_INSET));
        addSlot(new Slot(playerInventory, 31, GRID_X + 4 * GRID_PITCH + ITEM_INSET,
                GRID_Y + 2 * GRID_PITCH + ITEM_INSET));
        addSlot(new Slot(playerInventory, 32, GRID_X + 5 * GRID_PITCH + ITEM_INSET,
                GRID_Y + 2 * GRID_PITCH + ITEM_INSET));
        addSlot(new Slot(playerInventory, 33, GRID_X + 6 * GRID_PITCH + ITEM_INSET,
                GRID_Y + 2 * GRID_PITCH + ITEM_INSET));
        addSlot(new Slot(playerInventory, 34, GRID_X + 7 * GRID_PITCH + ITEM_INSET,
                GRID_Y + 2 * GRID_PITCH + ITEM_INSET));
        addSlot(new Slot(playerInventory, 35, GRID_X + 8 * GRID_PITCH + ITEM_INSET,
                GRID_Y + 2 * GRID_PITCH + ITEM_INSET));

        // ── 快捷栏 9x1（物品栏槽 0..8）：格网 y = 152 + 1 px 内缩 ──
        addSlot(new Slot(playerInventory, 0, GRID_X + ITEM_INSET, HOTBAR_GRID_Y + ITEM_INSET));
        addSlot(new Slot(playerInventory, 1, GRID_X + GRID_PITCH + ITEM_INSET, HOTBAR_GRID_Y + ITEM_INSET));
        addSlot(new Slot(playerInventory, 2, GRID_X + 2 * GRID_PITCH + ITEM_INSET,
                HOTBAR_GRID_Y + ITEM_INSET));
        addSlot(new Slot(playerInventory, 3, GRID_X + 3 * GRID_PITCH + ITEM_INSET,
                HOTBAR_GRID_Y + ITEM_INSET));
        addSlot(new Slot(playerInventory, 4, GRID_X + 4 * GRID_PITCH + ITEM_INSET,
                HOTBAR_GRID_Y + ITEM_INSET));
        addSlot(new Slot(playerInventory, 5, GRID_X + 5 * GRID_PITCH + ITEM_INSET,
                HOTBAR_GRID_Y + ITEM_INSET));
        addSlot(new Slot(playerInventory, 6, GRID_X + 6 * GRID_PITCH + ITEM_INSET,
                HOTBAR_GRID_Y + ITEM_INSET));
        addSlot(new Slot(playerInventory, 7, GRID_X + 7 * GRID_PITCH + ITEM_INSET,
                HOTBAR_GRID_Y + ITEM_INSET));
        addSlot(new Slot(playerInventory, 8, GRID_X + 8 * GRID_PITCH + ITEM_INSET,
                HOTBAR_GRID_Y + ITEM_INSET));
    }

    // ────────────────────────────── 菜单基类实现 ──────────────────────────────

    /**
     * Shift-click：只有玩家物品栏 36 格 ⇒ 主栏（0..26）与快捷栏（27..35）对倒，
     * 与 {@code CardShopMenu#quickMoveStack} 的玩家区口径一致（主栏在前、快捷栏在后）。
     */
    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        if (index < 0 || index >= slots.size()) {
            return ItemStack.EMPTY;
        }
        Slot slot = slots.get(index);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack copy = stack.copy();
        boolean fromHotbar = index >= MAIN_SIZE;
        boolean moved = fromHotbar
                ? moveItemStackTo(stack, 0, MAIN_SIZE, false)
                : moveItemStackTo(stack, MAIN_SIZE, PLAYER_SLOT_COUNT, false);
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

    /**
     * 恒 true：本菜单**没有可判距的方块来源**（开屏入口待逻辑批决定，规范 §9），
     * 故不设方块型菜单的距离契约；购买/刷新的二次校验将来在服务端回调里做（服务端权威）。
     */
    @Override
    public boolean stillValid(Player player) {
        return true;
    }
}
