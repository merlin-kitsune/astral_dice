package com.merlinkitsune.astral_dice.item.sign;

import com.merlinkitsune.astral_dice.AstralDiceMod;
import com.merlinkitsune.astral_dice.combat.DiceCombatModifiers;
import com.merlinkitsune.starengine.component.GameplayConstants;
import com.merlinkitsune.astral_dice.component.ModAttachments;
import com.merlinkitsune.astral_dice.effect.ModEffects;
import com.merlinkitsune.starengine.effect.MosesBrokenEffect;
import com.merlinkitsune.astral_dice.effect.WeaknessRevealEffect;
import com.merlinkitsune.astral_dice.event.WeirdDiceHandler;
import com.merlinkitsune.astral_dice.item.ChargeManager;
import com.merlinkitsune.astral_dice.item.ModItems;
import com.merlinkitsune.astral_dice.item.chip.CurrentCoreChipItem;
import com.merlinkitsune.starengine.target.TargetSelectionAction;
import com.merlinkitsune.astral_dice.target.TargetSelectionManager;
import com.merlinkitsune.starengine.target.TargetSelectionRegistry;
import com.merlinkitsune.starengine.target.TargetType;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResultHolder;
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

    // 发送"请选择目标"ActionBar 提示(进入选择模式瞬间)
    public static void sendReadyPrompt(Player player) {
        sendSignActionBar(player, "msg.astral_dice.moses_ready");
    }

    // 主动技能自带 ActionBar 反馈:注册到主动技能响应事件,阻止默认提示
    @SubscribeEvent
    public static void onSignActiveTriggered(com.merlinkitsune.starengine.event.SignActiveTriggeredEvent event) {
        if (event.getSignStack().is(ModItems.MOSES_SIGN.get())) {
            sendReadyPrompt(event.getPlayer());
            event.setHandled();
        }
    }

    @Override
    protected void clearSignData(Player player, ItemStack stack) {
        super.clearSignData(player, stack);
        // 清空弱点识破及其防御护甲修饰器
        WeaknessRevealEffect.removeAll(player);
        DiceCombatModifiers.setDefenseArmorBonus(player, "moses_weakness_armor", 0);
    }

    @Override
    protected InteractionResultHolder<ItemStack> handleUse(Level level, Player player, ItemStack stack) {
        if (level.isClientSide) {
            return InteractionResultHolder.success(stack);
        }
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResultHolder.fail(stack);
        }
        // 主动:进入目标选择模式(服务端权威;确认时施加"破绽"并开始冷却,取消/超时不冷却)
        boolean started = TargetSelectionManager.start(serverPlayer, "moses_apply_broken");
        return started ? InteractionResultHolder.success(stack) : InteractionResultHolder.fail(stack);
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
     * 对普通敌对目标施加"破绽"(持续 2:00)。
     * 若目标已带破绽则不再重复施加,并保持原有每段标记。
     */
    public static boolean applyBroken(Player player, LivingEntity target) {
        if (target == null || target.level().isClientSide()) return false;
        if (target.hasEffect(ModEffects.MOSES_BROKEN)) return false;
        ModAttachments.setMosesBrokenAttackRewarded(target, false);
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
