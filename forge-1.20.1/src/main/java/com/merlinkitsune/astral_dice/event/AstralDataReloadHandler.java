package com.merlinkitsune.astral_dice.event;

import com.merlinkitsune.astral_dice.AstralDiceMod;
import com.merlinkitsune.astral_dice.data.AnvilUpgradeReloadListener;
import com.merlinkitsune.astral_dice.data.LootInjectionReloadListener;
import com.merlinkitsune.astral_dice.data.ShopDataReloadListener;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** 把数据包 reload listener 挂到 Forge 1.20.1 的重载流程。 */
@Mod.EventBusSubscriber(modid = AstralDiceMod.MODID)
public final class AstralDataReloadHandler {

    private AstralDataReloadHandler() {
    }

    @SubscribeEvent
    public static void onAddReloadListener(AddReloadListenerEvent event) {
        event.addListener(new AnvilUpgradeReloadListener());
        event.addListener(new LootInjectionReloadListener());
        event.addListener(new ShopDataReloadListener());
    }
}
