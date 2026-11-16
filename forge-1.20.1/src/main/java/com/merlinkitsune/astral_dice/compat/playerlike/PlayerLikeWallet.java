package com.merlinkitsune.astral_dice.compat.playerlike;

import net.minecraft.world.entity.LivingEntity;

/**
 * 「类玩家生物」的钱包（星币账户）抽象。
 *
 * <p>背景：{@code StarEngineEconomy} 是前置库的**玩家账户**，注册生物没有账户。
 * 附属模组（例：车万女仆）若希望其生物能买东西，需要自己提供一份实现
 * （比如从主人钱包代扣），通过 {@link PlayerLikeWallets#register} 挂上来。
 *
 * <p>本模组的所有消费面（筹码商店、购买/发币类立牌效果）只通过本接口取余额与扣款，
 * 不直接摸 {@code StarEngineEconomy}。
 */
public interface PlayerLikeWallet {

    /** 该钱包当前是否可用（余额系统在线、账户存在）。 */
    boolean isAvailable();

    /**
     * 带宿主的可用性（2026-11-09 B）：默认转发无参版本。
     * 附属可用它区分「主人离线」与「没有账户」，商店据此返回 WALLET_UNAVAILABLE 而不是 NO_COINS。
     */
    default boolean isAvailable(LivingEntity holder) {
        return isAvailable();
    }

    /** 当前可用星币（不足或缺账户返回 0）。 */
    long balance(LivingEntity holder);

    /** 扣款；余额不足时返回 {@code false} 且不得有任何副作用。 */
    boolean withdraw(LivingEntity holder, int amount);

    /**
     * 退款（交付失败时回滚已扣的星币）。
     *
     * <p>默认 {@code false}：调用方必须把「扣了钱但退不回来」写进失败语义，不得静默吞掉。
     */
    default boolean deposit(LivingEntity holder, int amount) {
        return false;
    }
}
