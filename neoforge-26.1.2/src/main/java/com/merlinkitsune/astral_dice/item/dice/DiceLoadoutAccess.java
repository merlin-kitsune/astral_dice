package com.merlinkitsune.astral_dice.item.dice;

import com.merlinkitsune.astral_dice.component.ModDataComponents;
import com.merlinkitsune.astral_dice.component.WeaponEnhancement;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;

/**
 * 骰子卡牌栏的**跨版本访问垫片**：把「数据组件 API」与「掉落签名」两处版本差异收敛在这里，
 * 好让 {@code common} 侧的 {@code item/card/DiceCardLoadout} 保持一份。
 *
 * <p>本文件五线各一份，方法语义必须完全一致；改语义请五份一起改。
 */
public final class DiceLoadoutAccess {

    private DiceLoadoutAccess() {
    }

    /** 读取骰子的卡牌栏组件；缺失返回 {@link WeaponEnhancement#EMPTY}。 */
    public static WeaponEnhancement enhancement(ItemStack dice) {
        return dice.getOrDefault(ModDataComponents.WEAPON_ENHANCEMENT.get(), WeaponEnhancement.EMPTY);
    }

    /** 写入骰子的卡牌栏组件。 */
    public static void setEnhancement(ItemStack dice, WeaponEnhancement value) {
        dice.set(ModDataComponents.WEAPON_ENHANCEMENT.get(), value);
    }

    /** 读卡牌剩余次数；未设置时用 {@code fallback}。 */
    public static int cardUses(ItemStack card, int fallback) {
        return card.getOrDefault(ModDataComponents.CARD_USES.get(), fallback);
    }

    /** 写卡牌剩余次数。 */
    public static void setCardUses(ItemStack card, int uses) {
        card.set(ModDataComponents.CARD_USES.get(), uses);
    }

    /** 让宿主把物品掉在原地。 */
    public static void drop(Entity holder, ItemStack stack) {
        if (holder.level() instanceof net.minecraft.server.level.ServerLevel level) {
            holder.spawnAtLocation(level, stack);
        }
    }
}
