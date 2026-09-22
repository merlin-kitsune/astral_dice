package com.merlinkitsune.astral_dice.item.card;

import com.merlinkitsune.astral_dice.combat.CardRegistry;
import com.merlinkitsune.astral_dice.component.AppliedStone;
import com.merlinkitsune.astral_dice.component.ModDataComponents;
import com.merlinkitsune.astral_dice.component.WeaponEnhancement;
import com.merlinkitsune.astral_dice.item.ModItems;
import com.merlinkitsune.astral_dice.item.chip.VitaminPillChipItem;
import com.merlinkitsune.astral_dice.item.sign.MamushiSignItem;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 蛟龙立牌(mamushi)专属战斗牌「撕咬 / 龙之咆哮」的**纯工具**(无状态、无 tick):
 * 发放、装配计数、持有判据与「撕咬 → 龙之咆哮」的整包转换。
 *
 * <p>本类只做卡牌/骰子数据的读写,不含立牌的状态机与技能触发 —— 后者在
 * {@code item/sign/MamushiSignItem}。分成两类是为了让"读哪里、写哪里"的边界一眼可见:
 * 立牌类管觉醒/形态/冷却,本类管卡牌本体(物品栏、副手、骰子卡牌栏)。
 *
 * <p>两张牌的冻结口径(见 {@code docs/features/mamushi-sign-spec.md} §1):
 * 撕咬 typeId {@link #TYPE_BITE}(费用 2 / 耐久 1 / 定值攻击 +3);
 * 龙之咆哮 typeId {@link #TYPE_DRAGON_ROAR}(费用 3 / 耐久 5 / 定值攻击 +3)。
 * 两者均为专属牌({@link ExclusiveCardUtil#isExclusive}),不进任何随机池、无配方。
 */
public final class DragonCardUtil {

    /** 撕咬的 {@code CardRegistry} typeId(冻结值) */
    public static final String TYPE_BITE = "bite";
    /** 龙之咆哮的 {@code CardRegistry} typeId(冻结值) */
    public static final String TYPE_DRAGON_ROAR = "dragon_roar";

    private DragonCardUtil() {
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  发放
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * 发放 {@code count} 张专属战斗牌:物品按 {@code roar} 取「龙之咆哮 / 撕咬」。
     *
     * <p>专属绑定口径与既有专属牌(符卡-福/祸)逐字相同:先
     * {@link ExclusiveCardUtil#setOwner}(获得者),再走**唯一发牌漏斗**
     * {@link VitaminPillChipItem#giveCard(Player, Player, ItemStack)}(背包满则掉落、不销毁),
     * 并把发牌者 {@code giver} **透传**给漏斗 —— 这样"使其他角色获得卡牌"能照常计入蛟龙立牌被动
     * (自己给自己由立牌侧按 {@code giver != receiver} 排除)。
     */
    public static void giveExclusiveCard(Player giver, Player receiver, boolean roar, int count) {
        if (receiver == null || count <= 0) return;
        if (receiver.level().isClientSide()) return;
        Item item = roar ? ModItems.ATTACK_CARD_DRAGON_ROAR.get() : ModItems.ATTACK_CARD_BITE.get();
        ItemStack stack = new ItemStack(item, count);
        ExclusiveCardUtil.setOwner(stack, receiver);
        VitaminPillChipItem.giveCard(giver, receiver, stack);
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  计数 / 持有判据
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * 玩家**骰子卡牌栏**中指定 typeId 的装配张数(读 {@code weapon_enhancement.appliedStones})。
     *
     * <p>「已装备」的唯一口径 = 骰子物品的装配项;背包里没装配的不算(与
     * {@code NardisSignItem#countStones} 同源)。无骰子 ⇒ 0。
     */
    public static int countEquippedType(Player player, String typeId) {
        if (player == null || typeId == null) return 0;
        ItemStack dice = TemporaryCardUtil.findEquippedDice(player);
        if (dice.isEmpty()) return 0;
        WeaponEnhancement enh = dice.getOrDefault(ModDataComponents.WEAPON_ENHANCEMENT.get(),
                WeaponEnhancement.EMPTY);
        List<AppliedStone> stones = enh.appliedStones();
        if (stones == null || stones.isEmpty()) return 0;
        int count = 0;
        for (AppliedStone stone : stones) {
            if (stone != null && typeId.equals(stone.type())) count++;
        }
        return count;
    }

    /**
     * D1 判据:玩家**当前是否持有任何本模组卡牌** —— 主物品栏 + 双手 + 骰子卡牌栏,一处都没有 ⇒ false。
     *
     * <p>口径为规格冻结值({@code ModItems.isCardItem} = 战斗牌标签 ∪ 效果牌标签,含专属牌);
     * 骰子侧按装配项经 {@link CardRegistry#typeToItem} 还原成卡牌物品再判标签,
     * 与物品栏侧同一判据(不额外假定"装配项一定是卡牌")。
     */
    public static boolean hasAnyCard(Player player) {
        if (player == null) return false;
        // 主物品栏(含主手所选格)与副手
        for (ItemStack stack : player.getInventory().getNonEquipmentItems()) {
            if (!stack.isEmpty() && ModItems.isCardItem(stack)) return true;
        }
        if (ModItems.isCardItem(player.getOffhandItem())) return true;
        // 骰子卡牌栏:装配项 → 对应卡牌物品 → 卡牌标签判据
        ItemStack dice = TemporaryCardUtil.findEquippedDice(player);
        if (!dice.isEmpty()) {
            WeaponEnhancement enh = dice.getOrDefault(ModDataComponents.WEAPON_ENHANCEMENT.get(),
                    WeaponEnhancement.EMPTY);
            List<AppliedStone> stones = enh.appliedStones();
            if (stones != null) {
                for (AppliedStone stone : stones) {
                    if (stone == null || stone.type() == null) continue;
                    ItemStack card = CardRegistry.typeToItem(stone.type());
                    if (!card.isEmpty() && ModItems.isCardItem(card)) return true;
                }
            }
        }
        return false;
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  撕咬 → 龙之咆哮(真龙形态的牌转换,§2.3)
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * 把该玩家手里的**全部**撕咬转换为等张数的龙之咆哮(幂等:没有撕咬即空操作,返回 0)。
     *
     * <p>两侧同时处理(缺一不可):
     * <ol>
     *   <li><b>背包侧</b> —— 主物品栏全部格子 + 副手,逐格替换为等张数的新牌,**保留
     *       {@code owner_uuid} 绑定**(原本无主则绑定到该玩家;形状照
     *       {@code HuoCardItem#removeAll} 的"主物品栏 + 副手"持有集合口径);</li>
     *   <li><b>骰子侧</b> —— 遍历 {@code appliedStones},把 {@code type == "bite"} 的装配项改写为
     *       {@code dragon_roar};费用 2→3 逐项累加,**超出 {@code maxCost} 的那些丢弃**
     *       (从装配项移除,并按被移除/被改写卡的费用重算 {@code usedCost}/{@code usedDefenseCost},
     *       {@code maxCost} 不变 —— 与 {@code TemporaryCardUtil#purgeEquipped} 同一重算形状)。
     *       装配项的耐久改写为龙之咆哮的**满耐久** {@link MamushiSignItem#ROAR_USES}
     *       (与背包侧新牌同一口径,见 §2.3 转换口径统一),临时牌标记原样保留。</li>
     * </ol>
     *
     * @return 实际转换的总张数(背包 + 骰子侧;0 = 一张撕咬都没有)
     */
    public static int convertBiteToRoar(Player player) {
        if (player == null || player.level().isClientSide()) return 0;
        int converted = convertInventory(player);
        converted += convertEquipped(player);
        return converted;
    }

    // 背包侧:主物品栏全部格子 + 副手(逐格替换,保留绑定)
    private static int convertInventory(Player player) {
        int converted = 0;
        List<ItemStack> items = player.getInventory().getNonEquipmentItems();
        for (int i = 0; i < items.size(); i++) {
            ItemStack stack = items.get(i);
            if (stack.isEmpty() || !stack.is(ModItems.ATTACK_CARD_BITE.get())) continue;
            converted += stack.getCount();
            player.getInventory().setItem(i, toRoarStack(stack, player));
        }
        ItemStack off = player.getOffhandItem();
        if (!off.isEmpty() && off.is(ModItems.ATTACK_CARD_BITE.get())) {
            converted += off.getCount();
            player.setItemInHand(InteractionHand.OFF_HAND, toRoarStack(off, player));
        }
        return converted;
    }

    // 等张数替换为龙之咆哮,并保留原牌的 owner_uuid 绑定(无主则绑定到该玩家)
    private static ItemStack toRoarStack(ItemStack bite, Player ownerFallback) {
        ItemStack roar = new ItemStack(ModItems.ATTACK_CARD_DRAGON_ROAR.get(), bite.getCount());
        Optional<UUID> owner = bite.get(ModDataComponents.OWNER_UUID.get());
        if (owner != null && owner.isPresent()) {
            roar.set(ModDataComponents.OWNER_UUID.get(), owner);
        } else if (ownerFallback != null) {
            ExclusiveCardUtil.setOwner(roar, ownerFallback);
        }
        return roar;
    }

    // 骰子侧:按装配顺序逐项转换 + 费用溢出丢弃 + usedCost/usedDefenseCost 重算
    private static int convertEquipped(Player player) {
        ItemStack dice = TemporaryCardUtil.findEquippedDice(player);
        if (dice.isEmpty()) return 0;
        WeaponEnhancement enh = dice.getOrDefault(ModDataComponents.WEAPON_ENHANCEMENT.get(),
                WeaponEnhancement.EMPTY);
        List<AppliedStone> stones = enh.appliedStones();
        if (stones == null || stones.isEmpty()) return 0;
        int biteCount = 0;
        for (AppliedStone stone : stones) {
            if (stone != null && TYPE_BITE.equals(stone.type())) biteCount++;
        }
        if (biteCount <= 0) return 0;

        // 费用口径与 CardInventoryMenu#saveToDice / TemporaryCardUtil#purgeEquipped 同源:CardRegistry.cost(type, player)
        int roarCost = CardRegistry.cost(TYPE_DRAGON_ROAR, player);
        int biteCost = CardRegistry.cost(TYPE_BITE, player);
        boolean roarIsDefense = CardRegistry.isDefense(TYPE_DRAGON_ROAR);
        boolean biteIsDefense = CardRegistry.isDefense(TYPE_BITE);
        // 基准 = 当前值 − 全部撕咬的旧费用(逐个转换前先整体扣除,故"被丢弃"的项不会重复计入)
        int runningCost = Math.max(0, enh.usedCost() - (biteIsDefense ? 0 : biteCount * biteCost));
        int runningDefenseCost = Math.max(0,
                enh.usedDefenseCost() - (biteIsDefense ? biteCount * biteCost : 0));
        List<AppliedStone> kept = new ArrayList<>(stones.size());
        int converted = 0;
        int dropped = 0;
        for (AppliedStone stone : stones) {
            if (stone == null) continue;
            if (!TYPE_BITE.equals(stone.type())) {
                kept.add(stone);
                continue;
            }
            // 费用溢出(逐项累加后超过 maxCost)⇒ 该项丢弃;其旧费用已在基准里扣除,故不再回加
            if (!roarIsDefense && runningCost + roarCost > enh.maxCost()) {
                dropped++;
                continue;
            }
            if (roarIsDefense && runningDefenseCost + roarCost > enh.maxDefenseCost()) {
                dropped++;
                continue;
            }
            if (roarIsDefense) {
                runningDefenseCost += roarCost;
            } else {
                runningCost += roarCost;
            }
            // 耐久口径(两线统一):转换为龙之咆哮的**满耐久** ROAR_USES ——
            // 骰子侧原先"保留剩余耐久"(撕咬 leftover=1)会与背包侧口径不一致(背包侧新牌来自
            // 物品自带的 CARD_USES=5,即满耐久)。临时牌标记仍原样保留。
            kept.add(new AppliedStone(TYPE_DRAGON_ROAR, MamushiSignItem.ROAR_USES, stone.temporary()));
            converted++;
        }
        if (converted <= 0 && dropped <= 0) return 0;
        dice.set(ModDataComponents.WEAPON_ENHANCEMENT.get(), new WeaponEnhancement(
                runningCost,
                enh.maxCost(),
                runningDefenseCost,
                enh.maxDefenseCost(),
                enh.starLevel(),
                kept));
        return converted;
    }
}
