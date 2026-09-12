package com.merlinkitsune.astral_dice.effect;

import com.merlinkitsune.astral_dice.component.GameplayConstants;
import com.merlinkitsune.astral_dice.event.ModEffectRemoval;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.player.Player;

/**
 * 充能(流派资源/效果):层数 = amplifier + 1。
 * 不论层数多少,只要拥有至少 1 层,就提供固定流派效果:
 * 立牌主动/效果牌冷却时间 -20%(无防御力/护甲加成)。
 */
public class ChargeEffect extends MobEffect {
    /** 效果时长(无限,清空/层数归零时移除) */
    public static final int DURATION_TICKS = Integer.MAX_VALUE;

    public ChargeEffect() {
        super(MobEffectCategory.BENEFICIAL, 0xFFC800);
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

    /**
     * 一次性减少指定层数(单次结算,避免逐层重设效果产生的多余同步包);
     * 返回实际消耗的层数(不足时按剩余量计算)。
     */
    public static int consume(Player player, int amount) {
        if (player == null || player.level().isClientSide() || amount <= 0) return 0;
        MobEffectInstance instance = player.getEffect(ModEffects.CHARGE.get());
        if (instance == null) return 0;
        int current = instance.getAmplifier() + 1;
        int consumed = Math.min(current, amount);
        int remaining = current - consumed;
        if (remaining <= 0) {
            ModEffectRemoval.remove(player, ModEffects.CHARGE.get());
        } else {
            player.addEffect(new MobEffectInstance(ModEffects.CHARGE.get(),
                    DURATION_TICKS, remaining - 1, false, true, true));
        }
        return consumed;
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
