package com.merlinkitsune.astral_dice.client;

import com.merlinkitsune.astral_dice.AstralDiceMod;
import com.merlinkitsune.astral_dice.network.OpenCardInventoryPayload;
import com.merlinkitsune.astral_dice.network.SignActivatePayload;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
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

    @EventBusSubscriber(modid = AstralDiceMod.MODID, value = Dist.CLIENT)
    public static class ClientEvents {
        @SubscribeEvent
        public static void onClientTick(ClientTickEvent.Post event) {
            var player = Minecraft.getInstance().player;
            if (player == null) return;

            while (ACTIVATE_SIGN_KEY.consumeClick()) {
                // 「按键开启」的选择会话(立牌主动技能的目标选择)才把主动技能键当作「取消选择」;
                // ⚠️ 「手持即选择」会话(效果牌握在主手时自动开启,玩家从未按过键)不得吞掉该键 ——
                //    否则玩家只要握着选择器类效果牌就永远触发不了立牌主动技能(2026-09-24 用户报
                //    BUG:「手持活体书页时,无法触发主动技能」)。该类会话的取消手势是 下蹲+右键 / 移出主手。
                if (TargetSelectionClient.isActive() && !TargetSelectionClient.isHoldToSelect()) {
                    // 目标选择期间再次按下主动技能键 = 取消选择(不触发立牌技能)
                    TargetSelectionClient.logPrompt("j", "cancel");
                    TargetSelectionClient.cancel("key");
                } else {
                    PacketDistributor.sendToServer(new SignActivatePayload());
                }
            }
            while (OPEN_CARD_INVENTORY_KEY.consumeClick()) {
                if (!TargetSelectionClient.isActive()) {
                    PacketDistributor.sendToServer(new OpenCardInventoryPayload());
                }
            }
        }
    }
}
