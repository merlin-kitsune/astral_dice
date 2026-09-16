package com.merlinkitsune.astral_dice.item.card;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * 你有我有(效果牌):仅能对其他玩家使用(对准其他玩家右键)。
 * 使用后:使自身以及目标玩家各获得一张随机卡牌(战斗牌 + 效果牌,不含专属)。
 */
public class YouHaveIHaveCardItem extends BaseEffectCardItem {

    public YouHaveIHaveCardItem(Properties properties) {
        super(properties);
    }
    @Override
    protected String cardTypeId() {
        return "you_have_i_have";
    }


    @Override
    public boolean canUseOnOtherPlayers() {
        // 仅通过"对实体右键"对其他玩家使用
        return true;
    }

    @Override
    protected boolean isHealingCard() {
        // 不属治疗类效果牌(不触发大当家被动)
        return false;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        // 仅能对其他玩家使用,不能对自己使用
        return InteractionResultHolder.fail(player.getItemInHand(hand));
    }

    @Override
    public InteractionResult interactLivingEntity(ItemStack stack, Player player, LivingEntity target, InteractionHand hand) {
        // 仅能对其他玩家使用(对准其他玩家右键);对非玩家实体不响应
        if (!(target instanceof Player)) {
            return InteractionResult.PASS;
        }
        return super.interactLivingEntity(stack, player, target, hand);
    }

    @Override
    protected void applyEffect(Level level, Player user, LivingEntity applyTo, ItemStack stack) {
        if (!(applyTo instanceof Player target)) return;
        if (target == user) return;
        // 自身以及目标玩家各获得一张随机卡牌
        RandomCardHandler.giveCardTo(user, RandomCardHandler.CardCategory.ALL);
        RandomCardHandler.giveCardTo(target, RandomCardHandler.CardCategory.ALL);
    }
}
