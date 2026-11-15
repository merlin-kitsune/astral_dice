package com.merlinkitsune.astral_dice.event;

import com.merlinkitsune.astral_dice.data.AnvilUpgradeReloadListener;
import com.merlinkitsune.astral_dice.data.LootInjectionReloadListener;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.AddReloadListenerEvent;

import java.util.List;

/**
 * 把两个数据包 reload listener 挂到 NeoForge 的重载流程（{@code /reload} 与登录时都会走）。
 *
 * <p>⚠️ 时序：{@link net.neoforged.neoforge.event.LootTableLoadEvent} 发生在战利品注册表**反序列化过程中**，
 * 早于本事件；因此 {@code LootInjectionHandler} 在装载期只排队表对象，本事件再取走队列交给
 * {@link LootInjectionReloadListener}，等它的 {@code apply} 解析完 JSON 后回填 pool。
 * 这样首次启动与每次 {@code /reload} 都不会读到上一代（或空）数据快照。
 */
@EventBusSubscriber(modid = com.merlinkitsune.astral_dice.AstralDiceMod.MODID)
public final class AstralDataReloadHandler {

    private AstralDataReloadHandler() {
    }

    @SubscribeEvent
    public static void onAddReloadListener(AddReloadListenerEvent event) {
        List<LootInjectionHandler.PendingTable> pending = LootInjectionHandler.drainPending();
        event.addListener(new AnvilUpgradeReloadListener());
        event.addListener(new LootInjectionReloadListener(pending));
    }
}
