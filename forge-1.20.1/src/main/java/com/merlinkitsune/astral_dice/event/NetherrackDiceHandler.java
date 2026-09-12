package com.merlinkitsune.astral_dice.event;

import com.merlinkitsune.starenginelib.item.CuriosCompat;
import com.merlinkitsune.astral_dice.item.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 下界岩骰子:在下界采集矿物时,30% 概率额外掉落 1 枚星币,5% 概率额外掉落 1 个星盘。
 *
 * 判定:玩家佩戴下界岩骰子 + 维度为下界 + 破坏方块为下界矿物(石英矿/下界金矿/远古残骸)。
 * 额外掉落经 Block.popResource 生成于方块位置,与原版掉落一致;同一次采矿最多掉落一种(先判星盘)。
 */
@Mod.EventBusSubscriber(modid = com.merlinkitsune.astral_dice.AstralDiceMod.MODID)
public final class NetherrackDiceHandler {

    /** 星盘掉落概率 */
    public static final float STAR_PLATE_CHANCE = 0.05F;
    /** 星币掉落概率 */
    public static final float STAR_COIN_CHANCE = 0.30F;

    private NetherrackDiceHandler() {
    }

    public static boolean hasNetherrackDice(Player player) {
        return CuriosCompat.getCuriosInventory(player)
                .map(h -> h.findFirstCurio(s -> s.is(ModItems.NETHERRACK_DICE.get())).isPresent())
                .orElse(false);
    }

    private static boolean isNetherOre(BlockState state) {
        return state.is(Blocks.NETHER_QUARTZ_ORE)
                || state.is(Blocks.NETHER_GOLD_ORE)
                || state.is(Blocks.ANCIENT_DEBRIS);
    }

    @SubscribeEvent
    public static void onOreMined(BlockEvent.BreakEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        Player player = event.getPlayer();
        if (player == null || player.isSpectator()) return;
        // 仅下界维度
        if (level.dimension() != Level.NETHER) return;
        if (!isNetherOre(event.getState())) return;
        if (!hasNetherrackDice(player)) return;

        BlockPos pos = event.getPos();
        float roll = player.getRandom().nextFloat();
        if (roll < STAR_PLATE_CHANCE) {
            Blocks.STONE.popResource(level, pos, new ItemStack(ModItems.STAR_PLATE.get()));
        } else if (roll < STAR_PLATE_CHANCE + STAR_COIN_CHANCE) {
            Blocks.STONE.popResource(level, pos, new ItemStack(ModItems.STAR_COIN.get()));
        }
    }
}
