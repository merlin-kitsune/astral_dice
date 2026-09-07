package com.merlinkitsune.astral_dice.event;

import com.merlinkitsune.astral_dice.item.CuriosCompat;
import com.merlinkitsune.astral_dice.item.ModItems;
import com.merlinkitsune.astral_dice.item.dice.ObsidianDiceItem;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 黑曜石骰子:佩戴期间受到的火焰伤害减少 70%。
 *
 * 在 LivingHurtEvent 对火焰伤害(is_fire 标签)按剩余 30% 折算。
 */
@Mod.EventBusSubscriber(modid = com.merlinkitsune.astral_dice.AstralDiceMod.MODID)
public final class ObsidianDiceHandler {

    private ObsidianDiceHandler() {
    }

    public static boolean hasObsidianDice(Player player) {
        return CuriosCompat.getCuriosInventory(player)
                .map(h -> h.findFirstCurio(s -> s.is(ModItems.OBSIDIAN_DICE.get())).isPresent())
                .orElse(false);
    }

    @SubscribeEvent
    public static void onFireDamage(LivingHurtEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (player.level().isClientSide()) return;
        if (!ObsidianDiceItem.isFireDamage(event.getSource())) return;
        if (!hasObsidianDice(player)) return;
        float reduced = event.getAmount() * (1.0F - ObsidianDiceItem.FIRE_DAMAGE_REDUCTION);
        event.setAmount(reduced);
    }
}
