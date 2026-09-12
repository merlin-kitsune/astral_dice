package com.merlinkitsune.astral_dice.item.chip;

import com.merlinkitsune.astral_dice.AstralDiceMod;
import com.merlinkitsune.astral_dice.item.CuriosCompat;
import com.merlinkitsune.astral_dice.item.ModItems;
import com.merlinkitsune.astral_dice.item.card.RandomCardHandler;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 智能手表筹码:物品栏中卡牌数量不足 {@link #CARD_THRESHOLD} 张时,
 * **每击杀 1 个敌对目标**获得一张随机卡牌
 * (战斗牌 + 效果牌;专属牌经 {@link RandomCardHandler} 强制排除)。
 *
 * <p>无冷却、无计数器:只要当前卡牌数仍低于阈值,每次敌对目标击杀都会补充一张;
 * 达到阈值后不再发放(继续击杀也不会囤积)。
 */
@Mod.EventBusSubscriber(modid = AstralDiceMod.MODID)
public class SmartWatchChipItem extends BaseChipItem {
    /** 物品栏卡牌数量低于该值时补充 */
    public static final int CARD_THRESHOLD = 10;

    public SmartWatchChipItem(Properties properties) {
        super(properties);
    }

    // 玩家是否佩戴本筹码
    public static boolean isEquipped(Player player) {
        if (player == null) return false;
        var curios = CuriosCompat.getCuriosInventory(player);
        return curios.isPresent() && curios.get().findFirstCurio(s -> s.is(ModItems.SMART_WATCH_CHIP.get())).isPresent();
    }

    /** 玩家击杀敌对目标后:卡牌不足阈值时补充一张随机卡牌 */
    public static void onHostileKilled(Player killer) {
        if (killer == null || killer.level().isClientSide()) return;
        if (!isEquipped(killer)) return;
        if (countCards(killer) >= CARD_THRESHOLD) return;
        RandomCardHandler.giveCardTo(killer, RandomCardHandler.CardCategory.ALL);
    }

    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        if (event.isCanceled()) return;
        if (event.getEntity().level().isClientSide()) return;
        if (!(event.getEntity() instanceof Enemy)) return;
        if (!(event.getSource().getEntity() instanceof Player killer)) return;
        onHostileKilled(killer);
    }

    // 统计物品栏(主背包)中本模组卡牌总数
    private static int countCards(Player player) {
        int count = 0;
        for (ItemStack stack : player.getInventory().items) {
            if (!stack.isEmpty() && ModItems.isCardItem(stack)) {
                count += stack.getCount();
            }
        }
        return count;
    }
}
