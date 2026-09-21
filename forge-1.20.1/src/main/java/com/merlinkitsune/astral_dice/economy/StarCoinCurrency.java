package com.merlinkitsune.astral_dice.economy;

import com.merlinkitsune.astral_dice.config.ModCommonConfig;
import com.merlinkitsune.astral_dice.item.ModItems;
import com.merlinkitsune.starenginelib.economy.StarEngineEconomy;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * 星币面额与「货币物品 ↔ 钱包余额」的折算入口（消费方侧）。
 *
 * <p><b>分层</b>：钱包**余额**由前置库 starengine_lib 的 {@link StarEngineEconomy} 承载
 * （玩家持久化数据、不受死亡掉落影响）；「星币是物品、面额是多少、物品栏怎么折算」属本模组的
 * 货币语义，留在本类 —— 与库「不持物品概念」的分工一致。
 *
 * <p><b>面额</b>：1 星币 = 1，1 星币袋 = 9（{@link #STAR_COIN_VALUE} / {@link #STAR_COIN_BAG_VALUE}）。
 * 这两个值与 {@code StarCoinHammerChipItem.COINS_PER_BAG} 必须同步；改动面额时还要核对既有的
 * 「星光 ↔ 星币」换算（{@code ResourceConversion.STARLIGHT_PER_COIN}）。
 *
 * <p><b>口径</b>：
 * <ul>
 *   <li>存入（{@link #depositAllFromInventory}）= 物品栏 36 格 + **副手**，全部折算后物品被移除；</li>
 *   <li>取出（{@link #withdraw}）= 受**余额**与**物品栏剩余空间**双重约束，任一不足即零改动；</li>
 *   <li>吸收（{@link #tryAbsorbIntoWallet}）= 供「获得星币」路径调用的钩子，决定这一叠货币是
 *       直接进钱包还是照常进物品栏。</li>
 * </ul>
 *
 * <p>本类不含任何客户端状态：两个开关读的是 COMMON 配置，服务端与客户端各读本地文件
 * （按钮显示在客户端、账本写入在服务端，单人游戏下两侧一致）。
 */
public final class StarCoinCurrency {

    /** 星币面额。 */
    public static final long STAR_COIN_VALUE = 1L;
    /** 星币袋面额（1 袋 = 9 枚星币）。 */
    public static final long STAR_COIN_BAG_VALUE = 9L;

    private StarCoinCurrency() {
    }

    // === 面额 ===

    /** 物品栈的面额价值（非货币物品返回 0）。 */
    public static long valueOf(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return 0L;
        if (stack.is(ModItems.STAR_COIN.get())) return STAR_COIN_VALUE;
        if (stack.is(ModItems.STAR_COIN_BAG.get())) return STAR_COIN_BAG_VALUE;
        return 0L;
    }

    /** 物品的面额价值（非货币物品返回 0）。 */
    public static long valueOfItem(Item item) {
        if (item == null) return 0L;
        if (item == ModItems.STAR_COIN.get()) return STAR_COIN_VALUE;
        if (item == ModItems.STAR_COIN_BAG.get()) return STAR_COIN_BAG_VALUE;
        return 0L;
    }

    /** 是否为可存入钱包的货币物品（星币 / 星币袋）。 */
    public static boolean isCurrency(ItemStack stack) {
        return valueOf(stack) > 0L;
    }

    // === 开关（COMMON 配置；配置未就绪时退回默认值，不让渲染/事件路径因配置异常而崩） ===

    /** 星币钱包是否启用（默认 true）。 */
    public static boolean isWalletEnabled() {
        try {
            return ModCommonConfig.ENABLE_STAR_COIN_WALLET.get();
        } catch (Throwable ignored) {
            return true;
        }
    }

    /** 「获得星币时直接入钱包」是否生效（**仅在钱包启用时**有意义，默认 true）。 */
    public static boolean isAutoDepositEnabled() {
        if (!isWalletEnabled()) return false;
        try {
            return ModCommonConfig.DEPOSIT_STAR_COIN_ON_OBTAIN.get();
        } catch (Throwable ignored) {
            return true;
        }
    }

    // === 物品栏口径 ===

    /** 物品栏（36 格）与副手合计的货币价值（不读钱包；用于提示、测试与存档核对）。 */
    public static long countCarriedValue(Player player) {
        if (player == null) return 0L;
        long total = 0L;
        Inventory inv = player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack stack = inv.getItem(i);
            long unit = valueOf(stack);
            if (unit > 0L) total += unit * stack.getCount();
        }
        ItemStack offhand = player.getOffhandItem();
        long offUnit = valueOf(offhand);
        if (offUnit > 0L) total += offUnit * offhand.getCount();
        return total;
    }

    /**
     * 把物品栏（36 格）与副手的**全部**星币与星币袋折算存入钱包，并把对应物品移除。
     *
     * <p>顺序是「**先入账、后清物**」：账本写入失败时物品原样留在物品栏（绝不出现
     * 「东西没了钱也没到」的丢失窗口）。仅服务端有效，客户端调用返回 0。
     *
     * @return 实际存入的价值（0 = 物品栏与副手都没有星币/星币袋，或账本不可用）
     */
    public static long depositAllFromInventory(Player player) {
        if (player == null || player.level().isClientSide()) return 0L;
        if (!StarEngineEconomy.isAvailable()) return 0L;

        long total = countCarriedValue(player);
        if (total <= 0L) return 0L;
        if (!StarEngineEconomy.deposit(player, total)) return 0L;

        Inventory inv = player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            if (isCurrency(inv.getItem(i))) {
                inv.setItem(i, ItemStack.EMPTY);
            }
        }
        if (isCurrency(player.getOffhandItem())) {
            player.setItemSlot(EquipmentSlot.OFFHAND, ItemStack.EMPTY);
        }
        return total;
    }

    /**
     * 从钱包取出货币物品（「兑换（取出）」按钮的服务端口径）。
     *
     * <p>受**双重约束**：钱包余额能换的枚数、物品栏还能放下的枚数（空间口径与
     * {@code Inventory#add} 一致，= 主物品栏 36 格；副手不参与取出）。
     * 任一不足即零改动；余额先扣、若实际投放少于取出量则差额按原额退回（绝不吞余额）。
     *
     * @param item     目标货币物品（星币 / 星币袋）
     * @param maxCount 本次最多取出多少枚（≤0 = 不限制，取到余额或空间用尽为止）
     * @return 实际取出的枚数
     */
    public static int withdraw(Player player, Item item, int maxCount) {
        if (player == null || player.level().isClientSide()) return 0;
        long unit = valueOfItem(item);
        if (unit <= 0L) return 0;
        if (!StarEngineEconomy.isAvailable()) return 0;

        long byBalance = StarEngineEconomy.getBalance(player) / unit;
        if (byBalance <= 0L) return 0;
        long limit = maxCount > 0 ? Math.min(byBalance, maxCount) : byBalance;
        long count = Math.min(limit, countSpace(player, item));
        if (count <= 0L) return 0;
        if (!StarEngineEconomy.withdraw(player, unit * count)) return 0;

        int wanted = (int) count;
        ItemStack stack = new ItemStack(item, wanted);
        player.getInventory().add(stack);
        int placed = wanted - stack.getCount();
        if (placed < wanted) {
            StarEngineEconomy.deposit(player, unit * (wanted - placed));
        }
        return placed;
    }

    /** 主物品栏（36 格）还能容纳多少个该物品（考虑堆叠上限与既有同类堆）。 */
    public static int countSpace(Player player, Item item) {
        if (player == null || item == null) return 0;
        int max = new ItemStack(item).getMaxStackSize();
        int space = 0;
        Inventory inv = player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack stack = inv.getItem(i);
            if (stack.isEmpty()) {
                space += max;
            } else if (stack.is(item)) {
                space += Math.max(0, max - stack.getCount());
            }
        }
        return space;
    }

    // === 「获得星币」钩子 ===

    /**
     * 吸收钩子：钱包启用且「获得即入钱包」打开时，把这一叠货币折算入钱包并**清空该栈**。
     *
     * <p>调用方（发放漏斗 {@code ResourceConversion.giveItem}、拾取事件）依据返回值决定后续：
     * 返回 true 时**不得**再把它放进物品栏，否则等于白得一份星币。
     *
     * @return true = 已折算入钱包；false = 未处理（开关关闭、非货币物品或账本不可用），按原样继续
     */
    public static boolean tryAbsorbIntoWallet(Player player, ItemStack stack) {
        if (player == null || stack == null || stack.isEmpty()) return false;
        if (player.level().isClientSide()) return false;
        if (!isAutoDepositEnabled()) return false;
        if (!StarEngineEconomy.isAvailable()) return false;
        long unit = valueOf(stack);
        if (unit <= 0L) return false;
        long total = unit * stack.getCount();
        if (total <= 0L) return false;
        if (!StarEngineEconomy.deposit(player, total)) return false;
        stack.setCount(0);
        return true;
    }
}
