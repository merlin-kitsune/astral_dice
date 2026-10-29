package com.merlinkitsune.astral_dice.event;

import com.merlinkitsune.starengine.combat.PartyRelations;
import com.merlinkitsune.astral_dice.component.ModAttachments;
import com.merlinkitsune.astral_dice.item.ModItems;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.merlinkitsune.starengine.item.CuriosCompat;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 多秘密侦探仲裁(2026-10-10 用户裁决)。
 *
 * <p>规则:
 * <ul>
 *   <li>每个秘密侦探的阶级完全独立(每玩家的 {@code investigation_stage} 附件);</li>
 *   <li>「其它方式触发」秘密侦探专属事件(不存在击杀者)时:取<b>同队内阶段最高</b>的秘密侦探;
 *       并列最高时在这些并列者中<b>随机抽一个</b>作为该事件的<b>触发者</b>;
 *   <li>队内没有任何秘密侦探 ⇒ 该事件不可能出现(池闸门 + 防御性早退)。</li>
 * </ul>
 */
public final class InvestigationArbitration {
    private static final Logger LOGGER = LoggerFactory.getLogger(InvestigationArbitration.class);

    private InvestigationArbitration() {
    }

    /** 同队(含自己)佩戴秘密侦探立牌的玩家。 */
    public static List<Player> teamDetectives(Player triggerer) {
        List<Player> out = new ArrayList<>();
        if (holdsDetectiveSign(triggerer)) {
            out.add(triggerer);
        }
        if (triggerer.getServer() == null) {
            return out;
        }
        for (net.minecraft.server.level.ServerPlayer other : triggerer.getServer().getPlayerList().getPlayers()) {
            if (other != triggerer && holdsDetectiveSign(other) && PartyRelations.isSameTeam(triggerer, other)) {
                out.add(other);
            }
        }
        return out;
    }

    /** 触发者所在队伍内是否存在秘密侦探(公共事件池闸门用)。 */
    public static boolean teamHasSecretDetective(Player triggerer) {
        return !teamDetectives(triggerer).isEmpty();
    }

    /**
     * 「秘密侦探专属事件」对该玩家此刻是否<b>可派发</b> —— 候选池 / 派发器 / 强制结算兜底的<b>唯一</b>判据
     * (2026-10-29 P1 修复)。
     *
     * <p>派发器 {@code AstralEventDispatcher#trigger} 在<b>无击杀者</b>的来源下要求
     * {@link #pickHighestTier} 非空,否则打 {@code AP_EVENT_NO_DETECTIVE} 并 {@code return false}
     * (白丢一次结算);而 {@code pickHighestTier} 本身即以本判据为前置。事件选择界面的候选池
     * {@code AstralEventRegistry#publicPool(Player, AstralEventTrigger)} 与强制结算兜底
     * {@code EventChoiceManager#forceFirst} 也调用本方法 —— 三处共用一个来源,杜绝
     * 「候选里能选到、派发时必然失败」的口径漂移。
     *
     * @return 队内(含自己)至少有一名佩戴秘密侦探立牌的玩家
     */
    public static boolean canDispatchSecretOnly(Player triggerer) {
        return teamHasSecretDetective(triggerer);
    }

    /**
     * 取阶段最高的秘密侦探;并列最高时随机抽一个。
     *
     * @return 抽中的侦探;队内没有任何秘密侦探时返回 {@link Optional#empty()}
     */
    public static Optional<Player> pickHighestTier(Player triggerer) {
        // 2026-10-29 P1 修复:可派发判据与候选池**同源**(canDispatchSecretOnly) —— 派发器
        // 「取不到侦探 ⇒ 白丢一次结算」的早退条件,从此就是候选过滤用的同一个函数。
        if (!canDispatchSecretOnly(triggerer)) {
            return Optional.empty();
        }
        List<Player> detectives = teamDetectives(triggerer);
        if (detectives.isEmpty()) {
            return Optional.empty();
        }
        int max = Integer.MIN_VALUE;
        for (Player p : detectives) {
            max = Math.max(max, ModAttachments.getInvestigationStage(p));
        }
        List<Player> top = new ArrayList<>();
        for (Player p : detectives) {
            if (ModAttachments.getInvestigationStage(p) == max) {
                top.add(p);
            }
        }
        Player chosen = top.size() == 1 ? top.get(0) : top.get(triggerer.getRandom().nextInt(top.size()));
        LOGGER.info("AP_EVENT_TIER_PICK: chosen={}:tier={}:tied={}:candidates={}",
                chosen.getName().getString(), max, top.size(), detectives.size());
        return Optional.of(chosen);
    }

    /** 是否佩戴秘密侦探立牌。 */
    private static boolean holdsDetectiveSign(Player player) {
        var curios = CuriosCompat.getCuriosInventory(player);
        return curios.isPresent() && curios.get()
                .findFirstCurio(s -> s.is(ModItems.BONNIE_SIGN.get())).isPresent();
    }

    /** 便捷:目标是否已被施加「隐匿调查」(已带标记的怪不可再被任何人施加 —— 用户裁决)。 */
    public static boolean alreadyUnderInvestigation(net.minecraft.world.entity.LivingEntity target) {
        return target.hasEffect(com.merlinkitsune.astral_dice.effect.ModEffects.UNDERCOVER_INVESTIGATION.get());
    }

}
