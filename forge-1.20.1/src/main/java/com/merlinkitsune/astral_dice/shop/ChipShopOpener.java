package com.merlinkitsune.astral_dice.shop;

import com.merlinkitsune.astral_dice.network.ModNetwork;
import com.merlinkitsune.starengine.component.GameplayConstants;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 筹码商店开店入口（判据正本 = {@code docs/chip-shop-logic-plan.md} §3「进店补抽」与 §5「开店前置」）。
 *
 * <p><b>开店前置**三项**拒绝</b>（R6，用户裁决）：① 池已**全拥有**（没有可卖的筹码）；
 * ② 物品栏 36 格（不含副手）**已满**（买了也放不下）；③ **购买冷却中**（需求 4：上次购买后 5:00 内禁止使用）。
 * 三者都只在 actionbar 给红字、**不开店**。
 *
 * <p><b>进店补抽</b>（唯一入口）：仅当 {@link ChipShopState#needsReroll(Player)} 为真
 * （从未抽过 / 候选含已拥有 / 候选引用了未知 id）才掷一批并落盘。**免费**、且**与界面开关无关地幂等** ——
 * 因此「未购买退出再进店」候选逐字不变（用户裁决 Q9/R16）。
 *
 * <p><b>与菜单的分工</b>：本类只负责「能不能开 + 要不要补抽 + 开」；
 * 价格 / 扣款 / 关店 / 重抽一律在 {@link ChipShopMenu} 的服务端回调里（服务端权威）。
 * <p><b>平台差异（forge-1.20.1）</b>：拒绝提示不经过 1.21.1 的 {@code PacketDistributor} + {@code ActionBarPayload}，
 * 改走本线既有通道 {@code network/ModNetwork#sendToPlayer} + {@code ModNetwork.ActionBarMessage}
 * （与 {@code shop/CardShopMenu#actionBar}、{@code item/CurioSlotUtil} 同一写法）。
 */
public final class ChipShopOpener {

    private static final Logger LOGGER = LoggerFactory.getLogger(ChipShopOpener.class);

    /** 玩家物品栏参检格数（0..8 快捷栏 + 9..35 主物品栏；不含副手） */
    private static final int INVENTORY_SLOTS = 36;

    private ChipShopOpener() {
    }

    /** 方块 / 主动技能统一入口：校验 → 补抽 → 开菜单（服务端权威；客户端调用直接返回） */
    public static void open(Player player) {
        if (player.level().isClientSide() || !(player instanceof ServerPlayer serverPlayer)) {
            return;
        }
        if (ChipShopRoller.ownsEverything(player)) {
            deny(player, "owned_all", "msg.astral_dice.chip_shop_owned_all");
            return;
        }
        if (hasNoRoom(player)) {
            deny(player, "inventory_full", "msg.astral_dice.chip_shop_inventory_full");
            return;
        }
        if (ChipShopState.get(player).inCooldown(player.level().getGameTime())) {
            deny(player, "cooldown", "msg.astral_dice.chip_shop_cooldown");
            return;
        }
        ensureOffers(player);
        serverPlayer.openMenu(new SimpleMenuProvider(
                (containerId, inventory, p) -> new ChipShopMenu(containerId, inventory),
                Component.translatable("gui.astral_dice.chip_shop")));
        LOGGER.info("[astral_dice] CHIP_SHOP_OPEN player={} offers={} purchased={} refreshes={}",
                player.getName().getString(), ChipShopState.get(player).offersKey(),
                ChipShopState.get(player).purchasedCount(), ChipShopState.get(player).refreshesLeft());
    }

    /**
     * 进店补抽（**免费**，计划表 §3 的入口①）：只在 {@link ChipShopState#needsReroll(Player)} 为真时掷一批。
     *
     * <p>包级可见：{@link ChipShopMenu} 不调用它（菜单构造只读），仅供本类与测试 / 探针使用。
     */
    static void ensureOffers(Player player) {
        ChipShopState state = ChipShopState.get(player);
        if (!state.needsReroll(player)) {
            return;
        }
        ChipShopState next = state.withOffers(ChipShopRoller.roll(player, state.lastCategory().orElse(null)));
        ChipShopState.store(player, next);
        LOGGER.info("[astral_dice] CHIP_SHOP_REROLL_ON_ENTER player={} offers={} purchased={}",
                player.getName().getString(), next.offersKey(), next.purchasedCount());
    }

    /** 物品栏 36 格是否已满（购买落点 = 第一个空格 ⇒ 全满即无处可放） */
    private static boolean hasNoRoom(Player player) {
        for (int i = 0; i < INVENTORY_SLOTS; i++) {
            if (player.getInventory().getItem(i).isEmpty()) {
                return false;
            }
        }
        return true;
    }

    /** 开店拒绝：actionbar 红字 + 机器行（实机取证时一眼可见「为什么没开店」） */
    private static void deny(Player player, String reasonTag, String key) {
        if (player instanceof ServerPlayer serverPlayer) {
            ModNetwork.sendToPlayer(serverPlayer, new ModNetwork.ActionBarMessage(
                    Component.translatable(key).withStyle(ChatFormatting.RED),
                    GameplayConstants.ACTIONBAR_DURATION_TICKS));
        }
        LOGGER.info("[astral_dice] CHIP_SHOP_OPEN_DENIED player={} reason={}",
                player.getName().getString(), reasonTag);
    }
}
