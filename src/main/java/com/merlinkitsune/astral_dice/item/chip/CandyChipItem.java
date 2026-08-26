package com.merlinkitsune.astral_dice.item.chip;

import com.merlinkitsune.astral_dice.component.ModAttachments;
import com.merlinkitsune.astral_dice.item.HealingManager;
import com.merlinkitsune.astral_dice.item.ModItems;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.SlotContext;

/**
 * 可口糖果筹码:每使用一张效果牌时,增加 1 点治愈并恢复 1 点生命值。
 * 若使用效果牌时玩家生命值已满,则本轮出牌数 +1(每个效果牌出牌轮次最多触发一次,
 * 避免故意受伤再回血刷出牌数)。
 */
public class CandyChipItem extends BaseChipItem {
    public CandyChipItem(Properties properties) {
        super(properties);
    }

    // 玩家是否佩戴可口糖果筹码
    public static boolean isEquipped(Player player) {
        if (player == null) return false;
        var curios = CuriosApi.getCuriosInventory(player);
        return curios.isPresent() && curios.get().findFirstCurio(s -> s.is(ModItems.CANDY_CHIP.get())).isPresent();
    }

    // 使用效果牌后调用
    public static void onEffectCardUsed(Player player) {
        if (player == null || player.level().isClientSide()) return;
        if (!isEquipped(player)) return;

        boolean wasFull = player.getHealth() >= player.getMaxHealth();
        HealingManager.add(player, 1);
        player.heal(1);

        // 满血时本轮出牌数 +1,每个出牌轮次最多触发一次
        if (wasFull && !ModAttachments.isCandyChipPlayBonusActive(player)) {
            ModAttachments.setCandyChipPlayBonusActive(player, true);
        }
    }

    @Override
    protected void onChipUnequip(Player player, ItemStack stack) {
        ModAttachments.setCandyChipPlayBonusActive(player, false);
    }
}
