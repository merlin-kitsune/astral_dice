package com.merlinkitsune.astral_dice.item.sign;

import com.merlinkitsune.astral_dice.AstralDiceMod;
import com.merlinkitsune.starenginelib.component.GameplayConstants;
import com.merlinkitsune.astral_dice.component.ModAttachments;
import com.merlinkitsune.astral_dice.effect.ModEffects;
import com.merlinkitsune.astral_dice.event.EffectTimerGuard;
import com.merlinkitsune.astral_dice.item.card.ExclusiveCardUtil;
import com.merlinkitsune.astral_dice.item.ModItems;
import com.merlinkitsune.astral_dice.item.chip.VitaminPillChipItem;
import com.merlinkitsune.astral_dice.network.ActionBarPayload;
import com.merlinkitsune.starenginelib.target.TargetSelectionAction;
import com.merlinkitsune.astral_dice.target.TargetSelectionManager;
import com.merlinkitsune.starenginelib.target.TargetSelectionRegistry;
import com.merlinkitsune.starenginelib.target.TargetType;
import com.merlinkitsune.astral_dice.event.WeirdDiceHandler;
import com.merlinkitsune.astral_dice.item.chip.CurrentCoreChipItem;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.MobEffectEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;
import java.util.UUID;

/**
 * 占星师立牌(命名:haiqing)。
 * 被动 1:骰神赐福期间骰点=6 时立即获得 6 星币。
 * 被动 2:带"虚弱印记"的目标被击杀时,占星师获得 3 星币;若击杀者为玩家,该玩家获得一张"命运的指引"。
 * 主动:使用目标选择器选择目标并施加"虚弱印记"5:00(选择器目标规则:敌对生物或非队友玩家,
 * 选择者无队伍时对所有玩家生效;不符合规则的目标不可选中)。
 *
 * 主动为"目标选择器"类技能:触发后经 {@link TargetSelectionManager} 进入选择模式,
 * 确认时由 {@link TargetSelectionAction#apply} 施加效果并开始玩家级冷却;取消/超时不冷却。
 */
@EventBusSubscriber(modid = AstralDiceMod.MODID)
public class HaiqingSignItem extends BaseSignItem {
    private static final Logger LOGGER = LoggerFactory.getLogger(HaiqingSignItem.class);

    static {
        TargetSelectionRegistry.register(new TargetSelectionAction() {
            @Override
            public String id() {
                return "haiqing_weak_mark";
            }

            @Override
            public TargetType targetType() {
                return TargetType.ENEMY_OR_RIVAL;
            }

            @Override
            public void apply(ServerPlayer player, LivingEntity target) {
                // 施加"虚弱印记"5:00 + 虚弱效果,记录释放者(击杀后仅释放者获得奖励)
                ModAttachments.setWeakMarkSource(target, Optional.of(player.getUUID()));
                target.addEffect(new MobEffectInstance(ModEffects.WEAK_MARK, 6000, 0, false, true));
                EffectTimerGuard.apply(target, new MobEffectInstance(MobEffects.WEAKNESS, 6000, 0, false, true));
                // 主动成功施加:开始玩家级冷却(统一经 signCooldownTicks:含诡异骰子 -50% 与充能递减)
                ModAttachments.setSignActiveCooldownEnd(player,
                        player.level().getGameTime() + WeirdDiceHandler.signCooldownTicks(player));
                // 电流核心筹码:主动技能实际生效时充能 +1
                CurrentCoreChipItem.onActiveSkillUsed(player);
                PacketDistributor.sendToPlayer(player, new ActionBarPayload(
                        Component.translatable("msg.astral_dice.haiqing_weak_mark_applied", target.getDisplayName())
                                .withStyle(ChatFormatting.YELLOW), GameplayConstants.ACTIONBAR_DURATION_TICKS));
                LOGGER.debug("[Astral Dice][TargetSelection] haiqing_weak_mark applied to {}({}) by {}",
                        target.getId(), target.getName().getString(), player.getName().getString());
            }
        });
    }

    public HaiqingSignItem(Properties properties) {
        super(properties);
    }

    // 目标选择器前置门控(2026-09-17):本立牌主动为选择器类 —— 按下主动键只开启目标选择会话,
    // 会话时长取自 GameplayConstants.SKILL_WAIT_SECONDS(秒),此处不写死数字;
    // 确认合法目标后才继续原流程(风扇筹码发牌 + 立牌主动响应事件;冷却与电流核心充能由
    // 本类注册的 TargetSelectionAction#apply 在确认时写入)。
    @Override
    protected String selectorActionId() {
        return "haiqing_weak_mark";
    }

    @Override
    protected InteractionResultHolder<ItemStack> handleUse(Level level, Player player, ItemStack stack) {
        if (level.isClientSide) {
            return InteractionResultHolder.success(stack);
        }
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResultHolder.fail(stack);
        }
        // 主动:进入目标选择模式(服务端权威;确认时施加,取消/超时不冷却)
        boolean started = TargetSelectionManager.start(serverPlayer, "haiqing_weak_mark");
        return started ? InteractionResultHolder.success(stack) : InteractionResultHolder.fail(stack);
    }

    // 被动 2:带"虚弱印记"的目标被击杀时,释放该印记的占星师获得 3 星币;
    // 「命运的指引」归**击杀者**(击杀者可能是占星师自己,也可能不是玩家——非玩家击杀时不发牌)。
    public static void grantWeakMarkKillReward(Player applier, Player killer) {
        if (applier == null || applier.level().isClientSide()) return;
        ItemStack coinStack = new ItemStack(ModItems.STAR_COIN.get(), 3);
        if (!applier.getInventory().add(coinStack)) {
            applier.drop(coinStack, false);
        }
        if (killer != null) {
            ItemStack card = new ItemStack(ModItems.FATE_GUIDANCE_CARD.get());
            ExclusiveCardUtil.setOwner(card, killer);
            VitaminPillChipItem.giveCard(killer, card);
            if (killer != applier && killer instanceof ServerPlayer killerSp) {
                PacketDistributor.sendToPlayer(killerSp,
                        new ActionBarPayload(Component.translatable("msg.astral_dice.weak_mark_kill_reward")
                                .withStyle(ChatFormatting.YELLOW), GameplayConstants.ACTIONBAR_DURATION_TICKS));
            }
        }
        if (applier instanceof ServerPlayer sp) {
            PacketDistributor.sendToPlayer(sp,
                    new ActionBarPayload(Component.translatable("msg.astral_dice.weak_mark_kill_reward")
                            .withStyle(ChatFormatting.YELLOW), GameplayConstants.ACTIONBAR_DURATION_TICKS));
        }
    }

    @SubscribeEvent
    public static void onWeakMarkKill(LivingDeathEvent event) {
        LivingEntity target = event.getEntity();
        if (target.level().isClientSide()) return;
        if (!target.hasEffect(ModEffects.WEAK_MARK)) return;
        Optional<UUID> source = ModAttachments.getWeakMarkSource(target);
        if (source.isEmpty()) return;
        if (target.level().getPlayerByUUID(source.get()) instanceof Player applier) {
            // 只判定击杀者:非玩家击杀(如其它生物)时不发放「命运的指引」
            Player killer = event.getSource().getEntity() instanceof Player k ? k : null;
            HaiqingSignItem.grantWeakMarkKillReward(applier, killer);
        }
    }

    // 虚弱印记结束(计时归零或目标死亡):清除印记来源
    @SubscribeEvent
    public static void onWeakMarkExpired(MobEffectEvent.Expired event) {
        MobEffectInstance effect = event.getEffectInstance();
        if (effect == null || effect.getEffect() == null
                || effect.getEffect().value() != ModEffects.WEAK_MARK.get()) return;
        LivingEntity entity = event.getEntity();
        if (entity.level().isClientSide()) return;
        ModAttachments.setWeakMarkSource(entity, Optional.empty());
    }
}
