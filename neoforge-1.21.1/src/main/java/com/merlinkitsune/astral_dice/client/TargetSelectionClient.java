package com.merlinkitsune.astral_dice.client;

import com.merlinkitsune.astral_dice.AstralDiceMod;
import com.merlinkitsune.astral_dice.network.TargetSelectCancelPayload;
import com.merlinkitsune.astral_dice.network.TargetSelectConfirmPayload;
import com.merlinkitsune.starenginelib.client.ActionBarManager;
import com.merlinkitsune.starenginelib.target.TargetType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
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

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * 目标选择器客户端状态机（第一人称 UX，**Create 强力胶式按键语义**）。
 *
 * 按键口径（2026-09-17 用户裁决，“强力胶式”交互；本模组全部技能不开放自身使用）：
 * <ul>
 *   <li><b>左键</b> = 确认目标（发送 {@link TargetSelectConfirmPayload}）；</li>
 *   <li><b>右键</b> = 对自身使用 —— 本模组无任何技能可对自身使用，故只弹 actionbar 提示
 *       {@code msg.astral_dice.target_select.self_unsupported}，**不提交选择**（会话保留）；</li>
 *   <li><b>右键 + 潜行</b> = 取消选择；</li>
 *   <li><b>ESC</b> = 原版照常打开暂停菜单，菜单一打开（{@code ScreenEvent.Opening}）即取消选择；</li>
 *   <li><b>J</b>（主动技能键）= 取消选择（保留）；</li>
 *   <li>选择期间滚轮拦截、{@link ChatScreen} 豁免（命令聊天/自动化注入命令）均保留。</li>
 * </ul>
 *
 * 与旧实现的差异：删除了 Enter 键盘确认键与其 10 tick 就绪门槛、键盘白名单判定，
 * 以及客户端键盘拦截 Mixin（mixin 配置与匹配条目一并移除）—— 强力胶式语义下确认/取消全部
 * 由鼠标（左键/右键[+潜行]）与 ESC 菜单承担，键盘不再被模组吞掉（J 仍作取消，走 KeyMapping 消费）。
 *
 * 提示分工：中央 HUD 只画「目标名 + 距离」一行（见 {@link TargetSelectOverlay}），其余提示
 * 一律走 actionbar（每 tick 刷新，见 {@link #refreshActionBarPrompt}）。
 */
@EventBusSubscriber(modid = AstralDiceMod.MODID, value = Dist.CLIENT)
public final class TargetSelectionClient {
    private static final Logger LOGGER = LoggerFactory.getLogger(TargetSelectionClient.class);

    /** 高亮颜色：友方绿 / 敌对红 / 中立黄 */
    public static final int COLOR_FRIENDLY = 0x55FF55;
    public static final int COLOR_HOSTILE = 0xFF5555;
    public static final int COLOR_NEUTRAL = 0xFFFF55;

    /** actionbar 提示刷新时长（tick）：每 tick 续期 ⇒ 会话期间提示常驻 */
    private static final int ACTIONBAR_TICKS = 40;
    /** 半径内其它可选目标的渲染上限（取最近的若干个） */
    private static final int NEARBY_TARGET_LIMIT = 24;

    private static boolean active;
    private static int token;
    private static TargetType targetType = TargetType.LIVING;
    private static double radius = 16.0;
    private static long expireTick;
    private static String actionId = "";
    private static LivingEntity currentTarget;
    /** 准星命中但**不可选**的实体（类型不符 / 超出半径）：1/24 细边 + rejected 提示 */
    private static LivingEntity rejectedTarget;
    /** 半径内其它可选目标（1/64 细边，最多 {@link #NEARBY_TARGET_LIMIT} 个），每 tick 重建 */
    private static final List<LivingEntity> nearbyTargets = new ArrayList<>();
    /** 瞬态 actionbar 提示（优先于默认提示）；到期后恢复默认提示 */
    private static Component transientPrompt;
    private static long transientPromptUntil;

    private TargetSelectionClient() {
    }

    // === 状态查询（Overlay / Highlighter / KeyBindingSetup 共用） ===

    public static boolean isActive() {
        Minecraft mc = Minecraft.getInstance();
        return active && mc.player != null && mc.level != null;
    }

    public static LivingEntity currentTarget() {
        return currentTarget;
    }

    /** 准星命中但不可选的实体（渲染层用它画 1/24 细边） */
    public static LivingEntity rejectedTarget() {
        return rejectedTarget;
    }

    /** 半径内其它可选目标（渲染层用它画 1/64 细边）；只读视图，按距离由近到远 */
    public static List<LivingEntity> nearbyTargets() {
        return Collections.unmodifiableList(nearbyTargets);
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
        rejectedTarget = null;
        nearbyTargets.clear();
        transientPrompt = null;
        transientPromptUntil = 0;
        // 清除遗留的左键按下状态：选择期间攻击键被接管，避免进入前长按导致持续攻击
        mc.options.keyAttack.setDown(false);
        LOGGER.debug("[Astral Dice][TargetSelectionClient] start token={} type={} radius={} expire={} action={}",
                token, targetType, radius, expireTick, actionId);
        refreshActionBarPrompt(mc);
    }

    /** 客户端主循环 tick（由 ClientTickHandler 驱动）：射线目标更新 + actionbar 提示续期 + 超时取消 */
    public static void tick() {
        if (!isActive()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level.getGameTime() >= expireTick) {
            LOGGER.debug("[Astral Dice][TargetSelectionClient] cancel (expired) token={}", token);
            cancel("expired");
            return;
        }
        updateRaycastTarget(mc);
        refreshActionBarPrompt(mc);
    }

    /**
     * 左键 = 确认目标（强力胶式语义的确认键）。
     *
     * <p>无有效目标时**不提交**，只弹 actionbar `no_target` 提示（等价旧实现的「没有可指定的目标」闪烁）。
     */
    public static void confirmByPrimaryClick() {
        if (!isActive()) return;
        if (currentTarget == null) {
            LOGGER.debug("[Astral Dice][TargetSelectionClient] confirm ignored: no valid target (token={})", token);
            logPrompt("left", "no_target");
            showPrompt(Component.translatable("msg.astral_dice.target_select.no_target"));
            return;
        }
        logPrompt("left", "confirm");
        confirm();
    }

    /** 右键 = 对自身使用：本模组全部技能不开放自身使用 ⇒ 只提示，不提交选择（会话保留） */
    public static void useOnSelfBySecondaryClick() {
        if (!isActive()) return;
        logPrompt("right", "self_unsupported");
        showPrompt(Component.translatable("msg.astral_dice.target_select.self_unsupported"));
    }

    /** 确认：向服务端发送确认包并退出选择模式（调用方保证 currentTarget 有效） */
    public static void confirm() {
        if (!isActive() || currentTarget == null) return;
        int targetId = currentTarget.getId();
        LOGGER.debug("[Astral Dice][TargetSelectionClient] confirm sent token={} target={}({})",
                token, targetId, currentTarget.getName().getString());
        PacketDistributor.sendToServer(new TargetSelectConfirmPayload(token, targetId));
        deactivate();
    }

    /** 取消选择（右键+潜行 / J / ESC 菜单 / 第三方界面打开时调用） */
    public static void cancel(String reason) {
        if (!isActive()) return;
        LOGGER.debug("[Astral Dice][TargetSelectionClient] cancel token={} ({})", token, reason);
        PacketDistributor.sendToServer(new TargetSelectCancelPayload(token));
        deactivate();
        if (isUserCancel(reason)) {
            showPrompt(Component.translatable("msg.astral_dice.target_select.cancelled"));
        }
    }

    /** 用户主动取消（区别于超时/第三方界面）：给一条 `cancelled` actionbar 反馈 */
    private static boolean isUserCancel(String reason) {
        return "right_sneak".equals(reason) || "key".equals(reason) || "esc".equals(reason);
    }

    private static void deactivate() {
        active = false;
        currentTarget = null;
        rejectedTarget = null;
        nearbyTargets.clear();
        Minecraft mc = Minecraft.getInstance();
        if (mc.options != null) {
            mc.options.keyAttack.setDown(false);
        }
    }

    // === 提示（actionbar） ===

    /**
     * 强化胶式提示：每 tick 续期一次 actionbar（`ActionBarManager.show(component, 40)`）。
     *
     * <p>瞬态提示（自用不可用 / 无目标 / 目标不可选 / 已取消）在窗口期内优先，避免被默认
     * 「选择目标中」提示在同一 tick 内覆盖掉；窗口期过后自动回到默认提示。
     *
     * <p>注入点说明：`ActionBarManager` 正是服务端 {@code ActionBarPayload} 在客户端侧的落点
     * （见 `network/ModPayloads` 的处理器），故本客户端提示与服务端提示渲染在同一层、同一位置，
     * 不需要为一 tick 一次的本机提示绕一圈服务端网络。
     */
    private static void refreshActionBarPrompt(Minecraft mc) {
        if (mc.level == null) return;
        long now = mc.level.getGameTime();
        Component prompt;
        if (transientPrompt != null && now < transientPromptUntil) {
            prompt = transientPrompt;
        } else {
            transientPrompt = null;
            transientPromptUntil = 0;
            prompt = Component.translatable("msg.astral_dice.target_select.active");
        }
        ActionBarManager.show(prompt, ACTIONBAR_TICKS);
    }

    /** 显示一条瞬态 actionbar 提示（ACTIONBAR_TICKS 内不被默认提示覆盖） */
    private static void showPrompt(Component prompt) {
        transientPrompt = prompt;
        Minecraft mc = Minecraft.getInstance();
        transientPromptUntil = (mc.level == null ? 0L : mc.level.getGameTime()) + ACTIONBAR_TICKS;
        ActionBarManager.show(prompt, ACTIONBAR_TICKS);
    }

    /** 四种选中输入的调试输出（供日志/用例断言区分；格式固定，勿改） */
    public static void logPrompt(String key, String action) {
        LOGGER.debug("[Astral Dice][TargetSelectPrompt] key={} action={}", key, action);
    }

    // === 射线目标 ===

    private static void updateRaycastTarget(Minecraft mc) {
        if (!(mc.player instanceof LocalPlayer player)) {
            currentTarget = null;
            rejectedTarget = null;
            nearbyTargets.clear();
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
        LivingEntity newRejected = null;
        if (entityHit != null
                && entityHit.getEntity() instanceof LivingEntity living
                && living != player
                && living.isAlive()) {
            if (targetType.matches(player, living) && player.distanceToSqr(living) <= radius * radius) {
                newTarget = living;
            } else {
                // 命中但不可选（会话目标类型不符 / 超出半径）：走 1/24 细边 + rejected 提示
                newRejected = living;
            }
        }
        if (newTarget != currentTarget) {
            if (newTarget != null) {
                LOGGER.debug("[Astral Dice][TargetSelectionClient] target={}({}) hostile={} friendly={}",
                        newTarget.getId(), newTarget.getName().getString(),
                        isHostile(newTarget), isFriendly(player, newTarget));
            } else {
                LOGGER.debug("[Astral Dice][TargetSelectionClient] target=none");
            }
        }
        currentTarget = newTarget;
        rejectedTarget = newRejected;
        if (newRejected != null) {
            showPrompt(Component.translatable("msg.astral_dice.target_select.rejected"));
        }
        updateNearbyTargets(player);
    }

    /** 半径内其它可选目标（渲染 1/64 细边）：按距离升序取最近 {@link #NEARBY_TARGET_LIMIT} 个 */
    private static void updateNearbyTargets(LocalPlayer player) {
        nearbyTargets.clear();
        AABB search = player.getBoundingBox().inflate(radius);
        List<LivingEntity> found = new ArrayList<>();
        for (Entity entity : player.level().getEntities(player, search)) {
            if (!(entity instanceof LivingEntity living) || living == player) continue;
            if (!living.isAlive() || living.isSpectator()) continue;
            if (!targetType.matches(player, living)) continue;
            if (player.distanceToSqr(living) > radius * radius) continue;
            found.add(living);
        }
        found.sort(Comparator.comparingDouble((LivingEntity living) -> player.distanceToSqr(living)));
        for (int i = 0; i < found.size() && i < NEARBY_TARGET_LIMIT; i++) {
            nearbyTargets.add(found.get(i));
        }
    }

    // === 输入事件（游戏总线，仅客户端） ===

    /**
     * 选择期间接管鼠标（强力胶式按键语义）：
     * 左键 = 确认目标；右键 = 对自身使用（只提示，不提交）；右键 + 潜行 = 取消。
     *
     * <p>用 `InputEvent.MouseButton.Pre` 而非 KeyMapping：选择期间必须**先于原版**截住左键攻击
     * 与右键使用，原版逻辑与其它模组的鼠标绑定都不应生效（事件一律取消）。
     */
    @SubscribeEvent
    public static void onMouseButton(InputEvent.MouseButton.Pre event) {
        if (!isActive()) return;
        if (event.getAction() == GLFW.GLFW_PRESS) {
            if (event.getButton() == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
                confirmByPrimaryClick();
            } else if (event.getButton() == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
                Minecraft mc = Minecraft.getInstance();
                boolean sneaking = mc.player != null && mc.player.isShiftKeyDown();
                if (sneaking) {
                    logPrompt("right_sneak", "cancel");
                    cancel("right_sneak");
                } else {
                    useOnSelfBySecondaryClick();
                }
            }
        }
        // 选择期间接管鼠标:所有按键（左键攻击/右键原使用/中键）都不进原版逻辑
        event.setCanceled(true);
    }

    /** 选择期间拦截滚轮（防切栏/缩放等） */
    @SubscribeEvent
    public static void onMouseScroll(InputEvent.MouseScrollingEvent event) {
        if (isActive()) {
            event.setCanceled(true);
        }
    }

    /**
     * 任何界面被打开即取消选择。
     *
     * <p>两类例外/特例：
     * <ul>
     *   <li>{@link ChatScreen} 豁免 —— 命令聊天是刻意保留的通道（输指令 / 自动化测试注入命令），
     *       打开聊天不应取消选择；</li>
     *   <li>{@link PauseScreen}（ESC 菜单）—— 键盘 ESC 不再被模组拦截（键盘 Mixin 已删除），
     *       原版照常打开暂停菜单，本事件即取消时机（`key=esc action=cancel`）。</li>
     * </ul>
     */
    @SubscribeEvent
    public static void onScreenOpening(ScreenEvent.Opening event) {
        if (!isActive() || event.getScreen() == null) return;
        if (event.getScreen() instanceof ChatScreen) return;
        if (event.getScreen() instanceof PauseScreen) {
            logPrompt("esc", "cancel");
            cancel("esc");
            return;
        }
        cancel("screen");
    }
}
