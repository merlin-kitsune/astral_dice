package com.merlinkitsune.astral_dice.client;

import com.merlinkitsune.astral_dice.AstralDiceMod;
import com.merlinkitsune.astral_dice.network.TargetSelectCancelPayload;
import com.merlinkitsune.astral_dice.network.TargetSelectConfirmPayload;
import com.merlinkitsune.astral_dice.target.TargetType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 目标选择器客户端状态机（第一人称 UX）。
 *
 * 职责：
 * - 接收服务端 {@code TargetSelectStartPayload} 进入选择模式（HUD 提示、输入接管、高亮）；
 * - 每 tick 沿准星射线（{@code player.pick(radius,...)}）计算当前有效目标（按会话 targetType 过滤）；
 * - 右键 / Enter 确认（发送 {@link TargetSelectConfirmPayload}），Esc / 再按主动技能键 / 第三方界面打开取消；
 * - 选择期间拦截鼠标操作（左键攻击、右键原使用、滚轮），键盘拦截由
 *   {@code KeyboardHandlerMixin} 完成（本类提供白名单判定 {@link #isAllowedInputKey}）。
 */
@EventBusSubscriber(modid = AstralDiceMod.MODID, value = Dist.CLIENT)
public final class TargetSelectionClient {
    private static final Logger LOGGER = LoggerFactory.getLogger(TargetSelectionClient.class);

    /** 高亮颜色：友方绿 / 敌对红 / 中立黄 */
    public static final int COLOR_FRIENDLY = 0x55FF55;
    public static final int COLOR_HOSTILE = 0xFF5555;
    public static final int COLOR_NEUTRAL = 0xFFFF55;

    private static boolean active;
    private static int token;
    private static TargetType targetType = TargetType.LIVING;
    private static double radius = 16.0;
    private static long expireTick;
    private static String actionId = "";
    private static LivingEntity currentTarget;
    private static long noTargetFlashUntil;

    private TargetSelectionClient() {
    }

    // === 状态查询（Overlay / Mixin / KeyBindingSetup 共用） ===

    public static boolean isActive() {
        Minecraft mc = Minecraft.getInstance();
        return active && mc.player != null && mc.level != null;
    }

    public static LivingEntity currentTarget() {
        return currentTarget;
    }

    public static boolean noTargetFlash() {
        Minecraft mc = Minecraft.getInstance();
        return mc.level != null && mc.level.getGameTime() < noTargetFlashUntil;
    }

    public static int highlightColor(LivingEntity target) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && isFriendly(mc.player, target)) return COLOR_FRIENDLY;
        if (isHostile(target)) return COLOR_HOSTILE;
        return COLOR_NEUTRAL;
    }

    public static boolean isHostile(LivingEntity entity) {
        return entity instanceof Enemy;
    }

    public static boolean isFriendly(Player selector, LivingEntity entity) {
        if (entity instanceof Player other) {
            return selector.getTeam() != null && selector.getTeam() == other.getTeam();
        }
        if (entity instanceof OwnableEntity ownable) {
            return selector.getUUID().equals(ownable.getOwnerUUID());
        }
        return false;
    }

    /**
     * 键盘白名单（由 {@code KeyboardHandlerMixin} 调用）：移动键 + 确认键 + 主动技能键放行，
     * 其余键盘按键在选择期间全部拦截（含 F3/E/T/H 与第三方模组按键）。
     */
    public static boolean isAllowedInputKey(int key, int scanCode) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.options == null) return false;
        return mc.options.keyUp.matches(key, scanCode)
                || mc.options.keyDown.matches(key, scanCode)
                || mc.options.keyLeft.matches(key, scanCode)
                || mc.options.keyRight.matches(key, scanCode)
                || mc.options.keyJump.matches(key, scanCode)
                || mc.options.keyShift.matches(key, scanCode)
                || mc.options.keySprint.matches(key, scanCode)
                || KeyBindingSetup.CONFIRM_TARGET_KEY.matches(key, scanCode)
                || KeyBindingSetup.ACTIVATE_SIGN_KEY.matches(key, scanCode);
    }

    // === 会话生命周期 ===

    /** 服务端下发选择会话开始（TargetSelectStartPayload 处理器调用，主线程） */
    public static void start(int newToken, int targetTypeOrd, double newRadius, int durationTicks, String newActionId) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;
        token = newToken;
        targetType = TargetType.values()[Math.max(0, Math.min(targetTypeOrd, TargetType.values().length - 1))];
        radius = Math.max(1.0, newRadius);
        expireTick = mc.level.getGameTime() + Math.max(1, durationTicks);
        actionId = newActionId;
        active = true;
        currentTarget = null;
        noTargetFlashUntil = 0;
        // 清除遗留的左键按下状态：选择期间攻击键被接管，避免进入前长按导致持续攻击
        mc.options.keyAttack.setDown(false);
        LOGGER.debug("[Astral Dice][TargetSelectionClient] start token={} type={} radius={} expire={} action={}",
                token, targetType, radius, expireTick, actionId);
    }

    /** 客户端主循环 tick（由 ClientTickHandler 驱动）：射线目标更新 + 超时取消 */
    public static void tick() {
        if (!isActive()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level.getGameTime() >= expireTick) {
            LOGGER.debug("[Astral Dice][TargetSelectionClient] cancel (expired) token={}", token);
            cancel("expired");
            return;
        }
        updateRaycastTarget(mc);
    }

    /** 右键 / Enter 确认：向服务端发送确认包并退出选择模式 */
    public static void confirm() {
        if (!isActive()) return;
        if (currentTarget == null) {
            Minecraft mc = Minecraft.getInstance();
            noTargetFlashUntil = mc.level.getGameTime() + 40;
            LOGGER.debug("[Astral Dice][TargetSelectionClient] confirm ignored: no valid target (token={})", token);
            return;
        }
        int targetId = currentTarget.getId();
        LOGGER.debug("[Astral Dice][TargetSelectionClient] confirm sent token={} target={}({})",
                token, targetId, currentTarget.getName().getString());
        PacketDistributor.sendToServer(new TargetSelectConfirmPayload(token, targetId));
        deactivate();
    }

    /** 取消选择（Esc 由 Mixin 转发到这里；再按主动技能键 / 第三方界面打开由事件转发） */
    public static void cancel(String reason) {
        if (!isActive()) return;
        LOGGER.debug("[Astral Dice][TargetSelectionClient] cancel token={} ({})", token, reason);
        PacketDistributor.sendToServer(new TargetSelectCancelPayload(token));
        deactivate();
    }

    /** Esc 专用入口（KeyboardHandlerMixin 在 keyPress HEAD 调用；取消且不打开暂停界面） */
    public static void cancelByEscape() {
        cancel("esc");
    }

    private static void deactivate() {
        active = false;
        currentTarget = null;
        Minecraft mc = Minecraft.getInstance();
        if (mc.options != null) {
            mc.options.keyAttack.setDown(false);
        }
    }

    private static void updateRaycastTarget(Minecraft mc) {
        if (!(mc.player instanceof LocalPlayer player)) {
            currentTarget = null;
            return;
        }
        HitResult hit = player.pick(radius, 1.0F, false);
        LivingEntity newTarget = null;
        if (hit instanceof EntityHitResult entityHit
                && entityHit.getEntity() instanceof LivingEntity living
                && living != player
                && living.isAlive()
                && targetType.matches(player, living)
                && player.distanceToSqr(living) <= radius * radius) {
            newTarget = living;
        }
        if (newTarget != currentTarget) {
            if (newTarget != null) {
                LOGGER.debug("[Astral Dice][TargetSelectionClient] target={}({}) hostile={} friendly={}",
                        newTarget.getId(), newTarget.getName().getString(),
                        isHostile(newTarget), isFriendly(player, newTarget));
            } else {
                LOGGER.debug("[Astral Dice][TargetSelectionClient] target=none");
            }
            currentTarget = newTarget;
        }
    }

    // === 输入事件（游戏总线，仅客户端） ===

    /** 选择期间接管鼠标：右键=确认，其余按钮（左键攻击/中键）全部拦截 */
    @SubscribeEvent
    public static void onMouseButton(InputEvent.MouseButton.Pre event) {
        if (!isActive()) return;
        if (event.getButton() == GLFW.GLFW_MOUSE_BUTTON_RIGHT && event.getAction() == GLFW.GLFW_PRESS) {
            confirm();
        }
        event.setCanceled(true);
    }

    /** 选择期间拦截滚轮（防切栏/缩放等） */
    @SubscribeEvent
    public static void onMouseScroll(InputEvent.MouseScrollingEvent event) {
        if (isActive()) {
            event.setCanceled(true);
        }
    }

    /** 防御：任何第三方界面被打开时取消选择（正常情况下键盘/鼠标已被拦截，不会走到这里） */
    @SubscribeEvent
    public static void onScreenOpening(ScreenEvent.Opening event) {
        if (isActive() && event.getScreen() != null) {
            cancel("screen");
        }
    }
}
