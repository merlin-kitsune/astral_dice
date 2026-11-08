package com.merlinkitsune.astral_dice.item.sign;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 「给牌」事件的中立钩子。
 *
 * <p>上游把「让对方获得卡牌」的唯一漏斗收敛在 {@code VitaminPillChipItem#giveCard}，
 * 被动效果（例：少主立牌「双赢」）订阅本钩子即可同时覆盖主动发卡与「你有我有」。
 *
 * <p>玩家与注册生物都经过这里；未实现订阅者时是零成本的空调用。
 */
public final class CardGivenHooks {

    private static final Logger LOGGER = LoggerFactory.getLogger(CardGivenHooks.class);

    /** 订阅者实现本接口并注册。 */
    public interface Listener {
        void onCardGiven(ServerPlayer giver, LivingEntity receiver);
    }

    private static final java.util.List<Listener> LISTENERS = new java.util.ArrayList<>();

    private CardGivenHooks() {
    }

    public static void subscribe(Listener listener) {
        if (listener != null && !LISTENERS.contains(listener)) {
            LISTENERS.add(listener);
        }
    }

    public static void fire(ServerPlayer giver, LivingEntity receiver) {
        if (giver == null || receiver == null || giver == receiver) return;
        if (receiver.level() == null || receiver.level().isClientSide()) return;
        for (Listener listener : LISTENERS) {
            try {
                listener.onCardGiven(giver, receiver);
            } catch (RuntimeException e) {
                LOGGER.warn("[Astral Dice][CardGiven] listener failed", e);
            }
        }
    }
}
