package com.merlinkitsune.astral_dice.item.sign;

import com.merlinkitsune.astral_dice.AstralDiceMod;
import com.merlinkitsune.astral_dice.combat.DiceCombatModifiers;
import com.merlinkitsune.starenginelib.component.GameplayConstants;
import com.merlinkitsune.astral_dice.component.ModAttachments;
import com.merlinkitsune.astral_dice.effect.ModEffects;
import com.merlinkitsune.starenginelib.effect.MosesBrokenEffect;
import com.merlinkitsune.astral_dice.effect.WeaknessRevealEffect;
import com.merlinkitsune.starenginelib.event.ModEffectRemoval;
import com.merlinkitsune.astral_dice.event.WeirdDiceHandler;
import com.merlinkitsune.astral_dice.item.ChargeManager;
import com.merlinkitsune.astral_dice.item.ModItems;
import com.merlinkitsune.astral_dice.item.chip.CurrentCoreChipItem;
import com.merlinkitsune.starenginelib.target.TargetSelectionAction;
import com.merlinkitsune.astral_dice.target.TargetSelectionManager;
import com.merlinkitsune.starenginelib.target.TargetSelectionRegistry;
import com.merlinkitsune.starenginelib.target.TargetType;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.SlotContext;

/**
 * 枪匠立牌(命名:moses,史诗)。
 *
 * 弱点识破:每层攻击/防御 +1、骰点最低数 +1,骰神赐福结束减 1 层,最多 4 层。
 * 被动「精密技巧」:装备时主动冷却减为 120 秒。
 * 主动「弱点反击」:使用目标选择器选择普通敌对目标并施加「破绽」2:00
 * (选择器目标规则:仅 vanilla {@link net.minecraft.world.entity.monster.Enemy} 敌对生物)。
 * 破绽:目标与枪匠交战时骰点只能为 0,会被枪匠闪避;闪避后自动反击。
 *
 * 主动为"目标选择器"类技能:触发后经 {@link TargetSelectionManager} 进入选择模式,
 * 确认时由 {@link TargetSelectionAction#apply} 施加效果并开始玩家级冷却;取消/超时不冷却。
 */
@EventBusSubscriber(modid = AstralDiceMod.MODID)
public class MosesSignItem extends BaseSignItem {
    private static final Logger LOGGER = LoggerFactory.getLogger(MosesSignItem.class);

    /** 玩家级等待状态类型:枪匠=3 */
    public static final int READY_TYPE = 3;
    /** 主动冷却基础秒数(被动:120 秒) */
    public static final int ACTIVE_COOLDOWN_SECONDS = 120;

    // 目标选择动作注册(26.1.2 移植 B2b,2026-09-18):语义基准 = 1.21.1
    // `neoforge-1.21.1/.../item/sign/MosesSignItem.java` 第 50-79 行(静态块)**逐字移植**,
    // 只改平台 API 形态。注册时机与基准一致(类初始化时执行静态块)。
    // 注册的 id 与下方 selectorActionId() 的返回值逐字相同(见 BaseSignItem 门控分支)。
    // ⚠️ 本类在 26.1.2 侧**仍保留** @EventBusSubscriber 与既有 onSignActiveTriggered(@SubscribeEvent),
    //   故不采用 1.21.1 基准「本类不得标注 @EventBusSubscriber」的处理(那是 1.21.1 删掉旧提示
    //   订阅器后的形态);该差异属平台/批次差异,见 B2b 报告。
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
        // 主动技能等待期:超时清除已移到玩家级 tick(BaseSignItem#tickSignReadyTimeout,S6-C2 状态与计时器分离),
        // 不再依赖"立牌仍在饰品槽位"——否则立牌离身后残留的正计时器会让该玩家所有立牌的主动都不再进入冷却。
        long expire = ModAttachments.getSignReadyExpire(player);
        if (ModAttachments.getSignReadyType(player) == READY_TYPE && expire > 0
                && player.tickCount % 20 == 0) {
            sendReadyPrompt(player);
        }
    }

    // 发送"待命"ActionBar 提示(激活瞬间与等待期间共用)
    public static void sendReadyPrompt(Player player) {
        sendSignActionBar(player, "msg.astral_dice.moses_ready");
    }

    // 主动技能自带 ActionBar 反馈("待命"提示):注册到主动技能响应事件,阻止默认提示
    @SubscribeEvent
    public static void onSignActiveTriggered(com.merlinkitsune.starenginelib.event.SignActiveTriggeredEvent event) {
        if (event.getSignStack().is(ModItems.MOSES_SIGN.get())) {
            sendReadyPrompt(event.getPlayer());
            event.setHandled();
        }
    }

    @Override
    protected void clearSignData(Player player, ItemStack stack) {
        super.clearSignData(player, stack);
        // 立牌被移除:中断等待状态并清除"待命"提示效果
        if (ModAttachments.getSignReadyType(player) == READY_TYPE) {
            ModAttachments.setSignReadyType(player, 0);
            ModAttachments.setSignReadyExpire(player, 0);
        }
        ModEffectRemoval.remove(player, ModEffects.MOSES_READY);
        // 清空弱点识破及其防御护甲修饰器
        WeaknessRevealEffect.removeAll(player);
        DiceCombatModifiers.setDefenseArmorBonus(player, "moses_weakness_armor", 0);
    }

    // 目标选择器前置门控(26.1.2 移植 B1+B2,2026-09-18):本立牌主动为选择器类 —— 按下主动键只开启
    // 目标选择会话,会话时长取自 GameplayConstants.SKILL_WAIT_SECONDS(秒),此处不写死数字;
    // 确认合法目标后才继续原流程(风扇筹码发牌 + 立牌主动响应事件;冷却与电流核心充能由
    // moses_apply_broken 动作的 apply 在确认时写入)。
    // 语义基准 = 1.21.1 `MosesSignItem.java` 第 108-115 行(逐字同形,纯平台无关代码)。
    // ⚠️ 本批次为**叠加**门控:下方 READY_TYPE / sign_ready_type 旧待命等待器机制按用户要求保持原样,
    //    门控生效后本类 handleUse 不再被 performSkill 到达(该次主动改由 moses_apply_broken 的 apply 施加)。
    //    1.21.1 侧「请选择敌对目标」提示由注册动作的 onStarted 发送(见 1.21.1 MosesSignItem.sendReadyPrompt
    //    注释);26.1.2 侧动作注册属后续批次,本批次不搬,故 moses_ready 仍由旧路径的 sendReadyPrompt 发送。
    @Override
    protected String selectorActionId() {
        return "moses_apply_broken";
    }

    @Override
    protected InteractionResult handleUse(Level level, Player player, ItemStack stack) {
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        // 主动:进入等待期(玩家级状态),等待攻击敌对目标释放"破绽"
        ModAttachments.setSignReadyType(player, READY_TYPE);
        ModAttachments.setSignReadyExpire(player,
                level.getGameTime() + GameplayConstants.SKILL_WAIT_SECONDS * 20L);
        player.addEffect(new MobEffectInstance(ModEffects.MOSES_READY, Integer.MAX_VALUE, 0, false, false, true));
        return InteractionResult.SUCCESS;
    }

    // 玩家是否佩戴枪匠立牌
    public static boolean isEquipped(Player player) {
        if (player == null) return false;
        var curios = CuriosApi.getCuriosInventory(player);
        return curios.isPresent()
                && curios.get().findFirstCurio(s -> s.is(ModItems.MOSES_SIGN.get())).isPresent();
    }

    /**
     * 立牌主动冷却 tick:佩戴枪匠时基础 120 秒;诡异骰子仍可再减半。
     */
    public static int signCooldownTicks(Player player) {
        int ticks = isEquipped(player)
                ? ACTIVE_COOLDOWN_SECONDS * 20
                : GameplayConstants.SIGN_ACTIVE_COOLDOWN_TICKS;
        if (WeirdDiceHandler.hasWeirdDice(player)) {
            ticks = Math.max(1, ticks / 2);
        }
        return (int) ChargeManager.cooldownTicks(player, ticks);
    }

    /**
     * 对符合骰神赐福触发条件的目标施加"破绽"(持续 2:00)。
     * 若目标已带破绽则不再重复施加;重新施加时重置该目标的"每段破绽奖励"标记,
     * 使新一段破绽可以重新获得一次弱点识破层数。
     */
    public static boolean applyBroken(Player player, LivingEntity target) {
        if (target == null || target.level().isClientSide()) return false;
        if (target.hasEffect(ModEffects.MOSES_BROKEN)) return false;
        ModAttachments.setMosesBrokenAttackRewarded(target, false);
        ModAttachments.setMosesDodgeCounterRewarded(target, false);
        target.addEffect(new MobEffectInstance(ModEffects.MOSES_BROKEN,
                MosesBrokenEffect.DURATION_TICKS, 0, false, true));
        sendSignActionBar(player, "msg.astral_dice.moses_apply");
        return true;
    }

    /**
     * 攻击已带破绽的目标:每段破绽只获得 1 层弱点识破。
     */
    public static void onAttackBrokenTarget(Player player, LivingEntity target) {
        if (target == null || target.level().isClientSide()) return;
        if (!target.hasEffect(ModEffects.MOSES_BROKEN)) return;
        if (ModAttachments.isMosesBrokenAttackRewarded(target)) return;
        WeaknessRevealEffect.addStacks(player, 1);
        ModAttachments.setMosesBrokenAttackRewarded(target, true);
    }

    /**
     * 触发闪避/反击(任意来源):每名目标只获得 1 层弱点识破。
     */
    public static void onDodgeCounter(Player player, LivingEntity target) {
        if (player == null || target == null || player.level().isClientSide()) return;
        if (!isEquipped(player)) return;
        if (ModAttachments.isMosesDodgeCounterRewarded(target)) return;
        WeaknessRevealEffect.addStacks(player, 1);
        ModAttachments.setMosesDodgeCounterRewarded(target, true);
    }

    /**
     * 骰神赐福结束:弱点识破减少 1 层。
     */
    public static void onDiceBlessingEnded(Player player) {
        if (player == null || player.level().isClientSide()) return;
        if (!isEquipped(player)) return;
        if (WeaknessRevealEffect.getStacks(player) > 0) {
            WeaknessRevealEffect.consumeOne(player);
        }
        DiceCombatModifiers.setDefenseArmorBonus(player, "moses_weakness_armor",
                WeaknessRevealEffect.getStacks(player));
    }
}
