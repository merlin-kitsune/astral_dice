package com.merlinkitsune.astral_dice.item.chip;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import com.merlinkitsune.astral_dice.item.MarkManager;
import com.merlinkitsune.astral_dice.item.ModItems;

/**
 * 手持风扇-小筹码:使用(立牌)主动技能后,对自身周围 16 格范围内所有敌对目标施加一层标记。
 * 触发逻辑在 BaseSignItem.performSkillForCurio(主动技能触发成功后)。
 */
public class FanSmallChipItem extends BaseChipItem {
    /** 标记施加范围(格) */
    public static final int RANGE = 16;

    public FanSmallChipItem(Properties properties) {
        super(properties);
    }

    // 主动技能触发成功后调用:佩戴本筹码时对周围敌对目标施加标记
    public static void applyAfterSignSkill(Player player) {
        if (player.level().isClientSide()) return;
        var curios = top.theillusivec4.curios.api.CuriosApi.getCuriosInventory(player);
        if (curios.isEmpty()) return;
        if (curios.get().findFirstCurio(s -> s.is(ModItems.HAND_FAN_SMALL_CHIP.get())).isEmpty()) return;

        AABB aabb = player.getBoundingBox().inflate(RANGE);
        for (LivingEntity entity : player.level().getEntitiesOfClass(LivingEntity.class, aabb,
                e -> e instanceof Enemy && e.isAlive())) {
            MarkManager.apply(entity);
        }
    }
}
