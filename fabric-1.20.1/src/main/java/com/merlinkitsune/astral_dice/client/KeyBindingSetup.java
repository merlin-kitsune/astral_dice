package com.merlinkitsune.astral_dice.client;

import com.merlinkitsune.astral_dice.AstralDiceMod;
import com.merlinkitsune.astral_dice.network.ModNetwork.OpenCardInventoryMessage;
import com.merlinkitsune.astral_dice.network.ModNetwork.SignActivateMessage;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import com.merlinkitsune.astral_dice.platform.event.SubscribeEvent;
import com.merlinkitsune.astral_dice.platform.event.TickEvent;
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
                // 2026-10-05 用户裁决「主动技能键不再参与选择器」:任何选择会话进行中,按主动技能键
                // **都照常向服务端请求立牌主动技能**,不再把它当作「取消选择」
                // (撤销 2026-10-03 的「按键收口」口径)。选择器的取消改由鼠标(右键 / 右键+潜行)与
                // ESC 菜单承担 —— 主动技能的释放权完全归玩家。
                // ⚠️ 服务端仍有自己的闸门:BaseSignItem#performSkill 第 2 步对「按键开启的」会话
                //    直接 return(防重复进入);「手持即选择」会话不在其列 ⇒ 手持效果牌时按键照常生效。
                ModNetwork.sendToServer(new ModNetwork.SignActivateMessage());
            }
            while (OPEN_CARD_INVENTORY_KEY.consumeClick()) {
                if (!TargetSelectionClient.isActive() || TargetSelectionClient.isHoldToSelect()) {
                    ModNetwork.sendToServer(new ModNetwork.OpenCardInventoryMessage());
                }
            }
        }
    }
}
