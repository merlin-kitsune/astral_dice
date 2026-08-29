package com.merlinkitsune.astral_dice.event;

import com.merlinkitsune.astral_dice.AstralDiceMod;
import com.merlinkitsune.astral_dice.component.GameplayConstants;
import com.merlinkitsune.astral_dice.item.card.ExclusiveCardUtil;
import com.merlinkitsune.astral_dice.item.ModItems;
import com.merlinkitsune.astral_dice.item.chip.VitaminPillChipItem;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import com.merlinkitsune.astral_dice.item.CuriosCompat;
import top.theillusivec4.curios.api.CuriosApi;

import java.util.Optional;
import com.merlinkitsune.astral_dice.network.ModNetwork.ActionBarMessage;

/**
 * 事件系统:触发事件并应用立牌增益。
 * 立牌可注册事件类型,并可通过 trigger 触发事件;触发后为持有特定立牌的玩家提供特定增益。
 */
public final class AstralEventSystem {
    private AstralEventSystem() {
    }

    // 触发事件:收集目标 → 应用事件效果 → 触发提示 → 应用立牌增益
    public static void trigger(Player player, AstralEventType type) {
        if (player.level().isClientSide()) return;
        EventContext context = new EventContext(player, EventTargetCollector.collectTargets(player));
        type.trigger(context);
        notifyEventTriggered(player, type.id());
        onEventTriggered(player, type.id().getPath());
    }

    // 事件触发后的统一附加效果:立牌被动(如大侦探 +3 星币)与调查员立牌联动
    public static void onEventTriggered(Player triggerer, String eventId) {
        if (triggerer.level().isClientSide()) return;
        applySignBuffs(triggerer);
        applyRinSignPassive(triggerer, eventId);
    }

    // 自定义 actionbar(5s+1s淡出):xxx玩家触发了:xxx事件
    private static void notifyEventTriggered(Player triggerer, ResourceLocation eventId) {
        if (!(triggerer instanceof net.minecraft.server.level.ServerPlayer serverPlayer)) return;
        Component msg = Component.translatable("msg.astral_dice.event_triggered",
                triggerer.getDisplayName(),
                Component.translatable("event.astral_dice." + eventId.getPath()))
                .withStyle(ChatFormatting.YELLOW);
        com.merlinkitsune.astral_dice.network.ModNetwork.ActionBarMessage payload =
                new com.merlinkitsune.astral_dice.network.ModNetwork.ActionBarMessage(msg,
                        GameplayConstants.ACTIONBAR_DURATION_TICKS);
        com.merlinkitsune.astral_dice.network.ModNetwork.sendToPlayer(serverPlayer, payload);
    }

    // 通过 modid 命名空间下的 ID 触发事件
    public static void trigger(Player player, String eventId) {
        Optional<AstralEventType> type = Optional.ofNullable(
                AstralEvents.get(new ResourceLocation(AstralDiceMod.MODID, eventId)));
        type.ifPresent(t -> trigger(player, t));
    }

    // 调查阶段事件触发时的附加效果(该事件属于事件系统):大侦探立牌 +3 星币、调查员立牌被动
    public static void triggerInvestigationEvent(Player triggerer) {
        onEventTriggered(triggerer, "investigation");
    }

    // 立牌增益挂钩:触发事件后,持有特定立牌的玩家获得特定增益。
    private static void applySignBuffs(Player player) {
        // 大侦探立牌:自身触发事件后获得 3 星币
        if (holdsSign(player, ModItems.FANNY_SIGN.get())) {
            giveStarCoins(player, 3);
        }
        // TODO: 其他立牌增益
    }

    // 调查员立牌被动:自身触发事件(击杀"隐匿调查"目标),或受到事件影响
    // (大侦探触发事件影响到调查员 / 周围 32 格或团队内有人触发"调查阶段")后,
    // 佩戴调查员立牌的玩家获得一张"活体书页"。
    // 影响范围 32 格与团队判定为硬编码(不再走配置常量)。
    // 兼容入口:未指定事件 ID 时按默认签名去重(供外部直接调用)。
    public static void applyRinSignPassive(Player triggerer) {
        applyRinSignPassive(triggerer, "sign_effect");
    }

    /**
     * 带事件 ID 的被动触发。
     *
     * <p>去重规则:同一玩家(触发者)发出的同一事件 ID,在 2 tick 窗口内被重复分发时
     * (如多立牌槽导致 onKill 多次调用),每个佩戴调查员立牌的玩家只获得一次"活体书页",
     * 避免"1 次事件导致重复给牌"。不同事件 ID / 不同触发者 / 超过窗口的真实重复不受影响。
     */
    public static void applyRinSignPassive(Player triggerer, String eventId) {
        if (!(triggerer.level() instanceof ServerLevel serverLevel)) return;
        long now = serverLevel.getGameTime();
        String signature = triggerer.getUUID() + "|" + eventId;
        for (ServerPlayer sp : serverLevel.players()) {
            if (!holdsSign(sp, ModItems.RIN_SIGN.get())) continue;
            // 范围 32 格(硬编码)
            boolean inRange = sp.distanceToSqr(triggerer) <= 32 * 32;
            // 团队判定(硬编码:计入同队)
            boolean team = sp.getTeam() != null && sp.getTeam() == triggerer.getTeam();
            if (sp == triggerer || inRange || team) {
                // 同一事件 2 tick 窗口内已给过 → 跳过(防多槽重复分发)
                if (signature.equals(com.merlinkitsune.astral_dice.component.ModAttachments.getRinGiftSignature(sp))
                        && now - com.merlinkitsune.astral_dice.component.ModAttachments.getRinGiftTick(sp) <= 2) {
                    continue;
                }
                com.merlinkitsune.astral_dice.component.ModAttachments.setRinGiftSignature(sp, signature);
                com.merlinkitsune.astral_dice.component.ModAttachments.setRinGiftTick(sp, now);
                // 活体书页为专属牌,绑定获得者
                ItemStack page = new ItemStack(ModItems.LIVING_BOOK_PAGE.get());
                ExclusiveCardUtil.setOwner(page, sp);
                giveItem(sp, page);
            }
        }
    }

    private static boolean holdsSign(Player player, net.minecraft.world.item.Item signItem) {
        var curios = CuriosCompat.getCuriosInventory(player);
        return curios.isPresent() && curios.get().findFirstCurio(s -> s.is(signItem)).isPresent();
    }

    private static void giveStarCoins(Player player, int count) {
        giveItem(player, new ItemStack(ModItems.STAR_COIN.get(), count));
    }

    private static void giveItem(Player player, ItemStack item) {
        if (ModItems.isCardItem(item)) {
            VitaminPillChipItem.giveCard(player, item);
        } else if (!player.getInventory().add(item)) {
            player.drop(item, false);
        }
    }
}
