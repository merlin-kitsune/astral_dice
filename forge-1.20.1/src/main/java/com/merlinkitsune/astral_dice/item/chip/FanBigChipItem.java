package com.merlinkitsune.astral_dice.item.chip;

import com.merlinkitsune.astral_dice.item.CuriosCompat;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import com.merlinkitsune.astral_dice.item.sign.BaseSignItem;
import com.merlinkitsune.astral_dice.component.GameplayConstants;
import com.merlinkitsune.astral_dice.item.MarkManager;
import com.merlinkitsune.astral_dice.item.ModItems;
import com.merlinkitsune.astral_dice.item.chip.VitaminPillChipItem;
import com.merlinkitsune.astral_dice.item.card.EffectCardUtil;

/**
 * 手持风扇-大筹码:使用(立牌)主动技能后,获得一张随机效果牌(不包括专属牌),
 * 并对周围 HAND_FAN_BIG_RANGE 格(默认 16,可配置)范围内所有敌对目标施加一层标记。
 * 触发逻辑在 BaseSignItem.performSkillForCurio(主动技能触发成功后)。
 */
public class FanBigChipItem extends BaseChipItem {
    public FanBigChipItem(Properties properties) {
        super(properties);
    }

    // 主动技能触发成功后调用:佩戴本筹码时获得随机效果牌并对周围敌对目标施加标记
    public static void applyAfterSignSkill(Player player) {
        if (player.level().isClientSide()) return;
        var curios = com.merlinkitsune.astral_dice.item.CuriosCompat.getCuriosInventory(player);
        if (curios.isEmpty()) return;
        if (curios.get().findFirstCurio(s -> s.is(ModItems.HAND_FAN_BIG_CHIP.get())).isEmpty()) return;

        // 获得一张随机效果牌(随机池已排除专属牌:活体书页/命运的指引)
        List<ItemStack> pool = EffectCardUtil.getRandomEffectCardPool();
        if (!pool.isEmpty()) {
            ItemStack card = pool.get(ThreadLocalRandom.current().nextInt(pool.size()));
            VitaminPillChipItem.giveCard(player, card);
        }

        // 对周围 HAND_FAN_BIG_RANGE 格范围内所有敌对目标施加一层标记
        AABB aabb = player.getBoundingBox().inflate(com.merlinkitsune.astral_dice.component.GameplayConstants.HAND_FAN_BIG_RANGE);
        List<LivingEntity> nearby = player.level().getEntitiesOfClass(LivingEntity.class, aabb,
                e -> e instanceof Enemy && e.isAlive());
        for (LivingEntity entity : nearby) {
            MarkManager.apply(entity);
        }
    }
}
