package com.merlinkitsune.astral_dice.item;

import com.merlinkitsune.astral_dice.event.AstralEventDispatcher;
import com.merlinkitsune.astral_dice.event.AstralEventRegistry;
import com.merlinkitsune.astral_dice.event.EventAnimationLock;
import com.merlinkitsune.astral_dice.event.EventCardService;
import com.merlinkitsune.astral_dice.network.ModNetwork;
import com.merlinkitsune.astral_dice.event.api.AstralEvent;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import com.merlinkitsune.starengine.component.GameplayConstants;
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
            // 2026-10-29 用户裁决 D4：事件动画播放中不得再抽事件（否则弹卡动画与结算互相覆盖）。
            // 失败返回 ⇒ 不消耗卡牌；创造的「无所谓消耗」不改变这里的行为（D3 的冷却仍在下面按 started 施加）。
            if (player instanceof ServerPlayer serverPlayer && EventAnimationLock.isAnimating(serverPlayer)) {
                LOGGER.info("AP_EVENT_ANIM: block:{}:{}", serverPlayer.getGameProfile().getName(), "event_draw");
                ModNetwork.sendToPlayer(serverPlayer, new ModNetwork.ActionBarMessage(
                        Component.translatable(EventAnimationLock.NOTICE_KEY).withStyle(ChatFormatting.RED),
                        GameplayConstants.ACTIONBAR_DURATION_TICKS));
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
                if (started && !player.getAbilities().instabuild) {
                    stack.shrink(1);
                }
                // 2026-10-29 用户裁决 D3：抽中的事件要播动画，抽卡物在「动画 38t + 10s」内不得再用。
                // 常量与动画时长同源（EventCardService.QUESTION_COOLDOWN_TICKS），不写裸数字。
                // 创造模式同样进冷却：与末影珍珠口径一致（创造只免消耗，不免冷却）。
                if (started) {
                    player.getCooldowns().addCooldown(this, EventCardService.QUESTION_COOLDOWN_TICKS);
                }
            }
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }
}
