package com.merlinkitsune.astral_dice.item.card;

import com.merlinkitsune.astral_dice.component.AppliedStone;
import com.merlinkitsune.astral_dice.component.ModDataComponents;
import com.merlinkitsune.astral_dice.component.WeaponEnhancement;
import com.merlinkitsune.astral_dice.combat.CardRegistry;
import com.merlinkitsune.astral_dice.effect.ModEffects;
import com.merlinkitsune.astral_dice.effect.NardisPrivilegeEffect;
import com.merlinkitsune.astral_dice.item.chip.VitaminPillChipItem;
import com.merlinkitsune.astral_dice.item.dice.DiceCurioItem;
import com.merlinkitsune.starenginelib.event.ModEffectRemoval;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.theillusivec4.curios.api.CuriosApi;

import java.util.ArrayList;
import java.util.List;

/**
 * 「临时牌」统一工具类(绿洲女王立牌 nardis 主动「女王特权」)。
 *
 * <h2>临时牌的语义</h2>
 * 带 {@link ModDataComponents#TEMPORARY_CARD} 组件的卡牌 = 临时牌:有效期 3:00(真值 = 原生效果实例
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
 *   <li>发放:{@link #grantNardisPrivilege(Player)}(= 2 张战斗牌 + 1 张效果牌,2026-09-27 用户裁决①)
 *       与通用的 {@link #grantRandom(Player, RandomCardHandler.CardCategory, int)}
 *       (两者都走 {@link VitaminPillChipItem#giveCard} 发牌漏斗,保证「获得卡牌」类触发器全部生效);</li>
 *   <li>清理:{@link #purgeAll(Player)}(幂等)与玩家级 tick 自检 {@link #tick(Player)}
 *       (双向收口:无效果 ⇒ 清牌;无牌 ⇒ 移除效果并解冻);</li>
 *   <li>保护:不可丢弃见 {@link CardItem#onDroppedByPlayer} 与
 *       {@code event/TemporaryCardEvents};不可移入容器与附魔光效见
 *       {@link #isPlacementBlocked(ItemStack, boolean)}(槽位面:两个 mixin)、
 *       {@link #fitsInsideContainer(ItemStack, boolean)} 与 {@link #glint(ItemStack, boolean)}
 *       (物品面:**两个牌根类** {@link CardItem} 与 {@link BaseEffectCardItem} 各覆写一次,
 *       判据只有这一份)。</li>
 * </ul>
 *
 * <p>全类不新增任何玩家附件:计数与清理都是**读时现算**,清理动作全程 try/catch(Throwable) 只记日志,
 * 绝不让 tick / 死亡流程崩。
 */
public final class TemporaryCardUtil {
    private static final Logger LOGGER = LoggerFactory.getLogger(TemporaryCardUtil.class);

    /** 女王特权的固定发放张数 = {@value #GRANT_BATTLE_COUNT} 战斗牌 + {@value #GRANT_EFFECT_COUNT} 效果牌 */
    public static final int GRANT_COUNT = 3;

    /**
     * 其中**先**发放的战斗牌张数(2026-09-27 用户裁决①)。
     * 池 = {@link RandomCardHandler.CardCategory#BATTLE}(攻击牌 + 防御牌**混合池**,
     * 两张各自独立随机 ⇒ 允许两张都是攻击或都是防御)。
     */
    public static final int GRANT_BATTLE_COUNT = 2;

    /** 战斗牌之后发放的效果牌张数(池 = {@link RandomCardHandler.CardCategory#EFFECT}) */
    public static final int GRANT_EFFECT_COUNT = 1;

    /** 主动技能的最低可用格数门槛(2026-09-27 用户裁决放宽为 2)。
     *  卡牌物品可堆叠({@code stacksTo(64)}),且发牌前会先清空临时牌 ⇒ 恰好 2 格时两张战斗牌
     *  常并进一格(同 id 同临时标记 ⇒ {@code Inventory#add} 走合并分支),效果牌仍可能放得下;
     *  少于 2 格则必然只能发 ≤1 张,不值得消耗一次释放。 */
    public static final int MIN_FREE_SLOTS_TO_CAST = 2;

    private TemporaryCardUtil() {
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  标记读写
    // ══════════════════════════════════════════════════════════════════════════

    /** 该物品栈是否为临时牌(唯一的物品侧判据;光效/丢弃/容器拦截全部走这里) */
    public static boolean isTemporary(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        return Boolean.TRUE.equals(stack.get(ModDataComponents.TEMPORARY_CARD.get()));
    }

    /** 打上临时牌标记(幂等) */
    public static void mark(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return;
        stack.set(ModDataComponents.TEMPORARY_CARD.get(), Boolean.TRUE);
    }

    /** 去掉临时牌标记(幂等) */
    public static void unmark(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return;
        stack.remove(ModDataComponents.TEMPORARY_CARD.get());
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  物品级覆写判据(普通牌 CardItem 与效果牌 BaseEffectCardItem 共用同一份实现)
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * 「附魔光效」判据(唯一实现):临时牌常亮,其余原样交回原版判定。
     *
     * <p>牌有**两个根类**:战斗牌 {@link CardItem}(直接 {@code extends Item})与效果牌
     * {@link BaseEffectCardItem}({@code abstract extends Item})—— 两者没有共同父类,
     * 而「接口 default 方法被类方法优先规则压住」⇒ 无法靠接口给 {@code Item#isFoil} 提供默认实现,
     * 只能**每个根类各覆写一次**。覆写体只允许 `return TemporaryCardUtil.glint(stack, super.isFoil(stack));`
     * —— 判据只此一份,不允许在覆写里另写条件(否则会再次出现「普通牌有光效、效果牌没有」的漂移)。
     *
     * @param vanillaGlint 原版判定结果({@code super.isFoil(stack)});原版实现(1.21.1 实测
     *                     {@code Item.java:348} {@code return stack.isEnchanted();})无副作用,
     *                     故允许先行求值
     */
    public static boolean glint(ItemStack stack, boolean vanillaGlint) {
        return isTemporary(stack) || vanillaGlint;
    }

    /**
     * 「能否被收进物品内的容器」判据(唯一实现):临时牌一律拒绝。
     *
     * <p>1.21.1 的容器收下判定有两条路:① 槽位路径走 {@code Slot#mayPlace}(由
     * {@code SlotPlaceGuardMixin} / {@code ContainerMoveGuardMixin} 按 {@link #isPlacementBlocked} 拦);
     * ② **栈级钩子路径**走 {@code IItemStackExtension#canFitInsideContainerItems()} ——
     * 实测 {@code :523-524} → {@code IItemExtension#canFitInsideContainerItems(ItemStack)}
     * {@code :808-809} → **物品类的覆写**。②的调用点有**五处**,全部只看物品类,与槽位无关:
     * <ul>
     *   <li>潜影盒 GUI 槽 {@code ShulkerBoxSlot#mayPlace}(实测 {@code :17}
     *       {@code stack.canFitInsideContainerItems()})—— 它是 {@code Slot#mayPlace} 的**覆写且不调 super**,
     *       槽位 mixin 对它无效;</li>
     *   <li>潜影盒自动化面 {@code ShulkerBoxBlockEntity#canPlaceItemThroughFace}(实测 {@code :238});</li>
     *   <li>收纳袋 {@code BundleItem#overrideStackedOnOther}(实测 {@code :57})与
     *       {@code BundleContents.Mutable#tryInsert}(实测 {@code :144},收纳袋两条插入路径的唯一汇聚点);</li>
     *   <li>NeoForge 组件容器 {@code ComponentItemHandler#isItemValid}(实测 {@code :143})。</li>
     * </ul>
     * ⇒ 这四个入口此前只有 {@link CardItem} 覆写了钩子,效果牌 {@link BaseEffectCardItem} 未覆写
     * ⇒ 临时**效果牌**可从上述路径进入容器并长期留存(永久牌期间不清)。两个根类都覆写后,
     * 全部容器入口与物品类别无关地统一拦在 {@link #isTemporary(ItemStack)} 上。
     *
     * <p>1.20.1 **没有**栈级钩子({@code Item#canFitInsideContainerItems()} 是类型级,实测
     * {@code Item.java:452}),那条线的容器拦截全部由五个 mixin 承担(判据同样是
     * {@link #isTemporary(ItemStack)}),本方法在 1.20.1 不被调用。
     *
     * @param vanillaAllows 原版判定结果({@code super.canFitInsideContainerItems(stack)});
     *                      最终落到 {@code Item#canFitInsideContainerItems()}(实测 {@code :422}
     *                      缺省 {@code return true;})或 {@code BlockItem} 的覆写,均无副作用
     */
    public static boolean fitsInsideContainer(ItemStack stack, boolean vanillaAllows) {
        return !isTemporary(stack) && vanillaAllows;
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  发放(主动技能与探针共用)
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * 女王特权主动的发放(2026-09-27 用户裁决①):**先 2 张战斗牌,再 1 张效果牌**,返回实际发放张数。
     *
     * <p>顺序与"只剩 N 格"的行为(硬约束,两线逐字一致):
     * <ol>
     *   <li>先调 {@link #grantRandom(Player, RandomCardHandler.CardCategory, int)} 发
     *       {@value #GRANT_BATTLE_COUNT} 张战斗牌(攻击 + 防御混合池,允许两张同类);</li>
     *   <li>再发 {@value #GRANT_EFFECT_COUNT} 张效果牌;② 的可用格数是**调用时重新统计**的
     *       (战斗牌已先占格)⇒ 只剩 1 格时只发 1 张战斗牌、效果牌因无空格而不发;
     *       0 格时一张都不发 —— **绝不落地**。正常情况下这条"少发"路径不可达
     *       ({@code NardisSignItem#handleUse} 第 0 步的安全门已要求空槽 ≥ {@value #MIN_FREE_SLOTS_TO_CAST}),
     *       它只是安全网(2026-09-27 用户裁决⑦第 5 条:保留不删)。</li>
     * </ol>
     *
     * <p>两张战斗牌若随机到**同一张**牌,会合并进同一个空格(卡牌 {@code stacksTo(64)},
     * 与临时标记组件相同 ⇒ {@code Inventory#add} 走合并分支)—— 这不影响"发满 3 张"的语义。
     */
    public static int grantNardisPrivilege(Player player) {
        if (player == null || player.level().isClientSide()) return 0;
        int granted = grantRandom(player, RandomCardHandler.CardCategory.BATTLE, GRANT_BATTLE_COUNT);
        granted += grantRandom(player, RandomCardHandler.CardCategory.EFFECT, GRANT_EFFECT_COUNT);
        return granted;
    }

    /**
     * 发放最多 {@code maxCount} 张**指定类别**的随机临时牌,返回**实际发放张数**。
     *
     * <p>规则(与需求「临时牌只能在物品栏和手中」一致):
     * <ol>
     *   <li>先算物品栏可用空槽数,最多发 {@code min(maxCount, 空槽数)} 张 —— **绝不落地**
     *       (满了就少发,不发到地上);</li>
     *   <li>每张走 {@link RandomCardHandler#randomCard}(指定类别)
     *       —— 专属牌由既有池构建逻辑({@code RandomCardHandler#getCardPool} 末尾的
     *       {@code items.removeIf(... EXCLUSIVE_CARDS ...)})自动排除,**不要**在这里另写专属牌过滤;</li>
     *   <li>先 {@link #mark} 再交给 {@link VitaminPillChipItem#giveCard} —— 后者是本模组**唯一的发牌漏斗**
     *       (维生素药丸治愈 +1、教主立牌狐光计层都在里面),必须走它才能「激活所有获取卡牌的触发器」。
     *       ⚠️ 看板娘 mimi 的 +1 星币**刻意不随奖励发牌触发**(仅合成与看板娘主动返还两条路),
     *       不为此新增触发。</li>
     * </ol>
     */
    public static int grantRandom(Player player, RandomCardHandler.CardCategory category, int maxCount) {
        if (player == null || player.level().isClientSide()) return 0;
        if (maxCount <= 0) return 0;
        int toGrant = Math.min(maxCount, countFreeSlots(player));
        int granted = 0;
        for (int i = 0; i < toGrant; i++) {
            ItemStack card = RandomCardHandler.randomCard(category);
            if (card.isEmpty()) break;
            mark(card);
            VitaminPillChipItem.giveCard(player, card);
            granted++;
        }
        return granted;
    }

    /** 发放最多 {@code maxCount} 张随机临时牌(池 = {@link RandomCardHandler.CardCategory#ALL}) */
    public static int grantRandom(Player player, int maxCount) {
        return grantRandom(player, RandomCardHandler.CardCategory.ALL, maxCount);
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
        WeaponEnhancement enh = dice.getOrDefault(ModDataComponents.WEAPON_ENHANCEMENT.get(),
                WeaponEnhancement.EMPTY);
        int count = 0;
        for (AppliedStone stone : enh.appliedStones()) {
            if (stone != null && stone.temporary()) count++;
        }
        return count;
    }

    /** 玩家当前佩戴的骰子(骰子饰品槽只有 1 个,不存在多骰子) */
    public static ItemStack findEquippedDice(Player player) {
        if (player == null) return ItemStack.EMPTY;
        var curios = CuriosApi.getCuriosInventory(player);
        if (curios.isEmpty()) return ItemStack.EMPTY;
        var result = curios.get().findFirstCurio(DiceCurioItem::isDiceItem);
        return result.isPresent() ? result.get().stack() : ItemStack.EMPTY;
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  容器拦截(两个 mixin 与探针共用的唯一判据)
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * 放置拦截判据(唯一实现):临时牌 **且** 目标槽不是「玩家自己的槽 / 本模组卡牌栏槽」⇒ 拦截。
     *
     * <p>{@code SlotPlaceGuardMixin}(目标 {@code Slot#mayPlace})与
     * {@code ContainerMoveGuardMixin}(目标 {@code AbstractContainerMenu#moveItemStackTo})都必须调用本方法,
     * **不得**在 mixin 里另写一份判据 —— 探针据此可直接读同一条逻辑。
     *
     * <p>为什么必须两个 mixin:{@code moveItemStackTo} 先尝试「与同类栈合并」,该分支
     * (1.21.1 实测 {@code AbstractContainerMenu.java:637-663})**完全不查 {@code mayPlace}** ⇒ 只改
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
        WeaponEnhancement enh = dice.getOrDefault(ModDataComponents.WEAPON_ENHANCEMENT.get(),
                WeaponEnhancement.EMPTY);
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
        dice.set(ModDataComponents.WEAPON_ENHANCEMENT.get(), new WeaponEnhancement(
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
     * <p>真值 = 原生效果实例 {@link ModEffects#NARDIS_PRIVILEGE};本方法处理**两个互斥的收口条件**
     * (同一次调用内 {@code hasEffect} 与"张数"各只读一次 ⇒ 两者不可能在同拍互相触发):
     * <ol>
     *   <li>「玩家身上/骰子里还有临时牌,但该玩家**没有**女王特权效果」⇒ 清空全部临时牌。
     *       效果自然到期、{@code /effect clear} 移除、离线到期后重登、异常残留**都会**走到这一条,
     *       因此不需要在任何其它地方补第二套清理逻辑(死亡路径另有一次显式清理,两者都幂等);</li>
     *   <li>**(2026-09-27 用户裁决 3(a))「效果还在、但一张临时牌都没有」⇒ 该效果实例
     *       只表示"还有临时牌在有效期内",此时应立刻移除效果**(HUD 计时器随之消失),
     *       冻结(使用中)态也随同一条判定链结束 —— 立牌的门控效果判据就是这条效果实例,
     *       见 {@code NardisSignItem#isGateEffectActive} + {@code BaseSignItem#isSignActiveLocked},
     *       由 {@code BaseSignItem#tickSignActiveLock} 迁移为"不追加新冷却"的解锁。</li>
     * </ol>
     *
     * <p><b>防环/防抖(必读)</b>:
     * <ul>
     *   <li><b>不可能每 tick 反复加/删</b>:① 移除效果走 {@link ModEffectRemoval} 内部通道,
     *       同一次 {@code MobEffectEvent.Remove} 会让 {@code ModEffectEvents#onEffectTimerForget}
     *       调 {@code EffectTimerGuard#forget} 丢掉计时记录 —— 否则计时器守卫
     *       ({@code EffectTimerGuard#tick},它在 {@code PlayerTickEvent.Pre} 里跑)下一 tick 会把
     *       "缺失"的效果**重新施加回来**,与 ② 形成每 tick 删/加的死循环。
     *       直接 {@code player.removeEffect(...)} 也不行:{@code astral_dice:*} 效果会被
     *       {@code ModEffectEvents#onModEffectRemovalPrevented} 直接取消;</li>
     *   <li><b>释放当刻不会误判为 0 张</b>:{@code NardisSignItem#handleUse} 的顺序是
     *       「安全门 → 清旧牌 → 发牌 → **施加效果**」,即效果一定在牌已经进包之后才出现;
     *       额外再加一道显式防抖 —— 效果实例剩余时长 == 满时长({@link NardisPrivilegeEffect#DURATION_TICKS})
     *       的那一拍(施加效果的当拍)**一律不判定**,即使将来有人把"先施效果后发牌"的顺序改回去也不会自我解冻。</li>
     * </ul>
     */
    public static void tick(Player player) {
        if (player == null || player.level().isClientSide()) return;
        try {
            MobEffectInstance fx = player.getEffect(ModEffects.NARDIS_PRIVILEGE);
            boolean hasCards = countTemporary(player) > 0 || countTemporaryEquipped(player) > 0;
            if (fx == null) {
                if (!hasCards) return;                       // 无效果 + 无牌:无事可做
                int removed = purgeAll(player);
                if (removed > 0) {
                    LOGGER.debug("[Astral Dice][TemporaryCard] 效果已不在,自检清空临时牌: player={} removed={}",
                            player.getName().getString(), removed);
                }
                return;
            }
            if (hasCards) return;                            // 效果在 + 还有牌:正常生效期,什么都不做
            if (fx.getDuration() >= NardisPrivilegeEffect.DURATION_TICKS) return;   // 施加当拍的防抖
            // 「临时牌已全部用光」⇒ 移除效果(唯一真值),冻结随之结束
            ModEffectRemoval.remove(player, ModEffects.NARDIS_PRIVILEGE);
            LOGGER.debug("[Astral Dice][TemporaryCard] 临时牌已用光,移除女王特权效果并解冻: player={}",
                    player.getName().getString());
        } catch (Throwable t) {
            LOGGER.error("[Astral Dice][TemporaryCard] tick 自检失败", t);
        }
    }
}
