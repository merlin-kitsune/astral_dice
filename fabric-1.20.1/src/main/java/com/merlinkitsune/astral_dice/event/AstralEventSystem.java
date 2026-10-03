package com.merlinkitsune.astral_dice.event;
import com.merlinkitsune.astral_dice.compat.curios.CuriosApi;
import com.merlinkitsune.astral_dice.network.ModNetwork;

import com.merlinkitsune.astral_dice.AstralDiceMod;
import com.merlinkitsune.starenginelib.component.GameplayConstants;
import com.merlinkitsune.astral_dice.item.ModItems;
import com.merlinkitsune.astral_dice.item.card.LivingPageItem;
import com.merlinkitsune.astral_dice.item.chip.VitaminPillChipItem;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import com.merlinkitsune.astral_dice.combat.PartyRelations;
/**
 * 事件系统:事件触发后的统一附加效果。
 * 事件本体由各立牌自行实现(大侦探主动的随机事件、秘密侦探击杀「隐匿调查」目标触发的调查阶段);
 * 本类只负责在事件触发后应用立牌增益(大侦探 +3 星币)与调查员立牌被动(活体书页)。
 */
public final class AstralEventSystem {
    private AstralEventSystem() {
    }

    /** 事件效果的作用半径(格)。2026-10-03 用户裁决:取代旧口径的「32 格 + 仅队友」。 */
    public static final double EVENT_TARGET_RADIUS = 64.0D;

    // 事件触发后的统一附加效果:立牌被动(如大侦探 +3 星币)与调查员立牌联动
    public static void onEventTriggered(Player triggerer, String eventId) {
        if (triggerer.level().isClientSide()) return;
        // 2026-10-03 用户裁决:事件效果按「队伍 + 64 格」广播(口径见 PartyRelations#collectEventTargets)——
        //   触发者有队伍 = 全队 ∪ 64 格内非队友;无队伍 = 全部无队伍玩家 ∪ 64 格内所有玩家。
        java.util.List<Player> targets = PartyRelations.collectEventTargets(triggerer, EVENT_TARGET_RADIUS);
        applySignBuffs(targets);
        applyRinSignPassive(triggerer, eventId, targets);
    }

    // 调查阶段事件触发时的附加效果(该事件属于事件系统):大侦探立牌 +3 星币、调查员立牌被动
    public static void triggerInvestigationEvent(Player triggerer) {
        onEventTriggered(triggerer, "investigation");
    }

    // 立牌增益挂钩:事件影响范围内,持有所需立牌的玩家各获得对应增益。
    private static void applySignBuffs(java.util.List<Player> targets) {
        // 大侦探立牌:受事件影响且持有该立牌者各获得 3 星币(2026-10-03 起按事件目标集合发放)
        for (Player player : targets) {
            if (holdsSign(player, ModItems.FANNY_SIGN.get())) {
                giveStarCoins(player, 3);
            }
        }
    }

    // 调查员立牌被动:自身触发事件(击杀"隐匿调查"目标),或**受事件影响**
    // (目标集合由 PartyRelations#collectEventTargets 给出:有队伍 = 全队 ∪ 64 格内非队友;
    //  无队伍 = 全部无队伍玩家 ∪ 64 格内所有玩家)后,佩戴调查员立牌的玩家各获得一张"活体书页"。
    // 半径 = EVENT_TARGET_RADIUS(64 格);兼容入口:未指定事件 ID 时按默认签名去重(供外部直接调用)。
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
        applyRinSignPassive(triggerer, eventId,
                PartyRelations.collectEventTargets(triggerer, EVENT_TARGET_RADIUS));
    }

    /**
     * 带「已算好的目标集合」的被动触发(由 {@link #onEventTriggered} 传入,避免重复计算)。
     *
     * <p>目标集合口径见 {@link PartyRelations#collectEventTargets} —— 2026-10-03 用户裁决:
     * 有队伍 = 全队 ∪ 64 格内非队友;无队伍 = 全部无队伍玩家 ∪ 64 格内所有玩家
     * (旧口径为「自身 ∪ 32 格 ∪ 队内,无队伍时全服」)。
     *
     * <p>去重规则不变:同一玩家(触发者)发出的同一事件 ID,在 2 tick 窗口内被重复分发时
     * (如多立牌槽导致 onKill 多次调用),每个佩戴调查员立牌的玩家只获得一次"活体书页"。
     */
    public static void applyRinSignPassive(Player triggerer, String eventId, java.util.List<Player> targets) {
        if (!(triggerer.level() instanceof ServerLevel)) return;
        long now = triggerer.level().getGameTime();
        String signature = triggerer.getUUID() + "|" + eventId;
        for (Player sp : targets) {
            if (!holdsSign(sp, ModItems.RIN_SIGN.get())) continue;
            // 同一事件 2 tick 窗口内已给过 → 跳过(防多槽重复分发)
            if (signature.equals(com.merlinkitsune.astral_dice.component.ModAttachments.getRinGiftSignature(sp))
                    && now - com.merlinkitsune.astral_dice.component.ModAttachments.getRinGiftTick(sp) <= 2) {
                continue;
            }
            com.merlinkitsune.astral_dice.component.ModAttachments.setRinGiftSignature(sp, signature);
            com.merlinkitsune.astral_dice.component.ModAttachments.setRinGiftTick(sp, now);
            // 活体书页为专属牌,绑定获得者 —— 走唯一入口(见 LivingPageItem#createFor 的 javadoc)
            giveItem(sp, LivingPageItem.createFor(sp));
        }
    }

    private static boolean holdsSign(Player player, net.minecraft.world.item.Item signItem) {
        var curios = CuriosApi.getCuriosInventory(player);
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
