package com.merlinkitsune.astral_dice.item.sign;

import com.merlinkitsune.starenginelib.component.GameplayConstants;
import com.merlinkitsune.astral_dice.component.ModAttachments;
import com.merlinkitsune.astral_dice.effect.ModEffects;
import com.merlinkitsune.starenginelib.event.ModEffectRemoval;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import top.theillusivec4.curios.api.SlotContext;
import com.merlinkitsune.astral_dice.item.card.ExclusiveCardUtil;
import com.merlinkitsune.astral_dice.item.ModItems;
import com.merlinkitsune.astral_dice.item.chip.VitaminPillChipItem;
import com.merlinkitsune.astral_dice.network.ActionBarPayload;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import com.merlinkitsune.astral_dice.AstralDiceMod;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.MobEffectEvent;
import net.minecraft.world.entity.LivingEntity;
import java.util.Optional;
import java.util.UUID;
import net.neoforged.bus.api.SubscribeEvent;

/**
 * 占星师立牌(命名:haiqing)。
 * 被动 1:骰神赐福期间骰点=6 时立即获得 6 星币。
 * 被动 2:带"虚弱印记"的目标被击杀时,占星师获得 3 星币;若击杀者为玩家,该玩家获得一张"命运的指引"。
 * 主动:下次攻击的第一个目标(须符合骰神赐福触发条件)被施加"虚弱印记"5:00,目标受到任意伤害 +10% 并获得虚弱效果。
 * 主动为"等待目标释放"类技能:等待状态保存在玩家级(ModAttachments),激活后进入等待期(默认 30 秒),
 * 攻击目标即释放;超时或立牌被移除则中断等待。
 */
@EventBusSubscriber(modid = com.merlinkitsune.astral_dice.AstralDiceMod.MODID)
public class HaiqingSignItem extends BaseSignItem {
    // 玩家级等待状态类型:占星师=1
    public static final int READY_TYPE = 1;

    public HaiqingSignItem(Properties properties) {
        super(properties);
    }

    @Override
    protected void onCurioTick(SlotContext slotContext, ItemStack stack) {
        // 主动技能等待期:超时清除已移到玩家级 tick(BaseSignItem#tickSignReadyTimeout,S6-C2 状态与计时器分离),
        // 不再依赖"立牌仍在饰品槽位"——否则立牌离身后残留的正计时器会让该玩家所有立牌的主动都不再进入冷却。
        if (!(slotContext.entity() instanceof Player player)) return;
        long expire = ModAttachments.getSignReadyExpire(player);
        if (ModAttachments.getSignReadyType(player) == READY_TYPE && expire > 0
                && player.tickCount % 20 == 0) {
            sendReadyPrompt(player);
        }
    }

    // 发送"待命"ActionBar 提示(激活瞬间与等待期间共用)
    private static void sendReadyPrompt(Player player) {
        if (player instanceof ServerPlayer sp) {
            PacketDistributor.sendToPlayer(sp,
                    new ActionBarPayload(Component.translatable("msg.astral_dice.haiqing_ready")
                            .withStyle(ChatFormatting.YELLOW), GameplayConstants.ACTIONBAR_DURATION_TICKS));
        }
    }

    // 主动技能自带 ActionBar 反馈("待命"提示):注册到主动技能响应事件,阻止默认提示
    @SubscribeEvent
    public static void onSignActiveTriggered(com.merlinkitsune.starenginelib.event.SignActiveTriggeredEvent event) {
        if (event.getSignStack().is(ModItems.HAIQING_SIGN.get())) {
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
        ModEffectRemoval.remove(player, ModEffects.HAIQING_READY);
    }

    // 目标选择器前置门控(26.1.2 移植 B1+B2,2026-09-18):本立牌主动为选择器类 —— 按下主动键只开启
    // 目标选择会话,会话时长取自 GameplayConstants.SKILL_WAIT_SECONDS(秒),此处不写死数字;
    // 确认合法目标后才继续原流程(风扇筹码发牌 + 立牌主动响应事件;冷却与电流核心充能由
    // haiqing_weak_mark 动作的 apply 在确认时写入)。
    // 语义基准 = 1.21.1 `HaiqingSignItem.java` 第 88-95 行(逐字同形,纯平台无关代码)。
    // ⚠️ 本批次为**叠加**门控:下方 READY_TYPE / sign_ready_type 旧待命等待器机制按用户要求保持原样,
    //    门控生效后本类 handleUse 不再被 performSkill 到达(该次主动改由 haiqing_weak_mark 的 apply 施加)。
    @Override
    protected String selectorActionId() {
        return "haiqing_weak_mark";
    }

    @Override
    protected InteractionResult handleUse(Level level, Player player, ItemStack stack) {
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        // 主动:进入等待期(玩家级状态),等待攻击目标释放"虚弱印记";施加"待命"效果提示玩家
        ModAttachments.setSignReadyType(player, READY_TYPE);
        ModAttachments.setSignReadyExpire(player,
                level.getGameTime() + GameplayConstants.SKILL_WAIT_SECONDS * 20L);
        player.addEffect(new MobEffectInstance(ModEffects.HAIQING_READY, Integer.MAX_VALUE, 0, false, false, true));
        return InteractionResult.SUCCESS;
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
