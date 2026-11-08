package com.merlinkitsune.astral_dice.compat.playerlike;

import com.merlinkitsune.starengine.economy.StarEngineEconomy;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * {@link PlayerLikeWallet} 注册表。
 *
 * <p>解析顺序：**原版玩家恒走 {@code StarEngineEconomy}**（不受注册影响），
 * 其余实体按注册顺序找第一个匹配的绑定；都没有则返回 {@code null}（= 没有账户）。
 */
public final class PlayerLikeWallets {

    private static final Logger LOGGER = LoggerFactory.getLogger(PlayerLikeWallets.class);

    /** 原版玩家默认钱包：直接是前置库的玩家账户。 */
    private static final PlayerLikeWallet PLAYER_WALLET = new StarEngineWallet();

    private record Binding(ResourceLocation id, Predicate<LivingEntity> matches, PlayerLikeWallet wallet) {
    }

    private static final List<Binding> BINDINGS = new ArrayList<>();

    private PlayerLikeWallets() {
    }

    /** 注册一个类玩家生物族的钱包（重复 id 覆盖旧绑定，保持注册顺序）。 */
    public static void register(ResourceLocation id, Predicate<LivingEntity> matches, PlayerLikeWallet wallet) {
        if (id == null || matches == null || wallet == null) return;
        BINDINGS.removeIf(b -> b.id().equals(id));
        BINDINGS.add(new Binding(id, matches, wallet));
        LOGGER.info("AP_PLAYER_LIKE_WALLET: registered id={}", id);
    }

    /** 解析实体可用的钱包；玩家恒有，注册生物看绑定，其余 {@code null}。 */
    public static PlayerLikeWallet resolve(LivingEntity holder) {
        if (holder == null) return null;
        if (holder instanceof Player) return PLAYER_WALLET;
        for (Binding binding : BINDINGS) {
            if (binding.matches().test(holder)) return binding.wallet();
        }
        return null;
    }

    /** 默认玩家钱包适配器。 */
    private static final class StarEngineWallet implements PlayerLikeWallet {

        @Override
        public boolean isAvailable() {
            return StarEngineEconomy.isAvailable();
        }

        @Override
        public long balance(LivingEntity holder) {
            return holder instanceof Player player && isAvailable() ? StarEngineEconomy.getBalance(player) : 0;
        }

        @Override
        public boolean withdraw(LivingEntity holder, int amount) {
            if (amount <= 0) return true;
            return holder instanceof Player player && isAvailable() && StarEngineEconomy.hasBalance(player, amount)
                    && StarEngineEconomy.withdraw(player, amount);
        }
    }
}
