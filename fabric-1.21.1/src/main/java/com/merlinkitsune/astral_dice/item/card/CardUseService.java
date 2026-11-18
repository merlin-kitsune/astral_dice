package com.merlinkitsune.astral_dice.item.card;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 卡牌使用的统一入口：玩家走原版 {@link ItemStack#use} 管线；注册生物走各卡自己的
 * {@link #useCardForEntity} 通道（默认 no-op + WARN，由卡牌按需覆写）。
 *
 * <p>用途：让「注册生物也能用卡」有唯一落点，避免每个调用点各自 {@code instanceof Player}。
 */
public final class CardUseService {

    private static final Logger LOGGER = LoggerFactory.getLogger(CardUseService.class);

    private CardUseService() {
    }

    /** 尝试让该实体使用一张卡；返回是否消耗/生效。 */
    public static boolean useCardFor(LivingEntity user, ItemStack stack) {
        if (user == null || stack == null || stack.isEmpty()) return false;
        if (user instanceof Player player) {
            InteractionResultHolder<ItemStack> result = stack.use(player.level(), player, InteractionHand.MAIN_HAND);
            return result.getResult().consumesAction();
        }
        return useCardForEntity(user, stack);
    }

    /**
     * 非玩家使用卡牌的扩展点（默认不支持）。
     *
     * <p>卡牌类可覆写为「把 {@code applyEffect} 的目标从拥有者放宽到任意 LivingEntity」；
     * 未覆写时打一条 WARN，便于附属在验收时定位「这张卡还不支持注册生物」。
     */
    public static boolean useCardForEntity(LivingEntity user, ItemStack stack) {
        LOGGER.warn("[Astral Dice][CardUse] 卡牌尚未支持类玩家生物: entity={} card={}",
                user.getName().getString(), stack.getItem());
        return false;
    }
}
