package com.merlinkitsune.astral_dice.effect;

import com.merlinkitsune.astral_dice.event.ModEffectRemoval;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.player.Player;

/**
 * 赋能(资源/效果):层数 = amplifier + 1。
 *
 * <p>由「原初核心」筹码转换而来(每消耗 1 层充能获得 1 层赋能);
 * 每层提供攻击力 +1 与防御力 +1(防御力经
 * {@link com.merlinkitsune.astral_dice.combat.DiceCombatModifiers#setDefenseArmorBonus}
 * 折算为真实护甲)。
 *
 * <p>计时器:每 0:30 减少 1 层,且层数为 1 时直接归 0(不再递减到 0 以下),见
 * {@link com.merlinkitsune.astral_dice.item.EmpowerManager#tick}。效果本身无限时长,
 * 层数归零时移除。
 */
public class EmpowerEffect extends MobEffect {
    /** 最大层数(与充能上限 CHARGE_MAX_STACKS 对齐) */
    public static final int MAX_STACKS = 20;
    /** 效果时长(无限,层数归零/清除时移除;不参与 EffectTimerGuard 计时) */
    public static final int DURATION_TICKS = Integer.MAX_VALUE;

    public EmpowerEffect() {
        super(MobEffectCategory.BENEFICIAL, 0x00E5FF);
    }

    /** 增加层数(上限 MAX_STACKS) */
    public static int addStacks(Player player, int stacks) {
        if (player == null || player.level().isClientSide()) return getStacks(player);
        if (stacks <= 0) return getStacks(player);
        int total = Math.min(MAX_STACKS, getStacks(player) + stacks);
        if (total > 0) {
            player.addEffect(new MobEffectInstance(ModEffects.EMPOWER,
                    DURATION_TICKS, total - 1, false, true, true));
        }
        return total;
    }

    /** 当前层数(无效果为 0) */
    public static int getStacks(Player player) {
        if (player == null) return 0;
        MobEffectInstance instance = player.getEffect(ModEffects.EMPOWER);
        return instance != null ? instance.getAmplifier() + 1 : 0;
    }

    /** 减少 1 层;归零时移除效果(层数为 1 时直接归 0) */
    public static void consumeOne(Player player) {
        if (player == null || player.level().isClientSide()) return;
        MobEffectInstance instance = player.getEffect(ModEffects.EMPOWER);
        if (instance == null) return;
        int remaining = instance.getAmplifier();
        if (remaining <= 0) {
            ModEffectRemoval.remove(player, ModEffects.EMPOWER);
        } else {
            player.addEffect(new MobEffectInstance(ModEffects.EMPOWER,
                    DURATION_TICKS, remaining - 1, false, true, true));
        }
    }

    /** 清空赋能 */
    public static void removeAll(Player player) {
        if (player == null || player.level().isClientSide()) return;
        ModEffectRemoval.remove(player, ModEffects.EMPOWER);
    }
}
