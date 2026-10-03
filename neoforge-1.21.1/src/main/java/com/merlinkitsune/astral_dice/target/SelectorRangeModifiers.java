package com.merlinkitsune.astral_dice.target;

import com.merlinkitsune.astral_dice.effect.ModEffects;
import com.merlinkitsune.astral_dice.effect.RinPageRangeEffect;
import com.merlinkitsune.astral_dice.item.card.BaseEffectCardItem;
import com.merlinkitsune.astral_dice.item.card.LivingPageItem;
import com.merlinkitsune.astral_dice.item.chip.SatelliteChipItem;
import net.minecraft.world.entity.player.Player;

/**
 * 目标选择会话**锁定范围**的玩家侧加成(2026-10-03 用户裁决)。
 *
 * <p>两处来源、**同一项加成**(「选择距离/+%」)⇒ 按**相加**合并,再按 {@link #MAX_ENHANCED_RADIUS}
 * 夹取(用户裁决口径):
 * <ul>
 *   <li><b>探天卫星筹码</b>({@link SatelliteChipItem#EFFECT_CARD_RANGE_BONUS};**佩戴即生效**的常驻
 *       +50%):只作用于**效果牌**动作({@link BaseEffectCardItem#isEffectCardAction(String)} 判定,
 *       立牌动作不受影响);</li>
 *   <li><b>「书页射程」状态</b>({@link RinPageRangeEffect#RANGE_BONUS};调查员立牌 rin 主动追加的
 *       2:00 状态 +50%):只作用于**活体书页**({@link LivingPageItem#isLivingPageAction(String)})。</li>
 * </ul>
 * 两者可同时持有(佩戴卫星 + 刚放完 rin 主动)⇒ 活体书页 32 格可到 32 × 2.0 = 64 格。
 *
 * <p><b>为什么放在这里而不是动作的 {@code radius()}</b>:前置库 {@code TargetSelectionAction#radius()}
 * 无玩家参数(契约固定),而这两个加成都是**按玩家**的 ⇒ 只能在会话开局处
 * ({@link TargetSelectionManager#start})统一注入。
 *
 * <p><b>与 {@code MAX_SELECT_RADIUS}(32) 的两段夹取</b>:动作**声明值**仍按契约上限 32 夹取
 * (活体书页声明 32),**加成后**的结果再按 {@link #MAX_ENHANCED_RADIUS}(64)夹取 ⇒
 * 「声明值不放宽、加成可越界」互不干扰。客户端射线/半径高亮/服务端确认读的都是**同一个夹取后的
 * 会话半径**(随 {@code TargetSelectStartPayload} 下发),故三处天然同源。
 */
public final class SelectorRangeModifiers {
    /** 加成后(会话最终)半径的硬上限(格):32 × (1 + 0.5 + 0.5) = 64 */
    public static final double MAX_ENHANCED_RADIUS = 64.0D;

    private SelectorRangeModifiers() {
    }

    /**
     * 对动作声明(或配置缺省)的半径施加玩家侧加成;无加成时原样返回。
     *
     * @param player     选择者(服务端)
     * @param actionId   本次动作 id
     * @param baseRadius 已按契约上限 32 夹取过的声明半径
     * @return 加成并夹取后的会话半径(客户端与服务端共用)
     */
    public static double apply(Player player, String actionId, double baseRadius) {
        if (player == null || actionId == null || baseRadius <= 0.0D) return baseRadius;
        double bonus = 0.0D;
        if (BaseEffectCardItem.isEffectCardAction(actionId) && SatelliteChipItem.isEquipped(player)) {
            bonus += SatelliteChipItem.EFFECT_CARD_RANGE_BONUS;
        }
        if (LivingPageItem.isLivingPageAction(actionId) && player.hasEffect(ModEffects.RIN_PAGE_RANGE)) {
            bonus += RinPageRangeEffect.RANGE_BONUS;
        }
        if (bonus <= 0.0D) return baseRadius;
        return Math.min(baseRadius * (1.0D + bonus), MAX_ENHANCED_RADIUS);
    }
}
