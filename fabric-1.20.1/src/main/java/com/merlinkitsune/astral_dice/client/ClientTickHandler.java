package com.merlinkitsune.astral_dice.client;

import com.merlinkitsune.astral_dice.AstralDiceMod;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.event.TickEvent;

import com.merlinkitsune.starenginelib.client.ClientDamageNumbers;
/**
 * 客户端游戏总线 tick 订阅(游戏事件总线;注册类事件如 GuiLayers/KeyMappings 在模组总线,
 * 见 {@link ModClientEvents},两类事件不可混挂在同一订阅器上)。
 */
@Mod.EventBusSubscriber(modid = AstralDiceMod.MODID, value = Dist.CLIENT)
public class ClientTickHandler {

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        ClientDamageNumbers.tick();
        TargetSelectionClient.tick();
        // 效果牌「一次按下只出一张」:松开右键即复位长按标记(见 EffectCardUseGuard;主线带入)
        EffectCardUseGuard.onClientTick();
    }
}
