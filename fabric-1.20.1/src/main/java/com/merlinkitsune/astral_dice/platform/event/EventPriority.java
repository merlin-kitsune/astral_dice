package com.merlinkitsune.astral_dice.platform.event;

/**
 * 监听器优先级(与 Forge 1.20.1 同名同序)。
 *
 * <p>⚠️ **排序是承重语义**,不是装饰:{@code HIGHEST} 最先执行、{@code LOWEST} 最后执行。
 * 本模组的伤害链明确依赖它 —— 例:{@code ChipDamageHandler.onLivingDamage} 用 {@code LOWEST}
 * 保证拿到「护甲/药水/吸收全部结算后的最终伤害」;{@code onLivingDeath} 用 {@code HIGHEST}
 * 抢在其它保命逻辑(末影骰子的伪图腾)之前接管。
 */
public enum EventPriority {
    HIGHEST,
    HIGH,
    NORMAL,
    LOW,
    LOWEST
}
