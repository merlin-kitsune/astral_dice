package com.merlinkitsune.astral_dice.item.sign;

import com.merlinkitsune.astral_dice.component.ModAttachments;
import com.merlinkitsune.astral_dice.component.ModDataComponents;
import net.minecraft.ChatFormatting;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.level.Level;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.SlotContext;
import top.theillusivec4.curios.api.type.capability.ICurioItem;
import com.merlinkitsune.astral_dice.item.chip.FanBigChipItem;
import com.merlinkitsune.astral_dice.item.chip.FanSmallChipItem;
import com.merlinkitsune.astral_dice.item.CurioSlotUtil;

public abstract class BaseSignItem extends Item implements ICurioItem {
    private static final Logger LOGGER = LoggerFactory.getLogger(BaseSignItem.class);
    public BaseSignItem(Properties properties) {
        super(properties);
    }

    @Override
    public boolean canEquip(SlotContext slotContext, ItemStack stack) {
        // 立牌只能放入"stand"饰品栏
        if (!"stand".equals(slotContext.identifier())) return false;
        // 禁止重复装备相同的立牌(服务端校验;客户端直接放行避免误判)
        if (slotContext.entity().level().isClientSide()) return true;
        boolean dup = CurioSlotUtil.hasSameItemEquipped(slotContext.entity(), stack);
        if (dup) {
            LOGGER.warn("[Astral Dice][canEquip] 立牌被判定为重复装备被拒: item={}",
                    stack);
        }
        return !dup;
    }

    @Override
    public void curioTick(SlotContext slotContext, ItemStack stack) {
        if (slotContext.entity().level().isClientSide()) return;
        // 总闸门:未佩戴骰子 ⇒ 立牌的一切持续被动都不生效(值保留在物品数据/附件上)
        if (slotContext.entity() instanceof net.minecraft.world.entity.player.Player gatePlayer
                && !CurioSlotUtil.hasDiceEquipped(gatePlayer)) {
            return;
        }

        onCurioTick(slotContext, stack);
    }

    protected void onCurioTick(SlotContext slotContext, ItemStack stack) {
    }

    /**
     * 立牌主动技能触发(服务端):触发立牌栏(唯一槽位)中立牌的技能。
     * 判定顺序:
     * 0. 锁定(生效中)态(第二批「三态化」):本主动施加的计时器仍在跑时按键无效;
     * 1. 玩家级冷却(不受立牌装卸影响):冷却中按键无效;
     * 2. 等待状态(占星师/秘密侦探等需指定目标的技能):等待完成或超时前按键保持无效;
     * 3. 触发成功:施加了计时器的技能进入锁定态(生效中),锁定结束再起冷却;
     *    其余技能立即开始玩家级冷却;等待类技能待其完成指定目标/超时后再计算。
     */
    public static void performSkillForCurio(Player player) {
        if (player.level().isClientSide()) return;
        // 读取立牌栏(唯一槽位)的立牌:用于技能触发与提示前缀(立牌名称)
        var curios = CuriosApi.getCuriosInventory(player);
        if (curios.isEmpty()) return;
        var handlerOpt = curios.get().getStacksHandler("stand");
        if (handlerOpt.isEmpty()) return;
        var handler = handlerOpt.get();
        if (handler.getSlots() <= 0) return;
        ItemStack stack = handler.getStacks().getStackInSlot(0);
        performSkill(player, stack);
    }

    // 服务端统一执行立牌主动技能(立牌栏触发与手持立牌右键共用,保证冷却/目标选择/扇子筹码逻辑一致):
    // 1. 玩家级冷却(不受立牌装卸影响):冷却中按键无效;
    // 2. 目标选择会话检查(占星师/秘密侦探等需选择目标的技能):选择进行中按键无效;
    // 3. 触发成功:非选择器类技能立即开始玩家级冷却;选择器类技能待确认目标后再开始冷却(取消/超时不冷却)。
    private static void performSkill(Player player, ItemStack stack) {
        if (!(stack.getItem() instanceof BaseSignItem sign)) return;
        // 总闸门:未佩戴骰子 ⇒ 立牌主动技能不可用(与被动同一口径)
        if (!CurioSlotUtil.hasDiceEquipped(player)) {
            notifyActionBar(player, "msg.astral_dice.need_dice", net.minecraft.network.chat.Component.empty(), ChatFormatting.RED);
            return;
        }
        long now = player.level().getGameTime();
        net.minecraft.network.chat.Component signName = stack.getHoverName();
        // 0. 锁定(生效中)态检查(第二批「三态化」第 1/4 条):本主动技能施加的计时器仍在跑时按键无效,
        //    提示"<立牌名>主动技能生效中!"。判定**置于冷却分支之前**,因此锁定期内电流核心筹码
        //    根本走不到冷却分支(不触发、不扣充能),第 5 条自然满足。
        if (isSignActiveLocked(player)) {
            notifyActionBar(player, "msg.astral_dice.sign_active_in_effect", signName, ChatFormatting.YELLOW);
            return;
        }
        // 1. 玩家级冷却检查:冷却中按键默认无效,并明确提示"<立牌名>冷却中"(修复:触发成功与冷却拒绝的反馈混淆)
        //    电流核心筹码:冷却中按下主动技能键 → 按剩余冷却占比消耗充能并立即使冷却完成(佩戴且充能足够时)
        //    ⚠️ 强制冷却硬闸门(规格 §3.4;当前无实现者 —— 蛟龙已改用锁定态 + skipCooldownAfterLock):优先级**高于**电流核心筹码 ——
        //       窗口内无条件早退,连"消耗充能立即完成冷却"都不允许触发(不扣充能、不发电流核心文案)。
        long forcedUntil = sign.forcedCooldownUntil(player);
        if (forcedUntil > 0 && now < forcedUntil) {
            notifyActionBar(player, "hud.astral_dice.sign_active_cooldown", signName, ChatFormatting.RED);
            return;
        }
        long cdEnd = ModAttachments.getSignActiveCooldownEnd(player);
        if (cdEnd > 0 && now < cdEnd) {
            int coreResult = com.merlinkitsune.astral_dice.item.chip.CurrentCoreChipItem
                    .tryFinishCooldown(player, cdEnd, now);
            if (coreResult != com.merlinkitsune.astral_dice.item.chip.CurrentCoreChipItem.FINISH_NONE) {
                // 已完成冷却(等待玩家再次按键释放)或充能不足(已提示):不再叠加默认冷却提示
                return;
            }
            notifyActionBar(player, "hud.astral_dice.sign_active_cooldown", signName, ChatFormatting.RED);
            return;
        }
        // 2. 目标选择会话检查:已处于**按键开启的**选择模式时按键无效(防重复进入)。
        //    ⚠️ 2026-10-05 起客户端主动技能键**不再**先取消选择 ⇒ 本判定是该场景下**唯一**的闸门
        //    (该会话中按 J 会静默无效;要取消请用 右键+潜行 / ESC / 等超时)。
        //    ⚠️ 「手持即选择」会话(效果牌握在主手时自动开启,由物品而非按键驱动)不在此列 ——
        //    玩家从未按过键开它,故不得拦住主动技能键(2026-09-24 用户报 BUG:
        //    「手持活体书页时,无法触发主动技能」)。
        if (com.merlinkitsune.astral_dice.target.TargetSelectionManager.isSelectingByKey(player)) return;
        // 2.5 「目标选择器类」立牌的前置门控(2026-09-17 用户裁决):按下主动键**只**开启目标选择会话并立即返回。
        //     本次主动的效果、玩家级冷却/锁定、电流核心充能、风扇筹码发牌与立牌主动响应事件(含默认提示)
        //     **全部推迟到确认合法目标之后**(恢复点见 resumeGatedActiveSkill);效果与冷却/充能由各
        //     TargetSelectionAction#apply 负责,恢复流程不重复写一次。
        //     GameplayConstants.SKILL_WAIT_SECONDS(秒)内未选择或取消 ⇒ 记录被清除,
        //     该次主动等同「未使用」(不发牌/不进冷却/不施效果)。
        //     actionId 为 null 的立牌(其余全部立牌)不走本分支,下方原流程逐字不变。
        String gatedActionId = sign.selectorActionId();
        if (gatedActionId != null) {
            // 2.4 「选择器类」立牌的**前置拒绝**钩子(2026-09-27 新增,为教主立牌「降神生效中不可重复施放」):
            //     返回 false ⇒ 只发提示并立即返回 —— 不开选择会话、不发牌、不进冷却/锁定、不充能。
            //     缺省实现恒为 true ⇒ 其余立牌走与原流程逐字相同的分支。
            if (!sign.canBeginSelectorSession(player)) return;
            if (!(player instanceof net.minecraft.server.level.ServerPlayer serverPlayer)) return;
            if (com.merlinkitsune.astral_dice.target.TargetSelectionManager.start(serverPlayer, gatedActionId)) {
                com.merlinkitsune.starengine.target.SignSelectionGate.arm(player, gatedActionId, stack);
            }
            // 主动技能**生效**音效（2026-09-27 用户裁决：进入目标选择器也算生效）
            playSkillUseSound(player);
            return;
        }
        // 3. 触发主动技能
        InteractionResultHolder<ItemStack> result = sign.handleUse(player.level(), player, stack);
        if (result.getResult() != InteractionResult.SUCCESS) return;
        // 主动技能**生效**音效（非选择器类立牌的实际施放）
        // 2026-10-11 用户裁决：所有立牌统一「按下主动技能键（本方法走到 SUCCESS）即播」，
        // 不再有「点击使用时不播、等玩家在界面里确认才播」的立牌门控。
        playSkillUseSound(player);
        // 4. 手持风扇-大筹码:使用主动技能后,获得一张随机效果牌(不含专属),并对周围范围内敌对目标施加标记
        FanBigChipItem.applyAfterSignSkill(player);
        FanSmallChipItem.applyAfterSignSkill(player);
        // 5. 立牌主动技能响应事件:立牌类订阅本事件注册自身 ActionBar 反馈(见 SignActiveTriggeredEvent);
        //    无任何处理器响应(未注册)时,发送默认提示"xxx立牌:主动技能已启动!"
        com.merlinkitsune.starengine.event.SignActiveTriggeredEvent triggered =
                new com.merlinkitsune.starengine.event.SignActiveTriggeredEvent(player, stack);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(triggered);
        if (!triggered.isHandled()) {
            notifyActionBar(player, "msg.astral_dice.sign_active_triggered", signName, ChatFormatting.YELLOW);
        }
        // 6. 冷却:目标选择器类技能(已进入选择会话)待确认目标后在 apply 中开始冷却;其余立牌立即开始玩家级冷却。
        //    ⚠️ 判据用 isSelectingByKey:「手持即选择」会话不算「本次主动开了会话」,否则玩家握着效果牌
        //    放技能时会被误判 ⇒ 冷却/锁定与电流核心充能**全部不生效**。
        if (!com.merlinkitsune.astral_dice.target.TargetSelectionManager.isSelectingByKey(player)) {
            // 诡异骰子:立牌主动冷却 -50%
            // 强制冷却(规格 §3.4):{@code forcedActiveCooldownTicks() > 0} 的立牌(当前无实现者)
            // **直接使用该值**,完全不经过 WeirdDiceHandler.signCooldownTicks ⇒ 诡异骰子的 -50%、
            // 充能封顶、命运的指引及其它任何减免一律无效(硬闸门同时在冷却判定侧生效)。
            int forcedCooldown = sign.forcedActiveCooldownTicks();
            int signCooldownTicks = forcedCooldown > 0
                    ? forcedCooldown
                    : sign.activeCooldownBaseTicks(player);
            boolean enteredLock = sign.startActiveLockOnUse(player, now);
            if (enteredLock) {
                // ★ 本主动施加了"带时长效果/自身计时器"⇒ 进入锁定(生效中)态。
                //   锁定期间**不写** sign_active_cooldown_end:冷却要等锁定结束才起(第 13 条:无空档);
                //   但先把本次冷却基准写进 sign_active_max_cooldown,供锁定期间各减免方读它并累加进减免池。
                ModAttachments.setSignActiveMaxCooldown(player, signCooldownTicks);
                // 同处记录「未折电池」基准(命运指引卡减半用;电池只作用于立牌主动冷却本身)。
                // ⚠️ 取的是**该立牌自身**的基础值(虚方法 no-battery 版),不能用全局常量 —— 否则
                // Dorothy/Parunan/Sherry 等自定义基准的立牌会被写错(如 Dorothy 2400 被写成 3200/3600)。
                ModAttachments.setSignActiveMaxCooldownBase(player, sign.activeCooldownBaseTicksNoBattery(player));
            } else {
                ModAttachments.setSignActiveCooldownEnd(player, now + signCooldownTicks);
                // 路线 A:记录本次冷却实际使用的最大冷却值,所有减免方一律读它(不再各自重算基准)
                ModAttachments.setSignActiveMaxCooldown(player, signCooldownTicks);
                // 同处记录「未折电池」基准(命运指引卡减半用;电池只作用于立牌主动冷却本身)。
                // ⚠️ 取的是**该立牌自身**的基础值(虚方法 no-battery 版),不能用全局常量 —— 否则
                // Dorothy/Parunan/Sherry 等自定义基准的立牌会被写错(如 Dorothy 2400 被写成 3200/3600)。
                ModAttachments.setSignActiveMaxCooldownBase(player, sign.activeCooldownBaseTicksNoBattery(player));
            }
            // 电流核心筹码:主动技能实际生效时充能 +1(进入锁定时同样计一次;与是否立即起冷却无关)
            com.merlinkitsune.astral_dice.item.chip.CurrentCoreChipItem.onActiveSkillUsed(player);
            // 诡异骰子(2026-10-20):主动技能**结束** ⇒ 获得 5 点治愈。
            // 瞬时型立牌没有生效期 ⇒ 结束与施放完成同一刻;有生效期的立牌(enteredLock)
            // 由 SignActiveLockMachine#endLockAndStartCooldown 在锁定寿终时结算,两条互斥。
            if (!enteredLock) {
                com.merlinkitsune.astral_dice.event.WeirdDiceHandler.onSignActiveEnded(player);
            }
        }
    }

    /**
     * 「目标选择器类」立牌的主动技能 action id(默认 null = 非选择器类立牌,按原流程即时执行)。
     *
     * <p>返回非 null 时 {@link #performSkill} 会把该次主动**门控**在目标选择会话之后:
     * 按下主动键只开启选择会话并立即返回(不发牌、不抛事件、不进冷却、不施效果),
     * 确认合法目标后由 {@link #resumeGatedActiveSkill} 恢复原流程的剩余步骤。
     */
    protected String selectorActionId() {
        return null;
    }

    /** 主动技能**生效**时的音效（2026-09-27 用户裁决，源素材 {@code skill_use.wav}）。
     *
     * <p>「生效」两种形态都算：① 选择器类立牌**成功进入目标选择器**（含后续确认）；
     * ② 其余立牌的 {@code handleUse} 返回 SUCCESS。被前置门控拒绝 / 取消 / 未确认的路径不播。
     */
    public static void playSkillUseSound(Player player) {
        if (player == null) return;
        // 机器行：主动技能音效「按下即时播放」的唯一打点（2026-10-11 用户裁决）。
        // 实机判据：按技能键 ⇒ 本行出现一次；随后在事件选择界面点确认 ⇒ **不再**新增本行。
        LOGGER.info("AP_SIGN_SOUND: skill_use:player={}:pos={},{},{}",
                player.getName().getString(),
                (int) player.getX(), (int) player.getY(), (int) player.getZ());
        com.merlinkitsune.astral_dice.audio.SoundPlayback.playAt(player.level(),
                player.getX(), player.getY(), player.getZ(),
                com.merlinkitsune.astral_dice.audio.ModSounds.SKILL_USE.get());
    }

    /**
     * 「选择器类」立牌开启选择会话前的**前置拒绝**钩子(缺省恒为 {@code true} = 照原流程开会话)。
     *
     * <p>返回 {@code false} 时 {@link #performSkill} 的 2.5 分支只负责提示并立即返回:
     * 不开选择会话、不发牌、不进冷却/锁定、不充能 —— 即该次按键**等同未使用**
     * (适用场景:教主立牌 {@code teru} 的「降神生效中不可重复施放」)。
     *
     * @param player 触发主动技能的玩家(服务端)
     * @return true = 允许开启选择会话(缺省);false = 本次主动被拒绝
     */
    protected boolean canBeginSelectorSession(Player player) {
        return true;
    }

    /**
     * 目标选择**确认成功**后的恢复点(由 {@code TargetSelectionManager#confirm} 在 action.apply 之后调用)。
     *
     * <p>只对「由立牌门控登记的会话」生效:非立牌会话没有待执行记录 ⇒ 直接返回,
     * 原有行为不受影响。恢复的是原 performSkill 的第 4/5 步(风扇筹码发牌 + 立牌主动响应事件);
     * **不**重复写玩家级冷却/锁定与电流核心充能 —— 那两件事已由各 TargetSelectionAction#apply 完成。
     *
     * <p>⚠️ 事件本身照旧抛出(订阅方行为不变),但**有意不补发**默认「主动技能已启动」提示(2026-09-17
     * 用户裁决 O1,结论不变):本方法只由门控路径到达,而该 tick 已经发过反馈 —— 通用「已确认」提示由
     * {@code TargetSelectionManager#confirm} 在 {@code action.apply} **之前**发出,各动作的专属提示又在
     * {@code apply} 里发出;客户端 actionbar 是**单槽位**(starengine 的 {@code ActionBarManager#show}
     * 直接覆盖,同 tick 后发者覆盖先发者)⇒ 若在此补发,它反而会成为玩家唯一看到的那条,而这不是期望反馈。
     * 非门控立牌的原流程(performSkill 第 5 步)仍保留默认提示。
     */
    public static void resumeGatedActiveSkill(Player player, String actionId) {
        com.merlinkitsune.starengine.target.SignSelectionGate.Pending pending =
                com.merlinkitsune.starengine.target.SignSelectionGate.take(player, actionId);
        if (pending == null) return;
        ItemStack stack = pending.stack();
        // 4. 手持风扇-大/小筹码:确认释放后才发牌(未确认绝不发牌)
        FanBigChipItem.applyAfterSignSkill(player);
        FanSmallChipItem.applyAfterSignSkill(player);
        // 5. 立牌主动技能响应事件:立牌类订阅本事件注册自身 ActionBar 反馈。
        //    默认「主动技能已启动」提示**有意不补发**(见方法 javadoc:该 tick 已由通用提示与动作专属
        //    提示反馈过,再补发它会成为唯一可见的那条)。
        com.merlinkitsune.starengine.event.SignActiveTriggeredEvent triggered =
                new com.merlinkitsune.starengine.event.SignActiveTriggeredEvent(player, stack);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(triggered);
        // 诡异骰子(2026-10-20):目标选择器类立牌的主动在 apply 执行完毕后即「结束」⇒ 结算 5 点治愈。
        // 若该次施放同时进入了锁定(生效中)态,则改由 SignActiveLockMachine 在锁定寿终时结算,此处跳过。
        if (getSignActiveLockSignId(player).isEmpty()) {
            com.merlinkitsune.astral_dice.event.WeirdDiceHandler.onSignActiveEnded(player);
        }
    }

    // ===== actionbar 反馈:实现已抽到 {@link SignActionBar}(文案键/颜色/时长口径不变,此处只做薄委托)=====
    // 立牌主动技能反馈统一发送入口(黄色;供立牌类注册的 SignActiveTriggeredEvent 处理器与 handleUse 调用)
    protected static void sendSignActionBar(Player player, String langKey, Object... args) {
        SignActionBar.sendSignActionBar(player, langKey, args);
    }

    /** 发一条 actionbar 提示（**显式颜色**）。拒绝 / 阻止类提示必须为**红色**（2026-09-27 用户裁决）。 */
    protected static void sendSignActionBarColored(Player player, ChatFormatting color, String langKey, Object... args) {
        SignActionBar.sendSignActionBarColored(player, color, langKey, args);
    }

    // 服务端发送立牌技能反馈(actionbar 提示,带立牌名称前缀;统一由服务端判定成功/拒绝,避免客户端推测混淆)
    private static void notifyActionBar(Player player, String langKey, net.minecraft.network.chat.Component signName, ChatFormatting color) {
        SignActionBar.notifyActionBar(player, langKey, signName, color);
    }

    // ===== 旧「待命等待器」已由目标选择器取代(2026-09-17 主线 → dev-next 合并裁决)=====
    // 主线的待命等待器(isSkillWaiting / tickSignReadyTimeout + sign_ready_type / sign_ready_expire
    // + 三个立牌的 *_ready 提示效果)在本分支已被 target/TargetSelectionManager + TargetSelectionAction
    // 的目标选择器整体替换:占星师/秘密侦探/枪匠按下主动键即进入目标选择会话,确认目标后**即时释放**
    // (取消/超时不消耗冷却),不再"进入待命 → 等下一次攻击命中时释放"。
    // 故此处不再保留 isSkillWaiting / tickSignReadyTimeout。**冷却门槛分两类(2026-09-17 门控收口)**:
    // 「目标选择器类」立牌(覆写 selectorActionId() 非 null 者)已在第 2.5 步只开会话并 return ⇒
    // 冷却与充能由确认时的 TargetSelectionAction#apply 写入(取消/超时 ⇒ 该次主动不进冷却);
    // 其余立牌照旧在第 6 步立即起玩家级冷却。三态化(锁定 - 生效中)与之**共存**:
    // 选择器决定"何时释放",锁定决定"释放后的生效期是否算作冷却空档"。
    // 脚本侧读数 astraldice_ts_* 只反映选择器会话;旧的 sign_ready_type / sign_ready_expire
    // 附件已废弃(dev-next 侧无任何写入方)。

    // ===== 立牌主动技能"三态化"(可用 / 锁定-生效中 / 冷却)=====
    // 第二批改动(见 docs/batch2/PLAN.md §3):主动技能施加的"带时长效果 / 自身计时器"跑完之前处于
    // **锁定(生效中)**态,期间按键无效(提示 msg.astral_dice.sign_active_in_effect);
    // 锁定结束**必起冷却**(无空档),并一次性抵扣锁定期间累计的冷却减免池。
    // 状态机实现已整体抽到 {@link SignActiveLockMachine}(判定顺序/阈值/附件键不变,此处只做薄委托)。

    /** 忍者立牌的锁定标记(其主动无任何自身计时器,锁定跟随出牌周期,由周期完全重置结束) */
    public static final String KOMACHI_LOCK_ID = SignActiveLockMachine.KOMACHI_LOCK_ID;

    /** 正在生效中的主动所属立牌注册 id("" = 未锁定) */
    public static String getSignActiveLockSignId(Player player) {
        return SignActiveLockMachine.getSignActiveLockSignId(player);
    }

    /**
     * 锁定(生效中)态判定(第 1/4 条):"本技能施加的计时器"是否仍在跑。
     * 实现见 {@link SignActiveLockMachine#isSignActiveLocked},判据/口径逐字不变。
     */
    public static boolean isSignActiveLocked(Player player) {
        return SignActiveLockMachine.isSignActiveLocked(player);
    }

    /**
     * 本主动施加的"门控效果"实例是否仍在生效中(默认 true ⇒ 只按 {@code sign_active_lock_end} 硬上界判定)。
     * 效果可被其它路径提前移除的立牌覆写本方法(如骇客的隐身被攻击解除),使锁定随之提前结束。
     */
    protected boolean isGateEffectActive(Player player) {
        return true;
    }

    /**
     * 各立牌覆写:本次主动触发成功时登记"本技能施加的计时器"(写 {@code sign_active_lock_end}),
     * 返回 true = 需要进入锁定(生效中)态(而非立即起冷却)。默认 false(无计时器 ⇒ 与改动前逐字一致)。
     *
     * <p>无法用固定效果常量覆盖的立牌(随机效果)在本方法内读回**刚施加的效果实例**剩余时长,
     * 不新增全局注册表、不硬编码时长表。
     */
    protected boolean startActiveLockOnUse(Player player, long now) {
        return false;
    }

    /**
     * 锁定(生效中)态结束时**是否跳过冷却**(默认 false ⇒ 照旧在锁定结束那一刻起冷却)。
     *
     * <p>返回 true 的立牌:主动技能的"停机时长"**完全由锁定态表达** ——
     * {@code SignActiveLockMachine#tickSignActiveLock} 的迁移出口把冷却按 {@code effective = 0} 入账
     * ({@code sign_active_cooldown_end = now}),即锁定跑完立刻可再次释放,不再追加基础冷却。
     *
     * <p>与 {@link #forcedActiveCooldownTicks()} 可观察行为等价:锁定期间 {@link #isSignActiveLocked}
     * 的判定**前置于**冷却分支 ⇒ 电流核心筹码 / 诡异骰子 / 充能封顶 / 命运的指引等一切减免方都走不到,
     * 故"N tick 锁定 + 无冷却"与旧「N tick 强制冷却、不可减免」玩家看到的是一致的。
     * 当前实现者:蛟龙立牌 mamushi(锁定 1:00)。
     */
    protected boolean skipCooldownAfterLock() {
        return false;
    }

    /**
     * 本立牌主动技能的**强制冷却**时长(tick)。
     *
     * <p>返回 {@code > 0} 时,{@link #performSkill} 第 6 步(冷却入账;规格 §3.4 记作第⑧步)
     * **直接使用该值**,**不经过** {@code WeirdDiceHandler.signCooldownTicks} ⇒ 诡异骰子(-50%)、
     * 充能封顶、命运的指引及其它任何减免一律无效(规格 §3.4 冻结口径;当前无实现者 —— 蛟龙立牌 mamushi
     * 已改为「锁定 1:00 + 锁定结束无额外冷却」,见 {@link #skipCooldownAfterLock()})。
     *
     * <p>缺省 0 = 无强制冷却(其余立牌走原流程,行为逐字不变)。
     */
    protected int forcedActiveCooldownTicks() {
        return 0;
    }

    /**
     * 本立牌主动技能的**基础冷却 tick**(仍受既有减免链影响)。
     *
     * <p>缺省 = 全局口径 {@code WeirdDiceHandler.signCooldownTicks(player)}(含充能封顶、诡异骰子 −50%
     * 等全部既有减免)⇒ **其余立牌行为逐字不变**。个别立牌需要**自己的基础值**时覆写本方法
     * (当前实现者:怪力侦探立牌 sherry = 120 秒,与枪匠立牌同款链)。
     *
     * <p>与 {@link #forcedActiveCooldownTicks()} 的区别:后者是「**强制值、不受任何减免**」的硬闸门
     * (当前无实现者;蛟龙 mamushi 已改用锁定态 + skipCooldownAfterLock);本方法是「**自定义基础值、仍走减免链**」。两者同时非默认时,**强制值优先**。
     */
    protected int activeCooldownBaseTicks(Player player) {
        return com.merlinkitsune.astral_dice.event.WeirdDiceHandler.signCooldownTicks(player);
    }

    /**
     * 本立牌主动技能**基础冷却的「未折电池」版本**(tick)。
     *
     * <p>链路与 {@link #activeCooldownBaseTicks(Player)} **完全一致**(自定义基础值、充能封顶、
     * 诡异骰子折算同名同序),仅跳过「额外电池筹码 ×0.5」这一步 —— 即把其中的
     * {@code ChargeManager#signCooldownTicks} 换成 {@code ChargeManager#signCooldownTicksBase}。
     * 覆写者(当前:Dorothy / Parunan / Sherry)必须与 {@code activeCooldownBaseTicks} 逐行镜像。
     *
     * <p>用途:与 {@code sign_active_max_cooldown} **同处**写入附件
     * {@code sign_active_max_cooldown_base}(命运指引卡减半的基准)。电池只作用于立牌主动冷却本身,
     * 不得连带砍掉本卡的减半收益。
     */
    protected int activeCooldownBaseTicksNoBattery(Player player) {
        return (int) com.merlinkitsune.astral_dice.item.ChargeManager.signCooldownTicksBase(
                player, com.merlinkitsune.starengine.component.GameplayConstants.SIGN_ACTIVE_COOLDOWN_TICKS);
    }

    /**
     * 本立牌主动技能的**强制冷却截止刻**(绝对 gameTime;0 = 无)。
     *
     * <p>{@link #performSkill} 第 1 步(冷却判定;规格 §3.4 记作第②步)会先读本值:
     * {@code now < forcedCooldownUntil(player)} 时**无条件**视为冷却中并早退(提示既有冷却文案),
     * 优先级**高于** {@code CurrentCoreChipItem.tryFinishCooldown} —— 即强制冷却窗口内电流核心筹码
     * 也无法"消耗充能立即完成冷却"(规格 §3.4)。
     *
     * <p>缺省 0 = 无强制冷却(其余立牌行为逐字不变)。
     */
    protected long forcedCooldownUntil(Player player) {
        return 0L;
    }

    /** 进入锁定态:写锁定标记与硬上界(宽限/减免池归零,避免跨技能残留) */
    protected static void beginActiveLock(Player player, String signId, long lockEndTick) {
        SignActiveLockMachine.beginActiveLock(player, signId, lockEndTick);
    }

    /**
     * 玩家级 tick(幂等):0. 离线补偿;1. 忍者宽限保险;2. 其余立牌锁定结束时必起冷却。
     * 实现见 {@link SignActiveLockMachine#tickSignActiveLock},判定顺序/阈值逐字不变。
     */
    public static void tickSignActiveLock(Player player) {
        SignActiveLockMachine.tickSignActiveLock(player);
    }

    /**
     * 出牌轮"完全重置"回调(忍者专用,第 4 条):使忍者的主动冷却从"该轮出牌状态完全重置那一刻"开始。
     * 实现见 {@link SignActiveLockMachine#onEffectCardRoundReset}。
     */
    public static void onEffectCardRoundReset(Player player) {
        SignActiveLockMachine.onEffectCardRoundReset(player);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        // 下蹲右键:自动装备到"stand"饰品栏
        if (player.isShiftKeyDown()) {
            return CurioSlotUtil.tryAutoEquip(player, stack, "stand");
        }
        // 右键行为仅为装备:主动技能统一由快捷键(立牌栏)触发,手持右键不触发任何技能
        return InteractionResultHolder.fail(stack);
    }

    // 立牌被移除时:清除该立牌获得的增益/计数器/累计值,防止反复更换立牌实现效果叠加。
    // 主动技能冷却为玩家级(ModAttachments.SIGN_ACTIVE_COOLDOWN_END),不受立牌装卸影响。
    // 语义(2026-09-15 用户裁决,S6-C1):只有"玩家有意卸除"才清理;Curios 自身原因导致的重载
    // (from=to 同一物品、物品仍留在槽位)不清理,否则治愈点数等累计值会被反复清零。
    // Curios 官方签名为 onUnequip(SlotContext slotContext, ItemStack newStack, ItemStack stack):
    //   第 2 参 newStack 是"将要占用槽位的栈"(玩家真正卸下时为 EMPTY,换装时为新放入的那件),
    //   第 3 参 stack 才是**被卸下的那件饰品**。旧实现把第 2 参当成被卸下的物品,后果是:
    //   clearSignData 的组件归零写到了 newStack(常为 EMPTY,会污染 ItemStack.EMPTY 这个全局单例)上,
    //   被卸下的立牌自身反而没被清零;旧 stillInSlot 判据又用第 2 参比对槽位内容,换装时误判"仍在槽位"而跳过整段清理。
    // 现在:判据走 CurioSlotUtil.isIntentionalUnequip(只依赖 Curios 的两个参数,不再查槽位内容),
    //       清理对象固定为"被卸下的那个栈"(第 3 参),各立牌 clearSignData 的组件归零才会落在正确的物品上。
    @Override
    public void onUnequip(SlotContext slotContext, ItemStack newStack, ItemStack stack) {
        if (!(slotContext.entity() instanceof Player player)) return;
        if (player.level().isClientSide()) return;
        if (!CurioSlotUtil.isIntentionalUnequip(newStack, stack)) return;
        clearSignData(player, stack);
    }

    // 各立牌覆写以清除自身累计数据
    protected void clearSignData(Player player, ItemStack stack) {
    }

    // === 战斗钩子(供事件系统统一分发;子类覆写以响应玩家级事件) ===

    /**
     * 佩戴本立牌的玩家造成击杀时触发(由 {@link #invokeKillHooks} 分发)。
     * 子类覆写以实现击杀类被动(如秘密侦探/占星师的击杀奖励)。
     */
    protected void onKill(Player killer, net.minecraft.world.entity.LivingEntity killed) {
    }

    /**
     * 佩戴本立牌的玩家被**敌对目标攻击**时触发(由 {@link #invokeHurtHooks} 分发,并在此统一过闸门)。
     * 环境伤害(摔落 / 仙人掌 / 着火 / 饥饿…)与自伤**不算**(2026-09-29 用户裁决)。
     * 子类覆写以实现受击类被动(如史莱姆立牌的受击 +1 治愈)。
     *
     * <p><b>{@code amount} 口径(2026-10-27 近战审计 D3;2026-10-27 复核订正)</b>:两条线**不同** ——
     * 本线(neo)传的是 {@code LivingDamageEvent.Pre#getNewDamage()}(护甲 / 药水之后、**吸收之前**);
     * forge 线传的是 {@code LivingDamageEvent#getAmount()}(护甲 / 药水 / 吸收已结算、骰战覆盖之前的平台值)。
     * 另有闪避取消路径传入的是 {@code LivingAttackEvent} 的**未减免原始值**。
     * 该数值只用于「是否受击」这类开关判定,<b>禁止</b>拿它做数值计算 —— 落地终值以骰战自算值为准
     * (neo 是 {@code DiceCombatEvents#onLivingDamagePre} 写入的 setNewDamage;forge 是
     * {@code DiceCombatEvents#settleDiceDamage} 的返回值,两者都已扣过吸收)。
     */
    protected void onHurt(Player player, DamageSource source, float amount) {
    }

    // 分发:玩家造成击杀时,调用其全部已装备立牌的 onKill 钩子
    public static void invokeKillHooks(Player killer, net.minecraft.world.entity.LivingEntity killed) {
        SignHookDispatcher.fireKillHooks(killer, killed);
    }

    // 分发:玩家被**敌对目标攻击**时,调用其全部已装备立牌的 onHurt 钩子
    // 2026-09-29 用户裁决:受击类被动一律只在该条件下触发 —— 此前是「任何来源的伤害」,
    // 于是摔落 / 仙人掌 / 着火等环境伤害、以及本模组「符卡-祸」的无来源真伤都会算数。
    // 闸门放在**分发处**而非各立牌内部 ⇒ 整族口径一致,将来新增受击被动默认即安全。
    public static void invokeHurtHooks(Player player, DamageSource source, float amount) {
        SignHookDispatcher.fireHurtHooks(player, source, amount);
    }

    /**
     * 非门控立牌主动技能的实际效果(子类覆写;返回 {@code SUCCESS} 才算触发成功)。
     *
     * <p>默认实现 = {@code fail}:「目标选择器类」立牌的主动已被 {@link #performSkill} 第 2.5 步的前置门控
     * 接管 —— 门控分支末尾直接 {@code return},**永远走不到本方法** ⇒ 覆写 {@link #selectorActionId()}
     * 的立牌(占星师 / 秘密侦探 / 枪匠)不再需要、也不再保留一份"进入目标选择模式"的重复实现
     * (那等于第二处 {@code TargetSelectionManager.start} 入口;2026-09-17 用户裁决 O2 已删除)。
     * 其余立牌**必须**覆写本方法,否则其主动无任何效果(返回 fail ⇒ 不发牌、不进冷却);
     * 为弥补由 {@code abstract} 改为默认实现后失去的编译期约束,未覆写时会打一条 WARN。
     */
    protected InteractionResultHolder<ItemStack> handleUse(Level level, Player player, ItemStack stack) {
        LOGGER.warn("[Astral Dice][SignSkill] handleUse 未被覆写: item={} —— 该立牌主动无效果"
                + "(目标选择器类立牌走 performSkill 第 2.5 步门控,不会到达这里)", stack);
        return InteractionResultHolder.fail(stack);
    }
}
