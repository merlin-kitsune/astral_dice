package com.merlinkitsune.astral_dice.item.sign;

import com.merlinkitsune.starenginelib.component.GameplayConstants;
import com.merlinkitsune.astral_dice.component.ModAttachments;
import com.merlinkitsune.astral_dice.component.ModDataComponents;
import net.minecraft.ChatFormatting;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.SlotContext;
import top.theillusivec4.curios.api.type.capability.ICurioItem;
import com.merlinkitsune.astral_dice.network.ActionBarPayload;
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
        // 2. 目标选择会话检查:已处于目标选择模式时按键无效(防重复进入;客户端按 J 会先取消,此处为服务端兜底)
        if (com.merlinkitsune.astral_dice.target.TargetSelectionManager.isSelecting(player)) return;
        // 2.5 「目标选择器类」立牌的前置门控(2026-09-17 用户裁决):按下主动键**只**开启目标选择会话并立即返回。
        //     本次主动的效果、玩家级冷却/锁定、电流核心充能、风扇筹码发牌与立牌主动响应事件(含默认提示)
        //     **全部推迟到确认合法目标之后**(恢复点见 resumeGatedActiveSkill);效果与冷却/充能由各
        //     TargetSelectionAction#apply 负责,恢复流程不重复写一次。
        //     GameplayConstants.SKILL_WAIT_SECONDS(秒)内未选择或取消 ⇒ 记录被清除,
        //     该次主动等同「未使用」(不发牌/不进冷却/不施效果)。
        //     actionId 为 null 的立牌(其余全部立牌)不走本分支,下方原流程逐字不变。
        String gatedActionId = sign.selectorActionId();
        if (gatedActionId != null) {
            if (!(player instanceof net.minecraft.server.level.ServerPlayer serverPlayer)) return;
            if (com.merlinkitsune.astral_dice.target.TargetSelectionManager.start(serverPlayer, gatedActionId)) {
                com.merlinkitsune.astral_dice.target.SignSelectionGate.arm(player, gatedActionId, stack);
            }
            return;
        }
        // 3. 触发主动技能
        InteractionResultHolder<ItemStack> result = sign.handleUse(player.level(), player, stack);
        if (result.getResult() != InteractionResult.SUCCESS) return;
        // 4. 手持风扇-大筹码:使用主动技能后,获得一张随机效果牌(不含专属),并对周围范围内敌对目标施加标记
        FanBigChipItem.applyAfterSignSkill(player);
        FanSmallChipItem.applyAfterSignSkill(player);
        // 5. 立牌主动技能响应事件:立牌类订阅本事件注册自身 ActionBar 反馈(见 SignActiveTriggeredEvent);
        //    无任何处理器响应(未注册)时,发送默认提示"xxx立牌:主动技能已启动!"
        com.merlinkitsune.starenginelib.event.SignActiveTriggeredEvent triggered =
                new com.merlinkitsune.starenginelib.event.SignActiveTriggeredEvent(player, stack);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(triggered);
        if (!triggered.isHandled()) {
            notifyActionBar(player, "msg.astral_dice.sign_active_triggered", signName, ChatFormatting.YELLOW);
        }
        // 6. 冷却:目标选择器类技能(已进入选择会话)待确认目标后在 apply 中开始冷却;其余立牌立即开始玩家级冷却
        if (!com.merlinkitsune.astral_dice.target.TargetSelectionManager.isSelecting(player)) {
            // 诡异骰子:立牌主动冷却 -50%
            int signCooldownTicks = com.merlinkitsune.astral_dice.event.WeirdDiceHandler.signCooldownTicks(player);
            if (sign.startActiveLockOnUse(player, now)) {
                // ★ 本主动施加了"带时长效果/自身计时器"⇒ 进入锁定(生效中)态。
                //   锁定期间**不写** sign_active_cooldown_end:冷却要等锁定结束才起(第 13 条:无空档);
                //   但先把本次冷却基准写进 sign_active_max_cooldown,供锁定期间各减免方读它并累加进减免池。
                ModAttachments.setSignActiveMaxCooldown(player, signCooldownTicks);
            } else {
                ModAttachments.setSignActiveCooldownEnd(player, now + signCooldownTicks);
                // 路线 A:记录本次冷却实际使用的最大冷却值,所有减免方一律读它(不再各自重算基准)
                ModAttachments.setSignActiveMaxCooldown(player, signCooldownTicks);
            }
            // 电流核心筹码:主动技能实际生效时充能 +1(进入锁定时同样计一次;与是否立即起冷却无关)
            com.merlinkitsune.astral_dice.item.chip.CurrentCoreChipItem.onActiveSkillUsed(player);
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

    /**
     * 目标选择**确认成功**后的恢复点(由 {@code TargetSelectionManager#confirm} 在 action.apply 之后调用)。
     *
     * <p>只对「由立牌门控登记的会话」生效:非立牌会话(如 {@code test_echo_*})没有待执行记录 ⇒ 直接返回,
     * 原有行为不受影响。恢复的是原 performSkill 的第 4/5 步(风扇筹码发牌 + 立牌主动响应事件);
     * **不**重复写玩家级冷却/锁定与电流核心充能 —— 那两件事已由各 TargetSelectionAction#apply 完成。
     *
     * <p>⚠️ 事件本身照旧抛出(订阅方行为不变),但**有意不补发**默认「主动技能已启动」提示(2026-09-17
     * 用户裁决 O1,结论不变):本方法只由门控路径到达,而该 tick 已经发过反馈 —— 通用「已确认」提示由
     * {@code TargetSelectionManager#confirm} 在 {@code action.apply} **之前**发出,各动作的专属提示又在
     * {@code apply} 里发出;客户端 actionbar 是**单槽位**(starenginelib 的 {@code ActionBarManager#show}
     * 直接覆盖,同 tick 后发者覆盖先发者)⇒ 若在此补发,它反而会成为玩家唯一看到的那条,而这不是期望反馈。
     * 非门控立牌的原流程(performSkill 第 5 步)仍保留默认提示。
     */
    public static void resumeGatedActiveSkill(Player player, String actionId) {
        com.merlinkitsune.astral_dice.target.SignSelectionGate.Pending pending =
                com.merlinkitsune.astral_dice.target.SignSelectionGate.take(player, actionId);
        if (pending == null) return;
        ItemStack stack = pending.stack();
        // 4. 手持风扇-大/小筹码:确认释放后才发牌(未确认绝不发牌)
        FanBigChipItem.applyAfterSignSkill(player);
        FanSmallChipItem.applyAfterSignSkill(player);
        // 5. 立牌主动技能响应事件:立牌类订阅本事件注册自身 ActionBar 反馈。
        //    默认「主动技能已启动」提示**有意不补发**(见方法 javadoc:该 tick 已由通用提示与动作专属
        //    提示反馈过,再补发它会成为唯一可见的那条)。
        com.merlinkitsune.starenginelib.event.SignActiveTriggeredEvent triggered =
                new com.merlinkitsune.starenginelib.event.SignActiveTriggeredEvent(player, stack);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(triggered);
    }

    // 立牌主动技能反馈统一发送入口(黄色;供立牌类注册的 SignActiveTriggeredEvent 处理器与 handleUse 调用)
    protected static void sendSignActionBar(Player player, String langKey, Object... args) {
        if (!(player instanceof net.minecraft.server.level.ServerPlayer serverPlayer)) return;
        net.minecraft.network.chat.Component msg =
                net.minecraft.network.chat.Component.translatable(langKey, args).withStyle(ChatFormatting.YELLOW);
        net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(serverPlayer,
                new com.merlinkitsune.astral_dice.network.ActionBarPayload(msg,
                        GameplayConstants.ACTIONBAR_DURATION_TICKS));
    }

    // 服务端发送立牌技能反馈(actionbar 提示,带立牌名称前缀;统一由服务端判定成功/拒绝,避免客户端推测混淆)
    private static void notifyActionBar(Player player, String langKey, net.minecraft.network.chat.Component signName, ChatFormatting color) {
        if (!(player instanceof net.minecraft.server.level.ServerPlayer serverPlayer)) return;
        net.minecraft.network.chat.Component msg =
                net.minecraft.network.chat.Component.translatable(langKey, signName).withStyle(color);
        net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(serverPlayer,
                new com.merlinkitsune.astral_dice.network.ActionBarPayload(msg,
                        GameplayConstants.ACTIONBAR_DURATION_TICKS));
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

    /** 忍者立牌的锁定标记(其主动无任何自身计时器,锁定跟随出牌周期,由周期完全重置结束) */
    public static final String KOMACHI_LOCK_ID = "astral_dice:komachi_sign";

    /** 正在生效中的主动所属立牌注册 id("" = 未锁定) */
    public static String getSignActiveLockSignId(Player player) {
        String signId = ModAttachments.getSignActiveLockSign(player);
        return signId == null ? "" : signId;
    }

    /**
     * 锁定(生效中)态判定(第 1/4 条):"本技能施加的计时器"是否仍在跑。
     *
     * <p>判据 = {@code sign_active_lock_end}(触发时刻算定的全部计时器到期刻取 max,**硬上界**)尚未到期,
     * <b>且</b>本立牌的门控效果实例仍在 —— 后者只让锁定**提前**结束(如骇客被攻击破隐、
     * 效果被其它路径移除),外部把同一效果刷新得更长**不会**延长锁定(硬上界不随刷新重算)。
     *
     * <p>效果严格按 20t/s 流动由 {@link com.merlinkitsune.astral_dice.event.EffectTimerGuard} 保证,
     * 故效果实例的 {@code getDuration()} 即权威剩余时长;本判定**不硬编码任何时长表**,
     * 也**不复用** {@link com.merlinkitsune.astral_dice.item.card.EffectCardPeriod} 的
     * {@code EFFECT_PENDING_SOURCES}(那是"效果牌出牌锁"的语义域,与立牌主动无关)。
     *
     * <p>忍者(无自身计时器,{@code lock_end == 0})只由"锁定标记是否仍在"判定,
     * 其结束有两条出口:宽限到期未出过任何效果牌(见 {@link #tickSignActiveLock})、
     * 或出牌周期完全重置(见 {@link #onEffectCardRoundReset})。
     */
    public static boolean isSignActiveLocked(Player player) {
        if (player == null) return false;
        String signId = getSignActiveLockSignId(player);
        if (signId.isEmpty()) return false;
        long lockEnd = ModAttachments.getSignActiveLockEnd(player);
        if (lockEnd > 0 && player.level().getGameTime() >= lockEnd) return false;
        BaseSignItem sign = lockSignItem(signId);
        if (sign == null) {
            // 立牌实例解析失败(锁定标记写的是物品注册 id,理论上不会发生):
            // 有硬上界时按上方已判过的上界继续锁定;无硬上界(忍者)时判为未锁定,避免永久锁死
            return lockEnd > 0;
        }
        return sign.isGateEffectActive(player);
    }

    // 按立牌注册 id 取回立牌实例(锁定态判定需要回调具体立牌覆写的"门控效果仍在")
    private static BaseSignItem lockSignItem(String signId) {
        net.minecraft.resources.ResourceLocation id = net.minecraft.resources.ResourceLocation.tryParse(signId);
        if (id == null) return null;
        net.minecraft.world.item.Item item = net.minecraft.core.registries.BuiltInRegistries.ITEM.get(id);
        return item instanceof BaseSignItem sign ? sign : null;
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

    /** 进入锁定态:写锁定标记与硬上界(宽限/减免池归零,避免跨技能残留) */
    protected static void beginActiveLock(Player player, String signId, long lockEndTick) {
        ModAttachments.setSignActiveLockSign(player, signId);
        ModAttachments.setSignActiveLockEnd(player, lockEndTick);
        ModAttachments.setSignActiveLockGraceEnd(player, 0L);
        ModAttachments.setSignActiveLockPlayed(player, false);
        ModAttachments.setSignActiveReductionPool(player, 0L);
    }

    /**
     * 结束锁定并进入冷却(唯一出口,第 5/13 条):一次性抵扣锁定期间累计的减免池,
     * {@code effective = max(0, 基准 − 池)};写 {@code sign_active_cooldown_end = now + effective}
     * 并把 {@code sign_active_max_cooldown} 改写为 {@code effective}(电流核心档位分母取抵扣后的实际冷却),
     * 随后清空全部锁定键。锁定态下{@code cooldown_end} 为 0,故本方法一执行即"无空档"地进入冷却。
     */
    private static void endLockAndStartCooldown(Player player) {
        long now = player.level().getGameTime();
        long base = ModAttachments.getSignActiveMaxCooldown(player);
        long pool = ModAttachments.getSignActiveReductionPool(player);
        long effective = Math.max(0L, base - pool);
        ModAttachments.setSignActiveCooldownEnd(player, now + effective);
        ModAttachments.setSignActiveMaxCooldown(player, effective);
        ModAttachments.setSignActiveReductionPool(player, 0L);
        ModAttachments.setSignActiveLockSign(player, "");
        ModAttachments.setSignActiveLockEnd(player, 0L);
        ModAttachments.setSignActiveLockGraceEnd(player, 0L);
        ModAttachments.setSignActiveLockPlayed(player, false);
    }

    /**
     * 玩家级 tick(幂等):1. 忍者宽限保险;2. 其余立牌锁定结束时必起冷却。
     *
     * <p>为什么挂在**玩家级 tick**(见 {@code event/PlayerTickEvents}):
     * 判定必须与立牌是否仍在饰品槽无关;并且玩家离线时不 tick ⇒ 锁定结束那一刻离线的话,
     * 由上线后的第一次判定迁移到冷却(等效"离线期间冷却不走")。
     *
     * <p>幂等性(forge 侧 {@code TickEvent.PlayerTickEvent} 每 tick 触发两次):首行按"是否仍在锁定"早退,
     * 真正的迁移只发生一次(迁移后锁定标记被清空,第二次执行直接返回),与旧的玩家级状态迁移同构。
     */
    public static void tickSignActiveLock(Player player) {
        if (player == null) return;
        if (player.level().isClientSide()) return;
        String signId = getSignActiveLockSignId(player);
        if (signId.isEmpty()) return;
        long now = player.level().getGameTime();
        if (KOMACHI_LOCK_ID.equals(signId)) {
            // 忍者:锁定跟随出牌周期,本方法只负责"宽限 1:00 内自始至终未出任何效果牌"的保险。
            // 其余出口是出牌周期完全重置(EffectCardPeriod -> onEffectCardRoundReset)。
            long graceEnd = ModAttachments.getSignActiveLockGraceEnd(player);
            if (graceEnd <= 0 || now < graceEnd) return;
            if (ModAttachments.getSignActiveLockPlayed(player)) {
                // 宽限期内出过效果牌 ⇒ 保险失效:清宽限刻,遵循出牌周期(等周期完全重置)
                ModAttachments.setSignActiveLockGraceEnd(player, 0L);
                return;
            }
            // 宽限期内自始至终未出任何效果牌 ⇒ 强制重置出牌状态,并让主动技能进入冷却
            com.merlinkitsune.astral_dice.item.card.EffectCardPeriod.forceResetRound(player);
            endLockAndStartCooldown(player);
            return;
        }
        if (isSignActiveLocked(player)) return;   // 门控计时器仍在跑:保持锁定
        endLockAndStartCooldown(player);          // 锁定结束:必起冷却(第 13 条,无空档)
    }

    /**
     * 出牌轮"完全重置"回调(忍者专用,第 4 条):由 {@link com.merlinkitsune.astral_dice.item.card.EffectCardPeriod}
     * 的**两处**周期边界调用({@code tick} 情形 1 与 {@code registerPlay} 的边界块),
     * 使忍者的主动冷却从"该轮出牌状态完全重置那一刻"开始。
     */
    public static void onEffectCardRoundReset(Player player) {
        if (player == null || player.level().isClientSide()) return;
        if (!KOMACHI_LOCK_ID.equals(getSignActiveLockSignId(player))) return;
        endLockAndStartCooldown(player);
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
     * 佩戴本立牌的玩家受到伤害时触发(由 {@link #invokeHurtHooks} 分发)。
     * 子类覆写以实现受击类被动(如史莱姆立牌的受击 +1 治愈)。
     */
    protected void onHurt(Player player, float amount) {
    }

    // 分发:玩家造成击杀时,调用其全部已装备立牌的 onKill 钩子
    public static void invokeKillHooks(Player killer, net.minecraft.world.entity.LivingEntity killed) {
        if (killer == null || killer.level().isClientSide()) return;
        CuriosApi.getCuriosInventory(killer).ifPresent(handler -> {
            var results = handler.findCurios(s -> s.getItem() instanceof BaseSignItem);
            for (var r : results) {
                if (r.stack().getItem() instanceof BaseSignItem sign) {
                    sign.onKill(killer, killed);
                }
            }
        });
    }

    // 分发:玩家受到伤害时,调用其全部已装备立牌的 onHurt 钩子
    public static void invokeHurtHooks(Player player, float amount) {
        if (player == null || player.level().isClientSide()) return;
        CuriosApi.getCuriosInventory(player).ifPresent(handler -> {
            var results = handler.findCurios(s -> s.getItem() instanceof BaseSignItem);
            for (var r : results) {
                if (r.stack().getItem() instanceof BaseSignItem sign) {
                    sign.onHurt(player, amount);
                }
            }
        });
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
