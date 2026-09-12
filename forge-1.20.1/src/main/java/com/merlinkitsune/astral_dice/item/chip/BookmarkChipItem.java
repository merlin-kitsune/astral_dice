package com.merlinkitsune.astral_dice.item.chip;

import com.merlinkitsune.starenginelib.item.CuriosCompat;
import com.merlinkitsune.astral_dice.item.ModItems;
import net.minecraft.world.entity.player.Player;

/**
 * 书签筹码:使伤害效果牌伤害加成 +{@link #DAMAGE_BONUS}。
 *
 * <p>该加成与忍者立牌「效果牌伤害增益」计数叠加,统一经
 * {@link com.merlinkitsune.astral_dice.combat.SpellDamageRegistry#effectCardDamageBonus} 计入
 * 激光/板砖/轨道炮/定向爆破/活体书页的法伤,并用于 tooltip 显示,保证计算与显示一致。
 */
public class BookmarkChipItem extends BaseChipItem {
    /** 伤害效果牌伤害加成(固定 +1) */
    public static final int DAMAGE_BONUS = 1;

    public BookmarkChipItem(Properties properties) {
        super(properties);
    }

    // 玩家是否佩戴本筹码
    public static boolean isEquipped(Player player) {
        if (player == null) return false;
        var curios = CuriosCompat.getCuriosInventory(player);
        return curios.isPresent() && curios.get().findFirstCurio(s -> s.is(ModItems.BOOKMARK_CHIP.get())).isPresent();
    }

    /** 装备时提供 {@link #DAMAGE_BONUS},未装备时为 0。 */
    public static int damageBonus(Player player) {
        return isEquipped(player) ? DAMAGE_BONUS : 0;
    }
}
