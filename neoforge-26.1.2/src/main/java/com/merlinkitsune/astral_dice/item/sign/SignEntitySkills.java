package com.merlinkitsune.astral_dice.item.sign;

import net.minecraft.world.entity.LivingEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 类玩家（注册生物）使用立牌主动的共享落点与审计锚点。
 *
 * <p>2026-11-09 起每个立牌都覆写 {@code activateForEntity}：
 * 能完整复刻玩家语义的立牌走自己的实现；依赖 ServerPlayer 会话/背包/瞄准的立牌
 * 在这里返回 {@code false} 并留下 {@code reason}，由调用方按「不支持」提示。
 */
public final class SignEntitySkills {

    private static final Logger LOGGER = LoggerFactory.getLogger(SignEntitySkills.class);

    private SignEntitySkills() {
    }

    /** 成功锚点。 */
    public static boolean ok(String signId, LivingEntity owner, int durationTicks) {
        LOGGER.info("AP_SIGN_ACTIVE_ENTITY: sign={}:owner={}:duration={}",
                signId, owner.getName().getString(), durationTicks);
        return true;
    }

    /** 拒绝锚点：返回 false，交由调用方提示「不支持」。 */
    public static boolean deny(String signId, LivingEntity owner, String reason) {
        LOGGER.info("AP_SIGN_ACTIVE_ENTITY_DENIED: sign={}:reason={}:owner={}",
                signId, reason, owner.getName().getString());
        return false;
    }
}
