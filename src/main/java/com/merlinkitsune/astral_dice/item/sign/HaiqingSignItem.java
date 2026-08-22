package com.merlinkitsune.astral_dice.item.sign;

import com.merlinkitsune.astral_dice.component.GameplayConstants;
import com.merlinkitsune.astral_dice.component.ModAttachments;
import com.merlinkitsune.astral_dice.effect.ModEffects;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import top.theillusivec4.curios.api.SlotContext;
import com.merlinkitsune.astral_dice.item.card.ExclusiveCardUtil;
import com.merlinkitsune.astral_dice.event.ModEventHandlers;
import com.merlinkitsune.astral_dice.item.ModItems;
import com.merlinkitsune.astral_dice.network.ActionBarPayload;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * 占星师立牌(命名:haiqing)。
 * 被动 1:骰神赐福期间骰点=6 时立即获得 6 星币。
 * 被动 2:带"虚弱印记"的目标被击杀时,占星师获得 3 星币;若击杀者为玩家,该玩家获得一张"命运的指引"。
 * 主动:下次攻击的第一个目标(须符合骰神赐福触发条件)被施加"虚弱印记"5:00,目标受到任意伤害 +10% 并获得虚弱效果。
 * 主动为"等待目标释放"类技能:等待状态保存在玩家级(ModAttachments),激活后进入等待期(默认 30 秒),
 * 攻击目标即释放;超时或立牌被移除则中断等待。
 */
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
            player.removeEffect(ModEffects.HAIQING_READY);
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
        player.removeEffect(ModEffects.HAIQING_READY);
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
    // 由 ModEventHandlers.onWeakMarkKill 事件分发(任何玩家击杀都触发,奖励归属印记释放者)。
    public static void grantWeakMarkKillReward(Player applier) {
        if (applier == null || applier.level().isClientSide()) return;
        ItemStack coinStack = new ItemStack(ModItems.STAR_COIN.get(), 3);
        if (!applier.getInventory().add(coinStack)) {
            applier.drop(coinStack, false);
        }
        ItemStack card = new ItemStack(ModItems.FATE_GUIDANCE_CARD.get());
        ExclusiveCardUtil.setOwner(card, applier);
        if (!applier.getInventory().add(card)) {
            applier.drop(card, false);
        }
        if (applier instanceof ServerPlayer sp) {
            PacketDistributor.sendToPlayer(sp,
                    new ActionBarPayload(Component.translatable("msg.astral_dice.weak_mark_kill_reward")
                            .withStyle(ChatFormatting.YELLOW), GameplayConstants.ACTIONBAR_DURATION_TICKS));
        }
    }
}
