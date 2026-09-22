package com.merlinkitsune.astral_dice.effect;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.player.Player;

/**
 * 「人偶完成」:人偶师立牌(hanna)被动「幻想千金」的资源进入**完成态**后的常驻状态载体(1.20.1 移植版)。
 *
 * <p>触发链(用户 2026-09-21 技能原文):「人偶制作」达到 {@code HannaSignItem.MAX_CRAFT} = 7 层 ⇒
 * **层数归零**并转换为本状态(用户裁决:「归零并转为『人偶完成』状态」);
 * 此后路过友方玩家时,**额外**使该玩家获得 迅捷 II (1:00) 与 3 星币。
 *
 * <p>状态本身无时长(完成态不可逆),本效果**无限时长**且**不登记 {@code EffectTimerGuard}**
 * (照 teru 狐光/降神、mamushi 真龙形态、zhao 白泽赐福的口径);卸下立牌时由
 * {@code HannaSignItem#clearSignData} 一并移除。
 *
 * <p>图标 = {@code images/人偶完成.png}(实装路径 {@code textures/mob_effect/hanna_doll_complete.png})。
 */
public class HannaDollCompleteEffect extends MobEffect {
    /** 效果时长(无限;卸下立牌时移除) */
    public static final int DURATION_TICKS = Integer.MAX_VALUE;

    public HannaDollCompleteEffect() {
        super(MobEffectCategory.BENEFICIAL, 0xE8C547);
    }

    /** 是否已进入「人偶完成」状态(被动额外奖励的唯一判据) */
    public static boolean has(Player player) {
        return player != null && player.hasEffect(ModEffects.HANNA_DOLL_COMPLETE.get());
    }
}
