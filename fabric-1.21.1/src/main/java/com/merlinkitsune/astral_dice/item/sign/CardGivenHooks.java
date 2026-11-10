package com.merlinkitsune.astral_dice.item.sign;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 「角色获得卡牌」中立钩子（2026-11-09 用户裁决 C）。
 *
 * <p>发牌唯一漏斗（{@code VitaminPillChipItem#giveCard}）在同一 tick 内把**任何**发牌方与受益人
 * 交给订阅者，与具体立牌解耦：{@code giver == null} 表示「无发放者」的路径（命令、奖励等），
 * 订阅者自行决定是否结算。第一个订阅者 = 少主立牌（al）被动「双赢」A 面。
 *
 * <p>锚点日志：{@code AP_CARD_GIVEN}（钩子触发）、{@code AP_AL_GIFT_STARLIGHT}（少主结账），
 * 便于实机按行核对。
 */
public final class CardGivenHooks {

    private static final Logger LOGGER = LoggerFactory.getLogger(CardGivenHooks.class);

    /** 卡牌交接监听器（非玩家发牌方也可能进这里：{@code giver} 可为 {@code null}）。 */
    public interface Listener {
        void onCardGiven(LivingEntity giver, LivingEntity receiver);
    }

    private static final List<Listener> LISTENERS = new CopyOnWriteArrayList<>();

    private CardGivenHooks() {
    }

    /** 订阅（重复订阅同一个实例无副作用）。 */
    public static void subscribe(Listener listener) {
        if (listener == null || LISTENERS.contains(listener)) return;
        LISTENERS.add(listener);
    }

    /** 触发一次发牌事件；任何监听器抛异常都不影响其它监听器与发牌本体。 */
    public static void fire(LivingEntity giver, LivingEntity receiver) {
        if (receiver == null) return;
        if (receiver.level() == null || receiver.level().isClientSide()) return;
        if (giver != null && giver == receiver) return;
        LOGGER.info("AP_CARD_GIVEN: giver={}:receiver={}",
                giver == null ? "none" : giver.getName().getString(), receiver.getName().getString());
        for (Listener listener : LISTENERS) {
            try {
                listener.onCardGiven(giver, receiver);
            } catch (RuntimeException exception) {
                LOGGER.warn("[Astral Dice][CardGiven] listener failed", exception);
            }
        }
    }

    /** 带玩家发牌方的重载。 */
    public static void fire(ServerPlayer giver, LivingEntity receiver) {
        fire((LivingEntity) giver, receiver);
    }
}
