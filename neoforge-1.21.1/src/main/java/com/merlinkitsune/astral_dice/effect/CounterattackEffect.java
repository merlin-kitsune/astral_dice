package com.merlinkitsune.astral_dice.effect;

import com.merlinkitsune.astral_dice.event.ModEffectRemoval;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.player.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 反击(玩家效果/流派):层数 = amplifier + 1(HUD 图标显示层数)。
 * 拥有层数时受到敌对生物任何伤害 → 触发:消耗 1 层并把该伤害来源登记为「反噬目标」,此后该目标
 * 每次对玩家造成伤害都会受到一次返还伤害,直至目标死亡(触发/返还逻辑见
 * {@code DiceCombatEvents.onCounterattackTriggered})。返还伤害对总伤害计算七咒减益(含修正物),
 * 并可受「全力攻击」×1.5 等修正影响;对 Boss 生物无效(不触发、不登记)。
 * 层数获得来源后续补充;对外提供 {@link #addStacks} / {@link #getStacks} / {@link #consumeOne}。
 * 玩家死亡不清除层数:死亡时经 {@link #captureBeforeDeath} 记录,重生后经 {@link #restoreAfterRespawn} 恢复。
 */
public class CounterattackEffect extends MobEffect {
    /** 效果时长(无限,层数消耗完移除) */
    public static final int DURATION_TICKS = Integer.MAX_VALUE;

    /** 死亡待恢复层数(死亡时记录,重生后恢复) */
    private static final Map<UUID, Integer> DEATH_PENDING = new HashMap<>();

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

    // 死亡前记录当前层数(LivingDeathEvent 中调用;此时效果尚未被原版 removeAllEffects(DEATH) 清除)
    public static void captureBeforeDeath(Player player) {
        if (player == null || player.level().isClientSide()) return;
        int stacks = getStacks(player);
        if (stacks > 0) {
            DEATH_PENDING.put(player.getUUID(), stacks);
        } else {
            DEATH_PENDING.remove(player.getUUID());
        }
    }

    // 重生后恢复层数(PlayerRespawnEvent 中调用)
    public static void restoreAfterRespawn(Player player) {
        if (player == null || player.level().isClientSide()) return;
        Integer stacks = DEATH_PENDING.remove(player.getUUID());
        if (stacks != null && stacks > 0) {
            addStacks(player, stacks);
        }
    }
}
