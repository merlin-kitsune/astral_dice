package com.merlinkitsune.astral_dice.client;

import com.merlinkitsune.astral_dice.AstralDiceMod;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.LayeredDraw;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector4f;

import com.merlinkitsune.starenginelib.client.ActionBarManager;
import com.merlinkitsune.starenginelib.client.ClientDamageNumbers;
@EventBusSubscriber(modid = AstralDiceMod.MODID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public class ModClientEvents {

    @SubscribeEvent
    public static void registerGuiLayers(RegisterGuiLayersEvent event) {
        event.registerAbove(VanillaGuiLayers.CROSSHAIR,
                ResourceLocation.fromNamespaceAndPath(AstralDiceMod.MODID, "damage_number"),
                DamageNumberOverlay.INSTANCE);
        event.registerAbove(VanillaGuiLayers.CROSSHAIR,
                ResourceLocation.fromNamespaceAndPath(AstralDiceMod.MODID, "target_select"),
                TargetSelectOverlay.INSTANCE);
        event.registerAbove(VanillaGuiLayers.AIR_LEVEL,
                ResourceLocation.fromNamespaceAndPath(AstralDiceMod.MODID, "action_bar"),
                ActionBarOverlay.INSTANCE);
    }

    public static class ActionBarOverlay implements LayeredDraw.Layer {
        public static final ActionBarOverlay INSTANCE = new ActionBarOverlay();

        @Override
        public void render(GuiGraphics guiGraphics, DeltaTracker deltaTracker) {
            // F1（隐藏 HUD）守卫:与 TargetSelectOverlay 同源。1.21.1 原版不整体跳过 gui.render,
            // 不自行守会让本模组的 actionbar 提示在 F1 下仍然显示（1.20.1 侧原版已整块跳过）⇒ 两线不一致。
            if (Minecraft.getInstance().options.hideGui) return;
            ActionBarManager.render(guiGraphics, deltaTracker);
        }
    }

    @SubscribeEvent
    public static void registerKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(KeyBindingSetup.ACTIVATE_SIGN_KEY);
        event.register(KeyBindingSetup.OPEN_CARD_INVENTORY_KEY);
        // 目标选择器不注册键盘确认键：确认 = 鼠标左键、取消 = 右键+潜行 / ESC 菜单（强力胶式语义）
    }

    public static class DamageNumberOverlay implements LayeredDraw.Layer {
        public static final DamageNumberOverlay INSTANCE = new DamageNumberOverlay();

        @Override
        public void render(GuiGraphics guiGraphics, DeltaTracker deltaTracker) {
            Minecraft mc = Minecraft.getInstance();
            LocalPlayer player = mc.player;
            if (player == null || mc.level == null || mc.options.hideGui) return;

            var activeNumbers = ClientDamageNumbers.getActiveNumbers();
            if (activeNumbers.isEmpty()) return;

            int screenWidth = guiGraphics.guiWidth();
            int screenHeight = guiGraphics.guiHeight();

            var poseStack = guiGraphics.pose();
            poseStack.pushPose();

            for (var entry : activeNumbers.entrySet()) {
                Entity entity = mc.level.getEntity(entry.getKey());
                if (entity == null) continue;
                if (!(entity instanceof LivingEntity living)) continue;

                var number = entry.getValue();
                Vec3 pos = entity.getEyePosition().add(0, -0.5, 0);
                var camera = mc.gameRenderer.getMainCamera();
                var camPos = camera.getPosition();
                var clipPos = new Vector4f(
                    (float)(pos.x - camPos.x),
                    (float)(pos.y - camPos.y),
                    (float)(pos.z - camPos.z),
                    1.0f
                );
                // 视矩阵必须与**本版本原版的世界渲染**同构:1.21.1 的 GameRenderer#renderLevel 正是
                //     Quaternionf q = camera.rotation().conjugate(new Quaternionf());
                //     Matrix4f view = new Matrix4f().rotation(q);
                // (neoforge 源 GameRenderer#renderLevel:1272-1273),故此处写法正确。
                // ⚠️ **禁止**把 1.20.1 侧的写法(`Axis.XP/Ry(yRot+180)`)「同步」到这里,也禁止把本式
                //    复制到 1.20.1:1.20.1 原版的视图旋转与此式相差绕 Y 的 180° 与 pitch 符号,
                //    照搬会让正前方目标的 w<0、被当作「相机背后」丢弃(伤害数字永不显示)。
                var rot = new Quaternionf(camera.rotation()).conjugate();
                var viewMatrix = new Matrix4f().rotation(rot);
                double fov = mc.options.fov().get();
                var projMatrix = mc.gameRenderer.getProjectionMatrix(fov);
                var mvp = new Matrix4f(projMatrix);
                mvp.mul(viewMatrix);
                mvp.transform(clipPos);
                if (clipPos.w <= 0) continue;
                clipPos.div(clipPos.w);
                Vec3 screenPos = new Vec3(clipPos.x, clipPos.y, clipPos.z);

                double x = screenPos.x * (double) screenWidth / 2.0 + (double) screenWidth / 2.0;
                double y = -screenPos.y * (double) screenHeight / 2.0 + (double) screenHeight / 2.0;

                if (x < 0 || x > screenWidth || y < 0 || y > screenHeight) continue;

                float progress = 1.0f - (float) number.remaining / 40.0f;
                int alpha = (int) ((1.0f - progress) * 255);
                int color = (alpha << 24) | (number.color & 0xFFFFFF);
                int yOffset = -(int) (progress * 30);

                String text = "+" + number.damage;
                int textWidth = mc.font.width(text);
                poseStack.pushPose();
                poseStack.translate(x - textWidth / 2.0f, y + yOffset, 0);
                poseStack.scale(1.2f, 1.2f, 1.2f);
                guiGraphics.drawString(mc.font, text, 0, 0, color, true);
                poseStack.popPose();
            }

            poseStack.popPose();
        }
    }
}
