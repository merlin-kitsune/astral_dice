package com.merlinkitsune.astral_dice.item;

import com.merlinkitsune.astral_dice.event.AstralEventDispatcher;
import com.merlinkitsune.astral_dice.event.AstralEventRegistry;
import com.merlinkitsune.astral_dice.event.api.AstralEvent;
import com.merlinkitsune.astral_dice.event.api.AstralEventTrigger;
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
            Optional<AstralEvent> drawn = AstralEventRegistry.drawRandom(player.getRandom(), player);
            if (drawn.isEmpty()) {
                // Review Focus 1:公共池为空必须安全失败
                LOGGER.warn("AP_EVENT_DRAW: src=ITEM_USE:empty");
            } else {
                LOGGER.info("AP_EVENT_DRAW: src=ITEM_USE:id={}", drawn.get().id());
                AstralEventDispatcher.trigger(player, drawn.get().id(), AstralEventTrigger.ITEM_USE);
                if (!player.getAbilities().instabuild) {
                    stack.shrink(1);
                }
            }
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }
}
