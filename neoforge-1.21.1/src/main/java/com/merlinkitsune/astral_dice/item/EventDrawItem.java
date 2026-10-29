package com.merlinkitsune.astral_dice.item;

import com.merlinkitsune.astral_dice.event.AstralEventDispatcher;
import com.merlinkitsune.astral_dice.event.AstralEventRegistry;
import com.merlinkitsune.astral_dice.event.EventAnimationLock;
import com.merlinkitsune.astral_dice.event.EventCardService;
import com.merlinkitsune.astral_dice.event.api.AstralEvent;
import com.merlinkitsune.astral_dice.event.api.AstralEventTrigger;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

/**
 * 奇怪的问号(2026-10-10 事件体系重写 · 用户需求②「玩家使用抽事件的物品」)。
 *
 * <p>使用后从<b>公共事件池</b>({@link AstralEventRegistry#publicPool(net.minecraft.world.entity.player.Player)})随机抽一条事件并以
 * {@link AstralEventTrigger#ITEM_USE} 触发;池为空时安全失败(只记 warn 机器行,不崩)。
 * 生存模式消耗 1 张,创造模式不消耗。
 *
 * <p><b>冷却</b>(2026-10-29 用户裁决 D3):使用成功(事件确实开起来)即进物品冷却
 * {@link EventCardService#QUESTION_COOLDOWN_TICKS} = 动画窗 + 10 秒;动画窗内使用本物品会被拦下
 * (用户裁决 D4)且不进冷却。
 */
public class EventDrawItem extends Item {
    private static final Logger LOGGER = LoggerFactory.getLogger(EventDrawItem.class);

    public EventDrawItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide) {
            // 2026-10-29 用户裁决 D4：事件动画窗内一律不许再开新事件 / 打开选择界面。
            // 提前到抽取之前拦截 —— 不抽事件、不进冷却、不消耗。
            if (player instanceof ServerPlayer serverPlayer && EventAnimationLock.isAnimating(serverPlayer)) {
                EventAnimationLock.notifyBlocked(serverPlayer, "event_draw");
                return InteractionResultHolder.fail(stack);
            }
            Optional<AstralEvent> drawn = AstralEventRegistry.drawRandom(player.getRandom(), player);
            if (drawn.isEmpty()) {
                // Review Focus 1:公共池为空必须安全失败
                LOGGER.warn("AP_EVENT_DRAW: src=ITEM_USE:empty");
            } else {
                LOGGER.info("AP_EVENT_DRAW: src=ITEM_USE:id={}", drawn.get().id());
                // 权威入口 = AstralEventDispatcher；被全局硬间隔闸门拒绝时不消耗卡（2026-10-21）
                boolean started = AstralEventDispatcher.trigger(player, drawn.get().id(), AstralEventTrigger.ITEM_USE);
                if (started) {
                    // 2026-10-29 用户裁决 D3：使用成功即进物品冷却（唯一来源 = EventCardService
                    // .QUESTION_COOLDOWN_TICKS = 动画窗 DELAY_TICKS + 10 秒）。走原版 ItemCooldowns，
                    // 服务端会自动同步客户端 ⇒ 物品栏转圈、冷却期内点不动。创造模式同样上冷却
                    // （与原版末影珍珠一致；用户裁决括号原文），消耗逻辑保持原有口径：只扣生存模式。
                    player.getCooldowns().addCooldown(stack.getItem(), EventCardService.QUESTION_COOLDOWN_TICKS);
                    if (!player.getAbilities().instabuild) {
                        stack.shrink(1);
                    }
                }
            }
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }
}
