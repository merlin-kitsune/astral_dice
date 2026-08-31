package com.merlinkitsune.astral_dice.item.sign;

import com.merlinkitsune.astral_dice.component.GameplayConstants;
import com.merlinkitsune.astral_dice.component.ModAttachments;
import com.merlinkitsune.astral_dice.effect.ModEffects;
import com.merlinkitsune.astral_dice.event.ModEffectRemoval;
import net.minecraft.world.InteractionResultHolder;
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
        // 主动技能等待期:超时未对目标释放则取消技能,恢复到未使用状态
        if (!(slotContext.entity() instanceof Player player)) return;
        long expire = ModAttachments.getSignReadyExpire(player);
        if (ModAttachments.getSignReadyType(player) == READY_TYPE && expire > 0
                && player.level().getGameTime() >= expire) {
            ModAttachments.setSignReadyType(player, 0);
            ModAttachments.setSignReadyExpire(player, 0);
            ModEffectRemoval.remove(player, ModEffects.HAIQING_READY);
        }
        if (ModAttachments.getSignReadyType(player) == READY_TYPE && expire > 0
                && player.tickCount % 20 == 0 && player instanceof ServerPlayer sp) {
            PacketDistributor.sendToPlayer(sp,
                    new ActionBarPayload(Component.translatable("msg.astral_dice.haiqing_ready")
                            .withStyle(ChatFormatting.YELLOW), GameplayConstants.ACTIONBAR_DURATION_TICKS));
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

    @Override
    protected InteractionResultHolder<ItemStack> handleUse(Level level, Player player, ItemStack stack) {
        if (level.isClientSide) {
            return InteractionResultHolder.success(stack);
        }
        // 主动:进入等待期(玩家级状态),等待攻击目标释放"虚弱印记";施加"待命"效果提示玩家
        ModAttachments.setSignReadyType(player, READY_TYPE);
        ModAttachments.setSignReadyExpire(player,
                level.getGameTime() + GameplayConstants.SKILL_WAIT_SECONDS * 20L);
        player.addEffect(new MobEffectInstance(ModEffects.HAIQING_READY, Integer.MAX_VALUE, 0, false, false, true));
        return InteractionResultHolder.success(stack);
    }

    // 被动 2:带"虚弱印记"的目标被击杀时,仅释放该印记的玩家(占星师)获得 3 星币与一张"命运的指引"(绑定获得者)。
    // 由 HaiqingSignItem.onWeakMarkKill 事件分发(任何玩家击杀都触发,奖励归属印记释放者)。
    public static void grantWeakMarkKillReward(Player applier) {
        if (applier == null || applier.level().isClientSide()) return;
        ItemStack coinStack = new ItemStack(ModItems.STAR_COIN.get(), 3);
        if (!applier.getInventory().add(coinStack)) {
            applier.drop(coinStack, false);
        }
        ItemStack card = new ItemStack(ModItems.FATE_GUIDANCE_CARD.get());
        ExclusiveCardUtil.setOwner(card, applier);
        VitaminPillChipItem.giveCard(applier, card);
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
            HaiqingSignItem.grantWeakMarkKillReward(applier);
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


    // 主动技能自带 ActionBar 反馈("待命"提示/事件提示),不发送通用"已触发主动技能"
    @Override
    protected boolean hasOwnActionBarFeedback() {
        return true;
    }
}
