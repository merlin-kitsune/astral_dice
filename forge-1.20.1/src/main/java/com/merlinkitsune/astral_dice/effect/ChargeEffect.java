package com.merlinkitsune.astral_dice.effect;

import com.merlinkitsune.astral_dice.component.GameplayConstants;
import com.merlinkitsune.astral_dice.event.ModEffectRemoval;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;

/**
 * 充能(流派资源/效果):层数 = amplifier + 1。
 * 不论层数多少,只要拥有至少 1 层,就提供固定流派效果:
 * 护甲值 +10%(按最终护甲值计算)、立牌主动/效果牌冷却时间 -20%。
 */
public class ChargeEffect extends MobEffect {
    /** 效果时长(无限,清空/层数归零时移除) */
    public static final int DURATION_TICKS = Integer.MAX_VALUE;

    public ChargeEffect() {
        super(MobEffectCategory.BENEFICIAL, 0xFFC800);
        // 最终护甲值按原版属性公式计算:基础+固定加成后再按该倍率整体提升
        this.addAttributeModifier(Attributes.ARMOR, "e47c9e3a-2c64-4a8c-8dc0-44e85dca71d9",
                GameplayConstants.CHARGE_ARMOR_MULTIPLIER,
                AttributeModifier.Operation.MULTIPLY_TOTAL);
    }

    /** 增加层数(上限见 GameplayConstants.CHARGE_MAX_STACKS) */
    public static int addStacks(Player player, int stacks) {
        if (player == null || player.level().isClientSide()) return getStacks(player);
        if (stacks <= 0) return getStacks(player);
        int total = Math.min(GameplayConstants.CHARGE_MAX_STACKS, getStacks(player) + stacks);
        if (total > 0) {
            player.addEffect(new MobEffectInstance(ModEffects.CHARGE.get(),
                    DURATION_TICKS, total - 1, false, true, true));
        }
        return total;
    }

    /** 当前层数(无效果为 0) */
    public static int getStacks(Player player) {
        if (player == null) return 0;
        MobEffectInstance instance = player.getEffect(ModEffects.CHARGE.get());
        return instance != null ? instance.getAmplifier() + 1 : 0;
    }

    /** 减少 1 层;归零时移除效果 */
    public static void consumeOne(Player player) {
        if (player == null || player.level().isClientSide()) return;
        MobEffectInstance instance = player.getEffect(ModEffects.CHARGE.get());
        if (instance == null) return;
        int remaining = instance.getAmplifier();
        if (remaining <= 0) {
            ModEffectRemoval.remove(player, ModEffects.CHARGE.get());
        } else {
            player.addEffect(new MobEffectInstance(ModEffects.CHARGE.get(),
                    DURATION_TICKS, remaining - 1, false, true, true));
        }
    }

    /** 清空充能 */
    public static void removeAll(Player player) {
        if (player == null || player.level().isClientSide()) return;
        ModEffectRemoval.remove(player, ModEffects.CHARGE.get());
    }
}
