package com.merlinkitsune.astral_dice.effect;

import com.merlinkitsune.astral_dice.event.ModEffectRemoval;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.player.Player;

/**
 * 反击(玩家效果/流派):层数 = amplifier + 1(HUD 图标显示层数)。
 * 拥有层数时受到敌对生物任何伤害 → 触发:消耗 1 层并把该伤害来源登记为「反噬目标」,此后该目标
 * 每次对玩家造成伤害都会受到一次返还伤害,直至目标死亡(触发/返还逻辑见
 * {@code DiceCombatEvents.onCounterattackTriggered})。
 * 层数获得来源后续补充;对外提供 {@link #addStacks} / {@link #getStacks} / {@link #consumeOne}。
 */
public class CounterattackEffect extends MobEffect {
    /** 效果时长(无限,层数消耗完移除) */
    public static final int DURATION_TICKS = Integer.MAX_VALUE;

    public CounterattackEffect() {
        super(MobEffectCategory.BENEFICIAL, 0xFF5252);
    }

    // 增加反击层数(层数 = amplifier+1;无效果时按 0 起始叠加)
    public static void addStacks(Player player, int stacks) {
        if (player == null || player.level().isClientSide()) return;
        if (stacks <= 0) return;
        int total = getStacks(player) + stacks;
        player.addEffect(new MobEffectInstance(ModEffects.COUNTERATTACK,
                DURATION_TICKS, total - 1, false, true, true));
    }

    // 当前反击层数(无效果为 0)
    public static int getStacks(Player player) {
        if (player == null) return 0;
        MobEffectInstance instance = player.getEffect(ModEffects.COUNTERATTACK);
        return instance != null ? instance.getAmplifier() + 1 : 0;
    }

    // 消耗 1 层:归 0 时移除效果
    public static void consumeOne(Player player) {
        if (player == null || player.level().isClientSide()) return;
        MobEffectInstance instance = player.getEffect(ModEffects.COUNTERATTACK);
        if (instance == null) return;
        int remaining = instance.getAmplifier();
        if (remaining <= 0) {
            ModEffectRemoval.remove(player, ModEffects.COUNTERATTACK);
        } else {
            player.addEffect(new MobEffectInstance(ModEffects.COUNTERATTACK,
                    DURATION_TICKS, remaining - 1, false, true, true));
        }
    }
}
