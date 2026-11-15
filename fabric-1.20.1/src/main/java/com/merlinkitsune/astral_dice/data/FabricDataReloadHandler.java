package com.merlinkitsune.astral_dice.data;

import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.minecraft.server.packs.PackType;

/** 把数据包 reload listener 挂到 Fabric 的重载流程。 */
public final class FabricDataReloadHandler {

    private FabricDataReloadHandler() {
    }

    public static void register() {
        ResourceManagerHelper.get(PackType.SERVER_DATA)
                .registerReloadListener(new FabricAnvilUpgradeReloadListener());
    }
}
