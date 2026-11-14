package com.merlinkitsune.astral_dice.item.chip;

import com.merlinkitsune.astral_dice.combat.TeamTargets;
import com.merlinkitsune.astral_dice.damage.ModDamageTypes;
import com.merlinkitsune.astral_dice.effect.ModEffects;
import com.merlinkitsune.astral_dice.item.CurioSlotUtil;
import com.merlinkitsune.astral_dice.item.ModItems;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/**
 * 石中剑-传说：自身骰神赐福结束后，对 24 格内所有敌对目标造成
 * 「自身全部状态层数 × 2」的魔法伤害，最低 4 点。
 *
 * <p>层数 = 每个 MobEffectInstance 的 amplifier + 1 之和；统计时排除刚刚结束的骰神赐福本身。
 */
public class SwordInStoneLegendaryChipItem extends BaseChipItem {
    public static final double RADIUS = 24.0;

    public SwordInStoneLegendaryChipItem(Properties properties) {
        super(properties);
    }

    public static boolean isEquipped(LivingEntity entity) {
        if (entity == null) return false;
        if (!CurioSlotUtil.hasDiceEquipped(entity)) return false;
        return CurioSlotUtil.hasAnyCurioMatching(entity, s -> s.is(ModItems.SWORD_IN_STONE_LEGENDARY_CHIP.get()));
    }

    public static void onBlessingEnd(Player player) {
        if (player.level().isClientSide()) return;
        if (!isEquipped(player)) return;
        if (player.isDeadOrDying()) return;
        if (!(player.level() instanceof ServerLevel serverLevel)) return;

        int layers = 0;
        for (MobEffectInstance instance : player.getActiveEffects()) {
            if (instance.getEffect().value() == ModEffects.DICE_BLESSING.value()) continue;
            layers += instance.getAmplifier() + 1;
        }
        int damage = Math.max(4, layers * 2);
        for (LivingEntity target : serverLevel.getEntitiesOfClass(
                LivingEntity.class,
                player.getBoundingBox().inflate(RADIUS),
                entity -> entity != player && entity.isAlive() && TeamTargets.isHostileTo(player, entity))) {
            target.hurt(ModDamageTypes.cardSpell(serverLevel, player), damage);
        }
    }
}
