package com.merlinkitsune.astral_dice.item.chip;

import com.merlinkitsune.astral_dice.combat.TeamTargets;
import com.merlinkitsune.astral_dice.effect.ModEffects;
import com.merlinkitsune.astral_dice.item.CurioSlotUtil;
import com.merlinkitsune.astral_dice.item.ModItems;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import top.theillusivec4.curios.api.CuriosApi;

/**
 * 石中剑-精良：触发骰神赐福时，使自身与队伍角色获得「圣剑守护」1:00
 * （攻击力 +2、受到伤害 -2）。
 */
public class HolySwordGuardChipItem extends BaseChipItem {
    public static final int DURATION_TICKS = 1200;
    public static final double ALLY_RANGE = 64.0;

    public HolySwordGuardChipItem(Properties properties) {
        super(properties);
    }

    public static boolean isEquipped(LivingEntity entity) {
        if (entity == null) return false;
        if (!CurioSlotUtil.hasDiceEquipped(entity)) return false;
        var curios = CuriosApi.getCuriosInventory(entity);
        return curios.isPresent() && curios.get().findFirstCurio(s -> s.is(ModItems.SWORD_IN_STONE_EPIC_CHIP.get())).isPresent();
    }

    public static void onBlessingStart(Player player) {
        if (player.level().isClientSide()) return;
        if (!isEquipped(player)) return;
        if (player.isDeadOrDying()) return;
        for (LivingEntity ally : TeamTargets.collectFriendlyLiving(player, ALLY_RANGE)) {
            if (ally == null || !ally.isAlive()) continue;
            ally.addEffect(new MobEffectInstance(ModEffects.HOLY_SWORD_GUARD, DURATION_TICKS, 0, false, true, true));
        }
    }
}
