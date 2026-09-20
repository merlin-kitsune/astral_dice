package com.merlinkitsune.astral_dice.item.card;

import com.merlinkitsune.astral_dice.component.AppliedStone;
import com.merlinkitsune.astral_dice.component.ModDataComponents;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

public class CardItem extends Item {
    private final String cardType;

    public String getCardType() {
        return cardType;
    }

    public CardItem(Properties properties, String cardType) {
        super(properties);
        this.cardType = cardType;
    }

    // 未消耗耐久(满耐久)的战斗牌可堆叠 64 个;已消耗耐久后单独存放(单张)
    @Override
    public int getMaxStackSize(ItemStack stack) {
        int max = AppliedStone.defaultUses(cardType);
        int uses = ModDataComponents.CARD_USES.getOrDefault(stack, max);
        return uses >= max ? 64 : 1;
    }

    @Override
    public boolean isBarVisible(ItemStack stack) {
        int max = AppliedStone.defaultUses(cardType);
        int uses = ModDataComponents.CARD_USES.getOrDefault(stack, max);
        return uses > 0 && uses < max;
    }

    @Override
    public int getBarWidth(ItemStack stack) {
        int max = AppliedStone.defaultUses(cardType);
        int uses = ModDataComponents.CARD_USES.getOrDefault(stack, max);
        if (max <= 0) return 0;
        return Math.round(13.0f * uses / max);
    }

    @Override
    public int getBarColor(ItemStack stack) {
        float max = AppliedStone.defaultUses(cardType);
        float uses = ModDataComponents.CARD_USES.getOrDefault(stack, (int) max);
        float ratio = max > 0 ? uses / max : 0;
        if (ratio > 0.5f) {
            return 0x00FF00;
        } else if (ratio > 0.25f) {
            return 0xFFFF00;
        }
        return 0xFF0000;
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  临时牌(绿洲女王 nardis「女王特权」)的光效 / 两道物品级保护
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * 附魔光效:临时牌常亮以作区分(用户需求「临时牌全部添加附魔光效」)。
     *
     * <p>实现口径(**单层**,两线同法):覆写 {@code Item#isFoil(ItemStack)}
     * (1.20.1 实测 {@code Item.java:340})。
     *
     * <p>⚠️ 1.20.1 **没有** 1.21 的 per-stack 覆盖组件
     * ({@code DataComponents.ENCHANTMENT_GLINT_OVERRIDE});若塞真附魔会**改变物品名稀有度颜色**
     * 并多出附魔行({@code Item#getRarity} 受 {@code isEnchanted()} 影响)。{@code isFoil} 覆写
     * 在 1.20.1 天然 per-stack 且无副作用(用户已裁决接受单层强度;原版渲染不支持多层)。
     */
    @Override
    public boolean isFoil(ItemStack stack) {
        return super.isFoil(stack) || TemporaryCardUtil.isTemporary(stack);
    }

    /**
     * 不可丢弃:Q 键 / {@code ServerPlayer#drop(boolean)} 路径直接拒绝。
     *
     * <p>1.20.1 时序证据:丢弃路径在**移除物品之前**就判定
     * {@code selected.onDroppedByPlayer(this)}
     * (实测 {@code ServerPlayer.java:1726},早于 {@code :1728 removeFromSelected} 与
     * {@code :1732 ForgeHooks.onPlayerTossEvent}),返回 false 时直接 return
     * ⇒ 物品留在原地、不产生掉落物。
     *
     * <p>{@code ItemTossEvent} 侧另有一道兜底(见 {@code event/TemporaryCardEvents}):
     * 覆盖不经过 {@code ServerPlayer#drop(boolean)} 的其它抛出路径
     * (1.20.1 的 {@code Player#drop(ItemStack, boolean)} 直接委托
     * {@code ForgeHooks.onPlayerTossEvent},**不**调用本方法 ⇒ 该兜底是必需的,不是冗余)。
     */
    @Override
    public boolean onDroppedByPlayer(ItemStack stack, Player player) {
        if (TemporaryCardUtil.isTemporary(stack)) return false;
        return super.onDroppedByPlayer(stack, player);
    }

    // ⚠️ 1.20.1 **没有** stack-aware 的 {@code canFitInsideContainerItems(ItemStack)}
    //    (1.20.1 实测 {@code Item.java:452} 只有**类型级** {@code canFitInsideContainerItems()})。
    //    **禁止**覆写类型级版本:那会把同 id 的**永久牌**一并挡在潜影盒/收纳袋之外(功能不对等)。
    //    1.20.1 侧改由三个专有 mixin 按「栈」拦截(见 resources/astral_dice.mixins.json):
    //      · mixin/container/ShulkerBoxSlotGuardMixin        → 潜影盒 GUI 槽
    //      · mixin/container/ShulkerBoxBlockEntityGuardMixin → 潜影盒自动化(漏斗)面
    //      · mixin/container/BundleInsertGuardMixin          → 收纳袋(bundle)插入
    //    三者的判据都是 TemporaryCardUtil.isTemporary(stack),与 1.21.1 的栈级判定语义等价。
}