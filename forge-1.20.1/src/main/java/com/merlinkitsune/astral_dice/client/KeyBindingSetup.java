package com.merlinkitsune.astral_dice.client;

import com.merlinkitsune.astral_dice.AstralDiceMod;
import com.merlinkitsune.astral_dice.network.ModNetwork.OpenCardInventoryMessage;
import com.merlinkitsune.astral_dice.network.ModNetwork.SignActivateMessage;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.event.TickEvent;
import com.merlinkitsune.astral_dice.network.ModNetwork;
import org.lwjgl.glfw.GLFW;

public class KeyBindingSetup {
    public static final KeyMapping ACTIVATE_SIGN_KEY = new KeyMapping(
            "key.astral_dice.activate_sign",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_J,
            "key.categories.astral_dice"
    );

    public static final KeyMapping OPEN_CARD_INVENTORY_KEY = new KeyMapping(
            "key.astral_dice.open_card_inventory",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_H,
            "key.categories.astral_dice"
    );

    // 目标选择器无独立键盘确认键：确认 = 鼠标左键、取消 = 右键+潜行 / ESC 菜单
    // （Create 强力胶式语义；旧的 Enter 确认键与客户端键盘拦截 Mixin 已删除）

    @Mod.EventBusSubscriber(modid = AstralDiceMod.MODID, value = Dist.CLIENT)
    public static class ClientEvents {
        @SubscribeEvent
        public static void onClientTick(TickEvent.ClientTickEvent event) {
            if (event.phase != TickEvent.Phase.END) return;
            // ⚠️ 此处**不得**再调 ClientDamageNumbers.tick():它是「每客户端 tick 恰好一次」的状态推进,
            // 唯一调用点在 ClientTickHandler(END 相位)。历史上这两处同时调用导致 1.20.1 的
            // 伤害数字淡出速度为 1.21.1 的 3 倍(见 AGENTS.md「客户端 tick 状态推进」)。
            var player = Minecraft.getInstance().player;
            if (player == null) return;

            while (ACTIVATE_SIGN_KEY.consumeClick()) {
                if (TargetSelectionClient.isActive()) {
                    // 目标选择期间再次按下主动技能键 = 取消选择(不触发立牌技能)
                    TargetSelectionClient.logPrompt("j", "cancel");
                    TargetSelectionClient.cancel("key");
                } else {
                    ModNetwork.sendToServer(new ModNetwork.SignActivateMessage());
                }
            }
            while (OPEN_CARD_INVENTORY_KEY.consumeClick()) {
                if (!TargetSelectionClient.isActive()) {
                    ModNetwork.sendToServer(new ModNetwork.OpenCardInventoryMessage());
                }
            }
        }
    }
}
