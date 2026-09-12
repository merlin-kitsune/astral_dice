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
    /** 递减间隔:每 0:30 减少 1 层({@link com.merlinkitsune.astral_dice.item.EmpowerManager} 同源引用) */
    public static final int DECAY_INTERVAL_TICKS = 30 * 20;
    /**
     * 效果时长 = 递减间隔 + 20 tick 余量。
     *
     * <p><b>必须是有限值</b>:物品栏效果面板与其悬停提示显示的时长直接取自本值
     * (vanilla {@code MobEffectUtil.formatDuration}),原先的 {@code Integer.MAX_VALUE}
     * 会让面板显示一个天文数字、永远不倒数 —— 玩家实测「赋能没有倒计时」即此。
     * 取 620 tick 后,面板显示 0:31 → 0:01 的真实倒计时,归零前由
     * {@link com.merlinkitsune.astral_dice.item.EmpowerManager#tick} 减 1 层并重新起算。
     *
     * <p>留 20 tick 余量是必需的:服务端递减(600 tick)必须**先于**效果自然到期发生,
     * 否则实例先到期 → 层数直接归零,表现为「层数凭空消失」而不是递减 1 层。
     *
     * <p>不影响 {@code EffectTimerGuard}:守卫只管理经其 {@code apply} 登记的效果,
     * 且 {@code INFINITE_THRESHOLD} 以上的旧值本就被跳过;本效果不经守卫登记。
 *
 * <p><b>禁用粒子</b>:实例一律以 {@code visible=false} 构造(第 5 参),不产生原版药水粒子;
 * 面板图标与倒计时由 {@code showIcon=true} 保留(原版显示闸门是 showIcon,粒子闸门才是 visible)。
     */
    public static final int DURATION_TICKS = DECAY_INTERVAL_TICKS + 20;

    public EmpowerEffect() {
        super(MobEffectCategory.BENEFICIAL, 0x00E5FF);
    }

    /** 增加层数(上限 MAX_STACKS) */
    public static int addStacks(Player player, int stacks) {
        if (player == null || player.level().isClientSide()) return getStacks(player);
        if (stacks <= 0) return getStacks(player);
        int total = Math.min(MAX_STACKS, getStacks(player) + stacks);
        if (total > 0) {
            // visible=false:禁用粒子(showIcon 仍为 true → 图标/层数/倒计时照常显示)
            player.addEffect(new MobEffectInstance(ModEffects.EMPOWER.get(),
                    DURATION_TICKS, total - 1, false, false, true));
        }
        return total;
    }

    /** 当前层数(无效果为 0) */
    public static int getStacks(Player player) {
        if (player == null) return 0;
        MobEffectInstance instance = player.getEffect(ModEffects.EMPOWER.get());
        return instance != null ? instance.getAmplifier() + 1 : 0;
    }

    /** 减少 1 层;归零时移除效果(层数为 1 时直接归 0) */
    public static void consumeOne(Player player) {
        if (player == null || player.level().isClientSide()) return;
        MobEffectInstance instance = player.getEffect(ModEffects.EMPOWER.get());
        if (instance == null) return;
        int remaining = instance.getAmplifier();
        // 原版 MobEffectInstance#update 只接受更高的 amplifier,直接 addEffect 降级会被忽略
        // (低等级实例被塞进 hiddenEffect,层数永远不变)。故必须"先移除旧实例,再写入新层数"。
        ModEffectRemoval.remove(player, ModEffects.EMPOWER.get());
        if (remaining > 0) {
            player.addEffect(new MobEffectInstance(ModEffects.EMPOWER.get(),
                    DURATION_TICKS, remaining - 1, false, false, true));
        }
    }

    /** 清空赋能 */
    public static void removeAll(Player player) {
        if (player == null || player.level().isClientSide()) return;
        ModEffectRemoval.remove(player, ModEffects.EMPOWER.get());
    }

    /**
     * 以当前层数重写效果实例(不改层数),用于刷新时长:计时器缺失/被外部改动时,
     * 让「面板显示的倒计时」与「服务端 empowerDecayAt」重新对齐,避免倒计时与实际递减脱节。
     */
    public static void refresh(Player player) {
        if (player == null || player.level().isClientSide()) return;
        int stacks = getStacks(player);
        if (stacks <= 0) return;
        ModEffectRemoval.remove(player, ModEffects.EMPOWER.get());
        player.addEffect(new MobEffectInstance(ModEffects.EMPOWER.get(),
                DURATION_TICKS, stacks - 1, false, false, true));
    }
}
