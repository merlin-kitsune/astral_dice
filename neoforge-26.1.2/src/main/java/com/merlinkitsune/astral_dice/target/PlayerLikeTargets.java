package com.merlinkitsune.astral_dice.target;

import com.merlinkitsune.astral_dice.item.chip.VitaminPillChipItem;
import com.merlinkitsune.starengine.target.TargetType;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 「类玩家目标」注册面（用户裁决：**由附属模组注册的生物，与玩家一样拥有全部功能**）。
 *
 * <p>背景：本仓的目标选择器沿用前置库的 {@link TargetType#PLAYER}，其判定是
 * {@code entity instanceof Player}；附属模组注册的角色既不是 {@link Player}，也不应该被当成普通怪物
 * —— 它需要被「选玩家」的技能选中、被发牌、持有星光。
 *
 * <ol>
 *   <li><b>识别</b>：{@link #isPlayerLike(LivingEntity)} —— 原版玩家恒真，其余实体只有在
 *       {@link #register} 过 {@link Handler} 且 {@code matches} 成立时才算。</li>
 *   <li><b>发牌</b>：{@link #giveCard} —— 玩家走既有唯一漏斗
 *       {@link VitaminPillChipItem#giveCard(Player, Player, ItemStack)}；注册生物走它自己的
 *       {@link Handler#giveCard}。</li>
 * </ol>
 *
 * <p>⚠️ 注册生物**不**获得星币账户（{@code StarEngineEconomy} 是前置库的玩家账户）。
 */
public final class PlayerLikeTargets {

    private static final Logger LOGGER = LoggerFactory.getLogger(PlayerLikeTargets.class);

    /** 类玩家生物的发牌实现（附属模组注册）。 */
    public interface Handler {
        /** 这个实体是否归本 handler 管（应尽量窄，避免抢走别人的目标）。 */
        boolean matches(LivingEntity entity);

        /**
         * 把一张卡交给该实体。
         *
         * @return 是否成功接收（{@code false} 记为主手满/无法接收，调用方据此决定是否掉地）
         */
        boolean giveCard(ServerPlayer giver, LivingEntity receiver, ItemStack card);

        /** 把一件非卡牌物品（筹码 / 奖励）交给该实体；{@code false} 时调用方负责掉地。 */
        default boolean giveItem(LivingEntity receiver, ItemStack stack) {
            return false;
        }

        /** 是否允许装备本模组立牌（需要 curios 的 {@code stand} 槽）。 */
        default boolean supportsSigns() {
            return false;
        }

        /** 是否允许装备本模组筹码（需要 curios 的 {@code chip} 槽）。 */
        default boolean supportsChips() {
            return false;
        }

        /** 是否允许使用本模组筹码商店（需要钱包与商店状态）。 */
        default boolean supportsShop() {
            return false;
        }
    }

    /** 能力位（{@link #supports} 的入参）。 */
    public enum Capability {
        SIGNS,
        CHIPS,
        SHOP
    }

    /** 注册表：id → handler（保持注册顺序，先注册先匹配）。 */
    private static final Map<Identifier, Handler> HANDLERS = new LinkedHashMap<>();

    private PlayerLikeTargets() {
    }

    /** 注册一个类玩家生物族；重复 id 覆盖旧 handler。 */
    public static void register(Identifier id, Handler handler) {
        if (id == null || handler == null) return;
        HANDLERS.put(id, handler);
        LOGGER.info("AP_PLAYER_LIKE: registered id={}", id);
    }

    /** 是否是「类玩家」目标：原版玩家恒真，其余看注册的 handler。 */
    public static boolean isPlayerLike(LivingEntity entity) {
        if (entity == null) return false;
        if (entity instanceof Player) return true;
        for (Handler handler : HANDLERS.values()) {
            if (handler.matches(entity)) return true;
        }
        return false;
    }

    /**
     * 目标选择器的统一放行判据：前置库原判据 **或** 「会话要的是玩家 + 目标是类玩家生物」。
     *
     * <p>服务端确认（{@code TargetSelectionManager#confirm}）与客户端两处 UX 过滤
     * （准星射线 / 半径高亮）必须同源，否则会出现「客户端能选、服务端拒绝」。
     */
    public static boolean matchesSelection(TargetType type, Player selector, LivingEntity target) {
        if (type != TargetType.PLAYER || target == null || target == selector) return false;
        return isPlayerLike(target);
    }

    /** 给类玩家目标发一张卡（玩家走唯一漏斗；注册生物走 handler）。 */
    public static boolean giveCard(ServerPlayer giver, LivingEntity receiver, ItemStack card) {
        if (receiver == null || card == null || card.isEmpty()) return false;
        if (receiver instanceof Player player) {
            // 漏斗内部「进包失败即掉地」，两种情况都算把卡交给了对方
            VitaminPillChipItem.giveCard(giver, player, card);
            return true;
        }
        for (Handler handler : HANDLERS.values()) {
            if (!handler.matches(receiver)) continue;
            boolean ok = handler.giveCard(giver, receiver, card);
            if (ok) {
                // 与玩家路径同口径:附属生物收牌同样触发「给牌」类被动（少主立牌 A 面）
                com.merlinkitsune.astral_dice.item.sign.AlSignItem.onCardGivenToOther(giver, receiver);
            }
            return ok;
        }
        return false;
    }

    /** 找出匹配该实体的 handler（玩家返回 {@code null}：玩家能力恒真，不走注册表）。 */
    public static Handler find(LivingEntity entity) {
        if (entity == null || entity instanceof Player) return null;
        for (Handler handler : HANDLERS.values()) {
            if (handler.matches(entity)) return handler;
        }
        return null;
    }

    /** 能力查询：玩家恒真；注册生物看 handler 的能力位；未注册实体恒假。 */
    public static boolean supports(LivingEntity entity, Capability capability) {
        if (entity == null) return false;
        if (entity instanceof Player) return true;
        Handler handler = find(entity);
        if (handler == null) return false;
        return switch (capability) {
            case SIGNS -> handler.supportsSigns();
            case CHIPS -> handler.supportsChips();
            case SHOP -> handler.supportsShop();
        };
    }

    /** 交付一件物品给类玩家目标（玩家始终 {@code false}：由调用方走玩家物品栏逻辑）。 */
    public static boolean giveItem(LivingEntity receiver, ItemStack stack) {
        if (receiver == null || stack == null || stack.isEmpty()) return false;
        if (receiver instanceof Player) return false;
        Handler handler = find(receiver);
        return handler != null && handler.giveItem(receiver, stack);
    }
}
