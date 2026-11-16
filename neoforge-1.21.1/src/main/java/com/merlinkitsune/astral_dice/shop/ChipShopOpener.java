package com.merlinkitsune.astral_dice.shop;

import com.merlinkitsune.astral_dice.network.ActionBarPayload;
import com.merlinkitsune.starengine.component.GameplayConstants;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.PacketDistributor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 筹码商店开店入口（判据正本 = {@code docs/chip-shop-logic-plan.md} §3「进店补抽」与 §5「开店前置」）。
 *
 * <p><b>开店前置**三项**拒绝</b>（R6，用户裁决）：① 池已**全拥有**（没有可卖的筹码）；
 * ② 物品栏 36 格（不含副手）**已满**（买了也放不下）；③ **购买冷却中**（需求 4：上次购买后 5:00 内禁止使用）。
 * 三者都只在 actionbar 给红字、**不开店**。
 *
 * <p><b>进店补抽</b>（唯一入口）：仅当 {@link ChipShopState#needsReroll(net.minecraft.world.entity.LivingEntity)} 为真
 * （从未抽过 / 候选含已拥有 / 候选引用了未知 id）才掷一批并落盘。**免费**、且**与界面开关无关地幂等** ——
 * 因此「未购买退出再进店」候选逐字不变（用户裁决 Q9/R16）。
 *
 * <p><b>类玩家生物</b>（2026-11-09）：附属注册的生物没有 GUI，走 {@link #openFor(LivingEntity)}：
 * 同样做三项前置 + 进店补抽，但只把候选准备好并打 {@code CHIP_SHOP_READY} 机器行，
 * 不打开菜单；购买由 {@link ChipShopService#purchase(LivingEntity, int)} 完成。
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
        if (!prepare(player)) {
            return;
        }
        serverPlayer.openMenu(new SimpleMenuProvider(
                (containerId, inventory, p) -> new ChipShopMenu(containerId, inventory),
                Component.translatable("gui.astral_dice.chip_shop")));
        LOGGER.info("[astral_dice] CHIP_SHOP_OPEN player={} offers={} purchased={} refreshes={}",
                player.getName().getString(), ChipShopState.get(player).offersKey(),
                ChipShopState.get(player).purchasedCount(), ChipShopState.get(player).refreshesLeft());
    }

    /**
     * 类玩家生物的无界面开店：前置校验 + 进店补抽；玩家会一并打开菜单。
     *
     * @return {@code true} 表示候选已就绪（可直接调 {@link ChipShopService#purchase}）
     */
    public static boolean openFor(LivingEntity holder) {
        if (holder == null || holder.level().isClientSide()) {
            return false;
        }
        if (!prepare(holder)) {
            return false;
        }
        if (holder instanceof ServerPlayer serverPlayer) {
            serverPlayer.openMenu(new SimpleMenuProvider(
                    (containerId, inventory, p) -> new ChipShopMenu(containerId, inventory),
                    Component.translatable("gui.astral_dice.chip_shop")));
            LOGGER.info("[astral_dice] CHIP_SHOP_OPEN player={} offers={} purchased={} refreshes={}",
                    holder.getName().getString(), ChipShopState.get(holder).offersKey(),
                    ChipShopState.get(holder).purchasedCount(), ChipShopState.get(holder).refreshesLeft());
        } else {
            LOGGER.info("[astral_dice] CHIP_SHOP_READY holder={} offers={} purchased={} refreshes={}",
                    holder.getName().getString(), ChipShopState.get(holder).offersKey(),
                    ChipShopState.get(holder).purchasedCount(), ChipShopState.get(holder).refreshesLeft());
        }
        return true;
    }

    /** 三项开店前置 + 进店补抽；返回 {@code false} 表示已拒绝（并已发 actionbar / 机器行）。 */
    private static boolean prepare(LivingEntity holder) {
        if (ChipShopRoller.ownsEverything(holder)) {
            deny(holder, "owned_all", "msg.astral_dice.chip_shop_owned_all");
            return false;
        }
        if (hasNoRoom(holder)) {
            deny(holder, "inventory_full", "msg.astral_dice.chip_shop_inventory_full");
            return false;
        }
        if (ChipShopState.get(holder).inCooldown(holder.level().getGameTime())) {
            deny(holder, "cooldown", "msg.astral_dice.chip_shop_cooldown");
            return false;
        }
        ensureOffers(holder);
        return true;
    }

    /**
     * 进店补抽（**免费**，计划表 §3 的入口①）：只在 {@link ChipShopState#needsReroll(LivingEntity)} 为真时掷一批。
     *
     * <p>公开给服务面（{@link ChipShopService#prepareOffers(LivingEntity)}）与测试 / 探针使用。
     */
    public static void ensureOffers(LivingEntity holder) {
        ChipShopState state = ChipShopState.get(holder);
        if (!state.needsReroll(holder)) {
            return;
        }
        ChipShopState next = state.withOffers(ChipShopRoller.roll(holder, state.lastCategory().orElse(null)),
                ChipShopRoller.diceKey(holder));
        ChipShopState.store(holder, next);
        LOGGER.info("[astral_dice] CHIP_SHOP_REROLL_ON_ENTER holder={} offers={} purchased={}",
                holder.getName().getString(), next.offersKey(), next.purchasedCount());
    }

    /** 玩家物品栏 36 格是否已满 —— 开店前预检。非玩家没有 36 格容器模型，交付时兜底掉地，故不拦。 */
    private static boolean hasNoRoom(LivingEntity holder) {
        if (!(holder instanceof Player player)) {
            return false;
        }
        for (int i = 0; i < INVENTORY_SLOTS; i++) {
            if (player.getInventory().getItem(i).isEmpty()) {
                return false;
            }
        }
        return true;
    }

    /** 开店拒绝：actionbar 红字（玩家）+ 机器行（实机取证时一眼可见「为什么没开店」） */
    private static void deny(LivingEntity holder, String reasonTag, String key) {
        if (holder instanceof ServerPlayer serverPlayer) {
            PacketDistributor.sendToPlayer(serverPlayer, new ActionBarPayload(
                    Component.translatable(key).withStyle(ChatFormatting.RED),
                    GameplayConstants.ACTIONBAR_DURATION_TICKS));
        }
        LOGGER.info("[astral_dice] CHIP_SHOP_OPEN_DENIED holder={} reason={}",
                holder.getName().getString(), reasonTag);
    }
}
