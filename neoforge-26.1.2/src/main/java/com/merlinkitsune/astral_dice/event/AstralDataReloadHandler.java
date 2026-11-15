package com.merlinkitsune.astral_dice.event;

import com.merlinkitsune.astral_dice.data.AnvilUpgradeReloadListener;
import net.minecraft.resources.Identifier;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.AddServerReloadListenersEvent;

/** 把数据包 reload listener 挂到 NeoForge 26.1.2 的重载流程。 */
@EventBusSubscriber(modid = com.merlinkitsune.astral_dice.AstralDiceMod.MODID)
public final class AstralDataReloadHandler {

    private AstralDataReloadHandler() {
    }

    @SubscribeEvent
    public static void onAddReloadListener(AddServerReloadListenersEvent event) {
        event.addListener(Identifier.fromNamespaceAndPath("astral_dice", "anvil_upgrade"), new AnvilUpgradeReloadListener());
    }
}
