package com.merlinkitsune.astral_dice.api.playerlike;

import net.minecraft.world.entity.LivingEntity;

/**
 * 类玩家生物的钱包（门面层类型）。
 *
 * <p>金额单位与内部实现一致，一律 {@code int} 星币。玩家（{@code Player}）恒走
 * StarEngine 玩家账户，不需要注册；本接口只用于非玩家宿主。
 */
public interface PlayerLikeWallet {

    /** 钱包系统是否可用（账户存在、前置在线）。 */
    boolean isAvailable();

    /** 带宿主的可用性；附属可用它区分"主人离线"与"没有账户"。默认转发无参版本。 */
    default boolean isAvailable(LivingEntity holder) {
        return isAvailable();
    }

    /** 当前可用星币；缺账户返回 0。 */
    long balance(LivingEntity holder);

    /** 扣款；余额不足返回 false 且不得有副作用。 */
    boolean withdraw(LivingEntity holder, int amount);

    /** 退款；默认不支持。 */
    default boolean deposit(LivingEntity holder, int amount) {
        return false;
    }
}
