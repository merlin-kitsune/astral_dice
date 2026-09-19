package com.merlinkitsune.astral_dice.item.card;

import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;

import net.minecraft.ChatFormatting;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import com.merlinkitsune.astral_dice.item.chip.MagicTomeChipItem;
import com.merlinkitsune.astral_dice.item.chip.CandyChipItem;
import com.merlinkitsune.astral_dice.item.chip.SatelliteChipItem;
import com.merlinkitsune.astral_dice.item.ModItems;
import com.merlinkitsune.astral_dice.item.sign.FenSignItem;
import com.merlinkitsune.astral_dice.item.sign.PandamanSignItem;
import com.merlinkitsune.astral_dice.item.sign.JasmineSignItem;
import com.merlinkitsune.astral_dice.item.chip.MagicQuiverChipItem;
import com.merlinkitsune.astral_dice.item.chip.PiggyBankChipItem;
import com.merlinkitsune.astral_dice.item.sign.KomachiSignItem;
import com.merlinkitsune.astral_dice.network.ModNetwork;
import com.merlinkitsune.astral_dice.target.HoldToSelect;
import com.merlinkitsune.astral_dice.target.SelfTargetable;
import com.merlinkitsune.astral_dice.target.SignSelectionGate;
import com.merlinkitsune.astral_dice.target.TargetSelectionManager;
import com.merlinkitsune.starenginelib.component.GameplayConstants;
import com.merlinkitsune.starenginelib.target.TargetSelectionAction;
import com.merlinkitsune.starenginelib.target.TargetSelectionRegistry;
import com.merlinkitsune.starenginelib.target.TargetType;

/**
 * 效果牌统一基类(不再区分"功能效果牌/伤害效果牌")。
 *
 * 全部效果牌共用同一套使用流程,由本基类统一处理(use 与 interactLivingEntity
 * 共用私有方法 {@link #tryUseCard},避免双入口逻辑漂移):
 * 1. 客户端预检(专属校验 + 出牌锁,出牌状态附件已同步到客户端,判定与服务端一致;
 *    被阻止时直接失败——不消耗、不播放动画,杜绝"消耗了却没效果"的错位);
 * 2. 服务端权威判定(专属校验 → 出牌锁,见 {@link EffectCardPeriod#isBlocked});
 * 3. 调用子类的 {@link #applyEffect}(服务端,施加实际效果);
 * 4. 出牌登记(出牌数达到上限时才开始冷却,见 {@link EffectCardPeriod#registerPlay});
 * 5. 复制计数钩子(忍者立牌/魔法秘典/魔法箭袋,见 {@link #cardTypeId()};计数范围为全部效果牌,无类型过滤);
 * 6. 消耗一张。
 *
 * <p><b>释放方式二选一</b>:
 * <ul>
 *   <li><b>目标选择器类</b>(覆写 {@link #selectorActionId()} 返回非 null;加急加快 / 奢华大餐 / 你有我有 / 狂暴 / 活体书页):
 *       **主手手持该牌即自动进入目标选择会话**(2026-09-25 用户裁决「变更效果牌触发方式:
 *       当玩家手持该卡牌时(仅限主手),自动打开目标选择器,玩家移出手持则关闭目标选择器」,
 *       由 {@link #tickHeldSelector} 每 tick 驱动;**仅主手**,物品一离开主手立即退出选择),
 *       此类会话**没有倒计时**(见 {@code target/HoldToSelect});前置校验先判一次,而
 *       <b>效果 / 出牌登记 / 各类使用钩子 / 卡牌消耗全部推迟到确认目标之后</b>
 *       ({@link SelectorAction#apply} → {@link #playFromSelector});取消 / 移出手持 / 会话被替换 /
 *       登出 / 死亡 ⇒ 待执行记录被清除,该次出牌等同「未使用」(卡牌不消耗);</li>
 *   <li><b>自身牌</b>(缺省 {@code null}):按下即对自己施加效果,流程与旧实现逐字相同。</li>
 * </ul>
 * ⚠️ 2026-09-25 起**不再有**「下蹲右键对其他玩家使用」的路径,也不再需要「按下使用键才进入选择」:
 * 四张原可对他人使用的效果牌全部由**手持**触发选择器,旧的 {@code canUseOnOtherPlayers()} /
 * {@code findPlayerInFront()} 与对应分支随之删除(立牌侧同款门控见 {@code BaseSignItem#performSkill} 的第 2.5 步)。
 *
 * 子类二选一实现效果:
 * - 简单状态牌:覆写 {@link #getEffect()} 返回效果引用(基类自动施加 {@link #getEffectDuration()} 时长);
 * - 复杂逻辑牌:覆写 {@link #applyEffect()}(使用后对玩家/目标施加的效果)。
 * 按需覆写 {@link #cardTypeId()} / {@link #isExclusive()} / {@link #selectorActionId()}。
 * 全部效果牌均参与忍者立牌/魔法秘典/魔法箭袋的复制计数
 * (通过 {@link #cardTypeId()} 与 {@link #cardByTypeId(String)} 映射,无排除项)。
 */
public abstract class BaseEffectCardItem extends Item {

    public BaseEffectCardItem(Properties properties) {
        super(properties);
    }

    // 是否为专属效果牌(绑定获得者,他人不可用),默认否
    protected boolean isExclusive() {
        return false;
    }

    // 参与复制计数时的卡牌类型 id(与计数钩子的 cardByTypeId 映射对应;全部效果牌均参与复制,无排除项)
    protected abstract String cardTypeId();

    /**
     * 效果牌类型 id → 对应物品(忍者立牌复制/魔法秘典返还/魔法箭袋返还共用,
     * 与各牌 {@link #cardTypeId()} 保持单一实现;未识别的类型回退为王之力)。
     */
    public static ItemStack cardByTypeId(String cardTypeId) {
        return switch (cardTypeId) {
            case "berserk" -> new ItemStack(ModItems.EFFECT_CARD_BERSERK.get());
            case "unwavering" -> new ItemStack(ModItems.EFFECT_CARD_UNWAVERING.get());
            case "living_page" -> new ItemStack(ModItems.LIVING_PAGE.get());
            case "fight_poison_with_poison" -> new ItemStack(ModItems.EFFECT_CARD_FIGHT_POISON_WITH_POISON.get());
            case "king_power" -> new ItemStack(ModItems.EFFECT_CARD_KING_POWER.get());
            case "monster_laser" -> new ItemStack(ModItems.MONSTER_LASER_CARD.get());
            case "monster_brick" -> new ItemStack(ModItems.MONSTER_BRICK_CARD.get());
            case "orbital_strike" -> new ItemStack(ModItems.ORBITAL_STRIKE_CARD.get());
            case "directional_blast" -> new ItemStack(ModItems.DIRECTIONAL_BLAST_CARD.get());
            case "chocolate_cake" -> new ItemStack(ModItems.CHOCOLATE_CAKE.get());
            case "hamburger" -> new ItemStack(ModItems.HAMBURGER.get());
            case "luxury_feast" -> new ItemStack(ModItems.LUXURY_FEAST.get());
            case "you_have_i_have" -> new ItemStack(ModItems.YOU_HAVE_I_HAVE.get());
            case "express_delivery" -> new ItemStack(ModItems.EXPRESS_DELIVERY.get());
            case "fate_guidance" -> new ItemStack(ModItems.FATE_GUIDANCE_CARD.get());
            default -> new ItemStack(ModItems.EFFECT_CARD_KING_POWER.get());
        };
    }

    /**
     * 是否为治疗类效果牌(恢复生命值;大当家立牌被动:使用治疗类效果牌时"养精蓄锐"+1 层)。
     * 治疗类:巧克力蛋糕/汉堡/奢华大餐。
     */
    protected boolean isHealingCard() {
        return false;
    }

    /**
     * 简单状态牌:返回使用后施加的状态效果引用;返回 null 时基类不自动施加,
     * 需覆写 {@link #applyEffect} 实现复杂逻辑。默认返回 null。
     */
    protected MobEffect getEffect() {
        return null;
    }

    // 简单状态牌的效果时长(tick):默认 60 秒
    protected int getEffectDuration() {
        return 1200;
    }

    /**
     * 施加效果(仅服务端调用;出牌锁已校验通过)。
     * 默认实现:若 {@link #getEffect()} 非 null 则向目标施加该效果指定时长;
     * 复杂效果牌覆写本方法。
     *
     * @param applyTo 实际受益目标 —— 目标选择器类效果牌为**确认的玩家**(可能是自己);自身牌恒为自己
     */
    protected void applyEffect(Level level, Player user, LivingEntity applyTo, ItemStack stack) {
        MobEffect effect = getEffect();
        if (effect != null) {
            applyTo.addEffect(new MobEffectInstance(effect, getEffectDuration(), 0, false, true));
        }
    }

    // ── 目标选择器释放(2026-09-25 用户裁决:四张「可对他人使用」的效果牌全部改走选择器,且**手持即选择**)──
    /**
     * 「目标选择器类」效果牌的动作 id(缺省 {@code null} = 自身牌,按下即对自己生效)。
     *
     * <p>返回非 null 时:主手手持该牌即**自动开启目标选择会话**(见 {@link #tickHeldSelector}),
     * 移出主手立即关闭;{@link #use} 只作为客户端/服务端**状态不同步时的兜底入口**
     * (会话已存在时直接失败,不会重复开局)。专属校验与出牌锁先判一次,**效果、出牌登记、
     * 各类使用钩子与卡牌消耗全部推迟到确认目标之后**(见 {@link SelectorAction#apply});
     * 取消 / 移出手持 / 会话被替换 / 登出 / 死亡 ⇒ 待执行记录被清除,该次出牌等同「未使用」
     * (卡牌不消耗)。**目标类型由注册时传入**(见 {@link #registerSelectorAction(String, TargetType, boolean)},
     * 缺省 {@link TargetType#PLAYER});半径缺省取配置统一值
     * ({@link GameplayConstants#TARGET_SELECT_RADIUS}),亦可由注册时显式声明(见
     * {@link #registerSelectorAction(String, TargetType, boolean, double)});是否允许对自身使用由
     * {@code allowSelf} 决定。
     */
    public String selectorActionId() {
        return null;
    }

    /**
     * 目标选择器动作注册入口(由各选择器类效果牌的静态块调用,保证类加载即注册)。
     *
     * <p>两参重载保留给「可对他人使用」的既有四张牌(加急加快 / 奢华大餐 / 你有我有 / 狂暴):
     * 目标类型逐字为 {@link TargetType#PLAYER}(排除选择者自身,能否自用由 {@code allowSelf} 决定)。
     */
    protected static void registerSelectorAction(String actionId, boolean allowSelf) {
        registerSelectorAction(actionId, TargetType.PLAYER, allowSelf);
    }

    /**
     * 目标选择器动作注册入口(显式目标类型;2026-09-25「活体书页」改敌对目标选择器时新增)。
     *
     * <p>可选中判定统一走 {@code target/SelectorTargets#matches} ⇒ 传 {@link TargetType#ENEMY}
     * 即本模组全局的「敌对目标」口径(敌对生物 ∪ 已被激怒的中立生物,**不含玩家**),
     * 客户端准星过滤 / 半径高亮 / 服务端确认三处同一判据。
     *
     * <p>半径缺省 = 配置统一值({@link GameplayConstants#TARGET_SELECT_RADIUS});需要**更大**锁定范围
     * 的牌用四参重载显式声明(上限 = 前置库契约的 32 格,见
     * {@link #registerSelectorAction(String, TargetType, boolean, double)})。
     */
    protected static void registerSelectorAction(String actionId, TargetType targetType, boolean allowSelf) {
        TargetSelectionRegistry.register(new SelectorAction(actionId, targetType, allowSelf, -1.0D));
    }

    /**
     * 目标选择器动作注册入口(显式目标类型 + 显式锁定范围;2026-09-19「活体书页 32 格」新增)。
     *
     * @param radius 该动作的锁定范围(格),{@code <= 0} = 用配置统一值。**该值同时决定**
     *               客户端准星射线长度 / 半径高亮范围 / 「距目标多远还能确认」,且是**三维距离**
     *               (含垂直高度差);服务端 {@code target/TargetSelectionManager} 会把它夹到
     *               前置库契约的上限 32 格(见该类 start/confirm 的注释)。
     */
    protected static void registerSelectorAction(String actionId, TargetType targetType, boolean allowSelf,
                                                 double radius) {
        TargetSelectionRegistry.register(new SelectorAction(actionId, targetType, allowSelf, radius));
    }

    /**
     * 目标选择器确认路径的出牌入口:走完与 {@link #use} 完全相同的服务端流程
     * (专属校验 → 出牌锁 → 施加效果 → 出牌登记 → 各使用钩子),**不含消耗** ——
     * 消耗由 {@link SelectorAction#apply} 在成功返回后执行。
     *
     * @return 是否成功出牌;false 时调用方不得消耗卡牌
     */
    public boolean playFromSelector(ServerPlayer player, LivingEntity target, ItemStack stack) {
        return tryUseCard(player.level(), player, target, stack);
    }

    /**
     * 目标选择器动作:目标类型取注册时传入的值(缺省 {@link TargetType#PLAYER},选择者自身由
     * {@code allowSelf} 决定),确认后取回按下时登记的待执行记录、按物品找回仍在身上的那张牌并出牌,最后消耗一张。
     *
     * <p>同时实现 {@link HoldToSelect}:该动作由**主手手持卡牌**触发、**无倒计时** —— 服务端每 tick
     * 用 {@link #stillHeld} 判定,物品离开主手即关闭会话。
     */
    private static final class SelectorAction implements TargetSelectionAction, SelfTargetable, HoldToSelect {
        private final String actionId;
        private final TargetType targetType;
        private final boolean allowSelf;
        /** 显式声明的锁定范围(格);{@code <= 0} = 用前置库配置统一值 */
        private final double declaredRadius;

        private SelectorAction(String actionId, TargetType targetType, boolean allowSelf, double declaredRadius) {
            this.actionId = actionId;
            this.targetType = targetType == null ? TargetType.PLAYER : targetType;
            this.allowSelf = allowSelf;
            this.declaredRadius = declaredRadius;
        }

        @Override
        public String id() {
            return actionId;
        }

        @Override
        public TargetType targetType() {
            return targetType;
        }

        @Override
        public double radius() {
            // 缺省（未显式声明）沿用前置库接口默认值 = 配置 TARGET_SELECT_RADIUS；
            // 显式声明者原样返回，由 TargetSelectionManager 按契约上限 32 夹取。
            return declaredRadius > 0.0D ? declaredRadius : TargetSelectionAction.super.radius();
        }

        @Override
        public boolean allowSelf() {
            return allowSelf;
        }

        @Override
        public boolean stillHeld(Player player) {
            return player != null && heldCardMatches(player.getMainHandItem(), actionId);
        }

        @Override
        public void apply(ServerPlayer player, LivingEntity target) {
            // 取走按使用键时登记的待执行记录(同一次会话只会走到这里一次);
            // 取走后 sign 侧恢复点 resumeGatedActiveSkill 自然空转,不会误触发立牌逻辑。
            SignSelectionGate.Pending pending = SignSelectionGate.take(player, actionId);
            if (pending == null) return;
            if (!(pending.stack().getItem() instanceof BaseEffectCardItem card)) return;
            ItemStack live = findHeldCard(player, pending.stack());
            if (live.isEmpty()) {
                sendActionBar(player, "msg.astral_dice.effect_card_selector_no_card");
                return;
            }
            if (!card.playFromSelector(player, target, live)) {
                // 按下之后出牌锁才被占满/进入冷却 ⇒ 该次出牌作废,卡牌不消耗
                sendActionBar(player, "msg.astral_dice.effect_card_selector_blocked");
                return;
            }
            live.shrink(1);
        }
    }

    /** 在玩家背包(含快捷栏/副手/盔甲槽)里找出与快照同一物品、同一 NBT 的那个非空栈 */
    private static ItemStack findHeldCard(Player player, ItemStack snapshot) {
        for (ItemStack candidate : player.getInventory().items) {
            if (!candidate.isEmpty() && ItemStack.isSameItemSameTags(candidate, snapshot)) {
                return candidate;
            }
        }
        return ItemStack.EMPTY;
    }

    /** 该栈是否是 {@code actionId} 对应的选择器类效果牌(**仅判定物品与动作 id**,不看槽位) */
    private static boolean heldCardMatches(ItemStack stack, String actionId) {
        return !stack.isEmpty() && stack.getItem() instanceof BaseEffectCardItem card
                && actionId.equals(card.selectorActionId());
    }

    /**
     * 每 tick(服务端,由 {@code event/PlayerTickEvents#onPlayerTick} 驱动):
     * **主手手持选择器类效果牌且当前没有选择会话 ⇒ 自动开启会话并登记待执行记录**(2026-09-25 用户裁决)。
     *
     * <p>三条门槛与 {@link #useViaSelector} 完全一致(专属校验 → 出牌锁 → 已在选择中),任一不过就
     * **静默跳过**、留待下一 tick 重试 —— 于是「牌在冷却中时握着不生效,冷却一过自动进入选择态」是
     * 自然结果,不需要额外的等待提示。松手/换槽由 {@code TargetSelectionManager.tick} 侧的
     * {@link HoldToSelect#stillHeld} 判定负责关闭会话(此处只负责开)。
     */
    public static void tickHeldSelector(ServerPlayer player) {
        if (TargetSelectionManager.isSelecting(player)) return;
        ItemStack stack = player.getMainHandItem();
        if (!(stack.getItem() instanceof BaseEffectCardItem card) || card.selectorActionId() == null) return;
        // 显式取消过的动作:牌还在手里时不自动重开(要先把牌移出主手再握回来,见 TargetSelectionManager 的抑制闩)
        if (TargetSelectionManager.isHoldSuppressed(player, card.selectorActionId())) return;
        if (!tryStartSelector(card, player, stack, card.selectorActionId())) return;
        // 待执行记录:确认后 apply 取走它 → 效果 / 出牌登记 / 各钩子 / 消耗全部推迟到确认之后
        SignSelectionGate.arm(player, card.selectorActionId(), stack);
    }

    /**
     * 服务端选择器开局三连(专属校验 → 出牌锁 → 未在选择中)+ 真正 {@code start};
     * 成功时只开会话,**不**消耗、不施效(消耗与施效见 {@link SelectorAction#apply})。
     */
    private static boolean tryStartSelector(BaseEffectCardItem card, ServerPlayer serverPlayer, ItemStack stack,
                                            String actionId) {
        if (card.isExclusive() && !ExclusiveCardUtil.canUse(serverPlayer, stack)) {
            return false;
        }
        if (EffectCardPeriod.isBlocked(serverPlayer)) {
            return false;
        }
        if (TargetSelectionManager.isSelecting(serverPlayer)) {
            return false;
        }
        return TargetSelectionManager.start(serverPlayer, actionId);
    }

    /** 目标选择器路径的服务端反馈(actionbar,沿用本模组的统一发送通道) */
    private static void sendActionBar(ServerPlayer player, String langKey) {
        ModNetwork.sendToPlayer(player, new ModNetwork.ActionBarMessage(
                Component.translatable(langKey).withStyle(ChatFormatting.RED),
                GameplayConstants.ACTIONBAR_DURATION_TICKS));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);

        // 目标选择器类:正常路径是「主手手持即自动开局」(tickHeldSelector);这里只是兜底 ——
        // 仍**不消耗、不施效**,效果与消耗都推迟到确认之后
        if (selectorActionId() != null) {
            return useViaSelector(level, player, stack);
        }

        if (level.isClientSide) {
            // 客户端预检:出牌状态附件已同步到客户端,判定与服务端一致;
            // 被阻止时直接失败——不消耗、不播放使用动画,避免"消耗了却没效果"的错位
            if (isBlockedOnClient(player, stack)) {
                return InteractionResultHolder.fail(stack);
            }
        } else if (!tryUseCard(level, player, player, stack)) {
            // 自身牌:实际受益目标恒为自己
            return InteractionResultHolder.fail(stack);
        }
        stack.shrink(1);
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }

    @Override
    public InteractionResult interactLivingEntity(ItemStack stack, Player player, LivingEntity target, InteractionHand hand) {
        // 选择器类效果牌:对实体右键不立即生效 —— 会话正常由「手持即选择」驱动,
        // 这里只做与 use 同一入口的兜底(会话已存在则失败),避免出现「右键实体直接对目标生效」的旁路
        if (selectorActionId() != null) {
            return useViaSelector(player.level(), player, stack).getResult().consumesAction()
                    ? InteractionResult.SUCCESS : InteractionResult.FAIL;
        }
        // 其余效果牌不响应实体交互(维持旧行为:对实体右键直接落到 use 的自身牌路径)
        return InteractionResult.PASS;
    }

    /**
     * 选择器类效果牌的按键兜底入口(正常路径由 {@link #tickHeldSelector} 的「手持即选择」驱动):
     * 客户端只做预检(被阻止时失败),服务端先做权威前置校验再开启选择会话并登记待执行记录。
     *
     * <p>**两个方向都不消耗卡牌**:真正的消耗发生在确认目标之后(见 {@link SelectorAction#apply})。
     * 会话已存在时一律失败(客户端在选择期间会吞掉使用键,故这只覆盖状态不同步的兜底场景)。
     */
    private InteractionResultHolder<ItemStack> useViaSelector(Level level, Player player, ItemStack stack) {
        if (level.isClientSide) {
            return isBlockedOnClient(player, stack)
                    ? InteractionResultHolder.fail(stack) : InteractionResultHolder.success(stack);
        }
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResultHolder.fail(stack);
        }
        String actionId = selectorActionId();
        if (TargetSelectionManager.isSelecting(player) || !tryStartSelector(this, serverPlayer, stack, actionId)) {
            return InteractionResultHolder.fail(stack);
        }
        SignSelectionGate.arm(player, actionId, stack);
        return InteractionResultHolder.success(stack);
    }

    // 客户端预检:与服务端 tryUseCard 的判定保持一致(专属校验 + 出牌锁)。
    // 出牌数/冷却/忍者临时出牌附件均已 .sync() 到客户端,客户端可实时判定。
    private boolean isBlockedOnClient(Player player, ItemStack stack) {
        boolean exclusiveBlocked = isExclusive() && !ExclusiveCardUtil.canUse(player, stack);
        if (exclusiveBlocked) return true;
        if (EffectCardPeriod.isBurstFull(player)) {
            int seconds = EffectCardPeriod.getRemainingBlockSeconds(player);
            player.displayClientMessage(
                    Component.translatable("msg.astral_dice.effect_card_burst_full", seconds), true);
            return true;
        }
        return EffectCardPeriod.isBlocked(player);
    }

    /**
     * 服务端完整出牌流程(use 与 interactLivingEntity 共用,避免逻辑漂移):
     * 专属校验 → 出牌锁 → 施加效果 → 出牌登记 → 治疗类钩子 → 复制计数钩子。
     *
     * @return 是否成功出牌;false 时调用方不得消耗卡片
     */
    private boolean tryUseCard(Level level, Player player, LivingEntity applyTo, ItemStack stack) {
        // 专属牌:仅允许获得者使用
        if (isExclusive() && !ExclusiveCardUtil.canUse(player, stack)) {
            return false;
        }
        // 出牌锁:出牌数上限/冷却/效果待定任一阻止则失败
        if (EffectCardPeriod.isBlocked(player)) {
            return false;
        }

        // 施加效果(子类实现)
        applyEffect(level, player, applyTo, stack);

        // 出牌登记:立即开始/重置冷却倒计时(冷却与效果分离计算)
        EffectCardPeriod.registerPlay(player);

        // 可口糖果:每使用一张效果牌触发(治愈+1、回血+1、满血时本轮出牌数+1)
        CandyChipItem.onEffectCardUsed(player);

        // 电击手套:使用效果牌充能 +1;伤害效果牌且充能 ≥4 时消耗 4 层充能,武装本周期的法伤扩散
        com.merlinkitsune.astral_dice.item.chip.ElectricGloveChipItem.onEffectCardUsed(player, stack);

        // 探天卫星:使用"轨道炮"后本轮出牌数+1(每 1:00 一次)
        if (stack.is(ModItems.ORBITAL_STRIKE_CARD.get())) {
            SatelliteChipItem.onOrbitalStrikeUsed(player);
        }

        // 扫地机立牌被动:使用「加急加快」后,主动技能冷却立即减少最大冷却的 50%
        if (stack.is(ModItems.EXPRESS_DELIVERY.get())) {
            JasmineSignItem.onExpressDeliveryUsed(player);
        }

        // 治疗类效果牌:大当家立牌被动"养精蓄锐 +1 层"
        if (isHealingCard()) {
            FenSignItem.onHealingCardUsed(player);
        }
        // 肉弹战车立牌被动:使用汉堡/巧克力蛋糕后触发治疗与生命上限效果
        if (stack.is(ModItems.HAMBURGER.get()) || stack.is(ModItems.CHOCOLATE_CAKE.get())) {
            PandamanSignItem.onHealingFoodUsed(player, stack.is(ModItems.HAMBURGER.get()));
        }

        // 复制计数钩子(忍者立牌/魔法秘典/魔法箭袋):全部效果牌均参与,无排除项
        KomachiSignItem.onEffectCardUsed(player, cardTypeId());
        MagicTomeChipItem.onEffectCardUsed(player, cardTypeId());
        MagicQuiverChipItem.onEffectCardUsed(player, cardTypeId());
        // 小猪存钱罐筹码:每使用 2 张效果牌获得 3 星币(独立计数)
        PiggyBankChipItem.onEffectCardUsed(player);
        return true;
    }
}

