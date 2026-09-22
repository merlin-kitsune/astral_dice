package com.merlinkitsune.astral_dice.client;

import com.merlinkitsune.astral_dice.AstralDiceMod;
import com.merlinkitsune.astral_dice.screen.CardInventoryScreen;
import com.merlinkitsune.astral_dice.screen.ModMenuTypes;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.neoforged.neoforge.client.gui.GuiLayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import com.merlinkitsune.starenginelib.client.ActionBarManager;
import com.merlinkitsune.starenginelib.client.ClientDamageNumbers;

// ⚠️ 26.1.2 的 @EventBusSubscriber **只剩 value()/modid()** —— FML 11.0.15 的该注解
//    (loader-11.0.15.jar, javap 实证)已删除 `bus()` 与内部枚举 `EventBusSubscriber$Bus`
//    (1.21.1 的 FML 4.0.42 仍有 `public abstract EventBusSubscriber$Bus bus()`)。
//    故此处**不得**照抄 1.21.1 的 `bus = EventBusSubscriber.Bus.MOD`(会报「找不到符号: 方法 bus()」)。
//    语义等价:26.1.2 下本类的 @SubscribeEvent 静态方法一律视为 mod 总线订阅
//    ——本类注册的正是 RegisterMenuScreensEvent / RegisterGuiLayersEvent / RegisterKeyMappingsEvent
//    三个 mod 总线事件,方向一致。
@EventBusSubscriber(modid = AstralDiceMod.MODID, value = Dist.CLIENT)
public class ModClientEvents {

    // 菜单界面注册:本类带 @EventBusSubscriber(Dist.CLIENT),**在专用服务端整体不会被加载**,
    // 故此处引用纯客户端的 CardInventoryScreen 是安全的。
    // ⚠️ 不要把这个注册挪回 AstralDiceMod(主入口类服务端也加载,运行时 dist 分支拦不住符号解析):
    //     AstralDiceMod 构造函数里的 `if (getDist() == Dist.CLIENT) addListener(this::registerScreens)`
    //     会在常量池留下 BootstrapMethods 项 `REF_newInvokeSpecial CardInventoryScreen.<init>`,
    //     该项由**类初始化时的每个 invokedynamic 解析**触发(§5.4.3.6)⇒ 专用服务端启动即
    //     NoClassDefFoundError: CardInventoryScreen(除非它是纯服务端 mod,才会被 strip 掉)。
    //     与 forge-1.20.1 侧 ModClientEvents#onClientSetup 的写法保持对等。
    @SubscribeEvent
    public static void registerScreens(RegisterMenuScreensEvent event) {
        event.register(ModMenuTypes.CARD_INVENTORY.get(), CardInventoryScreen::new);
    }

    @SubscribeEvent
    public static void registerGuiLayers(RegisterGuiLayersEvent event) {
        event.registerAbove(VanillaGuiLayers.CROSSHAIR,
                Identifier.fromNamespaceAndPath(AstralDiceMod.MODID, "damage_number"),
                DamageNumberOverlay.INSTANCE);
        // 目标选择器中央 HUD:注册 id / 锚点与两发布线逐字对齐
        // （1.21.1 `client/ModClientEvents.java:33-35`,锚点 CROSSHAIR,id `target_select`）
        event.registerAbove(VanillaGuiLayers.CROSSHAIR,
                Identifier.fromNamespaceAndPath(AstralDiceMod.MODID, "target_select"),
                TargetSelectOverlay.INSTANCE);
        event.registerAbove(VanillaGuiLayers.AIR_LEVEL,
                Identifier.fromNamespaceAndPath(AstralDiceMod.MODID, "action_bar"),
                ActionBarOverlay.INSTANCE);
    }

    public static class ActionBarOverlay implements GuiLayer {
        public static final ActionBarOverlay INSTANCE = new ActionBarOverlay();

        @Override
        public void render(GuiGraphicsExtractor guiGraphics, DeltaTracker deltaTracker) {
            // F1（隐藏 HUD）守卫:与 TargetSelectOverlay 同源（条件与理由见该文件注释）——
            // 26.1.2 原版只把**原版层**包进 `guiVisible`、模组层不被包裹（GuiLayerManager.java:36-39）
            // ⇒ 必须自行守,否则按 F1 时本模组 actionbar 提示仍显示。
            Minecraft mc = Minecraft.getInstance();
            if (mc.options.hideGui && mc.screen == null) return;
            ActionBarManager.render(guiGraphics, deltaTracker);
        }
    }

    @SubscribeEvent
    public static void registerKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(KeyBindingSetup.ACTIVATE_SIGN_KEY);
        event.register(KeyBindingSetup.OPEN_CARD_INVENTORY_KEY);
    }

    public static class DamageNumberOverlay implements GuiLayer {
        public static final DamageNumberOverlay INSTANCE = new DamageNumberOverlay();

        @Override
        public void render(GuiGraphicsExtractor guiGraphics, DeltaTracker deltaTracker) {
            Minecraft mc = Minecraft.getInstance();
            LocalPlayer player = mc.player;
            if (player == null || mc.level == null || mc.options.hideGui) return;

            var activeNumbers = ClientDamageNumbers.getActiveNumbers();
            if (activeNumbers.isEmpty()) return;

            int screenWidth = guiGraphics.guiWidth();
            int screenHeight = guiGraphics.guiHeight();

            var poseStack = guiGraphics.pose();
            poseStack.pushMatrix();

            for (var entry : activeNumbers.entrySet()) {
                Entity entity = mc.level.getEntity(entry.getKey());
                if (entity == null) continue;
                if (!(entity instanceof LivingEntity living)) continue;

                var number = entry.getValue();
                Vec3 pos = entity.getEyePosition().add(0, -0.5, 0);
                var camera = mc.gameRenderer.getMainCamera();
                var camPos = camera.position();
                var clipPos = new Vector4f(
                    (float)(pos.x - camPos.x),
                    (float)(pos.y - camPos.y),
                    (float)(pos.z - camPos.z),
                    1.0f
                );
                // 视图×投影矩阵一律取**本版本原版**的相机实现,禁止跨版本抄公式:
                //   MC 26.1.2 `Camera#getViewRotationProjectionMatrix`(Camera.java:409-419)
                //   = projection × viewRotation,其中 viewRotation = rotation().conjugate()
                //   (同文件 :399-407 `getViewRotationMatrix`);`LevelRenderer:583` 用同一个
                //   `getViewRotationMatrix`,`GameRenderer:851` 用的就是本方法。
                // ⇒ 26.1.2 侧不再手算 Quaternionf、也不再用 `GameRenderer#getProjectionMatrix(double)`
                //   (该签名的 public 访问器在 26.1.2 已不存在);1.21.1 的 conjugate 写法与
                //   1.20.1 的 `Axis.XP/Ry(yRot+180)` 写法都**不得**带进本版本。
                var mvp = camera.getViewRotationProjectionMatrix(new Matrix4f());
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

                // 数显只给数值、不加 "+" 前缀(2026-09-19 用户要求:攻击伤与法伤一并移除)
                String text = Integer.toString(number.damage);
                int textWidth = mc.font.width(text);
                poseStack.pushMatrix();
                // 26.1.2 的 pose() 是 JOML Matrix3x2fStack,translate/scale 只接受 float
                poseStack.translate((float)(x - textWidth / 2.0), (float)(y + yOffset));
                poseStack.scale(1.2f, 1.2f);
                guiGraphics.text(mc.font, text, 0, 0, color, true);
                poseStack.popMatrix();
            }

            poseStack.popMatrix();
        }
    }
}
