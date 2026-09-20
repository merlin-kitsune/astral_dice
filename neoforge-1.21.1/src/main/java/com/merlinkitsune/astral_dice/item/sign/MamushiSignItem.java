package com.merlinkitsune.astral_dice.item.sign;

/**
 * 蛟龙立牌(mamushi,传奇 UNCOMMON)。
 *
 * <p><b>技能(主动 / 被动)待用户裁决</b>:本批只落资产与注册(物品、贴图、配方、标签、图鉴 crafting 页),
 * 不含任何技能实现;技能定稿后在本类内补齐。
 *
 * <p>按 {@link BaseSignItem} 的契约,本类**未覆写** {@code handleUse} ⇒ 主动使用暂无任何效果
 * (默认实现打一条 WARN 并返回 {@code fail}),被动钩子亦未登记 ⇒ 该立牌当前不带任何战斗效果。
 */
public class MamushiSignItem extends BaseSignItem {

    public MamushiSignItem(Properties properties) {
        super(properties);
    }
}
