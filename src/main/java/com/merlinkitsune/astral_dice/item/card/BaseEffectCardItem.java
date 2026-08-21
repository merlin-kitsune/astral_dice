package com.merlinkitsune.astral_dice.item.card;

import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import com.merlinkitsune.astral_dice.item.chip.MagicTomeChipItem;
import com.merlinkitsune.astral_dice.item.sign.FenSignItem;
import com.merlinkitsune.astral_dice.item.chip.MagicQuiverChipItem;
import com.merlinkitsune.astral_dice.item.sign.KomachiSignItem;

/**
 * 效果牌统一基类(不再区分"功能效果牌/伤害效果牌")。
 *
 * 全部效果牌共用同一套使用流程,由本基类统一处理(use 与 interactLivingEntity
 * 共用私有方法 {@link #tryUseCard},避免双入口逻辑漂移):
 * 1. 客户端预检(专属校验 + 出牌锁,出牌状态附件已同步到客户端,判定与服务端一致;
 *    被阻止时直接失败——不消耗、不播放动画,杜绝"消耗了却没效果"的错位);
 * 2. 服务端权威判定(专属校验 → 出牌锁,见 {@link EffectCardPeriod#isBlocked});
 * 3. 调用子类的 {@link #applyEffect}(服务端,施加实际效果);
 * 4. 出牌登记(统一开始/重置冷却,见 {@link EffectCardPeriod#registerPlay});
 * 5. 复制计数钩子(忍者立牌/魔法秘典/魔法箭袋,见 {@link #countsForCopy()} 与 {@link #cardTypeId()});
 * 6. 消耗一张。
 *
 * 子类二选一实现效果:
 * - 简单状态牌:覆写 {@link #getEffect()} 返回效果引用(基类自动施加 {@link #getEffectDuration()} 时长);
 * - 复杂逻辑牌:覆写 {@link #applyEffect()}(使用后对玩家/目标施加的效果)。
 * 按需覆写 {@link #canUseOnOtherPlayers()} / {@link #countsForCopy()} / {@link #cardTypeId()} /
 * {@link #isExclusive()}。
 */
public abstract class BaseEffectCardItem extends Item {

    public BaseEffectCardItem(Properties properties) {
        super(properties);
    }

    // 是否允许对其他玩家使用(下蹲+右键对面前玩家施放),默认否
    public boolean canUseOnOtherPlayers() {
        return false;
    }

    // 是否为专属效果牌(绑定获得者,他人不可用),默认否
    protected boolean isExclusive() {
        return false;
    }

    // 是否参与"忍者立牌/魔法秘典"的复制计数(默认否;功能效果牌覆写为 true)
    protected boolean countsForCopy() {
        return false;
    }

    // 参与复制计数时的卡牌类型 id(与计数钩子的 effectCardByType 映射对应)
    protected String cardTypeId() {
        return "";
    }

    /**
     * 是否为治疗类效果牌(恢复生命值;大当家立牌被动:使用治疗类效果牌时"养精蓄锐"+1 层)。
     * 治疗类:巧克力蛋糕/汉堡/奢华大餐。
     */
    protected boolean isHealingCard() {
        return false;
    }

    /**
     * 简单状态牌:返回使用后施加的状态效果引用;返回 null 时基类不自动施加,
     * 需覆写 {@link #applyEffect} 实现复杂逻辑。默认返回 null。
     */
    protected Holder<MobEffect> getEffect() {
        return null;
    }

    // 简单状态牌的效果时长(tick):默认 60 秒
    protected int getEffectDuration() {
        return 1200;
    }

    /**
     * 施加效果(仅服务端调用;出牌锁已校验通过)。
     * 默认实现:若 {@link #getEffect()} 非 null 则向目标施加该效果指定时长;
     * 复杂效果牌覆写本方法。
     *
     * @param applyTo 实际受益目标(自己或 {@link #canUseOnOtherPlayers()} 时面前的玩家)
     */
    protected void applyEffect(Level level, Player user, LivingEntity applyTo, ItemStack stack) {
        Holder<MobEffect> effect = getEffect();
        if (effect != null) {
            applyTo.addEffect(new MobEffectInstance(effect, getEffectDuration(), 0, false, true));
        }
    }

    // 通用:寻找玩家视线前方指定距离内的其他玩家
    public static Player findPlayerInFront(Player player, double range) {
        net.minecraft.world.phys.HitResult hit = player.pick(range, 1.0f, false);
        if (hit.getType() == net.minecraft.world.phys.HitResult.Type.ENTITY
                && hit instanceof net.minecraft.world.phys.EntityHitResult entityHit
                && entityHit.getEntity() instanceof Player target && target != player) {
            return target;
        }
        return null;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);

        if (level.isClientSide) {
            // 客户端预检:出牌状态附件已同步到客户端,判定与服务端一致;
            // 被阻止时直接失败——不消耗、不播放使用动画,避免"消耗了却没效果"的错位
            if (isBlockedOnClient(player, stack)) {
                return InteractionResultHolder.fail(stack);
            }
        } else {
            // 决定实际受益目标:下蹲+右键且允许对他人使用时,选择面前玩家;否则为自己
            LivingEntity applyTo = player;
            if (player.isShiftKeyDown() && canUseOnOtherPlayers()) {
                Player target = findPlayerInFront(player, 5.0);
                if (target != null) {
                    applyTo = target;
                }
            }

            // 服务端权威判定 + 完整出牌流程
            if (!tryUseCard(level, player, applyTo, stack)) {
                return InteractionResultHolder.fail(stack);
            }
        }
        stack.consume(1, player);
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }

    @Override
    public InteractionResult interactLivingEntity(ItemStack stack, Player player, LivingEntity target, InteractionHand hand) {
        // 仅"可对他人生效"的效果牌支持直接对实体使用(如狂暴);其余效果牌维持原行为(不响应实体交互)
        if (!canUseOnOtherPlayers()) {
            return InteractionResult.PASS;
        }
        if (player.level().isClientSide) {
            // 客户端预检(同上):被阻止时不消耗、不返回成功
            if (isBlockedOnClient(player, stack)) {
                return InteractionResult.FAIL;
            }
        } else {
            // 服务端权威判定 + 完整出牌流程,目标为被点击实体
            if (!tryUseCard(player.level(), player, target, stack)) {
                return InteractionResult.FAIL;
            }
        }
        stack.consume(1, player);
        return InteractionResult.SUCCESS;
    }

    // 客户端预检:与服务端 tryUseCard 的判定保持一致(专属校验 + 出牌锁)。
    // 出牌数/冷却/忍者临时出牌附件均已 .sync() 到客户端,客户端可实时判定。
    private boolean isBlockedOnClient(Player player, ItemStack stack) {
        boolean exclusiveBlocked = isExclusive() && !ExclusiveCardUtil.canUse(player, stack);
        if (exclusiveBlocked) return true;
        if (EffectCardPeriod.isBurstFull(player)) {
            int seconds = EffectCardPeriod.getRemainingBlockSeconds(player);
            player.displayClientMessage(
                    Component.translatable("msg.astral_dice.effect_card_burst_full", seconds), true);
            return true;
        }
        return EffectCardPeriod.isBlocked(player);
    }

    /**
     * 服务端完整出牌流程(use 与 interactLivingEntity 共用,避免逻辑漂移):
     * 专属校验 → 出牌锁 → 施加效果 → 出牌登记 → 治疗类钩子 → 复制计数钩子。
     *
     * @return 是否成功出牌;false 时调用方不得消耗卡片
     */
    private boolean tryUseCard(Level level, Player player, LivingEntity applyTo, ItemStack stack) {
        // 专属牌:仅允许获得者使用
        if (isExclusive() && !ExclusiveCardUtil.canUse(player, stack)) {
            return false;
        }
        // 出牌锁:出牌数上限/冷却/效果待定任一阻止则失败
        if (EffectCardPeriod.isBlocked(player)) {
            return false;
        }

        // 施加效果(子类实现)
        applyEffect(level, player, applyTo, stack);

        // 出牌登记:立即开始/重置冷却倒计时(冷却与效果分离计算)
        EffectCardPeriod.registerPlay(player);

        // 治疗类效果牌:大当家立牌被动"养精蓄锐 +1 层"
        if (isHealingCard()) {
            FenSignItem.onHealingCardUsed(player);
        }

        // 复制计数钩子(忍者立牌/魔法秘典/魔法箭袋)
        if (countsForCopy()) {
            KomachiSignItem.onEffectCardUsed(player, cardTypeId());
            MagicTomeChipItem.onEffectCardUsed(player, cardTypeId());
            MagicQuiverChipItem.onEffectCardUsed(player, cardTypeId());
        }
        return true;
    }
}

