package com.merlinkitsune.astral_dice.item.chip;

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
 * 若物品栏中持有超过 20 枚星币,则每次进入骰神赐福时消耗 6 星币,并按持有星币总数的 30% 提升攻击力
 * (星币袋按 9 星币算;零散星币不足时自动拆开 1 个星币袋,只扣走所需枚数、余额留在物品栏,
 * 无法安全拆袋时不消耗;加成持续整个赐福,赐福结束清除,结算在 DiceCombatModifiers 攻击修饰器)。
 */
public class StarCoinHammerChipItem extends BaseChipItem {
    /** 触发门槛:持有星币须超过该数量 */
    public static final int THRESHOLD_COINS = 20;
    /** 每次进入赐福消耗的星币数 */
    public static final int CONSUME_COINS = 6;
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
        var curios = CuriosApi.getCuriosInventory(player);
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

    /**
     * 消耗 N 枚星币,返回是否成功;星币总量不足或无法安全拆袋时返回 false 且**不消耗任何物品**。
     *
     * <p>扣除规则(与 tooltip 描述严格一致):
     * <ol>
     *   <li>先扣零散星币;</li>
     *   <li>零散不足且持有星币袋时,**拆开 1 个星币袋**(1 袋 = {@link #COINS_PER_BAG} 枚)后继续按枚扣除:
     *       只扣走本次所需的枚数,拆出的其余星币原样留在物品栏,<b>绝不因物品栏已满而把星币掉落到地上</b>。
     *       因此拆袋前先做「可存放性预检」:优先拆「单袋槽位」(拆空后该槽位立刻容纳拆出的星币),
     *       其余情况必须已存在能容纳整袋 9 枚的空槽位或未满的星币堆;预检不通过则整次消耗放弃
     *       (不拆袋、不扣币、不加成)。</li>
     * </ol>
     */
    public static boolean consumeStarCoins(Player player, int amount) {
        if (player == null) return false;
        if (amount <= 0) return true;
        var inv = player.getInventory();
        // 严格前置判定:可扣除的星币(零散 + 星币袋折算)不足时不做任何改动
        if (countStarCoins(player) < amount) return false;
        int loose = countLooseStarCoins(player);
        int bagsNeeded = loose >= amount
                ? 0
                : (int) Math.ceil((amount - loose) / (double) COINS_PER_BAG);
        if (!canStoreBagBreak(inv, bagsNeeded)) return false;

        int remaining = consumeLooseStarCoins(player, amount);
        while (remaining > 0) {
            if (!breakOneBag(inv)) return false;
            remaining = consumeLooseStarCoins(player, remaining);
        }
        return true;
    }

    // 物品栏中零散星币总数(不含星币袋)
    private static int countLooseStarCoins(Player player) {
        int total = 0;
        var inv = player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (s.is(ModItems.STAR_COIN.get())) total += s.getCount();
        }
        return total;
    }

    // 从零散星币中扣除至多 amount 枚,返回尚未扣够的枚数
    private static int consumeLooseStarCoins(Player player, int amount) {
        int remaining = amount;
        var inv = player.getInventory();
        for (int i = 0; i < inv.getContainerSize() && remaining > 0; i++) {
            ItemStack s = inv.getItem(i);
            if (!s.is(ModItems.STAR_COIN.get())) continue;
            int take = Math.min(s.getCount(), remaining);
            s.shrink(take);
            remaining -= take;
        }
        return remaining;
    }

    // 拆袋前预检:拆出的星币必须全部有处可放,否则整次消耗放弃(避免物品栏已满时掉落到地上)
    private static boolean canStoreBagBreak(net.minecraft.world.entity.player.Inventory inv, int bagsNeeded) {
        if (bagsNeeded <= 0) return true;
        int max = new ItemStack(ModItems.STAR_COIN.get()).getMaxStackSize();
        int singleBagSlots = 0;
        int freeCapacity = 0;
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (s.isEmpty()) {
                freeCapacity += max;
            } else if (s.is(ModItems.STAR_COIN.get())) {
                freeCapacity += max - s.getCount();
            } else if (s.is(ModItems.STAR_COIN_BAG.get()) && s.getCount() == 1) {
                // 单袋槽位:拆空后该槽位本身即可容纳整袋星币,不占额外容量
                singleBagSlots++;
            }
        }
        int fromSingles = Math.min(bagsNeeded, singleBagSlots);
        return freeCapacity >= (bagsNeeded - fromSingles) * COINS_PER_BAG;
    }

    // 拆开 1 个星币袋并把拆出的星币放入物品栏(优先单袋槽位,拆空后原槽位直接容纳)
    private static boolean breakOneBag(net.minecraft.world.entity.player.Inventory inv) {
        int max = new ItemStack(ModItems.STAR_COIN.get()).getMaxStackSize();
        int singleSlot = -1;
        int multiSlot = -1;
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (!s.is(ModItems.STAR_COIN_BAG.get()) || s.isEmpty()) continue;
            if (s.getCount() == 1) {
                if (singleSlot < 0) singleSlot = i;
            } else if (multiSlot < 0) {
                multiSlot = i;
            }
        }
        if (singleSlot >= 0) {
            inv.setItem(singleSlot, new ItemStack(ModItems.STAR_COIN.get(), COINS_PER_BAG));
            return true;
        }
        if (multiSlot < 0) return false;
        // 多袋槽位拆 1 个不会腾出槽位:必须已存在能容纳整袋 9 枚的去处(预检已保证)
        int dest = -1;
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (s.isEmpty()) {
                dest = i;
                break;
            }
            if (s.is(ModItems.STAR_COIN.get()) && s.getCount() + COINS_PER_BAG <= max) {
                dest = i;
                break;
            }
        }
        if (dest < 0) return false;
        inv.getItem(multiSlot).shrink(1);
        ItemStack target = inv.getItem(dest);
        if (target.isEmpty()) {
            inv.setItem(dest, new ItemStack(ModItems.STAR_COIN.get(), COINS_PER_BAG));
        } else {
            target.grow(COINS_PER_BAG);
        }
        return true;
    }

    /**
     * 进入骰神赐福时调用:持有星币超过 20 枚 → 消耗 6 星币,并按持有星币总数的 30% 记录攻击加成。
     */
    public static void onBlessingStart(Player player) {
        if (player.level().isClientSide()) return;
        if (!isEquipped(player)) return;
        int total = countStarCoins(player);
        if (total <= THRESHOLD_COINS) return;
        if (!consumeStarCoins(player, CONSUME_COINS)) return;
        // 百分比加成统一下限为 1(按持有总数 30% 提升攻击力,截断后至少 +1)
        ModAttachments.setStarCoinHammerBonus(player, Math.max(1, (int) (total * ATTACK_RATIO)));
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
