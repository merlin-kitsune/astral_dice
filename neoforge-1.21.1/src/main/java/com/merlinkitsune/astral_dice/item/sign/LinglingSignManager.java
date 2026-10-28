package com.merlinkitsune.astral_dice.item.sign;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.merlinkitsune.astral_dice.AstralDiceMod;
import com.merlinkitsune.astral_dice.component.ModAttachments;
import com.merlinkitsune.astral_dice.effect.ModEffects;
import com.merlinkitsune.astral_dice.entity.RepulsionEntity;
import com.merlinkitsune.astral_dice.item.ChargeManager;
import com.merlinkitsune.astral_dice.item.CurioSlotUtil;
import com.merlinkitsune.astral_dice.item.ModItems;
import com.merlinkitsune.astral_dice.component.ModDataComponents;
import com.merlinkitsune.astral_dice.item.card.BaseEffectCardItem;
import com.merlinkitsune.astral_dice.item.card.ExclusiveCardUtil;
import com.merlinkitsune.astral_dice.item.card.RepulsionCardItem;
import com.merlinkitsune.starengine.component.GameplayConstants;
import com.merlinkitsune.starengine.event.ModEffectRemoval;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import top.theillusivec4.curios.api.CuriosApi;

/**
 * 怪力乱神立牌（{@code lingling}，史诗）的**被动「拦截者」**逻辑中枢。
 *
 * <p>被动口径（§4，全部只在服务端落地，且全部幂等）：
 * <ol>
 *   <li><b>首次装备发放</b> —— 每位玩家**一生一次**发 {@value #FIRST_EQUIP_GRANT} 张强化拒止；
 *       用 attachment 布尔标记 {@code lingling_granted}（{@code copyOnDeath}，跨死亡保留）落地，
 *       重登 / Curios 重放 equip 都不会重复发放（<b>不得</b>改用
 *       {@code StarLightManager.claimEquipGrant} —— 那条链卸下会 release，可以刷）。</li>
 *   <li><b>路障 → 强化拒止</b> —— 拾取（{@code LinglingSignEvents}）、购买（{@code CardShopMenu}）、
 *       手持（{@code PlayerTickEvents}）三个入口全部收敛到本类的 {@link #toRepulsion}。</li>
 *   <li><b>强化拒止 → 充能</b> —— 2026-10-21 用户裁决（取代旧的「按距离重算层数」）：
 *       强化拒止吸附到一个**新**目标时，由 {@code RepulsionEntity#awardCharge} 立刻给**放置者**
 *       +1 **真实充能**（{@link ChargeManager#addStacks}），单座最多 5 点、**永久到账**
 *       （玩家走远 / 目标脱离 / 力场碎裂或到期都不回收）。本类**不再**做任何充能增删，
 *       只读 {@link ChargeManager#getStacks} 计算有效层数。</li>
 *   <li><b>能量突击数值</b> —— buff 期间每 {@value #REFRESH_INTERVAL_TICKS} tick 用固定
 *       {@link ResourceLocation} 重刷同两个修饰符（攻击力 {@code ADD_VALUE +有效充能}、
 *       移速 {@code ADD_MULTIPLIED_TOTAL +有效充能/100}），因此重复触发只覆盖数值、不叠加。</li>
 * </ol>
 */
public final class LinglingSignManager {

    /** 立牌 id（{@code astral_dice:lingling_sign}）。 */
    public static final String SIGN_ID = AstralDiceMod.MODID + ":lingling_sign";

    /** §4.1 首次装备发放的强化拒止张数。 */
    public static final int FIRST_EQUIP_GRANT = 2;
    /** §5 主动施放时发放的强化拒止张数。 */
    public static final int ACTIVE_GRANT = 1;
    /** §4.7 「被吸附」判定半径（格，从受击目标出发，任意强化拒止都算）。 */
    public static final double TRACK_QUERY_RADIUS = 16.0D;
    /** §4.7 命中被吸附目标的追加伤害（点）。 */
    public static final float TRACKED_TARGET_BONUS_DAMAGE = 5.0F;
    /** 佩戴态被动刷新节流（tick）：额外充能层 + 首次装备发放 + 手持转换共用这一档。 */
    public static final int REFRESH_INTERVAL_TICKS = 10;

    /** 攻击力修饰符 id 后缀（固定 key ⇒ 只更新同一个修饰符，永不叠加）。 */
    private static final String MODIFIER_ATTACK = "lingling_surge_attack";
    /** 移速修饰符 id 后缀。 */
    private static final String MODIFIER_SPEED = "lingling_surge_speed";

    private LinglingSignManager() {}

    // ══════════════════════════════════════════════════════════════════════
    //  佩戴判定 / 发放
    // ══════════════════════════════════════════════════════════════════════

    /**
     * 是否**功能生效**地佩戴了怪力乱神立牌：Curios {@code stand} 槽装着本牌，且骰子槽仍有骰子
     * （与 {@code AmeSignItem#isEquipped} 同一口径 —— 本仓「未佩戴骰子 ⇒ 立牌功能一律不生效」）。
     */
    public static boolean isEquipped(Player player) {
        return SignEquipCheck.isEquipped(player, ModItems.LINGLING_SIGN.get());
    }

    /** 发 {@code count} 张强化拒止（背包满则掉在脚下，同 §4.1 口径）；返回实际发放张数。 */
    public static int giveRepulsion(Player player, int count) {
        if (player == null || player.level().isClientSide() || count <= 0) return 0;
        ItemStack cards = BaseEffectCardItem.createCopyFor(RepulsionCardItem.CARD_TYPE_ID, player);
        cards.setCount(count);
        if (!player.getInventory().add(cards)) {
            player.drop(cards, false);
        }
        return count;
    }

    /**
     * §4.1 首次装备发放：标记未置位 ⇒ 发 2 张强化拒止 ⇒ 置位（幂等，一生一次）。
     *
     * @return 本次是否真的发放了
     */
    public static boolean grantFirstEquip(Player player) {
        if (player == null || player.level().isClientSide()) return false;
        if (ModAttachments.isLinglingGranted(player)) return false;
        giveRepulsion(player, FIRST_EQUIP_GRANT);
        ModAttachments.setLinglingGranted(player, true);
        return true;
    }

    // ══════════════════════════════════════════════════════════════════════
    //  路障 ⇄ 强化拒止 转换工具（§4.2 / §4.3 / §4.4 / §4.5 共用）
    // ══════════════════════════════════════════════════════════════════════

    /** 等张数地把一张路障换成强化拒止（强化拒止是独占牌 ⇒ 顺带绑给 {@code player}）。 */
    public static ItemStack toRepulsion(ItemStack barricade, Player player) {
        if (barricade == null || barricade.isEmpty()) return ItemStack.EMPTY;
        ItemStack repulsion = BaseEffectCardItem.createCopyFor(RepulsionCardItem.CARD_TYPE_ID, player);
        repulsion.setCount(barricade.getCount());
        return repulsion;
    }

    /** 等张数地把一张强化拒止换回路障（§4.5 丢出转换）。 */
    public static ItemStack toBarricade(ItemStack repulsion) {
        if (repulsion == null || repulsion.isEmpty()) return ItemStack.EMPTY;
        ItemStack barricade = new ItemStack(ModItems.EFFECT_CARD_BARRICADE.get(), repulsion.getCount());
        return barricade;
    }

    /**
     * 纯显示用：一张路障 ⇒ 等张数、<b>未绑定 owner</b> 的强化拒止（不写附件、不查服务端、不改任何状态；客户端安全）。
     * <p>与 {@link #toRepulsion} 的区别：后者 {@code createCopyFor} 绑定 owner，只给服务端真实发放 / 转换用。
     */
    public static ItemStack displayAsRepulsion(ItemStack barricade) {
        if (barricade == null || barricade.isEmpty()) return ItemStack.EMPTY;
        return new ItemStack(ModItems.EFFECT_CARD_REPULSION.get(), barricade.getCount());
    }

    /**
     * **落位时真正入包的物品**:佩戴「怪力乱神」立牌时「路障 ⇒ 等张数强化拒止」,其余一律原样返回。
     *
     * <p><b>唯一判定源</b>:服务端购买落位({@code CardShopMenu#handleBuy})与客户端可受理判定
     * ({@code CardShopScreen#canAcceptDrop})都调本方法 —— 两侧口径只有一份,不再各写一遍转换条件
     * (否则客户端按「货架原物」比较、服务端按「转换后物品」落位 ⇒ 戴铃铃时拖到已有强化拒止栈上会被误拦)。
     *
     * <p><b>客户端安全</b>:{@link #toRepulsion} 只做物品构造 + owner 组件写入(不读写世界 / 附件),
     * 双端产物组件一致 ⇒ {@code ItemStack.isSameItemSameComponents / isSameItemSameTags} 的比较结果一致。
     */
    public static ItemStack resolvePlacedItem(ItemStack shelfStack, Player player) {
        if (!isBarricade(shelfStack)) return shelfStack;
        if (player == null || !isEquipped(player)) return shelfStack;
        return toRepulsion(shelfStack, player);
    }

    /** 该 stack 是否是需要被换掉的路障。 */
    public static boolean isBarricade(ItemStack stack) {
        return stack != null && !stack.isEmpty() && stack.is(ModItems.EFFECT_CARD_BARRICADE.get());
    }

    /** 该 stack 是否是会被丢出转回的路障的强化拒止。 */
    public static boolean isRepulsion(ItemStack stack) {
        return stack != null && !stack.isEmpty() && stack.is(ModItems.EFFECT_CARD_REPULSION.get());
    }

    /**
     * §4.2 拾取转换：把玩家**持有集合**（主物品栏全部格子 + 副手）里的路障全部换成等张数强化拒止。
     *
     * <p>为什么是「持有集合扫描」而不是拿拾取事件的到手 stack：{@code ItemEntityPickupEvent.Post}
     * 在**物品已进背包之后**才触发，且 NeoForge 明确禁止在事件里调 {@code ItemEntity#setItem}
     * （未定义行为）。扫描幂等，且与 §4.4 的手持转换天然同向（佩戴者手里不可能长期留路障）。
     *
     * @return 实际转换的总张数（0 = 一张路障都没有）
     */
    public static int convertBarricadesInInventory(Player player) {
        if (player == null || player.level().isClientSide()) return 0;
        int converted = 0;
        List<ItemStack> items = player.getInventory().items;
        for (int i = 0; i < items.size(); i++) {
            ItemStack stack = items.get(i);
            if (!isBarricade(stack)) continue;
            converted += stack.getCount();
            player.getInventory().setItem(i, toRepulsion(stack, player));
        }
        ItemStack off = player.getOffhandItem();
        if (isBarricade(off)) {
            converted += off.getCount();
            player.setItemInHand(InteractionHand.OFF_HAND, toRepulsion(off, player));
        }
        return converted;
    }

    /**
     * §4.4 手持自动转换：主手 + 副手各看一格（{@code PlayerTickEvents} 10t 节流调用，幂等）。
     *
     * @return 实际转换的张数
     */
    public static int convertHeldBarricades(Player player) {
        if (player == null || player.level().isClientSide()) return 0;
        int converted = 0;
        ItemStack main = player.getMainHandItem();
        if (isBarricade(main)) {
            converted += main.getCount();
            player.setItemInHand(InteractionHand.MAIN_HAND, toRepulsion(main, player));
        }
        ItemStack off = player.getOffhandItem();
        if (isBarricade(off)) {
            converted += off.getCount();
            player.setItemInHand(InteractionHand.OFF_HAND, toRepulsion(off, player));
        }
        return converted;
    }

    // ══════════════════════════════════════════════════════════════════════
    //  §4.7 目标是否被吸附
    // ══════════════════════════════════════════════════════════════════════

    /**
     * §4.7 目标是否正被**任意**强化拒止吸附（不分归属）。判定口径：以目标包围盒 inflate
     * {@value #TRACK_QUERY_RADIUS} 格取全部力场，任一力场的 {@code trackedTargets} 含该 UUID 即算。
     */
    public static boolean isTrackedByAny(LivingEntity target) {
        if (target == null || target.level().isClientSide()) return false;
        List<RepulsionEntity> fields = target.level().getEntitiesOfClass(RepulsionEntity.class,
                target.getBoundingBox().inflate(TRACK_QUERY_RADIUS));
        for (RepulsionEntity field : fields) {
            if (field.isTracking(target.getUUID())) return true;
        }
        return false;
    }

    /**
     * 有效充能层 = {@code min(20, ChargeManager.getStacks(player))}。
     *
     * <p>2026-10-21 起强化拒止给的层数是**真实充能**（{@code ChargeEffect}），这里不再有
     * 「额外加成层」的独立账本 —— 只剩总上限 {@link GameplayConstants#CHARGE_MAX_STACKS} 的夹取。
     */
    public static int effectiveCharge(Player player) {
        if (player == null) return 0;
        return Math.min(GameplayConstants.CHARGE_MAX_STACKS, ChargeManager.getStacks(player));
    }

    /**
     * 佩戴态的 10t 被动刷新：首次装备发放 + 手持路障转换 + buff 数值刷新。
     * 不佩戴时一律空操作（清理由 {@code LinglingSignEvents} / {@code clearAll} 负责）。
     */
    public static void tickPassive(Player player) {
        if (player == null || player.level().isClientSide()) return;
        if (!isEquipped(player)) return;
        grantFirstEquip(player);
        convertHeldBarricades(player);
        // 无论 buff 是否还在都刷一遍：buff 已到期时，这一步就是「把两个修饰符摘干净」的唯一时机
        // （hasSurge = false ⇒ refreshActiveModifiers 走清空分支），漏掉会留下永久攻击力/移速加成。
        refreshActiveModifiers(player);
    }

    // ══════════════════════════════════════════════════════════════════════
    //  §5 能量突击的实时属性
    // ══════════════════════════════════════════════════════════════════════

    /** buff 是否在身上。 */
    public static boolean hasSurge(Player player) {
        return player != null && player.hasEffect(ModEffects.LINGLING_SURGE);
    }

    /**
     * 按当前 {@link #effectiveCharge} 重刷 buff 的两个修饰符（固定 id，重复调用只改数值）。
     * 攻击力 = {@code ADD_VALUE +charge}；移速 = {@code ADD_MULTIPLIED_TOTAL +charge/100}
     * （5 点 = 5%）。buff 已不在身上时走清空分支（不留残余修饰符）。
     */
    public static void refreshActiveModifiers(Player player) {
        if (player == null || player.level().isClientSide()) return;
        if (!hasSurge(player)) {
            clearModifiers(player);
            return;
        }
        int charge = effectiveCharge(player);
        SignAttributeUtil.apply(player, Attributes.ATTACK_DAMAGE, MODIFIER_ATTACK, charge,
                AttributeModifier.Operation.ADD_VALUE);
        SignAttributeUtil.apply(player, Attributes.MOVEMENT_SPEED, MODIFIER_SPEED, charge / 100.0D,
                AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
    }

    /** 摘掉 buff 用的全部修饰符（buff 结束 / 卸下 / 死亡都必须走到这里）。 */
    public static void clearModifiers(Player player) {
        if (player == null) return;
        SignAttributeUtil.remove(player, Attributes.ATTACK_DAMAGE, MODIFIER_ATTACK);
        SignAttributeUtil.remove(player, Attributes.MOVEMENT_SPEED, MODIFIER_SPEED);
    }

    /**
     * 卸下 / 失去佩戴时的完整清理：摘修饰符 + 摘 buff。
     *
     * <p>⚠️ **不再回收充能**：强化拒止给的是真实充能且「永久到账」（2026-10-21 用户裁决），
     * 卸下立牌只影响 buff 数值，不得回扣玩家已到账的层数。
     */
    public static void clearAll(Player player) {
        if (player == null) return;
        clearModifiers(player);
        ModEffectRemoval.remove(player, ModEffects.LINGLING_SURGE);
    }

    // ══════════════════════════════════════════════════════════════════════
    //  立牌依附闸门：强化拒止只在「怪力乱神」立牌**在位**时合法
    //  （2026-10-27 用户裁决，五条规则的代码侧落地）
    // ══════════════════════════════════════════════════════════════════════

    /**
     * 立牌是否**在位**（只看 Curios {@code stand} 槽，**不含骰子闸门**）。
     *
     * <p>强化拒止的合法性判据用它，而不是 {@link #isEquipped}：换骰子只是「功能暂时不生效」，
     * 不该把强化拒止销毁；只有立牌离身才算失去依附。
     */
    public static boolean hasSign(Player player) {
        return SignEquipCheck.hasSign(player, ModItems.LINGLING_SIGN.get());
    }

    /** 该 stack 是否是**物品形态**的强化拒止（{@code astral_dice:repulsion}，与效果牌区分）。 */
    public static boolean isRepulsionItem(ItemStack stack) {
        return stack != null && !stack.isEmpty() && stack.is(ModItems.REPULSION.get());
    }

    /**
     * 10t 对账（幂等，服务端）：让玩家持有集合与「立牌是否在位」保持一致。
     *
     * <ul>
     *   <li>立牌在位 ⇒ 未绑定 owner 的**卡牌**绑给当前持有者（并写显示名，供 tooltip）；
     *       已绑给别人的卡牌原样保留（{@code ExclusiveCardUtil.canUse} 会拦住使用）；</li>
     *   <li>立牌不在位 ⇒ 玩家手里**全部卡牌**就地换回等张数路障（用户裁决 ①）；
     *       物品形态**一律不动** —— 用户裁决 ④「物品可保留，但只有本人能取回和使用」，
     *       保留由 {@code RepulsionItem#use} 的立牌闸门 + owner 闸门拦。</li>
     * </ul>
     *
     * <p><b>为什么不在这里销毁物品</b>（2026-10-27 复核 P3 的答复）：用户裁决 ② 的销毁挂钩是
     * 「**主动卸下**立牌」这一个动作（{@link #onSignLost}），而裁决 ④ 明确「物品存进背包 / 箱子 /
     * 其它 mod 存储可保留」。因此非主动清槽（{@code /curios set} 换空、槽位缩水挤出、第三方强拆）
     * 走本方法时**不销毁**物品：它留在背包里，但已经被使用侧的立牌闸门锁死，已放置的力场
     * 由实体侧安全网收走。这样「有立牌才能用」与「非故意清槽不吞玩家物品」两条硬规则同时成立。
     */
    public static void reconcileInventory(Player player) {
        if (player == null || player.level().isClientSide()) return;
        boolean attached = hasSign(player);
        List<ItemStack> items = player.getInventory().items;
        for (int i = 0; i < items.size(); i++) {
            ItemStack stack = items.get(i);
            if (isRepulsion(stack)) {
                if (attached) {
                    bindOwnerSmartly(stack, player);
                } else {
                    player.getInventory().setItem(i, toBarricade(stack));
                }
                continue;
            }
            if (isRepulsionItem(stack)) {
                // 只有本人能补名字；别人的物品原样不动（tooltip 会显示「未知」）
                backfillItemOwnerName(stack, player);
            }
        }
        ItemStack off = player.getOffhandItem();
        if (isRepulsion(off)) {
            if (attached) {
                bindOwnerSmartly(off, player);
            } else {
                player.setItemInHand(InteractionHand.OFF_HAND, toBarricade(off));
            }
        } else if (isRepulsionItem(off)) {
            backfillItemOwnerName(off, player);
        }
    }

    /** 卡牌：无 owner ⇒ 绑定当前持有者；已有 owner 且是本人但缺显示名 ⇒ 补名字（老存档）。 */
    private static void bindOwnerSmartly(ItemStack stack, Player player) {
        Optional<UUID> owner = ExclusiveCardUtil.ownerOf(stack);
        if (owner.isEmpty()) {
            ExclusiveCardUtil.setOwner(stack, player);
        } else if (owner.get().equals(player.getUUID()) && ExclusiveCardUtil.ownerName(stack).isEmpty()) {
            stack.set(ModDataComponents.OWNER_NAME.get(), player.getGameProfile().getName());
        }
    }

    /** 物品：owner 是本人且显示名缺失 ⇒ 补写（老存档物品 / 命令生成的物品）。 */
    private static void backfillItemOwnerName(ItemStack stack, Player player) {
        Optional<UUID> owner = ExclusiveCardUtil.ownerOf(stack);
        if (owner.isPresent() && owner.get().equals(player.getUUID())
                && ExclusiveCardUtil.ownerName(stack).isEmpty()) {
            stack.set(ModDataComponents.OWNER_NAME.get(), player.getGameProfile().getName());
        }
    }

    /**
     * **立牌离身**的即时清理（用户裁决 ①②③ 一并落地；只在服务端生效、幂等）。
     *
     * <ol>
     *   <li>手里的强化拒止**卡牌**全部换回等张数路障；</li>
     *   <li>身上（主物品栏 + 副手）的强化拒止**物品**立即销毁；</li>
     *   <li>本人名下**已放置**的力场实体立即销毁。</li>
     * </ol>
     *
     * <p>死亡时跳过：掉立牌不是「主动卸下」，此时不做销毁；重生后若仍无立牌，
     * 卡牌会在下一个 10t 对账里变回路障，已放置力场由实体侧安全网回收。
     */
    public static void onSignLost(Player player) {
        if (player == null || player.level().isClientSide()) return;
        if (player.isDeadOrDying()) return;
        convertRepulsionCardsToBarricade(player);
        destroyRepulsionItems(player);
        destroyOwnedFields(player);
    }

    /** 把玩家持有集合里的强化拒止**卡牌**就地换回等张数路障（物品形态不动）。返回换算张数。 */
    public static int convertRepulsionCardsToBarricade(Player player) {
        if (player == null || player.level().isClientSide()) return 0;
        int converted = 0;
        List<ItemStack> items = player.getInventory().items;
        for (int i = 0; i < items.size(); i++) {
            ItemStack stack = items.get(i);
            if (!isRepulsion(stack)) continue;
            converted += stack.getCount();
            player.getInventory().setItem(i, toBarricade(stack));
        }
        ItemStack off = player.getOffhandItem();
        if (isRepulsion(off)) {
            converted += off.getCount();
            player.setItemInHand(InteractionHand.OFF_HAND, toBarricade(off));
        }
        return converted;
    }

    /** 销毁玩家持有集合里的强化拒止**物品**（立牌离身时调用）。返回销毁个数。 */
    public static int destroyRepulsionItems(Player player) {
        if (player == null || player.level().isClientSide()) return 0;
        int destroyed = 0;
        List<ItemStack> items = player.getInventory().items;
        for (int i = 0; i < items.size(); i++) {
            ItemStack stack = items.get(i);
            if (!isRepulsionItem(stack)) continue;
            destroyed += stack.getCount();
            player.getInventory().setItem(i, ItemStack.EMPTY);
        }
        ItemStack off = player.getOffhandItem();
        if (isRepulsionItem(off)) {
            destroyed += off.getCount();
            player.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
        }
        return destroyed;
    }

    /**
     * 销毁本人名下**已放置**的全部强化拒止实体（跨维度）。
     *
     * <p>先收集再删：{@code getAllEntities()} 返回的集合在 {@code discard()} 期间可能被改写
     * （同 {@code AstralPartyCommand} 的口径）。
     */
    public static int destroyOwnedFields(Player player) {
        if (player == null || player.level().isClientSide()) return 0;
        var server = player.getServer();
        if (server == null) return 0;
        UUID owner = player.getUUID();
        List<RepulsionEntity> doomed = new ArrayList<>();
        for (ServerLevel level : server.getAllLevels()) {
            for (Entity entity : level.getAllEntities()) {
                if (entity instanceof RepulsionEntity field && owner.equals(field.getOwnerUUID())) {
                    doomed.add(field);
                }
            }
        }
        for (RepulsionEntity field : doomed) {
            field.destroyByOwnerLoss();
        }
        return doomed.size();
    }

}
