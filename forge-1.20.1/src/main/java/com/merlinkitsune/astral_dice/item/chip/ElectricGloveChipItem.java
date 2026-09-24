package com.merlinkitsune.astral_dice.item.chip;

import com.merlinkitsune.starenginelib.item.CuriosCompat;
import com.merlinkitsune.astral_dice.component.ModAttachments;
import com.merlinkitsune.astral_dice.item.ChargeManager;
import com.merlinkitsune.astral_dice.item.ModItems;
import com.merlinkitsune.astral_dice.item.card.BaseEffectCardItem;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * 电击手套筹码:
 * <ul>
 *   <li>使用效果牌时,充能层数 +1;</li>
 *   <li>充能层数不少于 4 层时,使用伤害效果牌会消耗 4 层充能,并武装本周期的法伤扩散:
 *       下一次远程/魔法伤害会同时对目标 3 格范围内的其他敌对目标造成同样伤害
 *       (每个效果牌持续周期内仅触发一次,周期结束后重新武装)。</li>
 * </ul>
 *
 * 充能与武装状态见 {@link ChargeManager} 与
 * {@link ModAttachments#isElectricGloveAoe} / {@link ModAttachments#setElectricGloveAoe}。
 */
public class ElectricGloveChipItem extends BaseChipItem {
    /** 武装法伤扩散所需的最低充能层数 */
    public static final int CHARGE_REQUIRED = 4;
    /** 使用效果牌获得的充能层数 */
    public static final int CHARGE_GAIN_PER_CARD = 1;
    /** 法伤扩散的额外命中半径(格) */
    public static final int AOE_RADIUS = 3;

    public ElectricGloveChipItem(Properties properties) {
        super(properties);
    }

    /** 玩家是否佩戴电击手套 */
    public static boolean isEquipped(Player player) {
        if (player == null) return false;
        var curios = CuriosCompat.getCuriosInventory(player);
        return curios.isPresent()
                && curios.get().findFirstCurio(s -> s.is(ModItems.ELECTRIC_GLOVE_CHIP.get())).isPresent();
    }

    /**
     * 该效果牌是否属于「伤害效果牌」——委托 {@link BaseEffectCardItem#isDamageEffectCard}
     * (单一事实源:魔法箭袋的「第一张效果牌」追踪用同一判定)。
     */
    public static boolean isDamageEffectCard(ItemStack cardStack) {
        return BaseEffectCardItem.isDamageEffectCard(cardStack);
    }

    /**
     * 使用效果牌时调用(所有效果牌):
     * 充能 +1;若为伤害效果牌且充能不少于 {@link #CHARGE_REQUIRED} 层,
     * 消耗 4 层充能并武装本周期的法伤扩散(每周期最多武装一次)。
     */
    public static void onEffectCardUsed(Player player, ItemStack cardStack) {
        if (player == null || player.level().isClientSide()) return;
        if (!isEquipped(player)) return;

        ChargeManager.addStacks(player, CHARGE_GAIN_PER_CARD);

        if (!isDamageEffectCard(cardStack)) return;
        // 每个效果牌持续周期内仅触发一次:已武装则不再重复消耗充能
        if (ModAttachments.isElectricGloveAoe(player)) return;
        if (ChargeManager.getStacks(player) < CHARGE_REQUIRED) return;

        ChargeManager.consume(player, CHARGE_REQUIRED);
        ModAttachments.setElectricGloveAoe(player, true);
    }

    /** 本周期是否已武装法伤扩散(供法伤修饰器判定) */
    public static boolean isAoeArmed(Player player) {
        return isEquipped(player) && ModAttachments.isElectricGloveAoe(player);
    }

    /** 消耗/结束本次扩散(触发后或周期结束时调用) */
    public static void disarmAoe(Player player) {
        ModAttachments.setElectricGloveAoe(player, false);
    }
}
