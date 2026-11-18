package com.merlinkitsune.astral_dice.api.data;

/**
 * 骰子卡牌栏里的一张**已装配**卡牌（只读快照）。
 *
 * <p>为什么不是物品栈：装配会**销毁**卡牌物品栈，卡牌栏的真值是骰子上的
 * {@code component.WeaponEnhancement#appliedStones()}。因此对外只暴露这份数据，
 * 想拿回物品请调用 {@code AstralDiceApi.unequipAllCards(...)}。
 *
 * @param type      卡牌类型 id（见 {@code combat.CardRegistry}，如 {@code medium} / {@code defense_medium}）
 * @param uses      剩余耐久
 * @param temporary 是否为「临时牌」（绿洲女王 nardis 一类，来源在 {@code AppliedStone} 里）
 * @param owner     临时牌获得者 UUID 字符串；空串 = 无主 / 非临时牌
 * @param defense   是否为防御牌（装配时按攻/防各自计费与占位）
 */
public record InstalledCard(String type, int uses, boolean temporary, String owner, boolean defense) {
}
