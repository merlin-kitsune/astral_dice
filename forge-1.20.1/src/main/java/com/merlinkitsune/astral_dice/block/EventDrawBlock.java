package com.merlinkitsune.astral_dice.block;

import com.merlinkitsune.astral_dice.event.AstralEventDispatcher;
import com.merlinkitsune.astral_dice.event.AstralEventRegistry;
import com.merlinkitsune.astral_dice.event.api.AstralEvent;
import com.merlinkitsune.astral_dice.event.api.AstralEventTrigger;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

/**
 * 事件抽取机(2026-10-10 事件体系重写 · 用户需求②「玩家使用抽事件的方块(独立注册)」)。
 *
 * <p>右键从公共事件池随机抽一条事件并以 {@link AstralEventTrigger#BLOCK_USE} 触发(可重复使用,不消耗方块);
 * 池为空时安全失败(只记 warn 机器行)。交互入口 = 1.20.1 的六参 {@code use}(多一个 InteractionHand，与卡牌商店方块一致)。
 */
public class EventDrawBlock extends Block {
    private static final Logger LOGGER = LoggerFactory.getLogger(EventDrawBlock.class);

    public EventDrawBlock(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand,
                            BlockHitResult hitResult) {
        if (!level.isClientSide) {
            Optional<AstralEvent> drawn = AstralEventRegistry.drawRandom(player.getRandom());
            if (drawn.isEmpty()) {
                LOGGER.warn("AP_EVENT_DRAW: src=BLOCK_USE:empty");
            } else {
                LOGGER.info("AP_EVENT_DRAW: src=BLOCK_USE:id={}", drawn.get().id());
                AstralEventDispatcher.trigger(player, drawn.get().id(), AstralEventTrigger.BLOCK_USE);
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
