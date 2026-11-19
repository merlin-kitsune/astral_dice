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
 * 1.20.1 的 Forge / Fabric 两线走 {@code starengine ItemDataKey}（NBT shim），
 * 1.21.1 与 26.1.2 走原版 {@code DataComponentType}。
 */
public final class DiceLoadoutAccess {

    private DiceLoadoutAccess() {
    }

    /** 读取骰子的卡牌栏组件；缺失返回 {@link WeaponEnhancement#EMPTY}。 */
    public static WeaponEnhancement enhancement(ItemStack dice) {
        return ModDataComponents.WEAPON_ENHANCEMENT.getOrDefault(dice, WeaponEnhancement.EMPTY);
    }

    /** 写入骰子的卡牌栏组件。 */
    public static void setEnhancement(ItemStack dice, WeaponEnhancement value) {
        ModDataComponents.WEAPON_ENHANCEMENT.set(dice, value);
    }

    /** 读卡牌剩余次数；未设置时用 {@code fallback}。 */
    public static int cardUses(ItemStack card, int fallback) {
        return ModDataComponents.CARD_USES.getOrDefault(card, fallback);
    }

    /** 写卡牌剩余次数。 */
    public static void setCardUses(ItemStack card, int uses) {
        ModDataComponents.CARD_USES.set(card, uses);
    }

    /** 让宿主把物品掉在原地。 */
    public static void drop(Entity holder, ItemStack stack) {
        holder.spawnAtLocation(stack);
    }
}
