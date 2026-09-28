package com.merlinkitsune.astral_dice.item.chip;

import com.merlinkitsune.astral_dice.item.ChargeManager;
import com.merlinkitsune.starenginelib.item.CuriosCompat;
import com.merlinkitsune.astral_dice.item.ModItems;
import net.minecraft.world.entity.player.Player;

/**
 * 高级外设筹码:
 * <ul>
 *   <li>充能层数 ≥ 4 时,攻击力 +4(经 DiceCombatModifiers 攻击修饰器结算);</li>
 *   <li>每次触发骰神赐福时,**消耗 1 层充能**(经 {@link ChargeManager#consume},不是直接改层数):
 *       因此佩戴「原初核心」时该消耗同样计入赋能返还(每消耗 1 层充能 → 1 层赋能)。</li>
 * </ul>
 */
public class AdvancedPeripheralsChipItem extends BaseChipItem {
    /** 攻击力加成所需的最低充能层数 */
    public static final int CHARGE_REQUIRED = 4;
    /** 满足条件时提供的攻击力 */
    public static final int ATTACK_BONUS = 4;
    /** 每次触发骰神赐福消耗的充能层数 */
    public static final int BLESSING_CONSUME = 1;

    public AdvancedPeripheralsChipItem(Properties properties) {
        super(properties);
    }

    public static boolean isEquipped(Player player) {
        if (player == null) return false;
        var curios = CuriosCompat.getCuriosInventory(player);
        return curios.isPresent() && curios.get().findFirstCurio(s -> s.is(ModItems.ADVANCED_PERIPHERALS.get())).isPresent();
    }

    /** 当前充能是否满足攻击力加成条件;满足则返回 +4 */
    public static int getAttackBonus(Player player) {
        if (!isEquipped(player)) return 0;
        return ChargeManager.getStacks(player) >= CHARGE_REQUIRED ? ATTACK_BONUS : 0;
    }

    /**
     * 触发骰神赐福时调用:**消耗** {@link #BLESSING_CONSUME} 层充能。
     *
     * <p>必须走 {@link ChargeManager#consume}(而不是直接读写 {@code ChargeEffect} 的层数):
     * 该入口在真正扣掉层数后会挂钩 {@code EmpowerManager#onChargeConsumed}——
     * 这正是「原初核心」"每消耗 1 层充能获得 1 层赋能"的唯一来源。充能不足时
     * {@code consume} 返回 0,不会凭空产生赋能。
     */
    public static void onBlessingStart(Player player) {
        if (player == null || player.level().isClientSide()) return;
        if (!isEquipped(player)) return;
        ChargeManager.consume(player, BLESSING_CONSUME);
    }
}
