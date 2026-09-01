package com.merlinkitsune.astral_dice.client;

import com.merlinkitsune.astral_dice.AstralDiceMod;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

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
        // 键盘确认键(默认 Enter):选择激活时消费并确认
        if (TargetSelectionClient.isActive() && KeyBindingSetup.CONFIRM_TARGET_KEY.consumeClick()) {
            TargetSelectionClient.confirm();
        }
    }
}
