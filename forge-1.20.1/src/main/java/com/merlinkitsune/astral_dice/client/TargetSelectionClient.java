package com.merlinkitsune.astral_dice.client;

import com.merlinkitsune.astral_dice.AstralDiceMod;
import com.merlinkitsune.astral_dice.network.ModNetwork;
import com.merlinkitsune.starenginelib.client.ActionBarManager;
import com.merlinkitsune.starenginelib.target.TargetType;
import net.minecraft.ChatFormatting;
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
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
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
 * <p>按键口径（2026-09-17 用户裁决，"强力胶式"交互；本模组全部技能不开放自身使用），与 1.21.1 逐条对等：
 * <ul>
 *   <li><b>左键</b> = 确认目标（发送 {@link ModNetwork.TargetSelectConfirmMessage}）；</li>
 *   <li><b>右键</b> = 对自身使用 —— 会话允许自身目标（{@link #allowSelf()}）时提交对自身的确认；
 *       否则（**当前全部动作**的取值，无任何动作实现
 *       {@code target/SelfTargetable}）只弹 actionbar 提示
 *       {@code msg.astral_dice.target_select.self_unsupported}，**不提交选择**（会话保留）；</li>
 *   <li><b>右键 + 潜行</b> = 取消选择；</li>
 *   <li><b>ESC</b> = 原版照常打开暂停菜单，菜单一打开（{@link ScreenEvent.Opening}）即取消选择；</li>
 *   <li><b>J</b>（主动技能键）= 取消选择（保留）；</li>
 *   <li>选择期间滚轮拦截、{@link ChatScreen} 豁免（命令聊天/自动化注入命令）均保留。</li>
 * </ul>
 *
 * <p>与旧实现的差异（1.20.1 侧同步删除）：删除了默认绑定 Enter 的键盘确认键与其 10 tick 就绪门槛、
 * 键盘白名单判定、Esc 专用入口（三者均为旧代码里的方法/常量，标识符只留在 git 历史里，
 * 本文件不再出现它们的名字），以及**整个**客户端键盘拦截 Mixin 类
 * （{@code astral_dice.mixins.json} 的客户端清单里对应条目一并移除）—— 强力胶式语义下确认/取消全部
 * 由鼠标（左键/右键[+潜行]）与 ESC 菜单承担，键盘不再被模组吞掉（J 仍作取消，走 KeyMapping 消费）。
 *
 * <p>提示分工：中央 HUD 只画一行「目标名 + 距离 + 类型标签」（见 {@link TargetSelectOverlay}），其余提示
 * 一律走 actionbar —— 每 tick 刷新的**四态**稳态提示（未命中 / 可自身 / 正确目标 / 错误目标，
 * 末尾追加黄色剩余时间，见 {@link #steadyPrompt()}）与瞬态反馈（见 {@link #showPrompt}）。
 */
@Mod.EventBusSubscriber(modid = AstralDiceMod.MODID, value = Dist.CLIENT)
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
    /**
     * 本次会话是否允许对自身使用（服务端随会话下发；消费方接口
     * {@code target/SelfTargetable#allowSelf()} 的取值，当前无任何动作实现 ⇒ 恒 false）。
     */
    private static boolean allowSelf;

    private TargetSelectionClient() {
    }

    // === 状态查询（Overlay / Highlighter / TargetOutlineCapture 共用） ===

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

    /**
     * 该实体是否属于当前会话的可见目标集合（命中 / 命中不可选 / 半径内其它）。
     *
     * <p>供 {@link TargetOutlineCapture} 判断要不要替它实测可见外框 —— 只为真正要画框的实体付费。
     */
    public static boolean isTracked(LivingEntity entity) {
        if (entity == null || !isActive()) return false;
        return entity == currentTarget || entity == rejectedTarget || nearbyTargets.contains(entity);
    }

    public static int highlightColor(LivingEntity target) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && isFriendly(mc.player, target)) return COLOR_FRIENDLY;
        if (isHostile(target)) return COLOR_HOSTILE;
        return COLOR_NEUTRAL;
    }

    /**
     * 会话剩余秒数（{@code ceil((expireTick - level.getGameTime()) / 20)}，最小 0）。
     *
     * <p>与超时判据（{@link #tick()} 的 {@code gameTime >= expireTick}）同源：归零即会话超时。
     * 供 actionbar 的「（剩余 N 秒）」与 HUD/测试复用；无世界（未进游戏）时返回 0。
     */
    public static int remainingSeconds() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return 0;
        long remainingTicks = expireTick - mc.level.getGameTime();
        if (remainingTicks <= 0L) return 0;
        return (int) Math.ceil(remainingTicks / 20.0);
    }

    /** 本次会话是否允许对自身使用（服务端下发；当前无任何动作实现 ⇒ 恒 false） */
    public static boolean allowSelf() {
        return allowSelf;
    }

    /**
     * HUD 类型标签的 lang 键后缀（{@code hostile} / {@code teammate} / {@code pet} /
     * {@code neutral} / {@code player}），键名 = {@code hud.astral_dice.target_select.tag.<后缀>}。
     *
     * <p>推导口径与 {@link #highlightColor}/{@link #isFriendly}/{@link #isHostile} 同源：
     * 玩家 → 同队（{@link #isFriendly}）为 {@code teammate}、非同队为 {@code player}；
     * 其它生物中，选择者自己拥有的（{@link OwnableEntity#getOwnerUUID()} 等于选择者）为
     * {@code pet}，敌对（{@link #isHostile}）为 {@code hostile}，其余为 {@code neutral}。
     */
    public static String targetTagKey(LivingEntity entity) {
        Minecraft mc = Minecraft.getInstance();
        Player selector = mc.player;
        if (entity instanceof Player) {
            return selector != null && isFriendly(selector, entity) ? "teammate" : "player";
        }
        if (selector != null && entity instanceof OwnableEntity ownable
                && selector.getUUID().equals(ownable.getOwnerUUID())) {
            return "pet";
        }
        return isHostile(entity) ? "hostile" : "neutral";
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

    /** 服务端下发选择会话开始（TargetSelectStartMessage 处理器调用，主线程） */
    public static void start(int newToken, int targetTypeOrd, double newRadius, int durationTicks, String newActionId,
                             boolean newAllowSelf) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;
        token = newToken;
        targetType = TargetType.values()[Math.max(0, Math.min(targetTypeOrd, TargetType.values().length - 1))];
        radius = Math.max(1.0, newRadius);
        expireTick = mc.level.getGameTime() + Math.max(1, durationTicks);
        actionId = newActionId;
        allowSelf = newAllowSelf;
        active = true;
        currentTarget = null;
        rejectedTarget = null;
        nearbyTargets.clear();
        transientPrompt = null;
        transientPromptUntil = 0;
        // 清除遗留的左键按下状态：选择期间攻击键被接管，避免进入前长按导致持续攻击
        mc.options.keyAttack.setDown(false);
        LOGGER.debug("[Astral Dice][TargetSelectionClient] start token={} type={} radius={} expire={} action={} allowSelf={}",
                token, targetType, radius, expireTick, actionId, allowSelf);
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
     * <p>无有效目标时**不提交**，只弹 actionbar `no_target` 提示。
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

    /**
     * 右键 = 对自身使用。
     *
     * <p>会话允许自身目标（{@link #allowSelf()}）时提交对自身的确认包并退出选择模式；
     * 否则（当前**全部**动作的取值）只弹 actionbar {@code self_unsupported} 提示，
     * **不提交选择**、会话保留。
     */
    public static void useOnSelfBySecondaryClick() {
        if (!isActive()) return;
        if (allowSelf()) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null) return;
            int selfId = mc.player.getId();
            LOGGER.debug("[Astral Dice][TargetSelectionClient] self-confirm sent token={} target={}(self)",
                    token, selfId);
            ModNetwork.sendToServer(new ModNetwork.TargetSelectConfirmMessage(token, selfId));
            deactivate();
            return;
        }
        logPrompt("right", "self_unsupported");
        showPrompt(Component.translatable("msg.astral_dice.target_select.self_unsupported"));
    }

    /** 确认：向服务端发送确认包并退出选择模式（调用方保证 currentTarget 有效） */
    public static void confirm() {
        if (!isActive() || currentTarget == null) return;
        int targetId = currentTarget.getId();
        LOGGER.debug("[Astral Dice][TargetSelectionClient] confirm sent token={} target={}({})",
                token, targetId, currentTarget.getName().getString());
        ModNetwork.sendToServer(new ModNetwork.TargetSelectConfirmMessage(token, targetId));
        deactivate();
    }

    /** 取消选择（右键+潜行 / J / ESC 菜单 / 第三方界面打开时调用） */
    public static void cancel(String reason) {
        if (!isActive()) return;
        LOGGER.debug("[Astral Dice][TargetSelectionClient] cancel token={} ({})", token, reason);
        ModNetwork.sendToServer(new ModNetwork.TargetSelectCancelMessage(token));
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
        allowSelf = false;
        Minecraft mc = Minecraft.getInstance();
        if (mc.options != null) {
            mc.options.keyAttack.setDown(false);
        }
    }

    // === 提示（actionbar） ===

    /**
     * 强力胶式提示：每 tick 续期一次 actionbar（`ActionBarManager.show(component, 40)`）。
     *
     * <p>瞬态提示（无目标左键 / 自用不可用 / 已取消）在 40 tick 窗口期内优先，避免被稳态提示
     * 在同一 tick 内覆盖掉；窗口期过后自动回到稳态提示（{@link #steadyPrompt()}）。
     *
     * <p>注入点说明：`ActionBarManager` 正是服务端 `ActionBarMessage` 在客户端侧的落点
     * （见 `network/ModNetwork` 的处理器），故本客户端提示与服务端提示渲染在同一层、同一位置，
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
            prompt = steadyPrompt();
        }
        ActionBarManager.show(prompt, ACTIONBAR_TICKS);
    }

    /**
     * 稳态提示（2026-09-18 用户裁决的四态口径，与 1.21.1 逐条对等）：主文案随
     * 「准星目标 / 准星命中但不可选 / 未命中（分是否允许自身）」四态着色，末尾统一追加
     * **黄色**的「（剩余 N 秒）」。
     *
     * <p>四态优先级：① 有有效目标 → 绿 {@code prompt.valid}；② 否则准星命中但不可选 →
     * 红 {@code prompt.rejected}（参数 = 本次会话 {@link TargetType} 对应的有效目标名）；
     * ③ 否则 → 白 {@code prompt.no_target_self}（{@link #allowSelf()} 为真）或
     * {@code prompt.no_target}（为假），参数 = 技能名。
     */
    private static Component steadyPrompt() {
        Component main;
        if (currentTarget != null) {
            main = Component.translatable("msg.astral_dice.target_select.prompt.valid")
                    .withStyle(ChatFormatting.GREEN);
        } else if (rejectedTarget != null) {
            main = Component.translatable("msg.astral_dice.target_select.prompt.rejected", validTargetName())
                    .withStyle(ChatFormatting.RED);
        } else if (allowSelf()) {
            main = Component.translatable("msg.astral_dice.target_select.prompt.no_target_self", skillName())
                    .withStyle(ChatFormatting.WHITE);
        } else {
            main = Component.translatable("msg.astral_dice.target_select.prompt.no_target", skillName())
                    .withStyle(ChatFormatting.WHITE);
        }
        Component time = Component.translatable("msg.astral_dice.target_select.time", remainingSeconds())
                .withStyle(ChatFormatting.YELLOW);
        return Component.empty().append(main).append(time);
    }

    /**
     * 技能名（按会话 actionId 取 lang）。
     *
     * <p>用 {@code translatableWithFallback}：未登记的 actionId（演示/测试动作如
     * {@code test_echo_*}）回退显示 actionId 本身，而不是裸的 lang 键名。
     */
    private static Component skillName() {
        return Component.translatableWithFallback("msg.astral_dice.target_select.skill." + actionId, actionId);
    }

    /**
     * 本次会话有效目标名（按 {@link TargetType} 枚举名取 lang）。
     *
     * <p>用 {@code translatableWithFallback}，fallback = 枚举名 ⇒ 将来新增 {@link TargetType}
     * 而 lang 尚未补齐时只会显示枚举名，不会显示裸键。
     */
    private static Component validTargetName() {
        String name = targetType.name();
        return Component.translatableWithFallback("msg.astral_dice.target_select.valid_target." + name, name);
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
        // 1.20.1:mc.player 字段类型即 LocalPlayer,直接判空即可
        if (mc.player == null) {
            currentTarget = null;
            rejectedTarget = null;
            nearbyTargets.clear();
            return;
        }
        LocalPlayer player = mc.player;
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
                // 命中但不可选（会话目标类型不符 / 超出半径）：走 1/24 细边；
                // 「对准错误目标」的提示自 2026-09-18 起是**稳态红字**（见 steadyPrompt），
                // 不再在此处抛瞬态 showPrompt —— 否则每 tick 都会与稳态文案来回跳。
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
     * （1.20.1 Forge 的 `InputEvent.Key` 不可取消，但本语义下键盘已不再需要拦截，故无 Mixin。）
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
