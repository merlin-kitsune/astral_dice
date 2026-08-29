package com.merlinkitsune.astral_dice.item.chip;

import com.merlinkitsune.astral_dice.component.ModAttachments;
import com.merlinkitsune.astral_dice.item.ModItems;
import com.merlinkitsune.astral_dice.item.card.RandomCardHandler;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.SlotContext;

/**
 * 探天卫星筹码:
 * - 物品栏中"轨道炮"少于 6 张时,每 1:00 补充 1 张轨道炮;
 * - 使用一张"轨道炮"后,本轮出牌数 +1(每个出牌轮次最多一次);
 * - "轨道炮"生效期间,使用远程或魔法击杀一个敌方目标后,获得一张随机效果牌。
 */
public class SatelliteChipItem extends BaseChipItem {
    /** 轨道炮库存目标数量 */
    public static final int TARGET_ORBITAL_STRIKE_COUNT = 6;
    /** 补充轨道炮间隔(tick,1 分钟) */
    public static final int GIVE_INTERVAL_TICKS = 1200;

    public SatelliteChipItem(Properties properties) {
        super(properties);
    }

    // 玩家是否佩戴探天卫星
    public static boolean isEquipped(Player player) {
        if (player == null) return false;
        var curios = CuriosApi.getCuriosInventory(player);
        return curios.isPresent() && curios.get().findFirstCurio(s -> s.is(ModItems.SATELLITE_CHIP.get())).isPresent();
    }

    @Override
    public void curioTick(SlotContext slotContext, ItemStack stack) {
        if (!(slotContext.entity() instanceof Player player)) return;
        if (player.level().isClientSide()) return;
        // 每 1:00 尝试补充轨道炮
        long now = player.level().getGameTime();
        long cdEnd = ModAttachments.getSatelliteGiveCooldownEnd(player);
        if (now < cdEnd) return;
        if (countOrbitalStrike(player) < TARGET_ORBITAL_STRIKE_COUNT) {
            ItemStack card = new ItemStack(ModItems.ORBITAL_STRIKE_CARD.get());
            if (!player.getInventory().add(card)) {
                player.drop(card, false);
            }
        }
        ModAttachments.setSatelliteGiveCooldownEnd(player, now + GIVE_INTERVAL_TICKS);
    }

    // 使用一张"轨道炮"后调用:本轮出牌数 +1(每个轮次最多一次)
    public static void onOrbitalStrikeUsed(Player player) {
        if (player == null || player.level().isClientSide()) return;
        if (!isEquipped(player)) return;
        if (ModAttachments.isSatellitePlayBonusActive(player)) return;
        ModAttachments.setSatellitePlayBonusActive(player, true);
    }

    // 轨道炮生效期间远程/魔法击杀敌方目标后调用:获得一张随机效果牌
    public static void onRangedMagicKill(Player player) {
        if (player == null || player.level().isClientSide()) return;
        if (!isEquipped(player)) return;
        RandomCardHandler.giveCardTo(player, RandomCardHandler.CardCategory.EFFECT);
    }

    @Override
    protected void onChipUnequip(Player player, ItemStack stack) {
        ModAttachments.setSatellitePlayBonusActive(player, false);
    }

    // 统计物品栏中轨道炮总数
    private static int countOrbitalStrike(Player player) {
        int count = 0;
        for (ItemStack stack : player.getInventory().items) {
            if (!stack.isEmpty() && stack.is(ModItems.ORBITAL_STRIKE_CARD.get())) {
                count += stack.getCount();
            }
        }
        return count;
    }
}
