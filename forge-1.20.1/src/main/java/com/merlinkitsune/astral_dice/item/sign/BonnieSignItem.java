package com.merlinkitsune.astral_dice.item.sign;

import com.merlinkitsune.astral_dice.AstralDiceMod;
import com.merlinkitsune.astral_dice.component.GameplayConstants;
import com.merlinkitsune.astral_dice.component.ModAttachments;
import com.merlinkitsune.astral_dice.effect.ModEffects;
import com.merlinkitsune.astral_dice.event.ModEffectRemoval;
import com.merlinkitsune.astral_dice.item.MarkManager;
import com.merlinkitsune.astral_dice.item.ModItems;
import com.merlinkitsune.astral_dice.item.chip.VitaminPillChipItem;
import com.merlinkitsune.astral_dice.network.ModNetwork;
import com.merlinkitsune.astral_dice.target.TargetSelectionAction;
import com.merlinkitsune.astral_dice.target.TargetSelectionManager;
import com.merlinkitsune.astral_dice.target.TargetSelectionRegistry;
import com.merlinkitsune.astral_dice.target.TargetType;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 秘密侦探立牌(命名:bonnie)。
 * 被动:
 * 1. 攻击带有"标记"的目标时攻击力+3;
 * 2. 击杀带有"标记"的敌对目标后获得一张随机攻击牌;
 * (击杀"隐匿调查"目标触发调查阶段事件已由 InvestigationEventUtil 全局处理)
 * 主动:使用目标选择器选择目标并施加"隐匿调查"(永久,直到目标死亡/消失);若目标带"标记",
 * 按标记层数获得 标记层数*2 星币(选择器目标规则:敌对生物或非队友玩家,
 * 选择者无队伍时对所有玩家生效;不符合规则的目标不可选中)。
 *
 * 主动为"目标选择器"类技能:触发后经 {@link TargetSelectionManager} 进入选择模式,
 * 确认时由 {@link TargetSelectionAction#apply} 施加效果并开始玩家级冷却;取消/超时不冷却。
 */
@Mod.EventBusSubscriber(modid = AstralDiceMod.MODID)
public class BonnieSignItem extends BaseSignItem {
    private static final Logger LOGGER = LoggerFactory.getLogger(BonnieSignItem.class);

    static {
        TargetSelectionRegistry.register(new TargetSelectionAction() {
            @Override
            public String id() {
                return "bonnie_undercover";
            }

            @Override
            public TargetType targetType() {
                return TargetType.ENEMY_OR_RIVAL;
            }

            @Override
            public void apply(ServerPlayer player, LivingEntity target) {
                // 施加"隐匿调查"(永久,直到目标死亡/消失),记录施加者(击杀触发调查阶段事件)
                ModAttachments.setUndercoverSource(target, Optional.of(player.getUUID()));
                target.addEffect(new MobEffectInstance(ModEffects.UNDERCOVER_INVESTIGATION.get(),
                        Integer.MAX_VALUE, 0, false, true));
                // 目标带"标记"时:按标记层数 ×2 获得星币
                int markLevel = MarkManager.getLevel(target);
                if (markLevel > 0) {
                    ItemStack coinStack = new ItemStack(ModItems.STAR_COIN.get(), markLevel * 2);
                    if (!player.getInventory().add(coinStack)) {
                        player.drop(coinStack, false);
                    }
                }
                // 主动成功施加:开始玩家级冷却
                ModAttachments.setSignActiveCooldownEnd(player,
                        player.level().getGameTime() + GameplayConstants.SIGN_ACTIVE_COOLDOWN_TICKS);
                ModNetwork.sendToPlayer(player, new ModNetwork.ActionBarMessage(
                        Component.translatable("msg.astral_dice.bonnie_undercover_applied", target.getDisplayName())
                                .withStyle(ChatFormatting.YELLOW), GameplayConstants.ACTIONBAR_DURATION_TICKS));
                LOGGER.debug("[Astral Dice][TargetSelection] bonnie_undercover applied to {}({}) by {} markLevel={}",
                        target.getId(), target.getName().getString(), player.getName().getString(), markLevel);
            }
        });
    }

    public BonnieSignItem(Properties properties) {
        super(properties);
    }

    @Override
    protected void clearSignData(Player player, ItemStack stack) {
        super.clearSignData(player, stack);
        // 卸下立牌:重置调查阶段进度并清除调查增益效果(buff 累积)
        ModAttachments.setInvestigationStage(player, 1);
        ModEffectRemoval.remove(player, ModEffects.INVESTIGATION_BONUS.get());
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
        boolean started = TargetSelectionManager.start(serverPlayer, "bonnie_undercover");
        return started ? InteractionResultHolder.success(stack) : InteractionResultHolder.fail(stack);
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
                && killed instanceof Enemy
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
        ItemStack stack = new ItemStack(cards[ThreadLocalRandom.current().nextInt(cards.length)]);
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
