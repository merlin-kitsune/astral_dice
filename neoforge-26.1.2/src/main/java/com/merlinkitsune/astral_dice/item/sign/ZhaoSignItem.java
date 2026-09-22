package com.merlinkitsune.astral_dice.item.sign;

import com.merlinkitsune.astral_dice.AstralDiceMod;
import com.merlinkitsune.astral_dice.component.ModAttachments;
import com.merlinkitsune.astral_dice.effect.ModEffects;
import com.merlinkitsune.astral_dice.effect.MisfortuneEffect;
import com.merlinkitsune.astral_dice.effect.ZhaoBlessingEffect;
import com.merlinkitsune.astral_dice.event.WeirdDiceHandler;
import com.merlinkitsune.astral_dice.item.ModItems;
import com.merlinkitsune.astral_dice.item.card.FuCardItem;
import com.merlinkitsune.astral_dice.item.card.HuoCardItem;
import com.merlinkitsune.astral_dice.item.chip.CurrentCoreChipItem;
import com.merlinkitsune.astral_dice.network.ActionBarPayload;
import com.merlinkitsune.astral_dice.target.SelfTargetable;
import com.merlinkitsune.starenginelib.component.GameplayConstants;
import com.merlinkitsune.starenginelib.event.EventTargetCollector;
import com.merlinkitsune.starenginelib.target.TargetSelectionAction;
import com.merlinkitsune.starenginelib.target.TargetSelectionRegistry;
import com.merlinkitsune.starenginelib.target.TargetType;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingHealEvent;
import net.neoforged.neoforge.event.entity.living.MobEffectEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.theillusivec4.curios.api.CuriosApi;

import java.util.ArrayList;
import java.util.List;

/**
 * 风水师立牌(全局命名 {@code zhao};传奇品质)。
 *
 * <h2>被动「福祸相倚」</h2>
 * 骰战掷出的最终骰点:结果为 <b>1</b> ⇒ 佩戴者获得 1 张**符卡-祸**;结果为 <b>6</b> ⇒ 获得 1 张**符卡-福**。
 * 两张牌在发放那一刻就绑定获得者({@code ModDataComponents.OWNER_UUID}),他人无法使用。
 * 挂点 = {@code combat/DiceCombatEvents} 在攻击方骰点**定稿之后**(含上班族立牌的强制 6 等修正)
 * 调用 {@link #onDiceRollResult}(见该文件内的调用点注释),并以
 * {@link #tryClaimDiceJudgment} 保证「一次结算一次判定」。
 *
 * <h2>被动「完美帮手」</h2>
 * 对**装备大当家立牌**({@code fen_sign})的玩家施加「白泽赐福」时,该玩家额外获得 <b>1 层养精蓄锐</b>
 * —— 复用既有附件计数({@code ModAttachments#FEN_RECHARGE},与「使用治疗类效果牌 +1 层」同一条
 * {@link FenSignItem#addRecharge} 入口),**不新建任何效果**。
 *
 * <h2>主动「白泽赐福」</h2>
 * 目标选择器类技能({@link TargetType#PLAYER},可选任意玩家或**自身**;按下主动键只开启选择会话,
 * 确认后才施效并起玩家级冷却):
 * <ol>
 *   <li>对目标施加「白泽赐福」({@code astral_dice:zhao_blessing},常驻时长;图标复用风水师立牌贴图);</li>
 *   <li>**同时**施法者获得 1 张符卡-福,并把自身持有的**全部**符卡-祸转换为等量符卡-福;</li>
 *   <li>目标装备大当家立牌 ⇒ 1 层养精蓄锐(完美帮手)。</li>
 * </ol>
 *
 * <h2>「白泽赐福」的移除时机(两分支,需求文本「持续到下一次骰神赐福结束」)</h2>
 * <b>冻结口径 = 玩家级 tick 的下降沿检测</b>({@link #tickBlessing},由
 * {@code event/PlayerTickEvents} 每 tick 驱动),状态真值 =
 * {@code ModAttachments#ZHAO_BLESSING_ACTIVE} / {@code #ZHAO_BLESSING_SKIP_CYCLES} /
 * {@code #ZHAO_PREV_BLESSING}:
 * <ul>
 *   <li>施加时目标**不在**骰神赐福 ⇒ skip=0:其触发骰神赐福、该次进度**结束后**移除;</li>
 *   <li>施加时目标**已在**骰神赐福 ⇒ skip=1:**跳过当前这次**结束,等**下一次**骰神赐福结束后移除。</li>
 * </ul>
 * **为什么不用 {@code MobEffectEvent.Expired}**:该事件在「效果被外力移除(ModEffectRemoval /
 * 其它 mod / 死亡 / 重连清场)」时**不触发**(先例:{@code item/HealingManager} 明确说明不可依赖),
 * 下降沿把两条结束路径统一,且不会像"同时订阅 Expired"那样**重复消费**跳过计数 ⇒ 本类**不**订阅
 * {@code Expired}({@code DiceCombatEvents#onDiceBlessingExpired} 的既有语义也不得改)。
 *
 * <h2>溢出治疗 → 攻击力</h2>
 * 赐福生效期内该玩家的一切 {@code LivingEntity#heal} 治疗,只要请求量超过"离满血的缺口",
 * 溢出部分立即**等量**累加为玩家附件 {@code ModAttachments#ZHAO_OVERFLOW_BONUS}
 * (整数化,余数留在 {@code #ZHAO_OVERFLOW_REMAINDER} 继续累积;溢出 ≤ 0 什么都不做),
 * 由 {@code combat/DiceCombatModifiers} 的攻击力修饰器计入骰战攻击力;
 * **效果被移除/复位时**由 {@link #clearOverflowBonus} 归零回收(回收动作单一、三处调用均幂等)。
 * ⚠️ 直接 {@code setHealth(...)} 的"绕过 heal()"路径不经过 {@link LivingHealEvent},不在覆盖范围内
 * (本线全部"加血"入口都走 {@code heal()},见 HealingManager / 瞬间治疗效果 / 生命恢复效果)。
 *
 * <h2>「心意相连」(由大当家立牌调用)</h2>
 * {@link #onAllyActiveSkill}:大当家立牌使用主动技能时,**同队**中装备风水师立牌的玩家各获得
 * 1 张符卡-福;**未组队时不生效**。组队判定**必须**先经
 * {@link EventTargetCollector#hasAnyTeam}(否则会被该收集器"无队伍 ⇒ 全服在线玩家"的既有回退
 * 放大成全服发牌),再取 {@link EventTargetCollector#collectTeamPlayers}(原生 team / FTB Teams /
 * OPAC 三套系统一处收口)。
 *
 * <p>图标 = {@code images/风水师立牌.png}(实装路径 {@code textures/item/zhao_sign.png});
 * 主动效果图标复用同一张图({@code textures/mob_effect/zhao_blessing.png})。
 */
@EventBusSubscriber(modid = AstralDiceMod.MODID)
public class ZhaoSignItem extends BaseSignItem {
    private static final Logger LOGGER = LoggerFactory.getLogger(ZhaoSignItem.class);

    /** 主动技能的动作 id(注册表键;也是 actionbar 技能名 {@code msg.astral_dice.target_select.skill.<id>} 的来源) */
    public static final String ACTION_ID = "zhao_blessing";

    /** 立牌的物品注册 id(锁定态/调试读数用) */
    public static final String SIGN_ID = AstralDiceMod.MODID + ":zhao_sign";

    /**
     * 「一次结算一次判定」的**实例内**标记(骰点 1/6 发牌去重)。
     *
     * <p>同一挥击命中多目标会多次进入 {@code LivingDamageEvent.Pre}(见 {@code DiceCombatEvents}
     * 的既有注释),故必须防重;**禁止**把标记写进附件(会跨实例残留)。与
     * {@code DiceCombatModifiers#instanceVictim} 同口径:纯静态槽位、只在服务端主线程单次结算内有效、
     * 不持久化。同一 tick 内同一玩家的第二次判定视为同一次结算(不再发牌)。
     */
    private static Player lastJudgedPlayer;
    private static long lastJudgedTick = Long.MIN_VALUE;

    /**
     * 主动「白泽赐福」:选任意玩家(或对自身使用)⇒ 施加「白泽赐福」;施法者得 1 张符卡-福
     * 并把自身全部符卡-祸转为符卡-福。
     *
     * <p>必须用具名类:本动作要同时实现 {@link TargetSelectionAction} 与 {@link SelfTargetable}(自选目标)。
     */
    private static final class ZhaoBlessingAction implements TargetSelectionAction, SelfTargetable {
        @Override
        public String id() {
            return ACTION_ID;
        }

        @Override
        public TargetType targetType() {
            return TargetType.PLAYER;
        }

        @Override
        public boolean allowSelf() {
            return true;
        }

        @Override
        public void onStarted(ServerPlayer player) {
            // 进入选择模式瞬间的「请选择目标」提示(门控后提示点 = 会话开始,而非确认之后)
            sendSignActionBar(player, "msg.astral_dice.zhao_blessing_ready");
        }

        @Override
        public void apply(ServerPlayer player, LivingEntity target) {
            if (!applyBlessing(player, target)) return;
            // 主动成功施效:开始玩家级冷却(统一经 signCooldownTicks:含诡异骰子 -50% 与充能递减)
            ModAttachments.setSignActiveCooldownEnd(player,
                    player.level().getGameTime() + WeirdDiceHandler.signCooldownTicks(player));
            // 电流核心筹码:主动技能实际生效时充能 +1
            CurrentCoreChipItem.onActiveSkillUsed(player);
            LOGGER.debug("[Astral Dice][TargetSelection] {} applied to {}({}) by {} self={}",
                    ACTION_ID, target.getId(), target.getName().getString(), player.getName().getString(),
                    target == player);
        }
    }

    static {
        // 目标选择器动作注册(类加载即注册,与枪匠/游戏大师等选择器类立牌同一写法)
        TargetSelectionRegistry.register(new ZhaoBlessingAction());
    }

    public ZhaoSignItem(Properties properties) {
        super(properties);
    }

    // 目标选择器前置门控:按下主动键只开启选择会话并立即返回;确认合法目标后才由
    // resumeGatedActiveSkill 走「风扇筹码发牌 + 立牌主动响应事件」(冷却与充能由上面的 apply 写入)。
    @Override
    protected String selectorActionId() {
        return ACTION_ID;
    }

    /** 玩家是否佩戴风水师立牌(被动"福祸相倚"/"完美帮手"/"心意相连"的佩戴判定) */
    public static boolean isEquipped(Player player) {
        if (player == null) return false;
        var curios = CuriosApi.getCuriosInventory(player);
        return curios.isPresent()
                && curios.get().findFirstCurio(s -> s.is(ModItems.ZHAO_SIGN.get())).isPresent();
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  主动「白泽赐福」的施效
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * 施放「白泽赐福」(服务端权威;目标选择器确认与调试/测试入口共用同一实现)。
     *
     * @return 是否成功(目标必须是玩家)
     */
    public static boolean applyBlessing(ServerPlayer caster, LivingEntity target) {
        if (caster == null || target == null) return false;
        if (caster.level().isClientSide()) return false;
        if (!(target instanceof Player receiver)) return false;

        // ① 状态机初始化(§4.5 行①/行④):
        //    施加时目标已在骰神赐福 ⇒ skip=1(跳过当前这次结束);否则 0。prev 一并落成"此刻的骰神赐福真值"。
        boolean alreadyBlessed = receiver.hasEffect(ModEffects.DICE_BLESSING);
        ModAttachments.setZhaoBlessingActive(receiver, true);
        ModAttachments.setZhaoBlessingSkipCycles(receiver, alreadyBlessed ? 1 : 0);
        ModAttachments.setZhaoPrevBlessing(receiver, alreadyBlessed);
        // 新一轮赐福:溢出治疗加成从 0 起算(避免上一轮的残留被继承)
        clearOverflowBonus(receiver);
        // ② 施加「白泽赐福」(常驻时长;移除完全由下降沿状态机决定)
        ZhaoBlessingEffect.apply(receiver);
        // ③ 完美帮手:目标装备大当家立牌 ⇒ 1 层养精蓄锐(复用既有附件计数,不新建效果)
        if (FenSignItem.isEquipped(receiver)) {
            FenSignItem.addRecharge(receiver, 1);
        }
        // ④ 施法者:1 张符卡-福 + 自身全部符卡-祸 → 符卡-福
        FuCardItem.give(caster, caster, 1);
        int converted = HuoCardItem.convertAllToFu(caster);
        PacketDistributor.sendToPlayer(caster, new ActionBarPayload(
                Component.translatable("msg.astral_dice.zhao_blessing_applied",
                                target.getDisplayName(), converted)
                        .withStyle(ChatFormatting.YELLOW), GameplayConstants.ACTIONBAR_DURATION_TICKS));
        return true;
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  「白泽赐福」的玩家级状态机(下降沿,§4.4/§4.5/§4.6/§4.7)
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * 玩家级 tick(由 {@code event/PlayerTickEvents} **每 tick** 驱动;仅服务端)。
     *
     * <p>职责(全部收敛在这里,保证"结束路径唯一"):
     * <ol>
     *   <li><b>下降沿</b>:上一 tick 在骰神赐福、这一 tick 不在 ⇒ 这一次赐福**结束**了。skip &gt; 0
     *       则减 1 并继续等下一次;否则移除「白泽赐福」({@link #endBlessing});</li>
     *   <li><b>自检</b>:生效期内发现「白泽赐福」效果**自身**被外力移除(如 {@code /effect clear})
     *       ⇒ 复位真值并回收溢出加成(§4.6④ 的唯一例外方向);</li>
     *   <li><b>续期</b>:效果存在则刷新/补齐(常驻时长由本 tick 维持,效果自身不承担倒计时语义);</li>
     *   <li>记录本 tick 的骰神赐福真值到 {@code zhao_prev_blessing}(下一次下降沿的基准)。</li>
     * </ol>
     */
    public static void tickBlessing(Player player) {
        if (player == null || player.level().isClientSide()) return;
        boolean hasDice = player.hasEffect(ModEffects.DICE_BLESSING);
        if (ModAttachments.isZhaoBlessingActive(player)) {
            boolean prev = ModAttachments.isZhaoPrevBlessing(player);
            if (prev && !hasDice) {
                int skip = ModAttachments.getZhaoBlessingSkipCycles(player);
                if (skip > 0) {
                    // §4.5 行⑤:跳过当前这次结束(skip 递减,赐福保留)
                    ModAttachments.setZhaoBlessingSkipCycles(player, skip - 1);
                    LOGGER.debug("[Astral Dice][Zhao] 跳过本次骰神赐福结束: player={} remainSkip={}",
                            player.getName().getString(), skip - 1);
                } else {
                    // §4.5 行③/行⑥:这一次/下一次骰神赐福的进度已结束 ⇒ 移除白泽赐福
                    endBlessing(player);
                    ModAttachments.setZhaoPrevBlessing(player, false);
                    return;
                }
            }
            if (!player.hasEffect(ModEffects.ZHAO_BLESSING)) {
                // 自检:效果实例被外力移除 ⇒ 真值复位(附着状态与效果实例不允许长期不一致)
                ModAttachments.setZhaoBlessingActive(player, false);
                ModAttachments.setZhaoBlessingSkipCycles(player, 0);
                clearOverflowBonus(player);
            } else {
                ZhaoBlessingEffect.refresh(player);
            }
        }
        ModAttachments.setZhaoPrevBlessing(player, hasDice);
    }

    /**
     * 结束「白泽赐福」(移除时不留任何残留):溢出治疗加成归零 → 状态真值复位 → 移除效果实例。
     *
     * <p>回收动作**单一**({@link #clearOverflowBonus}),被本方法 / tick 的自检 /
     * {@link #onZhaoBlessingRemoved} 三处调用,三处均**幂等**。
     */
    private static void endBlessing(Player player) {
        clearOverflowBonus(player);
        ModAttachments.setZhaoBlessingSkipCycles(player, 0);
        ModAttachments.setZhaoBlessingActive(player, false);
        if (player.hasEffect(ModEffects.ZHAO_BLESSING)) {
            // 走本模组统一内部移除通道(不会被"外部清除拦截器"拦下)
            ZhaoBlessingEffect.remove(player);
        }
    }

    /** 溢出治疗加成的**唯一回收动作**(整数部分 + 余数累加器一并归零;幂等) */
    private static void clearOverflowBonus(Player player) {
        ModAttachments.clearZhaoOverflowBonus(player);
    }

    /**
     * 「白泽赐福」被移除(**任意路径**)时的即时回收:溢出加成归零 + 真值复位。
     *
     * <p>与 {@link #tickBlessing} 的自检互为双保险(事件即时、tick 兜底),两者都幂等 ⇒
     * "效果移除后攻击力加成留残留"这一失败模式在两条路径上都被关掉。
     * 注意:本处理器**不**消费 {@code zhao_blessing_skip_cycles}(那是下降沿的职责,重复消费会导致
     * "该跳过的一次被吃掉" ⇒ 赐福提前结束)。
     */
    @SubscribeEvent
    public static void onZhaoBlessingRemoved(MobEffectEvent.Remove event) {
        if (event.isCanceled()) return;
        if (!(event.getEntity() instanceof Player player)) return;
        if (player.level().isClientSide()) return;
        MobEffectInstance instance = event.getEffectInstance();
        if (instance == null || instance.getEffect() == null
                || instance.getEffect().value() != ModEffects.ZHAO_BLESSING.get()) {
            return;
        }
        clearOverflowBonus(player);
        ModAttachments.setZhaoBlessingSkipCycles(player, 0);
        ModAttachments.setZhaoBlessingActive(player, false);
        ModAttachments.setZhaoPrevBlessing(player, player.hasEffect(ModEffects.DICE_BLESSING));
    }

    /**
     * **死亡清场**(由 {@code event/PlayerLifecycleHandler} 的死亡处理在移除骰神赐福的同一段调用;
     * 规格 §4.6②)。
     *
     * <p>职责(全部是有真实副作用的清理,不是"逐项写默认值"):① 移除「白泽赐福」与「厄运」效果实例;
     * ② 溢出治疗加成归零(整数 + 余数);③ 状态机三键与「厄运」计时器复位。
     */
    public static void onOwnerDeathCleanup(Player player) {
        if (player == null || player.level().isClientSide()) return;
        clearOverflowBonus(player);
        ModAttachments.setZhaoBlessingActive(player, false);
        ModAttachments.setZhaoBlessingSkipCycles(player, 0);
        ModAttachments.setZhaoPrevBlessing(player, false);
        ModAttachments.setHuoCardNextDamageTick(player, 0L);
        if (player.hasEffect(ModEffects.ZHAO_BLESSING)) {
            ZhaoBlessingEffect.remove(player);
        }
        MisfortuneEffect.clear(player);
    }

    /**
     * **重登复位**(由 {@code PlayerLifecycleHandler#onPlayerLoggedInClearDiceBlessing} 在清除骰神赐福的
     * 同一段调用;规格 §4.7)。
     *
     * <p>「白泽赐福不跨会话残留」与骰神赐福同口径:移除效果 + 复位 {@code active}/{@code prev}
     * (清 prev 是防"重登被误判为一次赐福结束")。附件本身(未列 {@code copyOnDeath})随玩家 NBT 保留,
     * 但**溢出攻击力加成必须回收** —— 否则重登后明明没有赐福却仍吃这份加成(玩家可见残留),
     * 故在此一并归零。「厄运」效果保留:其真值是"持有张数",张数还在,由玩家级 tick 重新镜像。
     */
    public static void onOwnerRelogin(Player player) {
        if (player == null || player.level().isClientSide()) return;
        clearOverflowBonus(player);
        ModAttachments.setZhaoBlessingActive(player, false);
        ModAttachments.setZhaoBlessingSkipCycles(player, 0);
        ModAttachments.setZhaoPrevBlessing(player, false);
        if (player.hasEffect(ModEffects.ZHAO_BLESSING)) {
            ZhaoBlessingEffect.remove(player);
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  溢出治疗 → 攻击力
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * 赐福期间的治疗:请求量与"离满血的缺口"之差(溢出)立即等量转为攻击力。
     *
     * <p>溢出 = {@link LivingHealEvent#getAmount() 请求治疗量} −
     * {@code min(请求量, 最大生命 − 当前生命)}(原版 {@code heal()} 先抛本事件、再
     * {@code setHealth} 并 clamp 到最大生命 ⇒ 此处读到的 {@code getHealth()} 仍是治疗前值)。
     * 溢出 ≤ 0(**包括刚好回满**)时不写入 ⇒ 攻击力不增加。
     *
     * <p>优先级 {@link EventPriority#LOWEST}:在其它治疗侧处理器改完 {@code amount} 之后取最终值;
     * 并被其它处理器取消时({@link LivingHealEvent#isCanceled()})不产生溢出。
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onLivingHeal(LivingHealEvent event) {
        if (event.isCanceled()) return;
        if (!(event.getEntity() instanceof Player player)) return;
        if (player.level().isClientSide()) return;
        if (!ModAttachments.isZhaoBlessingActive(player)) return;
        if (!player.hasEffect(ModEffects.ZHAO_BLESSING)) return;
        float amount = event.getAmount();
        if (amount <= 0.0F) return;
        float missing = player.getMaxHealth() - player.getHealth();
        float actual = Math.min(amount, Math.max(0.0F, missing));
        float overflow = amount - actual;
        if (overflow <= 0.0F) return;
        ModAttachments.addZhaoOverflowBonus(player, overflow);
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  被动「福祸相倚」
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * 「一次结算一次判定」的实例内占位(见 {@link #lastJudgedPlayer})。
     *
     * @return true = 本实例由本调用者判定(可以发牌);false = 同一结算已判定过(跳过,防重复发牌)
     */
    public static boolean tryClaimDiceJudgment(Player player) {
        if (player == null) return false;
        long now = player.level().getGameTime();
        if (player == lastJudgedPlayer && now == lastJudgedTick) return false;
        lastJudgedPlayer = player;
        lastJudgedTick = now;
        return true;
    }

    /**
     * 骰点结果处理(由 {@code combat/DiceCombatEvents} 在攻击方骰点定稿后调用):
     * 1 ⇒ 该玩家获得 1 张符卡-祸;6 ⇒ 获得 1 张符卡-福。卡牌在发放时即绑定获得者。
     *
     * @return 本次发放的卡牌类型("huo"/"fu"/null)
     */
    public static String onDiceRollResult(Player player, int dice) {
        if (player == null || player.level().isClientSide()) return null;
        if (!isEquipped(player)) return null;
        if (dice == 1) {
            HuoCardItem.give(player, player, 1);
            // 厄运层数立即跟上(每 20 tick 的镜像也会兜底;计时器仍只由 HuoCardItem#tick 起算)
            HuoCardItem.refreshCurseState(player);
            notifyDiceGift(player, "msg.astral_dice.zhao_dice_huo");
            return "huo";
        }
        if (dice == 6) {
            FuCardItem.give(player, player, 1);
            notifyDiceGift(player, "msg.astral_dice.zhao_dice_fu");
            return "fu";
        }
        return null;
    }

    private static void notifyDiceGift(Player player, String langKey) {
        if (!(player instanceof ServerPlayer serverPlayer)) return;
        PacketDistributor.sendToPlayer(serverPlayer, new ActionBarPayload(
                Component.translatable(langKey).withStyle(ChatFormatting.YELLOW),
                GameplayConstants.ACTIONBAR_DURATION_TICKS));
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  被动「心意相连」(大当家立牌使用主动技能时调用)
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * 「心意相连」:大当家立牌使用主动技能时,**同队**中装备风水师立牌的玩家各获得 1 张符卡-福。
     *
     * <p>**未组队时不生效**:先判 {@link EventTargetCollector#hasAnyTeam} —— 这一步**不能省**,
     * 否则"无队伍"会被 {@link EventTargetCollector#collectTeamPlayers} 的既有回退把全服在线玩家
     * 当成队友(全服发牌)。
     *
     * @return 实际收到符卡-福的队友数量(施法者自身不在发放集合内:立牌栏只有一个槽位)
     */
    public static int onAllyActiveSkill(Player fenOwner) {
        if (fenOwner == null || fenOwner.level().isClientSide()) return 0;
        if (!EventTargetCollector.hasAnyTeam(fenOwner)) return 0;
        int granted = 0;
        for (Player ally : EventTargetCollector.collectTeamPlayers(fenOwner)) {
            if (ally == null || ally == fenOwner) continue;
            if (!isEquipped(ally)) continue;      // 只发给"装备风水师立牌"的同队者
            FuCardItem.give(ally, ally, 1);       // 绑定**受赠者**(§7.2 发放路径 3)
            if (ally instanceof ServerPlayer serverAlly) {
                PacketDistributor.sendToPlayer(serverAlly, new ActionBarPayload(
                        Component.translatable("msg.astral_dice.xinyi_linked_granted")
                                .withStyle(ChatFormatting.YELLOW), GameplayConstants.ACTIONBAR_DURATION_TICKS));
            }
            granted++;
        }
        return granted;
    }

    /**
     * 只读读数辅助(探针/调试用):当前"心意相连"会命中的接收者 UUID 列表。
     * **不写任何状态**,便于在单人环境里把"未组队 ⇒ 空集"这一事实留成证据。
     */
    public static List<String> linkedReceiverIds(Player fenOwner) {
        List<String> ids = new ArrayList<>();
        if (fenOwner == null || fenOwner.level().isClientSide()) return ids;
        if (!EventTargetCollector.hasAnyTeam(fenOwner)) return ids;
        for (Player ally : EventTargetCollector.collectTeamPlayers(fenOwner)) {
            if (ally == null || ally == fenOwner) continue;
            if (!isEquipped(ally)) continue;
            ids.add(ally.getUUID().toString());
        }
        return ids;
    }

    /** 只读读数辅助(探针/调试用):当前是否满足"组队"这一前置(未组队 ⇒ 心意相连整体不生效) */
    public static boolean teamGateOpen(Player player) {
        return player != null && EventTargetCollector.hasAnyTeam(player);
    }
}
