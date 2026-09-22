package com.merlinkitsune.astral_dice.client;

import com.merlinkitsune.astral_dice.AstralDiceMod;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

import com.merlinkitsune.starenginelib.client.ClientDamageNumbers;
/**
 * 客户端游戏总线 tick 订阅(游戏事件总线;注册类事件如 GuiLayers/KeyMappings 在模组总线,
 * 见 {@link ModClientEvents},两类事件不可混挂在同一订阅器上)。
 */
@EventBusSubscriber(modid = AstralDiceMod.MODID, value = Dist.CLIENT)
public class ClientTickHandler {

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        ClientDamageNumbers.tick();
        TargetSelectionClient.tick();
        // 效果牌「一次按下只出一张」:松开右键即复位长按标记(见 EffectCardUseGuard;主线带入)
        EffectCardUseGuard.onClientTick();
    }
}
