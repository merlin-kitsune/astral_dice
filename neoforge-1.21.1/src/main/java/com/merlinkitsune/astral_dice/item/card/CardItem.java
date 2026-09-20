package com.merlinkitsune.astral_dice.item.card;

import com.merlinkitsune.astral_dice.component.AppliedStone;
import com.merlinkitsune.astral_dice.component.ModDataComponents;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

public class CardItem extends Item {
    private final String cardType;

    public CardItem(Properties properties, String cardType) {
        super(properties);
        this.cardType = cardType;
    }

    // 未消耗耐久(满耐久)的战斗牌可堆叠 64 个;已消耗耐久后单独存放(单张)
    @Override
    public int getMaxStackSize(ItemStack stack) {
        int max = AppliedStone.defaultUses(cardType);
        int uses = stack.getOrDefault(ModDataComponents.CARD_USES.get(), max);
        return uses >= max ? 64 : 1;
    }

    @Override
    public boolean isBarVisible(ItemStack stack) {
        int max = AppliedStone.defaultUses(cardType);
        int uses = stack.getOrDefault(ModDataComponents.CARD_USES.get(), max);
        return uses > 0 && uses < max;
    }

    @Override
    public int getBarWidth(ItemStack stack) {
        int max = AppliedStone.defaultUses(cardType);
        int uses = stack.getOrDefault(ModDataComponents.CARD_USES.get(), max);
        if (max <= 0) return 0;
        return Math.round(13.0f * uses / max);
    }

    @Override
    public int getBarColor(ItemStack stack) {
        float max = AppliedStone.defaultUses(cardType);
        float uses = stack.getOrDefault(ModDataComponents.CARD_USES.get(), (int) max);
        float ratio = max > 0 ? uses / max : 0;
        if (ratio > 0.5f) {
            return 0x00FF00;
        } else if (ratio > 0.25f) {
            return 0xFFFF00;
        }
        return 0xFF0000;
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  临时牌(绿洲女王 nardis「女王特权」)的光效 / 三道保护
    //  ⚠️ 本类与效果牌根类 {@link BaseEffectCardItem} **各覆写同一对方法**,
    //     覆写体一律只调用 {@link TemporaryCardUtil#glint} / {@link TemporaryCardUtil#fitsInsideContainer},
    //     判据不得写在覆写里(两处会漂移)。
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * 附魔光效:临时牌常亮以作区分(用户需求「临时牌全部添加附魔光效」)。
     *
     * <p>实现口径(**单层**,两线同法):覆写 {@code isFoil(ItemStack)} 而不是写
     * {@code DataComponents.ENCHANTMENT_GLINT_OVERRIDE} —— 后者会往物品写入额外组件;
     * 且原版渲染只支持单层强度(没有「多层叠加」的渲染路径,用户已裁决接受单层)。
     */
    @Override
    public boolean isFoil(ItemStack stack) {
        return TemporaryCardUtil.glint(stack, super.isFoil(stack));
    }

    /**
     * 不可丢弃:Q 键 / {@code ServerPlayer#drop(boolean)} 路径直接拒绝。
     *
     * <p>1.21.1 时序证据:丢弃路径在移除物品**之前**就判定
     * {@code selected.onDroppedByPlayer(this)}(实测 {@code ServerPlayer.java:2053}),返回 false 时
     * 直接 return ⇒ 物品留在原地、不产生掉落物。
     *
     * <p>{@code ItemTossEvent} 侧另有一道兜底(见 {@code event/TemporaryCardEvents}):
     * 覆盖不经过 {@code drop(boolean)} 的其它抛出路径。
     */
    @Override
    public boolean onDroppedByPlayer(ItemStack stack, Player player) {
        if (TemporaryCardUtil.isTemporary(stack)) return false;
        return super.onDroppedByPlayer(stack, player);
    }

    /**
     * 不可放进「物品内的容器」(潜影盒 / 收纳袋等 stack-aware 容器)。
     *
     * <p>1.21.1 的调用点实测包含 {@code ShulkerBoxBlockEntity}、{@code BundleItem}、
     * {@code BundleContents}、{@code ShulkerBoxSlot}、{@code ComponentItemHandler},
     * 一律传**物品栈**;返回 false 时这些容器会拒绝收下临时牌 —— 这**一整类**入口
     * (GUI 槽 + 自动化面 + 收纳袋 + 组件容器)都由本覆写按**栈**拦住,与物品类别无关。
     * 效果牌根类 {@link BaseEffectCardItem} 有**同一份**覆写
     * (1.20.1 没有 stack-aware 钩子,那条线由槽位 mixin 覆盖)。
     */
    @Override
    public boolean canFitInsideContainerItems(ItemStack stack) {
        return TemporaryCardUtil.fitsInsideContainer(stack, super.canFitInsideContainerItems(stack));
    }
}