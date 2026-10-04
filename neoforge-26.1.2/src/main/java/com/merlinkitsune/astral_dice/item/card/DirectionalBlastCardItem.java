package com.merlinkitsune.astral_dice.item.card;

import com.merlinkitsune.astral_dice.combat.DamageCardFxScheduler;
import com.merlinkitsune.starenginelib.target.TargetType;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * 定向爆破（伤害效果牌，金）：**目标选择器**类 —— 主手手持即自动进入选择会话（移出主手即退出），
 * 确认后对选中目标造成 <b>5</b> 点法术伤害。
 *
 * <h2>目标口径</h2>
 * {@link com.merlinkitsune.starenginelib.target.TargetType#CREATURE} —— 敌对生物 ∪ 中立生物（宠物除外）
 * ∪ 未驯服的可驯服生物，**不含玩家**（与「活体书页」同一口径，见 {@code combat/CreatureTargets}）。
 *
 * <h2>FX 与结算时点（2026-10-04 用户裁决）</h2>
 * 释放后**延迟 0.5 秒**在目标处爆开，**对选中目标及其 6 格范围内的其他目标同时造成伤害**。
 * ⚠️ 伤害**不在使用瞬间**落地，而是**延迟到 FX 命中时点**才结算（见 {@link DamageCardFxScheduler}）；
 * 伤害类型 = **法术伤害**（{@code astral_dice:card_spell}，与活体书页同一条法伤链），
 * 数值 = 固定基础值 <b>5</b> + 「效果牌伤害加成」（忍者立牌效果牌伤害增益 + 书签筹码）。
 *
 * <h2>音效</h2>
 * 释放音效 {@code effect_card_use1.wav}、命中音效 {@code blast.wav}（均在服务端由 {@link DamageCardFxScheduler} 播放）。
 *
 * <p>⚠️ 本牌**不再**提供「远程 / 魔法伤害 +N（1:00）」的增伤效果（2026-10-04 起该能力移除；
 * 修饰器骨架保留但已停用，见 {@code combat/SpellDamageRegistry}）。
 */
public class DirectionalBlastCardItem extends BaseEffectCardItem {

    /** 目标选择器动作 id（注册表键；也是 actionbar 技能名 {@code msg.astral_dice.target_select.skill.<id>} 的来源）。 */
    public static final String ACTION_ID = "directional_blast";

    static {
        // CREATURE = 效果牌口径的敌对目标（不含玩家），不可对自己使用
        registerSelectorAction(ACTION_ID, TargetType.CREATURE, false);
    }

    public DirectionalBlastCardItem(Properties properties) {
        super(properties);
    }

    @Override
    protected String cardTypeId() {
        return ACTION_ID;
    }

    @Override
    public String selectorActionId() {
        return ACTION_ID;
    }

    @Override
    protected void applyEffect(Level level, Player user, LivingEntity applyTo, ItemStack stack) {
        if (level instanceof ServerLevel serverLevel && user instanceof ServerPlayer caster
                && applyTo != null && applyTo != user) {
            DamageCardFxScheduler.launch(DamageCardFxScheduler.Kind.BLAST, serverLevel, caster, applyTo);
        }
    }
}
