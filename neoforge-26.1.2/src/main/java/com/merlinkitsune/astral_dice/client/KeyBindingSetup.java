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
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import org.lwjgl.glfw.GLFW;

public class KeyBindingSetup {
    /**
     * 按键分类。
     * <p><b>26.1.2 迁移</b>:1.21.1 的 {@code KeyMapping} 构造器接受**字符串**分类名(配合
     * {@code key.categories.astral_dice} 翻译键);26.1.2 改为 {@code KeyMapping.Category} 记录类型,
     * 其 {@code label()} = {@code Component.translatable(id.toLanguageKey("key.category"))} ⇒
     * 翻译键为 {@code key.category.<namespace>.<path>},故本模组注册 {@code astral_dice:main},
     * 语言文件使用 {@code key.category.astral_dice.main}。
     */
    public static final KeyMapping.Category CATEGORY =
            KeyMapping.Category.register(
                    net.minecraft.resources.Identifier.fromNamespaceAndPath(AstralDiceMod.MODID, "main"));

    public static final KeyMapping ACTIVATE_SIGN_KEY = new KeyMapping(
            "key.astral_dice.activate_sign",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_J,
            CATEGORY
    );

    public static final KeyMapping OPEN_CARD_INVENTORY_KEY = new KeyMapping(
            "key.astral_dice.open_card_inventory",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_H,
            CATEGORY
    );

    @EventBusSubscriber(modid = AstralDiceMod.MODID, value = Dist.CLIENT)
    public static class ClientEvents {
        @SubscribeEvent
        public static void onClientTick(ClientTickEvent.Post event) {
            var player = Minecraft.getInstance().player;
            if (player == null) return;

            while (ACTIVATE_SIGN_KEY.consumeClick()) {
                ClientPacketDistributor.sendToServer(new SignActivatePayload());
            }
            while (OPEN_CARD_INVENTORY_KEY.consumeClick()) {
                ClientPacketDistributor.sendToServer(new OpenCardInventoryPayload());
            }
        }
    }
}
