package com.merlinkitsune.astral_dice.client;

import com.merlinkitsune.astral_dice.AstralDiceMod;
import com.merlinkitsune.astral_dice.network.TargetSelectCancelPayload;
import com.merlinkitsune.astral_dice.network.TargetSelectConfirmPayload;
import com.merlinkitsune.astral_dice.target.SelectorTargets;
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
import net.minecraft.world.phys.AABB;
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
import java.util.Optional;

/**
 * 目标选择器客户端状态机（第一人称 UX，**Create 强力胶式按键语义**）。
 *
 * 按键口径（2026-09-17 用户裁决，"强力胶式"交互；能否对自身使用由各动作的 {@code allowSelf} 决定 ——
 * 2026-09-25 当前为 true 的动作 = 游戏大师立牌「熊孩子特权」与加急加快 / 奢华大餐 / 狂暴三张效果牌）：
 * <ul>
 *   <li><b>左键</b> = 确认目标（发送 {@link TargetSelectConfirmPayload}）；</li>
 *   <li><b>右键</b> = 对自身使用 —— 会话允许自身目标（{@link #allowSelf()}）时提交对自身的确认；
 *       否则（{@code allowSelf=false} 的动作：bonnie / haiqing / moses 三个立牌动作，以及「你有我有」you_have_i_have）只弹 actionbar 提示
 *       {@code msg.astral_dice.target_select.self_unsupported}，**不提交选择**（会话保留）；</li>
 *   <li><b>右键 + 潜行</b> = 取消选择；</li>
 *   <li><b>ESC</b> = 原版照常打开暂停菜单，菜单一打开（{@code ScreenEvent.Opening}）即取消选择
 *       （**手持即选择类会话例外**：开着菜单也保留会话，见 {@link #onScreenOpening}）；</li>
 *   <li><b>J</b>（主动技能键）= 取消选择（保留）；</li>
 *   <li><b>移出主手</b> = 手持即选择类会话（四张效果牌）的收官方式：物品离开主手即退出选择
 *       （{@code reason=released}，无瞬态提示），此类会话**没有倒计时**、提示里也不出现剩余时间；</li>
 *   <li>选择期间滚轮拦截、{@link ChatScreen} 豁免（命令聊天/自动化注入命令）均保留。</li>
 * </ul>
 *
 * 与旧实现的差异：删除了 Enter 键盘确认键与其 10 tick 就绪门槛、键盘白名单判定，
 * 以及客户端键盘拦截 Mixin（mixin 配置与匹配条目一并移除）—— 强力胶式语义下确认/取消全部
 * 由鼠标（左键/右键[+潜行]）与 ESC 菜单承担，键盘不再被模组吞掉（J 仍作取消，走 KeyMapping 消费）。
 *
 * 提示分工：中央 HUD 只画一行「目标名 + 距离 + 类型标签」（见 {@link TargetSelectOverlay}），其余提示
 * 一律走 actionbar —— 每 tick 刷新的**四态**稳态提示（未命中 / 可自身 / 正确目标 / 错误目标，
 * 末尾追加黄色剩余时间，见 {@link #steadyPrompt()}）与瞬态反馈（见 {@link #showPrompt}）。
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
    /** 半径内其它可选目标（1/128 细边，最多 {@link #NEARBY_TARGET_LIMIT} 个），每 tick 重建 */
    private static final List<LivingEntity> nearbyTargets = new ArrayList<>();
    /** 瞬态 actionbar 提示（优先于默认提示）；到期后恢复默认提示 */
    private static Component transientPrompt;
    private static long transientPromptUntil;
    /**
     * 本次会话是否允许对自身使用（服务端随会话下发；消费方接口
     * {@code target/SelfTargetable#allowSelf()} 的取值；{@code ren_privilege} 与三张可自用效果牌（express_delivery / luxury_feast / berserk）为 true，其余动作 false）。
     */
    private static boolean allowSelf;
    /**
     * 本会话是否由「主手手持物品」驱动且**没有倒计时**（服务端随会话下发；消费方接口
     * {@code target/HoldToSelect} 的取值，当前 = 四张效果牌动作 express_delivery / luxury_feast /
     * you_have_i_have / berserk）。
     *
     * <p>为真时：① {@link #remainingSeconds()} 不参与提示（不显示「（剩余 N 秒）」）；
     * ② {@link #tick()} 改为校验主手物品是否仍是该动作对应的牌，离开主手即自行退出选择模式；
     * ③ 打开界面（背包等）**不取消**会话（玩家仍握着牌）。
     */
    private static boolean holdToSelect;

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

    /** 半径内其它可选目标（渲染层用它画 1/128 细边）；只读视图，按距离由近到远 */
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
        if (mc.level == null || holdToSelect) return 0;
        long remainingTicks = expireTick - mc.level.getGameTime();
        if (remainingTicks <= 0L) return 0;
        return (int) Math.ceil(remainingTicks / 20.0);
    }

    /** 本次会话是否允许对自身使用（服务端下发；ren_privilege 与三张可自用效果牌为 true，其余 false） */
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
     *
     * <p>口径裁决（2026-09-18 用户裁决，维持现状）：{@code hostile} 取原版 {@code Enemy} 标记接口
     * ⇒ 野生狼 / 北极熊 / 蜜蜂等「中立但可敌对」的生物显示「中立」（与框色黄同源）。**不得**改成
     * 「会主动攻击我的生物」——那会与 {@link #highlightColor} 的框色口径脱节（红框配中立标签）。
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

    /** 服务端下发选择会话开始（TargetSelectStartPayload 处理器调用，主线程） */
    public static void start(int newToken, int targetTypeOrd, double newRadius, int durationTicks, String newActionId,
                             boolean newAllowSelf, boolean newHoldToSelect) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;
        token = newToken;
        targetType = TargetType.values()[Math.max(0, Math.min(targetTypeOrd, TargetType.values().length - 1))];
        radius = Math.max(1.0, newRadius);
        // 手持即选择类会话没有倒计时(服务端传 durationTicks=0):expireTick 写 Long.MAX_VALUE,
        // 超时判定永不成立,收官一律走「主手物品校验」(见 tick())
        holdToSelect = newHoldToSelect;
        expireTick = newHoldToSelect ? Long.MAX_VALUE : mc.level.getGameTime() + Math.max(1, durationTicks);
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
        LOGGER.debug("[Astral Dice][TargetSelectionClient] start token={} type={} radius={} expire={} action={} allowSelf={} hold={}",
                token, targetType, radius, expireTick, actionId, allowSelf, holdToSelect);
        refreshActionBarPrompt(mc);
    }

    /**
     * 客户端主循环 tick（由 ClientTickHandler 驱动）：射线目标更新 + actionbar 提示续期 + 收官判定。
     *
     * <p>收官两条口径：① 手持即选择类会话 —— 主手物品不再是该动作对应的效果牌 ⇒ 立即取消
     * （reason=released，无瞬态提示：松手是玩家主动动作）；② 其余会话 —— 选择窗口超时即取消。
     */
    public static void tick() {
        if (!isActive()) return;
        Minecraft mc = Minecraft.getInstance();
        if (holdToSelect) {
            if (!holdsCardForAction(mc)) {
                releaseByHeldItem();
                return;
            }
        } else if (mc.level.getGameTime() >= expireTick) {
            LOGGER.debug("[Astral Dice][TargetSelectionClient] cancel (expired) token={}", token);
            cancel("expired");
            return;
        }
        updateRaycastTarget(mc);
        refreshActionBarPrompt(mc);
    }

    /**
     * 手持即选择:物品离开主手 ⇒ **本地**退出选择模式。
     *
     * <p>刻意**不**发取消包 —— 服务端 {@code TargetSelectionManager.tick} 有同源判定
     * （{@code HoldToSelect#stillHeld}）会自行收尾，两条路径抢着关同一会话只会让服务端日志从
     * `reason=released` 变成 `cancel`、并多写一条无意义的抑制闩（`hold suppressed`）。
     * 客户端先退出的窗口内服务端会话仍在，但此时玩家手里已经没有该牌，不可能再发出确认/自用包。
     */
    private static void releaseByHeldItem() {
        LOGGER.debug("[Astral Dice][TargetSelectionClient] cancel (released) token={} action={}", token, actionId);
        deactivate();
    }

    /** 主手是否仍持有本会话动作对应的选择器类效果牌（手持即选择类的收官判据，与服务端同源） */
    private static boolean holdsCardForAction(Minecraft mc) {
        if (mc.player == null) return false;
        return mc.player.getMainHandItem().getItem()
                instanceof com.merlinkitsune.astral_dice.item.card.BaseEffectCardItem card
                && actionId != null && actionId.equals(card.selectorActionId());
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

    /**
     * 右键 = 对自身使用。
     *
     * <p>会话允许自身目标（{@link #allowSelf()}）时提交对自身的确认包并退出选择模式；
     * 否则（{@code allowSelf=false} 的动作，如 bonnie / haiqing / moses 与 you_have_i_have）只弹 actionbar {@code self_unsupported} 提示，
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
            PacketDistributor.sendToServer(new TargetSelectConfirmPayload(token, selfId));
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
        allowSelf = false;
        holdToSelect = false;
        Minecraft mc = Minecraft.getInstance();
        if (mc.options != null) {
            mc.options.keyAttack.setDown(false);
        }
    }

    // === 提示（actionbar） ===

    /**
     * 强化胶式提示：每 tick 续期一次 actionbar（`ActionBarManager.show(component, 40)`）。
     *
     * <p>瞬态提示（无目标左键 / 自用不可用 / 已取消）在 40 tick 窗口期内优先，避免被稳态提示
     * 在同一 tick 内覆盖掉；窗口期过后自动回到稳态提示（{@link #steadyPrompt()}）。
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
            prompt = steadyPrompt();
        }
        ActionBarManager.show(prompt, ACTIONBAR_TICKS);
    }

    /**
     * 稳态提示（2026-09-18 四态口径 + 2026-09-25「手持即选择」两套文案）：主文案随
     * 「准星目标 / 准星命中但不可选 / 未命中（分是否允许自身）」四态着色。
     *
     * <p>退出/取消指引与时间后缀按会话类型分叉：
     * <ul>
     *   <li><b>手持即选择类</b>（{@link #holdToSelect}，四张效果牌）—— 用
     *       {@code msg.astral_dice.target_select.prompt.hold.*} 四键，指引「移出手持即退出选择」，
     *       **不追加剩余时间**（这类会话没有倒计时）；</li>
     *   <li><b>其余</b>（立牌主动）—— 用原四键，指引「下蹲+右键 退出选择」，末尾追加**黄色**
     *       「（剩余 N 秒）」，口径与改动前逐字一致。</li>
     * </ul>
     *
     * <p>四态优先级：① 有有效目标 → 绿 {@code .valid}；② 否则准星命中但不可选 →
     * 红 {@code .rejected}（参数 = 本次会话 {@link TargetType} 对应的有效目标名）；
     * ③ 否则 → 白 {@code .no_target_self}（{@link #allowSelf()} 为真）或
     * {@code .no_target}（为假），参数 = 技能名。
     */
    private static Component steadyPrompt() {
        String base = holdToSelect ? "msg.astral_dice.target_select.prompt.hold" : "msg.astral_dice.target_select.prompt";
        Component main;
        if (currentTarget != null) {
            main = Component.translatable(base + ".valid")
                    .withStyle(ChatFormatting.GREEN);
        } else if (rejectedTarget != null) {
            main = Component.translatable(base + ".rejected", validTargetName())
                    .withStyle(ChatFormatting.RED);
        } else if (allowSelf()) {
            main = Component.translatable(base + ".no_target_self", skillName())
                    .withStyle(ChatFormatting.WHITE);
        } else {
            main = Component.translatable(base + ".no_target", skillName())
                    .withStyle(ChatFormatting.WHITE);
        }
        if (holdToSelect) {
            // 无倒计时 ⇒ 不拼「（剩余 N 秒）」
            return main;
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

    /**
     * 显示一条瞬态 actionbar 提示（{@code ACTIONBAR_TICKS} 内不被常驻提示覆盖）。
     *
     * <p>口径裁决（2026-09-18 用户裁决，维持现状）：瞬态提示**刻意不追加剩余时间** ——
     * 「（剩余 N 秒）」只出现在四态常驻提示里（见 {@link #steadyPrompt}）。瞬态是点击反馈，
     * 与常驻倒计时拼在同一条上会出现时间跳变的观感。
     */
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
        double maxDist = radius;
        Vec3 eye = player.getEyePosition(1.0F);
        Vec3 look = player.getViewVector(1.0F);
        HitResult blockHit = player.pick(maxDist, 1.0F, false); // 方块射线(Entity.pick 内部为 OUTLINE clip)
        // 「已指向」判定（2026-09-19 用户要求「目标选择器只需指向目标外框范围即视为指向，
        // 不必完全对准目标本身」）：判定用**外框盒**，与描边渲染同源（碰撞盒 ∪ 模型实测外框，
        // 再按命中档半线宽外扩 —— 见 {@link TargetSelectionHighlighter#hitFrameBox}），
        // 而不是原版 ProjectileUtil 的**碰撞盒**实体射线 —— 后者要求准星落在碰撞盒上，
        // 僵尸抬臂 / 蜘蛛伸腿 / 马头颈这类「可见但在碰撞盒之外」的部位全都点不中。
        // 方块射线截断照旧：准星被方块挡住时不隔墙选中（实体搜索终点截断到方块处）。
        double blockDistSq = blockHit.getLocation().distanceToSqr(eye);
        double entityLimitSq = blockHit.getType() != HitResult.Type.MISS ? blockDistSq : maxDist * maxDist;
        double entityLimit = Math.sqrt(entityLimitSq);
        Vec3 entityEnd = eye.add(look.x * entityLimit, look.y * entityLimit, look.z * entityLimit);
        AABB searchBox = player.getBoundingBox().expandTowards(look.scale(entityLimit)).inflate(1.5D, 1.5D, 1.5D);
        LivingEntity aimed = pickFrameTarget(player, eye, entityEnd, entityLimitSq, searchBox);

        LivingEntity newTarget = null;
        LivingEntity newRejected = null;
        if (aimed != null && aimed != player && aimed.isAlive()) {
            if (SelectorTargets.matches(targetType, player, aimed) && player.distanceToSqr(aimed) <= radius * radius) {
                newTarget = aimed;
            } else {
                // 命中但不可选（会话目标类型不符 / 超出半径）：走 1/24 细边；
                // 「对准错误目标」的提示自 2026-09-18 起是**稳态红字**（见 steadyPrompt），
                // 不再在此处抛瞬态 showPrompt —— 否则每 tick 都会与稳态文案来回跳。
                newRejected = aimed;
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
        // 四态②（红字「对准错误目标」）的可断言读数（2026-09-19 用户裁决「补，仅 DEBUG，三条线对等」）：
        // 此前 rejectedTarget 只赋值、不打日志 ⇒ ②态只能靠人眼判、无法进自动化用例。
        // 与上面的 target 读数同口径：**只在变化时打印**（按前值去重），每 tick 至多一条，不刷屏。
        if (newRejected != rejectedTarget) {
            if (newRejected != null) {
                LOGGER.debug("[Astral Dice][TargetSelectionClient] rejected={}({}) hostile={} friendly={}",
                        newRejected.getId(), newRejected.getName().getString(),
                        isHostile(newRejected), isFriendly(player, newRejected));
            } else {
                LOGGER.debug("[Astral Dice][TargetSelectionClient] rejected=none");
            }
        }
        currentTarget = newTarget;
        rejectedTarget = newRejected;
        updateNearbyTargets(player);
    }

    /**
     * 准星命中判定：取**外框盒**被射线穿过且**离视线最近**的实体（与描边渲染同源）。
     *
     * <p>盒 = {@link TargetSelectionHighlighter#hitFrameBox}（碰撞盒 ∪ 本帧实测模型外框，再外扩半线宽）；
     * 命中判据 = **视点已在盒内记 0 距离**，否则取线段与盒的 AABB 交点（{@link AABB#clip}），
     * 最后取沿视线**最近**的那一个 ⇒ **指向外框范围即算指向该目标**（2026-09-19 用户要求），
     * 与「看着在框里」的观感一致。
     *
     * <p>截断沿用调用方给出的射线终点：射线在方块命中处结束，墙后的实体自然选不中
     * （{@code entityLimitSq} 同时作为最近距离的初值上界）。
     */
    private static LivingEntity pickFrameTarget(LocalPlayer player, Vec3 eye, Vec3 end, double entityLimitSq,
                                                AABB searchBox) {
        LivingEntity best = null;
        double bestDistSq = entityLimitSq;
        for (Entity entity : player.level().getEntities(player, searchBox)) {
            if (!(entity instanceof LivingEntity living) || living == player) continue;
            if (!living.isAlive() || living.isSpectator() || !living.isPickable()) continue;
            AABB frame = TargetSelectionHighlighter.hitFrameBox(living);
            double distSq;
            // ⚠️ **视点落在盒内**必须单独判：vanilla `AABB#clip` 只认「严格从板外进入」的相交
            // （`getDirection` → `clipPoint` 的 startSide < minSide 条件），起点已在盒内时**必返回 empty**；
            // 原版 `ProjectileUtil#getEntityHitResult` 正是用 `aabb.contains(startVec)` 分支把这种情况
            // 记为距离 0（1.21.1 `ProjectileUtil.java:77-82`、1.20.1 `:64-69`）。贴脸正对时（僵尸伸直双臂
            // 使外框盒前伸约 0.75 格，而玩家与生物的最小中心距约 0.6 格）框会把视点整个包住 ——
            // 省掉这一分支就会「画面里画着框、左键却只弹『没有可用的目标』」，与「指向外框即可选中」相反。
            if (frame.contains(eye)) {
                distSq = 0.0D;
            } else {
                Optional<Vec3> hit = frame.clip(eye, end);
                if (hit.isEmpty()) continue;
                distSq = eye.distanceToSqr(hit.get());
            }
            if (distSq <= bestDistSq) {
                bestDistSq = distSq;
                best = living;
            }
        }
        return best;
    }

    /** 半径内其它可选目标（渲染 1/128 细边）：按距离升序取最近 {@link #NEARBY_TARGET_LIMIT} 个 */
    private static void updateNearbyTargets(LocalPlayer player) {
        nearbyTargets.clear();
        AABB search = player.getBoundingBox().inflate(radius);
        List<LivingEntity> found = new ArrayList<>();
        for (Entity entity : player.level().getEntities(player, search)) {
            if (!(entity instanceof LivingEntity living) || living == player) continue;
            if (!living.isAlive() || living.isSpectator()) continue;
            if (!SelectorTargets.matches(targetType, player, living)) continue;
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
        if (!isActive()) {
            // 未在选择中:手里拿着**选择器类效果牌**时,左键(确认键)也要给出「效果牌冷却/出牌数已满」提示 ——
            // 否则本轮冷却期间持牌毫无反馈(2026-09-19 用户要求:按下左键同样要有冷却提示)。
            // 文案与判据同自身牌(B...BaseEffectCardItem#isBlockedOnClient)。
            if (event.getAction() == GLFW.GLFW_PRESS && event.getButton() == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
                notifyHeldSelectorBlocked();
            }
            return;
        }
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

    /**
     * 手持**选择器类效果牌**但本轮被锁(出牌数已满 / 冷却中)时的左键提示。
     *
     * <p>文案与判据和自身牌 {@code BaseEffectCardItem#isBlockedOnClient} 完全一致(同一个 lang 键
     * {@code msg.astral_dice.effect_card_burst_full},含剩余秒数),只在本地显示、不发包。
     * 未选中目标时的左键原本完全静默,玩家只会觉得"牌没反应"。
     */
    private static void notifyHeldSelectorBlocked() {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null) return;
        if (!(player.getMainHandItem().getItem()
                instanceof com.merlinkitsune.astral_dice.item.card.BaseEffectCardItem card)
                || card.selectorActionId() == null) {
            return;
        }
        if (!com.merlinkitsune.astral_dice.item.card.EffectCardPeriod.isBlocked(player)) return;
        int seconds = com.merlinkitsune.astral_dice.item.card.EffectCardPeriod.getRemainingBlockSeconds(player);
        player.displayClientMessage(
                Component.translatable("msg.astral_dice.effect_card_burst_full", seconds), true);
    }

    /**
     * 选择期间拦截滚轮(防切栏/缩放等) —— **「手持即选择」类会话例外**(2026-09-19 用户报告并裁决)。
     *
     * <p>立牌主动的会话是「按键开局」的:拦滚轮可避免换槽顺手把会话弄没。
     * 但效果牌走的是「手持即选择」——主手拿着牌就自动开会话,此时**滚轮必须可用**:
     * 玩家正是靠滚轮换到别的槽位把牌换下主手来收官(见 {@code target/HoldToSelect#stillHeld}),
     * 拦滚轮等于把牌焊在手上(用户报告:可释放时滚轮不可用)。
     */
    @SubscribeEvent
    public static void onMouseScroll(InputEvent.MouseScrollingEvent event) {
        if (!isActive()) return;
        if (holdToSelect) return;
        event.setCanceled(true);
    }

    /**
     * 界面打开时的处理。
     *
     * <p>三类口径：
     * <ul>
     *   <li>{@link ChatScreen} 一律豁免 —— 命令聊天是刻意保留的通道（输指令 / 自动化测试注入命令）；</li>
     *   <li><b>手持即选择类会话一律不取消</b>（2026-09-25「手持即选择」）—— 玩家仍握着牌，开背包 /
     *       按 ESC 只是暂时盖住提示，关掉界面后提示照常；收官只由「物品离开主手」触发；</li>
     *   <li>其余会话（立牌主动）—— {@link PauseScreen}（ESC 菜单）即取消（{@code key=esc action=cancel}），
     *       其它界面（背包等）也取消（{@code reason=screen}），口径与改动前逐字一致。</li>
     * </ul>
     */
    @SubscribeEvent
    public static void onScreenOpening(ScreenEvent.Opening event) {
        if (!isActive() || event.getScreen() == null) return;
        if (event.getScreen() instanceof ChatScreen) return;
        if (holdToSelect) return;
        if (event.getScreen() instanceof PauseScreen) {
            logPrompt("esc", "cancel");
            cancel("esc");
            return;
        }
        cancel("screen");
    }
}
