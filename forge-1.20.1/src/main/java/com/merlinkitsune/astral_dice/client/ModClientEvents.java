package com.merlinkitsune.astral_dice.client;

import com.merlinkitsune.astral_dice.AstralDiceMod;
import com.merlinkitsune.astral_dice.screen.CardInventoryScreen;
import com.merlinkitsune.astral_dice.screen.ModMenuTypes;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector4f;

@Mod.EventBusSubscriber(modid = AstralDiceMod.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public class ModClientEvents {

    @SubscribeEvent
    public static void registerGuiOverlays(RegisterGuiOverlaysEvent event) {
        // 1.20.1:registerAbove 的 id 参数是纯 path,Forge 自动拼 modid 前缀
        event.registerAbove(VanillaGuiOverlay.CROSSHAIR.id(),
                "damage_number", DamageNumberOverlay.INSTANCE);
        event.registerAbove(VanillaGuiOverlay.CROSSHAIR.id(),
                "target_select", TargetSelectOverlay.INSTANCE);
        event.registerAbove(VanillaGuiOverlay.AIR_LEVEL.id(),
                "action_bar", ActionBarOverlay.INSTANCE);
    }

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() ->
                net.minecraft.client.gui.screens.MenuScreens.register(
                        ModMenuTypes.CARD_INVENTORY.get(), CardInventoryScreen::new));
    }

    public static class ActionBarOverlay implements IGuiOverlay {
        public static final ActionBarOverlay INSTANCE = new ActionBarOverlay();

        @Override
        public void render(ForgeGui gui, GuiGraphics guiGraphics, float partialTick, int width, int height) {
            ActionBarManager.render(guiGraphics, partialTick);
        }
    }

    @SubscribeEvent
    public static void registerKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(KeyBindingSetup.ACTIVATE_SIGN_KEY);
        event.register(KeyBindingSetup.OPEN_CARD_INVENTORY_KEY);
        event.register(KeyBindingSetup.CONFIRM_TARGET_KEY);
    }

    public static class DamageNumberOverlay implements IGuiOverlay {
        public static final DamageNumberOverlay INSTANCE = new DamageNumberOverlay();

        @Override
        public void render(ForgeGui gui, GuiGraphics guiGraphics, float partialTick, int width, int height) {
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
