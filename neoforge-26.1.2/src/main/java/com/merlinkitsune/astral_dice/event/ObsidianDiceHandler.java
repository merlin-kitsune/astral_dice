package com.merlinkitsune.astral_dice.event;

import com.merlinkitsune.astral_dice.item.ModItems;
import com.merlinkitsune.astral_dice.item.dice.DiceCurioItem;
import com.merlinkitsune.astral_dice.item.dice.ObsidianDiceItem;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import top.theillusivec4.curios.api.CuriosApi;

/**
 * 黑曜石骰子:佩戴期间受到的爆炸伤害减少 50%。
 *
 * 在 LivingIncomingDamageEvent(HIGHEST+1)对爆炸伤害(is_explosion 标签)按剩余 50% 折算,
 * 与原版抗性/护甲链路叠加(乘法关系)。
 */
@net.neoforged.fml.common.EventBusSubscriber(modid = com.merlinkitsune.astral_dice.AstralDiceMod.MODID)
public final class ObsidianDiceHandler {

    private ObsidianDiceHandler() {
    }

    public static boolean hasObsidianDice(Player player) {
        var curios = CuriosApi.getCuriosInventory(player);
        return curios.isPresent()
                && curios.get().findFirstCurio(s -> s.is(ModItems.OBSIDIAN_DICE.get())).isPresent();
    }

    @SubscribeEvent(priority = net.neoforged.bus.api.EventPriority.HIGH)
    public static void onFireDamage(LivingIncomingDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (player.level().isClientSide()) return;
        if (!ObsidianDiceItem.isExplosionDamage(event.getSource())) return;
        if (!hasObsidianDice(player)) return;
        float reduced = event.getAmount() * (1.0F - ObsidianDiceItem.EXPLOSION_DAMAGE_REDUCTION);
        event.setAmount(reduced);
    }
}
