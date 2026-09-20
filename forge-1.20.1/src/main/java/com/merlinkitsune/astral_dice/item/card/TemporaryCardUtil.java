package com.merlinkitsune.astral_dice.item.card;

import com.merlinkitsune.astral_dice.component.AppliedStone;
import com.merlinkitsune.astral_dice.component.ModDataComponents;
import com.merlinkitsune.astral_dice.component.WeaponEnhancement;
import com.merlinkitsune.astral_dice.combat.CardRegistry;
import com.merlinkitsune.astral_dice.effect.ModEffects;
import com.merlinkitsune.astral_dice.item.chip.VitaminPillChipItem;
import com.merlinkitsune.astral_dice.item.dice.DiceCurioItem;
import com.merlinkitsune.starenginelib.item.CuriosCompat;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * 「临时牌」统一工具类(绿洲女王立牌 nardis 主动「女王特权」)。
 *
 * <h2>临时牌的语义</h2>
 * 带 {@link ModDataComponents#TEMPORARY_CARD} 键的卡牌 = 临时牌:有效期 3:00(真值 = 原生效果实例
 * {@link ModEffects#NARDIS_PRIVILEGE}),期间**不可丢弃**、**不可移入其它容器**、带附魔光效;
 * 效果结束(自然到期 / 被外力移除 / 死亡 / 重登自检)即**整体清空**(含已装配到骰子里的那些)。
 *
 * <h2>为什么「只有物品标记」不够</h2>
 * 装备卡牌会**销毁**物品栈、卸除时按 {@code (type, uses)} 重建全新栈 —— 见
 * {@code screen/CardInventoryMenu#saveToDice} 内既有注释。故临时性在「已装配」状态下由
 * {@link AppliedStone#temporary()} 承载,本类清理时两者一起处理。
 *
 * <h2>落点</h2>
 * <ul>
 *   <li>发放:{@link #grantRandom(Player, int)}(走 {@link VitaminPillChipItem#giveCard} 发牌漏斗,
 *       保证「获得卡牌」类触发器全部生效);</li>
 *   <li>清理:{@link #purgeAll(Player)}(幂等)与玩家级 tick 自检 {@link #tick(Player)};</li>
 *   <li>保护:不可丢弃见 {@link CardItem#onDroppedByPlayer} 与
 *       {@code event/TemporaryCardEvents};不可移入容器见 {@link #isPlacementBlocked(ItemStack, boolean)}
 *       (全部 mixin 共用这一条判据)。</li>
 * </ul>
 *
 * <p>全类不新增任何玩家附件:计数与清理都是**读时现算**,清理动作全程 try/catch(Throwable) 只记日志,
 * 绝不让 tick / 死亡流程崩。
 *
 * <h2>1.20.1 平台适配(相对 1.21.1 的镜像改写)</h2>
 * <ul>
 *   <li>物品数据走 {@link ModDataComponents#TEMPORARY_CARD}({@code ItemDataKey} = ItemStack NBT):
 *       读 {@code get(stack)}(缺省 {@code null} ⇒ 必须 {@code Boolean.TRUE.equals(...)} 判定)、
 *       写 {@code set(stack, v)}、删 {@code remove(stack)};</li>
 *   <li>Curios 经本仓库包装 {@link CuriosCompat#getCuriosInventory} 统一为 {@code Optional}
 *       (1.20.1 原生返回 {@code LazyOptional});</li>
 *   <li>效果引用一律 {@code ModEffects.NARDIS_PRIVILEGE.get()}(1.20.1 是 {@code RegistryObject});</li>
 *   <li>骰子组件的读写 {@code ModDataComponents.WEAPON_ENHANCEMENT.getOrDefault(dice, …)} /
 *       {@code .set(dice, …)}。</li>
 * </ul>
 */
public final class TemporaryCardUtil {
    private static final Logger LOGGER = LoggerFactory.getLogger(TemporaryCardUtil.class);

    /** 女王特权的固定发放张数 */
    public static final int GRANT_COUNT = 3;

    private TemporaryCardUtil() {
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  标记读写
    // ══════════════════════════════════════════════════════════════════════════

    /** 该物品栈是否为临时牌(唯一的物品侧判据;光效/丢弃/容器拦截全部走这里) */
    public static boolean isTemporary(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        return Boolean.TRUE.equals(ModDataComponents.TEMPORARY_CARD.get(stack));
    }

    /** 打上临时牌标记(幂等) */
    public static void mark(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return;
        ModDataComponents.TEMPORARY_CARD.set(stack, Boolean.TRUE);
    }

    /** 去掉临时牌标记(幂等) */
    public static void unmark(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return;
        ModDataComponents.TEMPORARY_CARD.remove(stack);
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  发放(主动技能与探针共用)
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * 发放最多 {@code maxCount} 张随机临时牌,返回**实际发放张数**。
     *
     * <p>规则(与需求「临时牌只能在物品栏和手中」一致):
     * <ol>
     *   <li>先算物品栏可用空槽数,最多发 {@code min(maxCount, 空槽数)} 张 —— **绝不落地**
     *       (满了就少发,不发到地上);</li>
     *   <li>每张走 {@link RandomCardHandler#randomCard}({@link RandomCardHandler.CardCategory#ALL})
     *       —— 专属牌由既有池构建逻辑自动排除,**不要**在这里另写专属牌过滤;</li>
     *   <li>先 {@link #mark} 再交给 {@link VitaminPillChipItem#giveCard} —— 后者是本模组**唯一的发牌漏斗**
     *       (维生素药丸治愈 + 教主立牌狐光计层都在里面),必须走它才能「激活所有获取卡牌的触发器」。
     *       ⚠️ 看板娘 mimi 的 +1 星币**刻意不随奖励发牌触发**(仅合成与看板娘主动返还两条路),
     *       不为此新增触发。</li>
     * </ol>
     */
    public static int grantRandom(Player player, int maxCount) {
        if (player == null || player.level().isClientSide()) return 0;
        if (maxCount <= 0) return 0;
        int toGrant = Math.min(maxCount, countFreeSlots(player));
        int granted = 0;
        for (int i = 0; i < toGrant; i++) {
            ItemStack card = RandomCardHandler.randomCard(RandomCardHandler.CardCategory.ALL);
            if (card.isEmpty()) break;
            mark(card);
            VitaminPillChipItem.giveCard(player, card);
            granted++;
        }
        return granted;
    }

    /** 物品栏(主物品栏 0..35)可用空槽数;{@code giveCard} 的入包路径只使用这一段 */
    public static int countFreeSlots(Player player) {
        if (player == null) return 0;
        int free = 0;
        for (ItemStack stack : player.getInventory().items) {
            if (stack.isEmpty()) free++;
        }
        return free;
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  计数(读时现算,不落附件)
    // ══════════════════════════════════════════════════════════════════════════

    /** 当前玩家**身上**(主物品栏 0..35 + 副手;主手是物品栏中的一格)带临时标记的卡牌总张数 */
    public static int countTemporary(Player player) {
        if (player == null) return 0;
        int count = 0;
        for (ItemStack stack : player.getInventory().items) {
            if (isTemporary(stack)) count += stack.getCount();
        }
        for (ItemStack stack : player.getInventory().offhand) {
            if (isTemporary(stack)) count += stack.getCount();
        }
        return count;
    }

    /** 装备中骰子的 {@code weapon_enhancement.appliedStones} 里 {@code temporary()==true} 的卡牌张数 */
    public static int countTemporaryEquipped(Player player) {
        if (player == null) return 0;
        ItemStack dice = findEquippedDice(player);
        if (dice.isEmpty()) return 0;
        WeaponEnhancement enh = ModDataComponents.WEAPON_ENHANCEMENT.getOrDefault(dice, WeaponEnhancement.EMPTY);
        int count = 0;
        for (AppliedStone stone : enh.appliedStones()) {
            if (stone != null && stone.temporary()) count++;
        }
        return count;
    }

    /** 玩家当前佩戴的骰子(骰子饰品槽只有 1 个,不存在多骰子) */
    public static ItemStack findEquippedDice(Player player) {
        if (player == null) return ItemStack.EMPTY;
        var curios = CuriosCompat.getCuriosInventory(player);
        if (curios.isEmpty()) return ItemStack.EMPTY;
        var result = curios.get().findFirstCurio(DiceCurioItem::isDiceItem);
        return result.isPresent() ? result.get().stack() : ItemStack.EMPTY;
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  容器拦截(全部 mixin 共用的唯一判据)
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * 放置拦截判据(唯一实现):临时牌 **且** 目标槽不是「玩家自己的槽 / 本模组卡牌栏槽」⇒ 拦截。
     *
     * <p>{@code SlotPlaceGuardMixin}(目标 {@code Slot#mayPlace})、{@code ContainerMoveGuardMixin}
     * (目标 {@code AbstractContainerMenu#moveItemStackTo})以及 1.20.1 专有的三个入口守卫
     * ({@code ShulkerBoxSlotGuardMixin} / {@code ShulkerBoxBlockEntityGuardMixin} /
     * {@code BundleInsertGuardMixin})都必须调用本方法,**不得**在 mixin 里另写一份判据。</p>
     *
     * <p>为什么必须有两个通用 mixin:{@code moveItemStackTo} 先尝试「与同类栈合并」,该分支
     * (1.20.1 实测 {@code AbstractContainerMenu.java:631-687})**完全不查 {@code mayPlace}** ⇒ 只改
     * {@code mayPlace} 挡不住「Shift 点击把临时牌并进箱子里的同类栈」。
     */
    public static boolean isPlacementBlocked(ItemStack stack, boolean targetIsPlayerOwnedSlot) {
        return isTemporary(stack) && !targetIsPlayerOwnedSlot;
    }

    /**
     * 该槽是否「临时牌可以进」的槽:玩家自己的物品栏槽,或本模组卡牌栏槽
     * (后者实现 {@link TemporaryCardPermissiveSlot} 标记接口 —— **不要**用
     * {@code instanceof SimpleContainer} 这种过宽判据,那会误放行其它用 {@code SimpleContainer} 的容器)。
     */
    public static boolean isPlayerOwnedOrPermissive(Slot slot) {
        if (slot == null) return false;
        if (slot instanceof TemporaryCardPermissiveSlot) return true;
        return slot.container instanceof net.minecraft.world.entity.player.Inventory;
    }

    /** 槽区间 {@code [startIndex, endIndex)} 是否**全部**为可放临时牌的槽(区间取空/越界时按 startIndex 单点判定) */
    public static boolean isRangePlayerOwnedOrPermissive(List<Slot> slots, int startIndex, int endIndex) {
        if (slots == null) return false;
        int from = Math.max(0, Math.min(startIndex, endIndex));
        int to = Math.min(slots.size(), Math.max(startIndex, endIndex));
        if (to <= from) {
            // 空区间:没有目标槽,不构成"移入其它容器",按放行处理(实际也不会写入任何槽)
            return true;
        }
        for (int i = from; i < to; i++) {
            if (!isPlayerOwnedOrPermissive(slots.get(i))) return false;
        }
        return true;
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  清理
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * 清空该玩家的**全部**临时牌(幂等),返回移除的总张数。
     *
     * <p>清理范围(缺一不可):主物品栏 0..35、副手、**骰子
     * {@code weapon_enhancement.appliedStones} 中 {@code temporary()==true} 的项**
     * (过滤后重建 record,并按被移除卡的费用重算 {@code usedCost}/{@code usedDefenseCost},
     * {@code maxCost} 不变;费用口径 = {@code CardRegistry.cost(type, player)},与
     * {@code CardInventoryMenu#saveToDice} 的重算路径同源)。
     *
     * <p>全程 try/catch(Throwable) 只记日志:本方法会被 tick 自检与死亡流程调用,
     * 不允许把异常抛进那些路径。
     */
    public static int purgeAll(Player player) {
        if (player == null || player.level().isClientSide()) return 0;
        int removed = 0;
        try {
            removed += purgeInventory(player);
        } catch (Throwable t) {
            LOGGER.error("[Astral Dice][TemporaryCard] 清空物品栏临时牌失败", t);
        }
        try {
            removed += purgeEquipped(player);
        } catch (Throwable t) {
            LOGGER.error("[Astral Dice][TemporaryCard] 清空骰子装配栏临时牌失败", t);
        }
        return removed;
    }

    // 主物品栏 0..35 + 副手
    private static int purgeInventory(Player player) {
        int removed = 0;
        List<ItemStack> items = player.getInventory().items;
        for (int i = 0; i < items.size(); i++) {
            ItemStack stack = items.get(i);
            if (!isTemporary(stack)) continue;
            removed += stack.getCount();
            player.getInventory().setItem(i, ItemStack.EMPTY);
        }
        List<ItemStack> offhand = player.getInventory().offhand;
        for (int i = 0; i < offhand.size(); i++) {
            ItemStack stack = offhand.get(i);
            if (!isTemporary(stack)) continue;
            removed += stack.getCount();
            offhand.set(i, ItemStack.EMPTY);
        }
        return removed;
    }

    // 骰子已装配的临时牌:过滤 + 按被移除卡的费用重算 usedCost/usedDefenseCost(maxCost 不变)
    private static int purgeEquipped(Player player) {
        ItemStack dice = findEquippedDice(player);
        if (dice.isEmpty()) return 0;
        WeaponEnhancement enh = ModDataComponents.WEAPON_ENHANCEMENT.getOrDefault(dice, WeaponEnhancement.EMPTY);
        List<AppliedStone> stones = enh.appliedStones();
        if (stones == null || stones.isEmpty()) return 0;
        List<AppliedStone> kept = new ArrayList<>(stones.size());
        int freedAttackCost = 0;
        int freedDefenseCost = 0;
        int removed = 0;
        for (AppliedStone stone : stones) {
            if (stone == null || !stone.temporary()) {
                kept.add(stone);
                continue;
            }
            int cost = CardRegistry.cost(stone.type(), player);
            if (CardRegistry.isDefense(stone.type())) {
                freedDefenseCost += cost;
            } else {
                freedAttackCost += cost;
            }
            removed++;
        }
        if (removed <= 0) return 0;
        ModDataComponents.WEAPON_ENHANCEMENT.set(dice, new WeaponEnhancement(
                Math.max(0, enh.usedCost() - freedAttackCost),
                enh.maxCost(),
                Math.max(0, enh.usedDefenseCost() - freedDefenseCost),
                enh.maxDefenseCost(),
                enh.starLevel(),
                kept));
        return removed;
    }

    /**
     * 玩家级 tick 自检(**幂等**,由 {@code event/PlayerTickEvents} 在 {@code % 20} 早退**之前**调用)。
     *
     * <p>真值 = 原生效果实例 {@link ModEffects#NARDIS_PRIVILEGE};本方法只处理**唯一的收口条件**:
     * 「玩家身上/骰子里还有临时牌,但该玩家**没有**女王特权效果」⇒ 清空全部临时牌。
     * 效果自然到期、{@code /effect clear} 移除、离线到期后重登、异常残留**都会**走到这一条,
     * 因此不需要在任何其它地方补第二套清理逻辑(死亡路径另有一次显式清理,两者都幂等)。
     *
     * <p>⚠️ 1.20.1 的 {@code PlayerTickEvent} 每 tick 派发 START+END **两次** ⇒ 本方法会被调两遍。
     * 幂等性由「无临时牌 / 有效果 ⇒ 直接 return」保证:第二遍在已清空后自然早退,不会重复扣费用。
     */
    public static void tick(Player player) {
        if (player == null || player.level().isClientSide()) return;
        try {
            if (player.hasEffect(ModEffects.NARDIS_PRIVILEGE.get())) return;
            if (countTemporary(player) <= 0 && countTemporaryEquipped(player) <= 0) return;
            int removed = purgeAll(player);
            if (removed > 0) {
                LOGGER.debug("[Astral Dice][TemporaryCard] 效果已不在,自检清空临时牌: player={} removed={}",
                        player.getName().getString(), removed);
            }
        } catch (Throwable t) {
            LOGGER.error("[Astral Dice][TemporaryCard] tick 自检失败", t);
        }
    }
}
