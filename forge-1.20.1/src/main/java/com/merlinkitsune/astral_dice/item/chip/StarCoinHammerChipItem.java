package com.merlinkitsune.astral_dice.item.chip;
import com.merlinkitsune.astral_dice.item.CuriosCompat;

import com.merlinkitsune.astral_dice.component.ModAttachments;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.SlotContext;
import com.merlinkitsune.astral_dice.item.ModItems;
import com.merlinkitsune.astral_dice.combat.DiceCombatModifiers;
import com.merlinkitsune.astral_dice.item.StarLightManager;

/**
 * 星币锤筹码:装备时获得 5 点星光(一次性);
 * 若物品栏中持有超过 20 枚星币,则每次进入骰神赐福时消耗 3 星币,并按持有星币总数的 30% 提升攻击力
 * (星币袋按 9 星币算;加成持续整个赐福,赐福结束清除,结算在 DiceCombatModifiers 攻击修饰器)。
 */
public class StarCoinHammerChipItem extends BaseChipItem {
    /** 触发门槛:持有星币须超过该数量 */
    public static final int THRESHOLD_COINS = 20;
    /** 每次进入赐福消耗的星币数 */
    public static final int CONSUME_COINS = 3;
    /** 攻击力提升比例(持有星币总数的 30%) */
    public static final double ATTACK_RATIO = 0.30;
    /** 星币袋折算星币数 */
    public static final int COINS_PER_BAG = 9;

    public StarCoinHammerChipItem(Properties properties) {
        super(properties);
    }

    // 玩家是否佩戴星币锤筹码
    public static boolean isEquipped(Player player) {
        if (player == null) return false;
        var curios = CuriosCompat.getCuriosInventory(player);
        return curios.isPresent() && curios.get().findFirstCurio(s -> s.is(ModItems.STAR_COIN_HAMMER.get())).isPresent();
    }

    // 物品栏持有的星币总数(星币袋按 9 星币算)
    public static int countStarCoins(Player player) {
        int total = 0;
        var inv = player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (s.isEmpty()) continue;
            if (s.is(ModItems.STAR_COIN.get())) {
                total += s.getCount();
            } else if (s.is(ModItems.STAR_COIN_BAG.get())) {
                total += s.getCount() * COINS_PER_BAG;
            }
        }
        return total;
    }

    // 消耗 N 枚星币(优先扣除零散星币,不足时扣除星币袋,1 袋按 9 星币算),返回是否足够扣除
    public static boolean consumeStarCoins(Player player, int amount) {
        int remaining = amount;
        var inv = player.getInventory();
        // 先扣零散星币
        for (int i = 0; i < inv.getContainerSize() && remaining > 0; i++) {
            ItemStack s = inv.getItem(i);
            if (s.is(ModItems.STAR_COIN.get())) {
                int take = Math.min(s.getCount(), remaining);
                s.shrink(take);
                remaining -= take;
            }
        }
        // 不足时扣星币袋(1 袋 = 9 星币)
        if (remaining > 0) {
            for (int i = 0; i < inv.getContainerSize(); i++) {
                ItemStack s = inv.getItem(i);
                if (s.is(ModItems.STAR_COIN_BAG.get())) {
                    s.shrink(1);
                    remaining -= COINS_PER_BAG;
                    break;
                }
            }
        }
        return remaining <= 0;
    }

    /**
     * 进入骰神赐福时调用:持有星币超过 20 枚 → 消耗 3 星币,并按持有星币总数的 30% 记录攻击加成。
     */
    public static void onBlessingStart(Player player) {
        if (player.level().isClientSide()) return;
        if (!isEquipped(player)) return;
        int total = countStarCoins(player);
        if (total <= THRESHOLD_COINS) return;
        if (!consumeStarCoins(player, CONSUME_COINS)) return;
        ModAttachments.setStarCoinHammerBonus(player, (int) (total * ATTACK_RATIO));
    }

    /**
     * 骰神赐福结束时调用:清除本次赐福的攻击加成。
     */
    public static void onBlessingEnd(Player player) {
        if (player.level().isClientSide()) return;
        ModAttachments.setStarCoinHammerBonus(player, 0);
    }

    @Override
    public void onEquip(SlotContext slotContext, ItemStack curio, ItemStack prevStack) {
        if (!(slotContext.entity() instanceof Player player)) return;
        if (player.level().isClientSide()) return;
        if (!prevStack.isEmpty()) return;
        // 装备时星光 +5(上限由 StarLightManager 统一管理)
        StarLightManager.add(player, 5);
    }

    // 卸下筹码:清除当前赐福的攻击加成(下次装备重新计算)
    @Override
    protected void onChipUnequip(Player player, ItemStack stack) {
        ModAttachments.setStarCoinHammerBonus(player, 0);
    }
}
