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

    // 目标选择器无独立键盘确认键：确认 = 鼠标左键、取消 = 右键+潜行 / ESC 菜单
    // （Create 强力胶式语义；旧的 Enter 确认键与客户端键盘拦截 Mixin 已删除）
    // 26.1.2 移植 B2：选择期间 J 键 = 收起（取消选择）；H 键仅在「手持即选择」类会话下放行
    // （2026-10-03「按键收口」，见下方 ClientEvents）。

    @EventBusSubscriber(modid = AstralDiceMod.MODID, value = Dist.CLIENT)
    public static class ClientEvents {
        @SubscribeEvent
        public static void onClientTick(ClientTickEvent.Post event) {
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
                    ClientPacketDistributor.sendToServer(new SignActivatePayload());
                }
            }
            while (OPEN_CARD_INVENTORY_KEY.consumeClick()) {
                // 2026-10-03「按键收口」:卡牌栏键不再被「手持即选择」会话吞掉 —— 该类会话**本来就不因
                // 开界面而取消**(见 TargetSelectionClient#onScreenOpening),吞掉它两者自相矛盾;
                // 按键开启的立牌会话照旧不放行(它一开界面就取消,让 H 生效只会白丢会话)。
                if (!TargetSelectionClient.isActive() || TargetSelectionClient.isHoldToSelect()) {
                    ClientPacketDistributor.sendToServer(new OpenCardInventoryPayload());
                }
            }
        }
    }
}
