package com.merlinkitsune.astral_dice.item.sign;

import net.minecraft.world.entity.LivingEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 「给牌」事件的中立钩子。
 *
 * <p>上游把「让对方获得卡牌」的唯一漏斗收敛在 {@code VitaminPillChipItem#giveCard}，
 * 被动效果（例：少主立牌「双赢」）订阅本钩子即可同时覆盖主动发卡与「你有我有」。
 *
 * <p>发牌方放宽到 {@link LivingEntity}：非玩家发牌（注册生物 / 未知来源）也会进入钩子并打
 * {@code AP_CARD_GIVEN} 锚点；是否结算由订阅者按自身语义决定。
 */
public final class CardGivenHooks {

    private static final Logger LOGGER = LoggerFactory.getLogger(CardGivenHooks.class);

    /** 订阅者实现本接口并注册。 */
    public interface Listener {
        void onCardGiven(LivingEntity giver, LivingEntity receiver);
    }

    private static final java.util.List<Listener> LISTENERS = new java.util.ArrayList<>();

    private CardGivenHooks() {
    }

    public static void subscribe(Listener listener) {
        if (listener != null && !LISTENERS.contains(listener)) {
            LISTENERS.add(listener);
        }
    }

    /** 中立发牌入口：giver 可为 null（未知发牌方）。 */
    public static void fire(LivingEntity giver, LivingEntity receiver) {
        if (receiver == null) return;
        if (receiver.level() == null || receiver.level().isClientSide()) return;
        if (giver != null && giver == receiver) return;
        LOGGER.info("AP_CARD_GIVEN: giver={}:receiver={}",
                giver == null ? "none" : giver.getName().getString(), receiver.getName().getString());
        for (Listener listener : LISTENERS) {
            try {
                listener.onCardGiven(giver, receiver);
            } catch (RuntimeException e) {
                LOGGER.warn("[Astral Dice][CardGiven] listener failed", e);
            }
        }
    }

    /** 兼容旧调用点（发牌方已知为玩家）。 */
    public static void fire(net.minecraft.server.level.ServerPlayer giver, LivingEntity receiver) {
        fire((LivingEntity) giver, receiver);
    }
}
