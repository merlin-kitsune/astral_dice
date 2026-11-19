package com.merlinkitsune.astral_dice.item.card;

import com.merlinkitsune.starengine.api.astral.AstralDiceApi;
import com.merlinkitsune.starengine.api.astral.data.DiceLoadout;
import com.merlinkitsune.starengine.api.astral.data.InstalledCard;
import com.merlinkitsune.astral_dice.combat.CardRegistry;
import com.merlinkitsune.astral_dice.component.AppliedStone;
import com.merlinkitsune.astral_dice.component.WeaponEnhancement;
import com.merlinkitsune.astral_dice.item.CardReturnHelper;
import com.merlinkitsune.astral_dice.item.dice.DiceCurioItem;
import com.merlinkitsune.astral_dice.item.dice.DiceLoadoutAccess;
import com.merlinkitsune.astral_dice.item.sign.TeruSignItem;
import com.merlinkitsune.astral_dice.component.AstralGameplayConstants;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 骰子卡牌栏的读写服务：把 {@code screen/CardInventoryMenu} 里私有的
 * {@code loadFromDice} / {@code saveToDice} 语义抽成**与界面无关**的入口，
 * 供 {@code AstralDiceApi} 与附属（例如女仆自动装牌）使用。
 *
 * <p>契约与菜单逐行等价：
 * <ul>
 *   <li>装配会**销毁**卡牌物品栈，数据只落在 {@link WeaponEnhancement#appliedStones()}。
 *       因此 {@link #equip} 成功时**调用方自行扣掉卡牌栈**（门面方法同样不代扣），
 *       卸下由 {@link #unequipAll} 按 {@code (type, uses)} 重建全新栈。</li>
 *   <li>费用上限 = {@code AstralGameplayConstants.cardCostForStar(configStarLevel(dice))}，与菜单同口径；
 *       攻击牌与防御牌**各自独立计费**、各自各占一半槽位。</li>
 *   <li>星级持久化写骰子的**实际**星级，不得改写为配置星级
 *       （下界之星骰子 GUI 显示 3★ 档配置，但存档里的实际星级必须保留）。</li>
 *   <li>临时性 / owner 从物品栈**读回**写进 {@link AppliedStone}：装配后物品栈不存在，
 *       记录是唯一载体；卸下时再补回栈上。</li>
 * </ul>
 *
 * <p>数据组件读写与掉落签名有版本差异，统一收在每线的
 * {@link DiceLoadoutAccess} 里，本类因此可以留在 {@code common} 只写一份。
 *
 * <p>只在**服务端**执行；客户端调用返回 {@code false} / {@code 0}。
 */
public final class DiceCardLoadout {

    private DiceCardLoadout() {
    }

    /** 读取卡牌栏快照；非骰子 / 空栈返回 {@link DiceLoadout#EMPTY}。 */
    public static DiceLoadout read(ItemStack dice) {
        if (dice == null || dice.isEmpty() || !DiceCurioItem.isDiceItem(dice)) return DiceLoadout.EMPTY;
        WeaponEnhancement enh = DiceLoadoutAccess.enhancement(dice);
        int slots = DiceCurioItem.getCardSlots(dice);
        List<InstalledCard> cards = new ArrayList<>(enh.appliedStones().size());
        for (AppliedStone stone : enh.appliedStones()) {
            if (stone == null || stone.type() == null) continue;
            cards.add(new InstalledCard(stone.type(), stone.uses(), stone.temporary(), stone.owner(),
                    CardRegistry.isDefense(stone.type())));
        }
        return new DiceLoadout(enh.starLevel(), slots, slots / 2,
                enh.usedCost(), enh.maxCost(), enh.usedDefenseCost(), enh.maxDefenseCost(), cards);
    }

    /** 只做可行性判断，不写入骰子。 */
    public static boolean canEquip(ItemStack dice, ItemStack card, Player owner) {
        return equipInternal(dice, card, owner, true);
    }

    /**
     * 把一张卡牌装进骰子卡牌栏。
     *
     * @param owner 费用折扣的判定对象（护法立牌「名刀」折扣等）；{@code null} 表示无主宿主（女仆 / 生物），
     *              此时按基础费用计算
     * @return 是否写入成功；成功时**不**扣卡牌栈，由调用方负责
     */
    public static boolean equip(ItemStack dice, ItemStack card, Player owner) {
        return equipInternal(dice, card, owner, false);
    }

    private static boolean equipInternal(ItemStack dice, ItemStack card, Player owner, boolean simulate) {
        if (dice == null || dice.isEmpty() || card == null || card.isEmpty()) return false;
        if (!DiceCurioItem.isDiceItem(dice)) return false;
        if (owner != null && owner.level().isClientSide()) return false;
        String type = CardRegistry.itemToType(card);
        if (type == null) return false;

        WeaponEnhancement enh = DiceLoadoutAccess.enhancement(dice);
        List<AppliedStone> stones = new ArrayList<>(enh.appliedStones());
        int attackCards = 0;
        int defenseCards = 0;
        for (AppliedStone stone : stones) {
            if (stone == null || stone.type() == null) continue;
            if (CardRegistry.isDefense(stone.type())) {
                defenseCards++;
            } else {
                attackCards++;
            }
        }

        boolean defense = CardRegistry.isDefense(type);
        int slots = DiceCurioItem.getCardSlots(dice);
        int perSide = slots / 2;
        if (defense ? defenseCards >= perSide : attackCards >= perSide) return false;

        int cost = costOf(type, owner);
        int maxCost = AstralGameplayConstants.cardCostForStar(DiceCurioItem.configStarLevel(dice));
        if (defense) {
            if (enh.usedDefenseCost() + cost > maxCost) return false;
        } else if (enh.usedCost() + cost > maxCost) {
            return false;
        }
        if (simulate) return true;

        int uses = DiceLoadoutAccess.cardUses(card, AppliedStone.defaultUses(type));
        stones.add(new AppliedStone(type, uses, TemporaryCardUtil.isTemporary(card), TemporaryCardUtil.ownerKeyOf(card)));

        int usedAttack = enh.usedCost();
        int usedDefense = enh.usedDefenseCost();
        if (defense) {
            usedDefense += cost;
        } else {
            usedAttack += cost;
        }
        // 星级持久化:写实际星级(同菜单 saveToDice 的 actualStar 口径)
        DiceLoadoutAccess.setEnhancement(dice,
                new WeaponEnhancement(usedAttack, maxCost, usedDefense, maxCost, enh.starLevel(), List.copyOf(stones)));

        if (owner != null) {
            notifyAttackCardsEquipped(owner, stones);
        }
        return true;
    }

    /**
     * 卸下全部卡牌并把它们还原成物品栈交还宿主。
     *
     * <p>玩家走 {@link CardReturnHelper#returnCards}（进包 / 满包掉地，与原版归还逻辑一致）；
     * 其他类玩家宿主走 {@link AstralDiceApi#giveItemTo}，再退化为原地掉落。
     * 非玩家宿主重建的临时牌**无法补回临时标记**（标记 API 需要 {@link Player}），
     * 会还原成普通卡牌 —— 这是类玩家宿主当前的已知边界。
     *
     * @return 实际归还张数
     */
    public static int unequipAll(ItemStack dice, LivingEntity holder) {
        if (dice == null || dice.isEmpty() || holder == null) return 0;
        if (holder.level().isClientSide()) return 0;
        WeaponEnhancement enh = DiceLoadoutAccess.enhancement(dice);
        List<AppliedStone> stones = enh.appliedStones();
        if (stones.isEmpty()) return 0;

        int returned;
        if (holder instanceof Player player) {
            returned = CardReturnHelper.returnCards(player, stones);
        } else {
            returned = 0;
            for (AppliedStone stone : stones) {
                if (stone == null) continue;
                ItemStack stack = CardRegistry.typeToItem(stone.type());
                if (stack.isEmpty()) continue;
                DiceLoadoutAccess.setCardUses(stack, stone.uses());
                if (!AstralDiceApi.giveItemTo(holder, stack)) {
                    DiceLoadoutAccess.drop(holder, stack);
                }
                returned++;
            }
        }

        int maxCost = AstralGameplayConstants.cardCostForStar(DiceCurioItem.configStarLevel(dice));
        DiceLoadoutAccess.setEnhancement(dice,
                new WeaponEnhancement(0, maxCost, 0, maxCost, enh.starLevel(), List.of()));
        return returned;
    }

    /** 费用:有玩家时走折扣判定，无主宿主按基础费用。 */
    private static int costOf(String type, Player owner) {
        if (owner != null) return CardRegistry.cost(type, owner);
        var cardType = CardRegistry.get(type);
        return cardType == null ? 1 : cardType.cost();
    }

    /** 教主立牌「狐光」装备计层:与菜单关闭时同一钩子，历史水位去重由立牌自身负责。 */
    private static void notifyAttackCardsEquipped(Player player, List<AppliedStone> stones) {
        Map<String, Integer> equipped = new LinkedHashMap<>();
        for (AppliedStone stone : stones) {
            if (stone == null || stone.type() == null || CardRegistry.isDefense(stone.type())) continue;
            equipped.merge(stone.type(), 1, Integer::sum);
        }
        TeruSignItem.onAttackCardsEquipped(player, equipped);
    }
}
