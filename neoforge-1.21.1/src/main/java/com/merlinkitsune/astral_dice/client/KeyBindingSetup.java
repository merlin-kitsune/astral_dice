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
                // 2026-10-05 用户裁决「主动技能键不再参与选择器」:任何选择会话进行中,按主动技能键
                // **都照常向服务端请求立牌主动技能**,不再把它当作「取消选择」
                // (撤销 2026-10-03 的「按键收口」口径)。选择器的取消改由鼠标(右键 / 右键+潜行)与
                // ESC 菜单承担 —— 主动技能的释放权完全归玩家。
                // ⚠️ 服务端仍有自己的闸门:BaseSignItem#performSkill 第 2 步对「按键开启的」会话
                //    直接 return(防重复进入);「手持即选择」会话不在其列 ⇒ 手持效果牌时按键照常生效。
                PacketDistributor.sendToServer(new SignActivatePayload());
            }
            while (OPEN_CARD_INVENTORY_KEY.consumeClick()) {
                // 2026-10-03「按键收口」:卡牌栏键不再被「手持即选择」会话吞掉 —— 该类会话**本来就不因
                // 开界面而取消**(见 TargetSelectionClient#onScreenOpening),吞掉它两者自相矛盾;
                // 按键开启的立牌会话照旧不放行(它一开界面就取消,让 H 生效只会白丢会话)。
                if (!TargetSelectionClient.isActive() || TargetSelectionClient.isHoldToSelect()) {
                    PacketDistributor.sendToServer(new OpenCardInventoryPayload());
                }
            }
        }
    }
}
