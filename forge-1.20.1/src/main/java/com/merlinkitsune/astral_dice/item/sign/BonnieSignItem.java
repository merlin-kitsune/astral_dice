package com.merlinkitsune.astral_dice.item.sign;
import com.merlinkitsune.astral_dice.network.ModNetwork;

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
import com.merlinkitsune.astral_dice.item.MarkManager;
import com.merlinkitsune.astral_dice.item.ModItems;
import com.merlinkitsune.astral_dice.item.chip.VitaminPillChipItem;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import com.merlinkitsune.astral_dice.AstralDiceMod;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * 秘密侦探立牌(命名:bonnie)。
 * 被动:
 * 1. 攻击带有"标记"的目标时攻击力+3;
 * 2. 击杀带有"标记"的敌对目标后获得一张随机攻击牌;
 * (击杀"隐匿调查"目标触发调查阶段事件已由 InvestigationEventUtil 全局处理)
 * 主动:下次攻击的第一个目标被施加"隐匿调查"(永久,直到目标死亡/消失);若目标带"标记",按标记层数获得 标记层数*2 星币。
 * 主动为"等待目标释放"类技能:等待状态保存在玩家级(ModAttachments),激活后进入等待期(默认 30 秒),
 * 攻击目标即释放;超时或立牌被移除则中断等待。
 */
@Mod.EventBusSubscriber(modid = com.merlinkitsune.astral_dice.AstralDiceMod.MODID)
public class BonnieSignItem extends BaseSignItem {
    // 玩家级等待状态类型:秘密侦探=2
    public static final int READY_TYPE = 2;

    public BonnieSignItem(Properties properties) {
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
            ModEffectRemoval.remove(player, ModEffects.BONNIE_READY.get());
        }
        if (ModAttachments.getSignReadyType(player) == READY_TYPE && expire > 0
                && player.tickCount % 20 == 0) {
            sendReadyPrompt(player);
        }
    }

    // 发送"待命"ActionBar 提示(激活瞬间与等待期间共用)
    private static void sendReadyPrompt(Player player) {
        if (player instanceof ServerPlayer sp) {
            ModNetwork.sendToPlayer(sp,
                    new ModNetwork.ActionBarMessage(Component.translatable("msg.astral_dice.bonnie_ready")
                            .withStyle(ChatFormatting.YELLOW), GameplayConstants.ACTIONBAR_DURATION_TICKS));
        }
    }

    // 主动技能自带 ActionBar 反馈("待命"提示):注册到主动技能响应事件,阻止默认提示
    @SubscribeEvent
    public static void onSignActiveTriggered(com.merlinkitsune.astral_dice.event.SignActiveTriggeredEvent event) {
        if (event.getSignStack().is(ModItems.BONNIE_SIGN.get())) {
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
        // 卸下立牌:重置调查阶段进度并清除调查增益效果(buff 累积)与"待命"提示效果
        ModAttachments.setInvestigationStage(player, 1);
        ModEffectRemoval.remove(player, ModEffects.INVESTIGATION_BONUS.get());
        ModEffectRemoval.remove(player, ModEffects.BONNIE_READY.get());
    }

    @Override
    protected InteractionResultHolder<ItemStack> handleUse(Level level, Player player, ItemStack stack) {
        if (level.isClientSide) {
            return InteractionResultHolder.success(stack);
        }
        // 主动:进入等待期(玩家级状态),等待攻击目标释放"隐匿调查";施加"待命"效果提示玩家
        ModAttachments.setSignReadyType(player, READY_TYPE);
        ModAttachments.setSignReadyExpire(player,
                level.getGameTime() + GameplayConstants.SKILL_WAIT_SECONDS * 20L);
        player.addEffect(new MobEffectInstance(ModEffects.BONNIE_READY.get(), Integer.MAX_VALUE, 0, false, false, true));
        return InteractionResultHolder.success(stack);
    }

    // 被动 2(击杀钩子,由 BaseSignItem.invokeKillHooks 分发):
    // - 击杀带"标记"的敌对目标 → 获得一张随机攻击牌;
    // 被动 3 已移至 InvestigationEventUtil.onUndercoverInvestigationKill 统一处理,
    // 使任意玩家击杀"隐匿调查"目标都能触发调查阶段事件(不再要求击杀者佩戴秘密侦探立牌)。
    @Override
    protected void onKill(Player killer, net.minecraft.world.entity.LivingEntity killed) {
        // 被动 2:击杀带"标记"的目标 → 获得一张随机战斗牌
        if (MarkManager.getLevel(killed) > 0
                && !(killed instanceof Player)
                && killed instanceof net.minecraft.world.entity.monster.Enemy
                && killed.getMaxHealth() > 20) {
            giveRandomBattleCard(killer);
        }
    }

    // 给击杀者一张随机攻击牌(攻击-中/大/特大 之一)
    private static void giveRandomBattleCard(Player player) {
        net.minecraft.world.item.Item[] cards = {
                ModItems.ATTACK_CARD_MEDIUM.get(),
                ModItems.ATTACK_CARD_LARGE.get(),
                ModItems.ATTACK_CARD_EPIC.get()
        };
        ItemStack stack = new ItemStack(cards[java.util.concurrent.ThreadLocalRandom.current().nextInt(cards.length)]);
        VitaminPillChipItem.giveCard(player, stack);
    }

    // 秘密侦探立牌被动:击杀带"标记"目标获得随机战斗牌。
    // 通过立牌击杀钩子分发(见 BonnieSignItem.onKill);"隐匿调查"击杀事件由下方全局处理器统一触发。
    @SubscribeEvent
    public static void onBonnieKill(LivingDeathEvent event) {
        LivingEntity target = event.getEntity();
        if (target.level().isClientSide()) return;
        if (!(event.getSource().getEntity() instanceof Player killer)) return;
        BaseSignItem.invokeKillHooks(killer, target);
    }

}
