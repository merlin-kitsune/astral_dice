package com.merlinkitsune.astral_dice.event;

import com.merlinkitsune.astral_dice.component.ModDataComponents;
import com.merlinkitsune.astral_dice.component.WeaponEnhancement;
import com.merlinkitsune.astral_dice.item.CardReturnHelper;
import com.merlinkitsune.astral_dice.item.DiceStarUpgrade;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 实验性升阶合成：新骰子重置为 0★ / 空卡槽，并把母体骰子上已装配的卡牌退回玩家。
 *
 * <p>只在服务端、实验开关开启、产物是 0★ 空卡骰子时执行；费用不退（B 方案，2026-11-15 用户裁决）。
 */
@EventBusSubscriber(modid = com.merlinkitsune.astral_dice.AstralDiceMod.MODID)
public final class DiceUpgradeCraftHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(DiceUpgradeCraftHandler.class);

    private DiceUpgradeCraftHandler() {
    }

    @SubscribeEvent
    public static void onItemCrafted(PlayerEvent.ItemCraftedEvent event) {
        Player player = event.getEntity();
        if (player.level().isClientSide()) {
            return;
        }
        if (!DiceStarUpgrade.experimentalEnabled()) {
            return;
        }
        ItemStack crafted = event.getCrafting();
        if (!DiceStarUpgrade.isDice(crafted)) {
            return;
        }
        WeaponEnhancement result = crafted.getOrDefault(ModDataComponents.WEAPON_ENHANCEMENT.get(), WeaponEnhancement.EMPTY);
        if (result.starLevel() != 0 || !result.appliedStones().isEmpty()) {
            return;
        }
        Container matrix = event.getInventory();
        if (matrix == null) {
            return;
        }
        for (int i = 0; i < matrix.getContainerSize(); i++) {
            ItemStack source = matrix.getItem(i);
            if (!DiceStarUpgrade.isDice(source)) {
                continue;
            }
            WeaponEnhancement sourceEnhancement = source.getOrDefault(
                    ModDataComponents.WEAPON_ENHANCEMENT.get(), WeaponEnhancement.EMPTY);
            if (sourceEnhancement.appliedStones().isEmpty()) {
                continue;
            }
            int returned = CardReturnHelper.returnCards(player, sourceEnhancement.appliedStones());
            LOGGER.info("[astral_dice] CRAFT_STAR_RESET_CARDS_RETURNED player={} source={} star={}->0 cards={}",
                    player.getName().getString(), source.getItem(), sourceEnhancement.starLevel(), returned);
            return;
        }
    }
}
