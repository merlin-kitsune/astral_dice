package com.merlinkitsune.astral_dice.item.card;

import com.merlinkitsune.astral_dice.combat.CardRegistry;
import com.merlinkitsune.astral_dice.component.AppliedStone;
import com.merlinkitsune.astral_dice.component.ModDataComponents;
import com.merlinkitsune.astral_dice.component.WeaponEnhancement;
import com.merlinkitsune.astral_dice.item.ModItems;
import com.merlinkitsune.astral_dice.item.chip.VitaminPillChipItem;
import com.merlinkitsune.astral_dice.item.sign.MamushiSignItem;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 蛟龙立牌专属战斗牌(「撕咬」{@code attack_card_bite} / 「龙之咆哮」{@code attack_card_dragon_roar})
 * 的**纯工具**集合(2026-09-27,规格 §3.2)。
 *
 * <h2>为什么单独成类</h2>
 * <ul>
 *   <li>这两张牌是**战斗牌**(不是效果牌):{@link CardItem} 根类没有 {@code applyEffect} 一类的挂点,
 *       而它们的全部机制(觉醒计层 / 骰子卡牌栏转换 / 专属绑定 / 持有判据)都是**跨牌通用**的读写动作,
 *       放在立牌类里会与"立牌自身的技能状态机"混在一起;</li>
 *   <li>{@code item/sign/MamushiSignItem} 只调用这里的方法,本类**不反向依赖**立牌类
 *       (单向依赖,两线易保持一致);</li>
 *   <li>**不新建物品类**:两张牌都是普通 {@link CardItem},注册与费用/耐久由
 *       {@code ModItems} + {@link CardRegistry} 的既有表项承担(规格 §1 卡牌表)。</li>
 * </ul>
 *
 * <h2>1.20.1 平台适配</h2>
 * <ul>
 *   <li>物品数据走 {@link ModDataComponents}({@code ItemDataKey} = ItemStack NBT):读
 *       {@code getOrDefault(dice, …)}、写 {@code set(dice, …)};</li>
 *   <li>佩戴骰子经 {@link TemporaryCardUtil#findEquippedDice} 取(与临时牌、绿洲女王立牌同源);</li>
 *   <li>费用一律走 {@link CardRegistry#cost(String, Player)}(读取方传入玩家,便于按立牌联动改价),
 *       **不硬编码** 2/3;</li>
 *   <li>发牌统一走 {@link VitaminPillChipItem#giveCard(Player, Player, ItemStack)} 漏斗 ——
 *       该三参重载由 2026-09-27 批次新增(规格 §3.1),蛟龙自身发牌**必须**走它才会触发觉醒计数;
 *       两参重载等价于 {@code giver = null}(不计觉醒),本类不使用。</li>
 * </ul>
 */
public final class DragonCardUtil {

    /** 撕咬的 {@link CardRegistry} 类型 id(规格 §1:冻结值) */
    public static final String TYPE_BITE = "bite";
    /** 龙之咆哮的 {@link CardRegistry} 类型 id(规格 §1:冻结值) */
    public static final String TYPE_DRAGON_ROAR = "dragon_roar";

    private DragonCardUtil() {
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  持有判据(读时现算,不落任何状态)
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * 玩家**主物品栏**(0..35)里是否存在本模组的任意卡牌(规格 §3.2 的 D1 判据的组成部分)。
     *
     * <p>口径与 {@code HuoCardItem#countFu} 同形:只遍历 {@code getInventory().items}。
     * ⚠️ 1.20.1 的 {@code Inventory#getContainerSize()} 含护甲与副手槽 ⇒ **不得**用它遍历。
     */
    public static boolean hasAnyCardInInventory(Player player) {
        if (player == null) return false;
        for (ItemStack s : player.getInventory().items) {
            if (ModItems.isCardItem(s)) return true;
        }
        return false;
    }

    /** 玩家**双手**(主手 + 副手)里是否存在本模组的任意卡牌 */
    public static boolean hasAnyCardInHands(Player player) {
        if (player == null) return false;
        return ModItems.isCardItem(player.getMainHandItem())
                || ModItems.isCardItem(player.getOffhandItem());
    }

    /** 玩家**骰子卡牌栏**已装配的卡牌张数(全部类型;专属牌同样计入)。{@code null} 项不计。 */
    public static int countEquippedCards(Player player) {
        return equippedStones(player).size();
    }

    /**
     * 玩家骰子卡牌栏里**指定类型**的装配张数({@code typeId} 走 {@link CardRegistry} 的冻结类型 id,
     * 如 {@link #TYPE_BITE} / {@link #TYPE_DRAGON_ROAR})。
     *
     * <p>「已装备」的唯一真值 = 骰子物品的 {@code weapon_enhancement.appliedStones}
     * (背包里没装配的牌不算),与 {@code NardisSignItem#countStones} 的口径一致。
     */
    public static int countEquippedType(Player player, String typeId) {
        if (typeId == null) return 0;
        int count = 0;
        for (AppliedStone stone : equippedStones(player)) {
            if (stone != null && typeId.equals(stone.type())) count++;
        }
        return count;
    }

    /** 玩家当前佩戴骰子的 {@code appliedStones}(无骰子 ⇒ 空表;过滤 null 项) */
    public static List<AppliedStone> equippedStones(Player player) {
        if (player == null) return List.of();
        ItemStack dice = TemporaryCardUtil.findEquippedDice(player);
        if (dice.isEmpty()) return List.of();
        WeaponEnhancement enh = ModDataComponents.WEAPON_ENHANCEMENT.getOrDefault(dice, WeaponEnhancement.EMPTY);
        List<AppliedStone> stones = enh.appliedStones();
        if (stones == null || stones.isEmpty()) return List.of();
        List<AppliedStone> out = new ArrayList<>(stones.size());
        for (AppliedStone stone : stones) {
            if (stone != null) out.add(stone);
        }
        return out;
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  发放(带发牌者 ⇒ 蛟龙觉醒计数随之生效)
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * 发放 {@code count} 张**已绑定获得者**的专属牌({@code roar = true} ⇒ 龙之咆哮,否则撕咬)。
     *
     * <p>照 {@code FuCardItem#give} / {@code HuoCardItem#give} 的既有形状:
     * <ol>
     *   <li>{@link ExclusiveCardUtil#setOwner} 把 {@code owner_uuid} 绑到**获得者**身上(规格 §2.7
     *       「与符卡-福/祸同口径」);</li>
     *   <li>走 {@link VitaminPillChipItem#giveCard(Player, Player, ItemStack)} 发牌漏斗,
     *       {@code giver} 透传 ⇒ 蛟龙立牌被动「湖沼之王」的觉醒计数(规格 §2.1)随之生效;
     *       背包满则掉落,不丢失不销毁。</li>
     * </ol>
     *
     * @param giver    发牌者(= 主动技能施放者);为 {@code null} 时不触发觉醒计数
     * @param receiver 获得者(绑定的 owner 也是此人)
     * @param roar     true = 龙之咆哮;false = 撕咬
     * @param count    张数(≤ 0 时直接返回)
     */
    public static void giveExclusiveCard(Player giver, Player receiver, boolean roar, int count) {
        if (receiver == null || count <= 0) return;
        if (receiver.level().isClientSide()) return;
        ItemStack stack = new ItemStack(roar ? ModItems.ATTACK_CARD_DRAGON_ROAR.get()
                : ModItems.ATTACK_CARD_BITE.get(), count);
        ExclusiveCardUtil.setOwner(stack, receiver);
        VitaminPillChipItem.giveCard(giver, receiver, stack);
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  转换:撕咬 → 龙之咆哮(真龙形态)
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * **背包侧**转换:把主物品栏(0..35)与副手里的每一张 {@code attack_card_bite} 替换为**等张数**的
     * {@code attack_card_dragon_roar},并**保留 {@code owner_uuid} 绑定**(规格 §2.3 第 1 条)。
     *
     * <p>归属口径(2026-09-27 R1 修正,**两线统一**):原本**有主** ⇒ 原样保留该绑定(绝不改归属);
     * 原本**无主**(只在创造栏/命令等旁路出现,正常发放路径必先 {@code setOwner})⇒ **绑定到转换者** ——
     * 与 1.21.1 线 {@code DragonCardUtil#toRoarStack} 逐字同法,否则同一张牌在两线会得到"有主 / 无主"
     * 两种结果(无主者可被任意玩家装备)。
     *
     * <p>手法照 {@code HuoCardItem#removeAll} 的形状(逐格遍历 + 直接 {@code setItem} 覆写),
     * 不产生中间容器、不落地任何掉落物。
     *
     * @return 实际转换的张数
     */
    public static int convertInventoryBites(Player player) {
        if (player == null || player.level().isClientSide()) return 0;
        int moved = 0;
        List<ItemStack> items = player.getInventory().items;
        for (int i = 0; i < items.size(); i++) {
            ItemStack s = items.get(i);
            if (s.isEmpty() || !s.is(ModItems.ATTACK_CARD_BITE.get())) continue;
            moved += convertStackInPlace(player, items, i, s);
        }
        List<ItemStack> offhand = player.getInventory().offhand;
        for (int i = 0; i < offhand.size(); i++) {
            ItemStack s = offhand.get(i);
            if (s.isEmpty() || !s.is(ModItems.ATTACK_CARD_BITE.get())) continue;
            moved += convertStackInPlace(player, offhand, i, s);
        }
        return moved;
    }

    // 单格就地替换:等张数的龙之咆哮 + 保留原 owner_uuid 绑定(原本无主 ⇒ **绑定到转换者**,
    // 与 1.21.1 的 `DragonCardUtil#toRoarStack` 的无主分支逐字同法;有主时原样保留、绝不改归属)
    private static int convertStackInPlace(Player converter, List<ItemStack> container, int index, ItemStack bite) {
        int count = bite.getCount();
        ItemStack converted = new ItemStack(ModItems.ATTACK_CARD_DRAGON_ROAR.get(), count);
        Optional<UUID> owner = ModDataComponents.OWNER_UUID.getOrDefault(bite, Optional.empty());
        if (owner.isPresent()) {
            ModDataComponents.OWNER_UUID.set(converted, owner);
        } else if (converter != null) {
            ExclusiveCardUtil.setOwner(converted, converter);
        }
        container.set(index, converted);
        return count;
    }

    /**
     * **骰子侧**转换:遍历骰子 {@code weapon_enhancement.appliedStones},对每个 {@code type == "bite"}
     * 的装配项按装配顺序**逐个**转换 —— 费用由 {@link CardRegistry#cost(String, Player)} 重算
     * (撕咬 2 → 龙之咆哮 3),`runningCost + roarCost > maxCost` 的**那一项丢弃**
     * (从 {@code appliedStones} 移除),其余写回为 {@code dragon_roar}(规格 §2.3 第 2 条)。
     *
     * <p><b>算法与 1.21.1 线逐字同源(2026-09-28 修正)</b>:基准 = 当前 {@code usedCost}
     * **减去全部撕咬的旧费用**(先整体扣除 ⇒ "被丢弃"的项不会重复计入),随后按装配顺序逐项累加
     * 咆哮费用、**累加前先判超支**。这样"先到先得"成立,且留下的装配表**必然**不超预算
     * (撕咬 2 → 龙之咆哮 3 是涨价,mixed 档位下前面几项放得下、后面的被挤掉)。
     *
     * <p>⚠️ <b>本方法原先不是这个算法</b>:旧写法"先按全部转换投影总占用,再从最后一张撕咬往前丢,
     * 每丢一张只把投影减 1"会把**已被移出**装配表的撕咬旧费用又加回去(净 −1 而非 −3)⇒
     * **多丢**卡牌 —— 实测 3 张撕咬在 {@code maxCost}=7 时只剩 1 张、{@code maxCost}=4 时一张都不剩,
     * 与规格 §2.3「按装配顺序逐个转换」及 1.21.1 线(3/2/1)不符(1.20.1 实测 3/1/0)。
     * 已按 1.21.1 的 {@code DragonCardUtil#convertEquipped} 改写为"基准扣除 + 逐项累加判超支"。
     *
     * <p><b>F2(2026-09-27 收口)—— 转换后的耐久</b>:写回的装配项 {@code uses} 一律取
     * {@link MamushiSignItem#ROAR_USES}(= 5,规格 §1 冻结常量;与 {@code ModDataComponents.CARD_USES}
     * 给卡牌物品的默认耐久同一真值),**不再**保留撕咬的剩余耐久。理由:背包侧转换产出的是全新物品,
     * 自带 {@code CARD_USES} 的默认值(= 龙之咆哮满耐久 5)⇒ 骰子侧若保留"撕咬 leftover"(耐久 1)
     * 会造成同一次真龙转换两处口径不一致(1 耐久 / 5 耐久)。两线(1.21.1 / 1.20.1)同批统一为满耐久。
     *
     * <p>⚠️ 只处理 {@code bite};{@code dragon_roar} 已装配项原样保留(幂等)。
     *
     * @return 实际转换(写回为龙之咆哮)的装配项数
     */
    public static int convertEquippedBites(Player player) {
        if (player == null || player.level().isClientSide()) return 0;
        ItemStack dice = TemporaryCardUtil.findEquippedDice(player);
        if (dice.isEmpty()) return 0;
        WeaponEnhancement enh = ModDataComponents.WEAPON_ENHANCEMENT.getOrDefault(dice, WeaponEnhancement.EMPTY);
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
            // 耐久口径(两线统一,见方法注释 F2):写回为龙之咆哮的**满耐久**;临时牌标记原样保留
            kept.add(new AppliedStone(TYPE_DRAGON_ROAR, MamushiSignItem.ROAR_USES, stone.temporary()));
            converted++;
        }
        if (converted <= 0 && dropped <= 0) return 0;
        ModDataComponents.WEAPON_ENHANCEMENT.set(dice, new WeaponEnhancement(
                runningCost,
                enh.maxCost(),
                runningDefenseCost,
                enh.maxDefenseCost(),
                enh.starLevel(),
                kept));
        return converted;
    }
}
