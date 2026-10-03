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
                // 2026-10-03 用户裁决「按键收口」:选择会话期间(含「手持即选择」的效果牌会话),
                // 主动技能键 = **收起**(取消选择器),不再穿透成立牌主动技能;
                // 收起后服务端写抑制闩(牌仍在主手期间选择器不自动重开) ⇒ 之后再按 J 自然落到立牌主动技能,
                // 该键不会被永久吞掉(本行撤销 2026-09-24 对「手持即选择」会话的豁免)。
                if (TargetSelectionClient.isActive()) {
                    // 目标选择期间按下主动技能键 = 收起(取消选择,不触发立牌技能)
                    TargetSelectionClient.logPrompt("j", "cancel");
                    TargetSelectionClient.cancel("key");
                } else {
                    ModNetwork.sendToServer(new ModNetwork.SignActivateMessage());
                }
            }
            while (OPEN_CARD_INVENTORY_KEY.consumeClick()) {
                // 2026-10-03「按键收口」:卡牌栏键不再被「手持即选择」会话吞掉 —— 该类会话**本来就不因
                // 开界面而取消**(见 TargetSelectionClient#onScreenOpening),吞掉它两者自相矛盾;
                // 按键开启的立牌会话照旧不放行(它一开界面就取消,让 H 生效只会白丢会话)。
                if (!TargetSelectionClient.isActive() || TargetSelectionClient.isHoldToSelect()) {
                    ModNetwork.sendToServer(new ModNetwork.OpenCardInventoryMessage());
                }
            }
        }
    }
}
