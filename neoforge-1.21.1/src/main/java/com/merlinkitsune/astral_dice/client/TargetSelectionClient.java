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
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
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
    // 激活后的 tick 计数:选择激活前 0.5 秒(10 tick)内忽略 Enter 键盘确认,
    // 避免聊天命令发送的 Enter 残留 KeyMapping.click 在选择刚激活瞬间误确认(真实 UX 修复)
    private static int activeTicks;

    private TargetSelectionClient() {
    }

    // === 状态查询（Overlay / Mixin / KeyBindingSetup 共用） ===

    public static boolean isActive() {
        Minecraft mc = Minecraft.getInstance();
        return active && mc.player != null && mc.level != null;
    }

    /** 键盘确认(Enter)是否已就绪:选择激活 10 tick(0.5s)后才接受,防残留点击误确认 */
    public static boolean isConfirmReady() {
        return activeTicks >= 10;
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
     * 键盘白名单（由 {@code KeyboardHandlerMixin} 调用）：移动键 + 确认键 + 主动技能键 + 命令聊天键(/)放行，
     * 其余键盘按键在选择期间全部拦截（含 F3/E/T(普通聊天)/H 与第三方模组按键）。
     * 放行 keyCommand(/)是刻意的:选择期间允许玩家打开命令聊天(输指令/自动化测试注入命令),
     * 普通聊天(T)与物品栏(E)等仍被拦截。
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
                || mc.options.keyCommand.matches(key, scanCode)
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
        activeTicks = 0;
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
        activeTicks++;
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
        // 实体射线:注意 1.21.1 的 Entity.pick() 只做方块射线(永不返回 EntityHitResult),
        // 须参照 GameRenderer.pick 的标准做法:方块射线截断 + ProjectileUtil.getEntityHitResult 找最近实体。
        double maxDist = radius;
        Vec3 eye = player.getEyePosition(1.0F);
        Vec3 look = player.getViewVector(1.0F);
        HitResult blockHit = player.pick(maxDist, 1.0F, false); // 方块射线(Entity.pick 内部为 OUTLINE clip)
        double blockDistSq = blockHit.getLocation().distanceToSqr(eye);
        // 有方块命中时,实体搜索终点截断到方块处(准星被方块挡住时不应隔墙选中目标)
        double entityLimitSq = blockHit.getType() != HitResult.Type.MISS ? blockDistSq : maxDist * maxDist;
        double entityLimit = Math.sqrt(entityLimitSq);
        Vec3 entityEnd = eye.add(look.x * entityLimit, look.y * entityLimit, look.z * entityLimit);
        AABB searchBox = player.getBoundingBox().expandTowards(look.scale(entityLimit)).inflate(1.0, 1.0, 1.0);
        EntityHitResult entityHit = ProjectileUtil.getEntityHitResult(player, eye, entityEnd, searchBox,
                e -> !e.isSpectator() && e.isPickable(), entityLimitSq);

        LivingEntity newTarget = null;
        if (entityHit != null
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

    /** 键盘确认键(默认 Enter)物理按下时确认。
     *  用 InputEvent.Key 而非 KeyMapping.click 队列:聊天框打开时按 Enter 发送命令会被
     *  ChatScreen 消费(键盘事件提前 return,不触发本事件),故聊天注入命令不会误确认目标;
     *  仅游戏画面下物理按下 Enter 才确认(且须确认就绪,防触发瞬间残留)。 */
    @SubscribeEvent
    public static void onKey(InputEvent.Key event) {
        if (!isActive() || event.getAction() != GLFW.GLFW_PRESS) return;
        if (!isConfirmReady()) return;
        if (KeyBindingSetup.CONFIRM_TARGET_KEY.matches(event.getKey(), event.getScanCode())) {
            confirm();
        }
    }

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

    /** 防御：任何第三方界面被打开时取消选择（聊天框 ChatScreen 除外——命令聊天是白名单放行的，
     *  选择期间允许输指令/测试注入，不应取消选择；正常情况下其他键盘/鼠标已被拦截不会走到这里） */
    @SubscribeEvent
    public static void onScreenOpening(ScreenEvent.Opening event) {
        if (isActive() && event.getScreen() != null
                && !(event.getScreen() instanceof net.minecraft.client.gui.screens.ChatScreen)) {
            cancel("screen");
        }
    }
}
