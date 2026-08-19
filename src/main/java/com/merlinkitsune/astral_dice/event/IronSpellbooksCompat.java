package com.merlinkitsune.astral_dice.event;

import io.redspace.ironsspellbooks.api.events.ChangeManaEvent;
import net.neoforged.bus.api.SubscribeEvent;
import com.merlinkitsune.astral_dice.AstralDiceMod;

/**
 * Iron 的法术与魔法书 (irons_spellbooks) 联动。
 * 仅在模组加载时由 AstralDiceMod 条件注册本类处理器(未加载时本类不会被加载)。
 */
public class IronSpellbooksCompat {

    // 命运的指引·魔力:命运的指引激活期间,法术魔力消耗减半
    @SubscribeEvent
    public static void onChangeMana(ChangeManaEvent event) {
        var player = event.getEntity();
        if (player.level().isClientSide()) return;
        if (!ModEventHandlers.isFateGuidanceActive(player)) return;
        float oldMana = event.getOldMana();
        float newMana = event.getNewMana();
        // 仅处理消耗方向(新魔力 < 旧魔力):将消耗量减半
        if (newMana < oldMana) {
            event.setNewMana(newMana + (oldMana - newMana) / 2.0f);
        }
    }
}
