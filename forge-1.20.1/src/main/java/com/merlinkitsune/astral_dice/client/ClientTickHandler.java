package com.merlinkitsune.astral_dice.client;

import com.merlinkitsune.astral_dice.AstralDiceMod;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.event.TickEvent;

/**
 * 客户端游戏总线 tick 订阅(游戏事件总线;注册类事件如 GuiLayers/KeyMappings 在模组总线,
 * 见 {@link ModClientEvents},两类事件不可混挂在同一订阅器上)。
 */
@Mod.EventBusSubscriber(modid = AstralDiceMod.MODID, value = Dist.CLIENT)
public class ClientTickHandler {

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        // ⚠️ 伤害数字的存活 tick 每客户端 tick 只能推进**一次**,且只认 END 相位:
        // Forge 的 TickEvent.ClientTickEvent 每 tick 派发 START + END 两次,1.21.1 对应的是
        // ClientTickEvent.Post(每 tick 一次)。此前这里不带相位判断(2 次/tick)、
        // KeyBindingSetup 又调了一次(1 次/tick)⇒ 共 3 次/tick,同一个 DURATION=40 在
        // 1.20.1 只存活约 0.67 秒(1.21.1 为 2 秒),表现为「伤害数字几乎看不到」。
        if (event.phase == TickEvent.Phase.END) {
            ClientDamageNumbers.tick();
        }
        // 效果牌「一次按下只出一张」:松开右键即复位长按标记(见 EffectCardUseGuard)
        EffectCardUseGuard.onClientTick();
    }
}
