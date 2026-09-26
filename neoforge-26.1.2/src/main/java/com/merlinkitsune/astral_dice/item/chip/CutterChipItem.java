package com.merlinkitsune.astral_dice.item.chip;

import com.merlinkitsune.astral_dice.effect.ModEffects;
import net.minecraft.world.entity.player.Player;

/**
 * 美工刀-初级筹码:生命值不低于 60%(或处于「汲取」)时 —— **基础攻击力 +2**(计入攻击力),
 * 并按当前治愈层数提供**伤害加成**(走额外加伤,{@code astral_dice:extra_damage} 独立结算)。
 *
 * <p>2026-09-26 用户裁决(修订 2026-09-25 那次「整块进额外加伤」):「美工刀提供的基础攻击力(+2 和 +4)
 * 被错误的划分到了伤害加成。正确情况是 <b>基础值为攻击力加成,治愈点提供伤害加成</b>」⇒ 两条链拆分。
 * 具体注册见 {@link com.merlinkitsune.astral_dice.combat.DiceCombatModifiers} 的内置块。
 *
 * <p>本类同时持有美工刀家族共用的常量与门槛判定 —— 与 {@link CutterBladeChipItem} 必须
 * <b>同进同出</b>(门槛一变两者一起变),故不各自复制一份。
 */
public class CutterChipItem extends BaseChipItem {

    /** 生效门槛:生命值 ≥ 该比例。与 tooltip / 手册「生命值不低于 60%」同源。 */
    public static final float ACTIVE_HEALTH_RATIO = 0.6f;

    /** 美工刀-初级的基础攻击力加成。与 tooltip / 手册「攻击力 +2」同源。 */
    public static final int BASE_ATTACK = 2;

    public CutterChipItem(Properties properties) {
        super(properties);
    }

    /** 美工刀两条加成链共同的门槛:生命值 ≥ {@link #ACTIVE_HEALTH_RATIO},或处于「汲取」(papara_bite)状态。 */
    public static boolean isActive(Player player) {
        return player.getHealth() >= player.getMaxHealth() * ACTIVE_HEALTH_RATIO
                || player.hasEffect(ModEffects.PAPARA_BITE);
    }
}
