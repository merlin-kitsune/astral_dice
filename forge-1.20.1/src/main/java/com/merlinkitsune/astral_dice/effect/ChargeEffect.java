package com.merlinkitsune.astral_dice.effect;

import com.merlinkitsune.starenginelib.component.GameplayConstants;
import com.merlinkitsune.starenginelib.event.ModEffectRemoval;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.player.Player;

/**
 * 充能(流派资源/效果):层数 = amplifier + 1。
 * 不论层数多少,只要拥有至少 1 层,就提供固定流派效果:
 * 立牌主动/效果牌冷却时间 -20%(无防御力/护甲加成)。
 *
 * <p><b>禁用粒子</b>:实例一律以 {@code visible=false} 构造(MobEffectInstance 第 5 参),
 * 故不产生原版药水粒子;图标与层数/倒计时显示由 {@code showIcon=true} 保留。已核实两版本
 * 原版源码:HUD 与物品栏效果面板的显示闸门是 {@code showIcon}(Forge/NeoForge 的
 * {@code isVisibleInGui} 默认 true),粒子闸门才是 {@code visible}。
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
            // visible=false:禁用粒子(showIcon 仍为 true → 图标/层数/倒计时照常显示)
            player.addEffect(new MobEffectInstance(ModEffects.CHARGE.get(),
                    DURATION_TICKS, total - 1, false, false, true));
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
     *
     * <p><b>必须先移除旧实例、再写入新层数</b>:原版 {@code MobEffectInstance#update} 只接受
     * <b>更高的 amplifier</b>,直接 {@code addEffect} 一个更低层数的实例会被忽略——低层实例只会被
     * 塞进 {@code hiddenEffect},可见层数永不下降(1.20.1 同一实现:{@code other.amplifier > this.amplifier}
     * 才赋值,降级只进 {@code hiddenEffect};{@code LivingEntity.addEffect} 仅在
     * {@code mobeffectinstance.update(...)} 返回 true 时生效)。
     * 此前这里没有先移除,导致充能实际从未被扣减(电磁炮/电流核心/高级外设的消耗全部失效)。
     */
    public static int consume(Player player, int amount) {
        if (player == null || player.level().isClientSide() || amount <= 0) return 0;
        MobEffectInstance instance = player.getEffect(ModEffects.CHARGE.get());
        if (instance == null) return 0;
        int current = instance.getAmplifier() + 1;
        int consumed = Math.min(current, amount);
        int remaining = current - consumed;
        ModEffectRemoval.remove(player, ModEffects.CHARGE.get());
        if (remaining > 0) {
            player.addEffect(new MobEffectInstance(ModEffects.CHARGE.get(),
                    DURATION_TICKS, remaining - 1, false, false, true));
        }
        return consumed;
    }

    /** 减少 1 层;归零时移除效果(同样必须先移除旧实例,见 {@link #consume}) */
    public static void consumeOne(Player player) {
        if (player == null || player.level().isClientSide()) return;
        MobEffectInstance instance = player.getEffect(ModEffects.CHARGE.get());
        if (instance == null) return;
        int remaining = instance.getAmplifier();
        ModEffectRemoval.remove(player, ModEffects.CHARGE.get());
        if (remaining > 0) {
            player.addEffect(new MobEffectInstance(ModEffects.CHARGE.get(),
                    DURATION_TICKS, remaining - 1, false, false, true));
        }
    }

    /** 清空充能 */
    public static void removeAll(Player player) {
        if (player == null || player.level().isClientSide()) return;
        ModEffectRemoval.remove(player, ModEffects.CHARGE.get());
    }
}
