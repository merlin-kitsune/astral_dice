package com.merlinkitsune.astral_dice.economy;

import com.merlinkitsune.astral_dice.audio.ModSounds;
import com.merlinkitsune.astral_dice.audio.SoundPlayback;
import com.merlinkitsune.astral_dice.item.ModItems;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * 星币钱包的**服务端动作执行器** —— 客户端点击意图（C2S 包）的唯一落点。
 *
 * <p>分工：客户端按钮只负责「把点击意图发出去」，**一切判定都在这里**（功能开关、余额是否够、
 * 物品栏是否有空间、物品栏里到底有什么）。因此客户端无法通过伪造包来多拿星币 ——
 * 与既有的立牌主动技能 / 卡牌栏打开同一条链路（服务端权威）。
 *
 * <p>动作与按钮的对应关系（详见 {@code client/gui/StarCoinWalletButtons}）：
 * <ul>
 *   <li>星币钱包按钮（左上角）→ {@link Action#DEPOSIT_ALL}：把物品栏 36 格 + 副手的全部
 *       星币与星币袋折算存入钱包（星币袋按 9 折算）；</li>
 *   <li>星币按钮 → {@link Action#WITHDRAW_COIN_ONE} / {@link Action#WITHDRAW_COIN_ALL}；</li>
 *   <li>星币袋按钮 → {@link Action#WITHDRAW_BAG_ONE} / {@link Action#WITHDRAW_BAG_ALL}。</li>
 * </ul>
 * 「兑换」= **取出**（把钱包余额换成实体货币物品放回物品栏）；「存入」只由钱包按钮一个入口承担。
 */
public final class StarCoinWalletActions {

    /**
     * 客户端按钮点击意图。
     *
     * <p>⚠️ 序数会被写进网络包（见 C2S 载荷），**禁止重排或删除已有分量** ——
     * 新动作只能追加在末尾。
     */
    public enum Action {
        /** 钱包按钮：物品栏 + 副手的全部星币/星币袋折算存入。 */
        DEPOSIT_ALL,
        /** 星币按钮（左键）：取出 1 枚星币。 */
        WITHDRAW_COIN_ONE,
        /** 星币按钮（Shift + 左键）：取出尽可能多的星币。 */
        WITHDRAW_COIN_ALL,
        /** 星币袋按钮（左键）：取出 1 个星币袋。 */
        WITHDRAW_BAG_ONE,
        /** 星币袋按钮（Shift + 左键）：取出尽可能多的星币袋。 */
        WITHDRAW_BAG_ALL;

        /** 按网络序数还原动作；越界返回 {@code null}（服务端静默丢弃非法包）。 */
        public static Action byOrdinal(int ordinal) {
            Action[] all = values();
            return ordinal >= 0 && ordinal < all.length ? all[ordinal] : null;
        }
    }

    private StarCoinWalletActions() {
    }

    /** 服务端执行一次点击意图；钱包功能关闭时一律不动作（也不回提示）。 */
    public static void execute(ServerPlayer player, Action action) {
        if (player == null || action == null) return;
        if (!StarCoinCurrency.isWalletEnabled()) return;
        switch (action) {
            case DEPOSIT_ALL -> {
                long deposited = StarCoinCurrency.depositAllFromInventory(player);
                if (deposited > 0L) {
                    // 存钱音效:只在真的存进去时响(空操作保持静默,与"取不出"的处理一致)
                    SoundPlayback.playTo(player, ModSounds.WALLET_DEPOSIT.get());
                    send(player, Component.translatable(
                            "msg.astral_dice.star_coin_wallet_deposited", deposited));
                } else {
                    send(player, Component.translatable(
                            "msg.astral_dice.star_coin_wallet_nothing_to_deposit"));
                }
            }
            case WITHDRAW_COIN_ONE -> withdraw(player, ModItems.STAR_COIN.get(), 1);
            case WITHDRAW_COIN_ALL -> withdraw(player, ModItems.STAR_COIN.get(), 0);
            case WITHDRAW_BAG_ONE -> withdraw(player, ModItems.STAR_COIN_BAG.get(), 1);
            case WITHDRAW_BAG_ALL -> withdraw(player, ModItems.STAR_COIN_BAG.get(), 0);
        }
    }

    /** 取出并给出反馈；取不出（余额不足或物品栏没空间）时提示原因而不是静默。 */
    private static void withdraw(ServerPlayer player, Item item, int maxCount) {
        int taken = StarCoinCurrency.withdraw(player, item, maxCount);
        if (taken > 0) {
            // 取钱音效:星币与星币袋各一条;取不出时不响(与失败提示同条件)
            SoundPlayback.playTo(player, (item == ModItems.STAR_COIN_BAG.get()
                    ? ModSounds.COIN_BAG_WITHDRAW : ModSounds.COIN_WITHDRAW).get());
            send(player, Component.translatable("msg.astral_dice.star_coin_wallet_withdrawn",
                    new ItemStack(item).getHoverName(), taken));
        } else {
            send(player, Component.translatable("msg.astral_dice.star_coin_wallet_withdraw_failed"));
        }
    }

    private static void send(ServerPlayer player, Component message) {
        // 26.1.2 平台差异:Player#displayClientMessage(text, true) 已改名 ⇒ sendOverlayMessage(Component)
        player.sendOverlayMessage(message);
    }
}
