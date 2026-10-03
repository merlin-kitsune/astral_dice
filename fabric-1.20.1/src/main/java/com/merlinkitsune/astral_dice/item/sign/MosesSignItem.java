package com.merlinkitsune.astral_dice.item.sign;

import com.merlinkitsune.astral_dice.item.CurioSlotUtil;
import com.merlinkitsune.astral_dice.combat.DiceCombatModifiers;
import com.merlinkitsune.starenginelib.component.GameplayConstants;
import com.merlinkitsune.astral_dice.component.ModAttachments;
import com.merlinkitsune.astral_dice.effect.ModEffects;
import com.merlinkitsune.starenginelib.effect.MosesBrokenEffect;
import com.merlinkitsune.astral_dice.effect.WeaknessRevealEffect;
import com.merlinkitsune.astral_dice.event.WeirdDiceHandler;
import com.merlinkitsune.astral_dice.item.ChargeManager;
import com.merlinkitsune.astral_dice.compat.curios.CuriosApi;
import com.merlinkitsune.astral_dice.item.ModItems;
import com.merlinkitsune.astral_dice.item.chip.CurrentCoreChipItem;
import com.merlinkitsune.starenginelib.target.TargetSelectionAction;
import com.merlinkitsune.astral_dice.target.TargetSelectionManager;
import com.merlinkitsune.starenginelib.target.TargetSelectionRegistry;
import com.merlinkitsune.starenginelib.target.TargetType;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.merlinkitsune.astral_dice.compat.curios.SlotContext;

/**
 * 枪匠立牌(命名:moses,史诗)。
 *
 * 弱点识破:每层攻击/防御 +1、骰点最低数 +1,**每分钟(效果自然到期)减 1 层**,最多 4 层。
 * 被动「精密技巧」:装备时主动冷却减为 120 秒。
 * 主动「弱点反击」:使用目标选择器选择普通敌对目标并施加「破绽」2:00
 * (选择器目标规则:{@code TargetType.ENEMY} 经 {@code SelectorTargets} 委托到全局唯一入口
 *  {@code HostileTargets.isHostile} ⇒ 敌对生物 ∪ **中立生物(宠物除外)** ∪ 试验假人,**已含中立**;
 *  旧注记「仅 vanilla Enemy」自 2026-09-22 口径下沉起即已过时,2026-09-24 订正)。
 * 破绽:目标与枪匠交战时骰点只能为 0,会被枪匠闪避;闪避后自动反击。
 *
 * 主动为"目标选择器"类技能:触发后经 {@link TargetSelectionManager} 进入选择模式,
 * 确认时由 {@link TargetSelectionAction#apply} 施加效果并开始玩家级冷却;取消/超时不冷却。
 */
// 本类不注册任何 @SubscribeEvent(「请选择目标」提示改由注册动作的 onStarted 发送,见下方 sendReadyPrompt 注释),
// 故**不得**标注 @EventBusSubscriber —— NeoForge 21.1 对「无 @SubscribeEvent 方法的订阅者类」直接抛
// IllegalArgumentException(class ... has no @SubscribeEvent methods, but register was called anyway),
// 进而 Failed to register automatic subscribers 让模组构造失败(2026-09-17 实测崩线)。
public class MosesSignItem extends BaseSignItem {
    private static final Logger LOGGER = LoggerFactory.getLogger(MosesSignItem.class);

    /** 主动冷却基础秒数(被动:120 秒) */
    public static final int ACTIVE_COOLDOWN_SECONDS = 120;

    static {
        TargetSelectionRegistry.register(new TargetSelectionAction() {
            @Override
            public String id() {
                return "moses_apply_broken";
            }

            @Override
            public TargetType targetType() {
                return TargetType.ENEMY;
            }

            @Override
            public void onStarted(ServerPlayer player) {
                // 进入选择模式瞬间的「请选择敌对目标」提示(门控后提示点 = 会话开始,而非确认之后)
                sendReadyPrompt(player);
            }

            @Override
            public void apply(ServerPlayer player, LivingEntity target) {
                // 施加"破绽"2:00;目标已带破绽时不重复施加,此时不消耗冷却
                if (!applyBroken(player, target)) return;
                // 主动成功施加:开始玩家级冷却(被动「精密技巧」120 秒)并计入「电流核心」充能
                ModAttachments.setSignActiveCooldownEnd(player,
                        player.level().getGameTime() + signCooldownTicks(player));
                CurrentCoreChipItem.onActiveSkillUsed(player);
                LOGGER.debug("[Astral Dice][TargetSelection] moses_apply_broken applied to {}({}) by {}",
                        target.getId(), target.getName().getString(), player.getName().getString());
            }
        });
    }

    public MosesSignItem(Properties properties) {
        super(properties);
    }

    @Override
    protected void onCurioTick(SlotContext slotContext, ItemStack stack) {
        if (!(slotContext.entity() instanceof Player player)) return;
        if (player.level().isClientSide()) return;
        // 防御力按弱点识破层数折算为真实护甲(1 防御力 = 2 护甲)
        DiceCombatModifiers.setDefenseArmorBonus(player, "moses_weakness_armor",
                isEquipped(player) ? WeaknessRevealEffect.getStacks(player) : 0);
    }

    // 发送"请选择目标"ActionBar 提示:门控后本提示由注册动作的 onStarted 在**会话开始**(按下主动键)时发送,
    // 不再挂在 SignActiveTriggeredEvent 上 —— 该事件现已推迟到确认成功之后,彼时再提示"请选择目标"与事实冲突。
    public static void sendReadyPrompt(Player player) {
        if (player instanceof ServerPlayer) {
            sendSignActionBar(player, "msg.astral_dice.moses_ready");
        }
    }

    @Override
    protected void clearSignData(Player player, ItemStack stack) {
        super.clearSignData(player, stack);
        // 清空弱点识破及其防御护甲修饰器
        WeaknessRevealEffect.removeAll(player);
        DiceCombatModifiers.setDefenseArmorBonus(player, "moses_weakness_armor", 0);
    }

    // 目标选择器前置门控(2026-09-17):本立牌主动为选择器类 —— 按下主动键只开启目标选择会话,
    // 会话时长取自 GameplayConstants.SKILL_WAIT_SECONDS(秒),此处不写死数字;
    // 确认合法目标后才继续原流程(风扇筹码发牌 + 立牌主动响应事件;冷却与电流核心充能由
    // 本类注册的 TargetSelectionAction#apply 在确认时写入)。
    @Override
    protected String selectorActionId() {
        return "moses_apply_broken";
    }

    // 玩家是否佩戴枪匠立牌
    public static boolean isEquipped(Player player) {
        // 总闸门(骰子装备和卸除调整):未佩戴骰子 ⇒ 本件功能一律不生效(数值保留)
        if (!CurioSlotUtil.hasDiceEquipped(player)) return false;
        if (player == null) return false;
        return CuriosApi.getCuriosInventory(player)
                .map(h -> h.findFirstCurio(s -> s.is(ModItems.MOSES_SIGN.get())).isPresent())
                .orElse(false);
    }

    /**
     * 立牌主动冷却 tick:佩戴枪匠时基础 120 秒(低于充能上限 160 秒 ⇒ 有充能时不受影响);
     * 先按充能上限封顶,再按诡异骰子减半。
     */
    public static int signCooldownTicks(Player player) {
        int ticks = isEquipped(player)
                ? ACTIVE_COOLDOWN_SECONDS * 20
                : GameplayConstants.SIGN_ACTIVE_COOLDOWN_TICKS;
        ticks = (int) ChargeManager.signCooldownTicks(player, ticks);
        if (WeirdDiceHandler.hasWeirdDice(player)) {
            ticks = Math.max(1, ticks / 2);
        }
        return ticks;
    }

    /**
     * 对符合骰神赐福触发条件的目标施加"破绽"(持续 2:00)。
     * <p>若目标已带破绽则不再重复施加(此时不消耗冷却)。
     * <p>⚠️ 2026-10-02:原先这里还会重置「每段破绽奖励」标记,以配合「每段破绽只 +1 层」的限制;
     * 该限制已废除(改为每次攻击都给层数),两个标记附件不再被读写,故不再重置。
     */
    public static boolean applyBroken(Player player, LivingEntity target) {
        if (target == null || target.level().isClientSide()) return false;
        if (target.hasEffect(ModEffects.MOSES_BROKEN.get())) return false;
        // 2026-10-02:两个「已奖励」标记已废弃(每次攻击都给层数),不再重置。
        target.addEffect(new MobEffectInstance(ModEffects.MOSES_BROKEN.get(),
                MosesBrokenEffect.DURATION_TICKS, 0, false, true));
        sendSignActionBar(player, "msg.astral_dice.moses_apply");
        return true;
    }

    /**
     * 攻击已带破绽的目标:**每次攻击各获得 1 层**弱点识破(2026-10-02 用户裁决)。
     *
     * <p>此前带「每段破绽只 +1 层」限制(读附件 {@code moses_broken_attack_rewarded}),
     * 导致对同一目标连续攻击时只有第一下给层数;现改为**每次攻击都 +1 层**,直至上限
     * {@link WeaknessRevealEffect#MAX_STACKS}。**满层后继续攻击仍会刷新 1 分钟计时器** ——
     * 由 {@link WeaknessRevealEffect#addStacks} 的同层刷新完成(原版
     * {@code MobEffectInstance#update} 在「amplifier 相同且新时长更长」时刷新 duration)。
     *
     * <p>⚠️ 附件 {@code moses_broken_attack_rewarded} / {@code moses_dodge_counter_rewarded}
     * 自本改动起**不再被读写**(保留定义仅为存档兼容),{@link #applyBroken} 也不再重置它们。
     */
    public static void onAttackBrokenTarget(Player player, LivingEntity target) {
        if (target == null || target.level().isClientSide()) return;
        if (!target.hasEffect(ModEffects.MOSES_BROKEN.get())) return;
        WeaknessRevealEffect.addStacks(player, 1);
    }

    /**
     * 触发闪避/反击(任意来源):**每次各获得 1 层**弱点识破(2026-10-02 用户裁决)。
     * <p>与 {@link #onAttackBrokenTarget} 同口径:不再受「每目标每段破绽只 +1 层」限制,
     * 满层后继续触发仍刷新计时器。
     */
    public static void onDodgeCounter(Player player, LivingEntity target) {
        if (player == null || target == null || player.level().isClientSide()) return;
        if (!isEquipped(player)) return;
        WeaknessRevealEffect.addStacks(player, 1);
    }

}
