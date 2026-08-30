package com.merlinkitsune.astral_dice.item.sign;

import com.merlinkitsune.astral_dice.event.EffectTimerGuard;

import com.merlinkitsune.astral_dice.component.GameplayConstants;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.animal.camel.Camel;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.monster.Strider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import top.theillusivec4.curios.api.SlotContext;

import java.util.List;
import com.merlinkitsune.astral_dice.component.ModAttachments;
import com.merlinkitsune.astral_dice.item.HealingManager;
import com.merlinkitsune.astral_dice.item.chip.FriendshipBadgeChipItem;

/**
 * 史莱姆立牌。
 * 治愈点数已解耦至 {@link HealingManager}(玩家级共享资源),本类仅负责:
 * - 被动:受到伤害 +1 点治愈(受击钩子 {@link #onHurt},本类不持有治愈数据);
 * - 主动:自身 +3 点治愈并治疗附近友好目标;
 * - 卸载立牌不做任何扣除(治愈点数保留,避免卸下/重载丢失点数)。
 */
public class LuluSignItem extends BaseSignItem {

    public LuluSignItem(Properties properties) {
        super(properties);
    }


    // 被动:受到伤害时,获得 1 点"治愈"(上限为玩家最大生命值的一半,即 ♥ 数),并使主动技能冷却 -10 秒。
    // 任何来源的伤害均触发(近战/远程/环境等),与骰神赐福的玩家攻击链路相互独立。
    // 受击 +1 有 1 秒冷却(20 tick),防止被围攻时治愈点数暴涨。
    @Override
    protected void onHurt(Player player, float amount) {
        long nowTick = player.level().getGameTime();
        if (nowTick - com.merlinkitsune.astral_dice.component.ModAttachments.getLuluLastHurtTick(player) < 20) {
            return;
        }
        com.merlinkitsune.astral_dice.component.ModAttachments.setLuluLastHurtTick(player, nowTick);
        // 主动技能冷却 -10 秒(200 tick)
        long cdEnd = com.merlinkitsune.astral_dice.component.ModAttachments.getSignActiveCooldownEnd(player);
        if (cdEnd > 0) {
            com.merlinkitsune.astral_dice.component.ModAttachments.setSignActiveCooldownEnd(player,
                    Math.max(0, cdEnd - 200));
        }
        // 治愈点 +1(上限为玩家最大生命值的一半,即 ♥ 数)
        HealingManager.add(player, 1);
    }

    protected InteractionResultHolder<ItemStack> handleUse(Level level, Player player, ItemStack stack) {
        if (level.isClientSide) {
            return InteractionResultHolder.success(stack);
        }
        // 主动技能:自身获得 3 点"治愈"(上限为玩家最大生命值的一半,即 ♥ 数)
        HealingManager.add(player, 3);

        // 自身获得瞬间治疗 1
        player.addEffect(new MobEffectInstance(MobEffects.HEAL, 1, 0, false, true));

        AABB aabb = player.getBoundingBox().inflate(GameplayConstants.LULU_ACTIVE_RANGE);
        List<LivingEntity> nearby = level.getEntitiesOfClass(LivingEntity.class, aabb,
                e -> e != player);

        for (LivingEntity entity : nearby) {
            if (entity instanceof Enemy) {
                // 敌对生物:缓慢 60 秒
                EffectTimerGuard.apply(entity, new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 1200, 0, false, true));
            } else if (isHealTarget(entity, player)) {
                // 玩家/宠物/可骑乘生物:瞬间治疗 1
                entity.addEffect(new MobEffectInstance(MobEffects.HEAL, 1, 0, false, true));
                // 友情徽章:对友方玩家施加治疗时,双方各获得 2 点治愈
                if (entity instanceof Player targetPlayer) {
                    FriendshipBadgeChipItem.onHealApplied(player, targetPlayer);
                }
            }
        }
        return InteractionResultHolder.success(stack);
    }

    // 判定可治疗的友好目标:玩家、玩家驯服的宠物、可骑乘生物(马/驴/骡等、猪、炽足兽、骆驼)
    private static boolean isHealTarget(LivingEntity entity, Player player) {
        if (entity instanceof Player) return true;
        if (entity instanceof TamableAnimal tame && tame.isOwnedBy(player)) return true;
        return entity instanceof AbstractHorse
                || entity instanceof Pig
                || entity instanceof Strider
                || entity instanceof Camel;
    }
}
