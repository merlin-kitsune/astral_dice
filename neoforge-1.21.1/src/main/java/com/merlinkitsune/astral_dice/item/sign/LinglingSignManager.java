package com.merlinkitsune.astral_dice.item.sign;

import java.util.List;
import java.util.UUID;

import com.merlinkitsune.astral_dice.AstralDiceMod;
import com.merlinkitsune.astral_dice.component.ModAttachments;
import com.merlinkitsune.astral_dice.effect.ModEffects;
import com.merlinkitsune.astral_dice.entity.RepulsionEntity;
import com.merlinkitsune.astral_dice.item.ChargeManager;
import com.merlinkitsune.astral_dice.item.CurioSlotUtil;
import com.merlinkitsune.astral_dice.item.ModItems;
import com.merlinkitsune.astral_dice.item.card.BaseEffectCardItem;
import com.merlinkitsune.astral_dice.item.card.RepulsionCardItem;
import com.merlinkitsune.starengine.component.GameplayConstants;
import com.merlinkitsune.starengine.event.ModEffectRemoval;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
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
 *   <li><b>额外充能层</b> —— {@code lingling_bonus_charge} attachment 记的**独立加成层**（不写进
 *       {@code ChargeEffect}）；{@link #effectiveCharge} 把「真实充能 + 额外层」夹在
 *       {@link GameplayConstants#CHARGE_MAX_STACKS} 之内。层数口径（2026-10-20 用户裁决）：
 *       **每控制一个新目标 +1**，**单座强化拒止最多提供 5 点**，同一目标重复吸附不重复计数，
 *       目标阵亡不再扣层（见 {@link #bonusChargeFromOwnedFields}）。</li>
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
    /** §4.6 额外充能层的扫描半径（格，从佩戴者出发）。 */
    public static final double BONUS_SCAN_RADIUS = 32.0D;
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
    //  §4.6 额外充能层
    // ══════════════════════════════════════════════════════════════════════

    /**
     * 汇总「**自己放置的**强化拒止」提供的额外充能层数（用户裁决 2026-10-20）。
     *
     * <p>口径（取代旧的「当前仍在吸附的目标数」与「击杀后 -1」）：
     * <ul>
     *   <li>只算 {@code ownerUUID} 是自己的力场；</li>
     *   <li>每座力场按**它控制过的不同目标数**各计 1 层，单座封顶
     *       {@link RepulsionEntity#MAX_CHARGE_STACKS}（= 5）—— 上限按**每个强化拒止实体**
     *       各 5 点，不是全局 5 点；</li>
     *   <li>同一目标重复吸附不重复计数（去重由力场自身的 {@code controlledTargets} 承担）；
     *       目标阵亡 / 脱离**不再**扣层（旧「击杀 -1」已废弃）。</li>
     * </ul>
     *
     * <p>多座相加后的总上限由 {@link #effectiveCharge} 夹在
     * {@link GameplayConstants#CHARGE_MAX_STACKS}（20）之内。
     */
    public static int bonusChargeFromOwnedFields(Player player) {
        if (player == null || player.level().isClientSide()) return 0;
        AABB area = player.getBoundingBox().inflate(BONUS_SCAN_RADIUS);
        int total = 0;
        for (RepulsionEntity field : player.level().getEntitiesOfClass(RepulsionEntity.class, area)) {
            UUID owner = field.getOwnerUUID();
            if (owner == null || !owner.equals(player.getUUID())) continue;
            total += field.chargeStacks();
        }
        return total;
    }

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

    /** 重算并写回 {@code lingling_bonus_charge}（佩戴时每 {@value #REFRESH_INTERVAL_TICKS} tick 调一次）。 */
    public static int recomputeBonusCharge(Player player) {
        if (player == null || player.level().isClientSide()) return 0;
        int bonus = bonusChargeFromOwnedFields(player);
        if (bonus != ModAttachments.getLinglingBonusCharge(player)) {
            ModAttachments.setLinglingBonusCharge(player, bonus);
        }
        return bonus;
    }

    /** 额外充能层（只读，磁盘上是 attachment；未佩戴时按 0 处理）。 */
    public static int bonusCharge(Player player) {
        if (player == null) return 0;
        return ModAttachments.getLinglingBonusCharge(player);
    }

    /**
     * §4.6 有效充能层 = {@code min(20, ChargeManager.getStacks(player) + bonus)}。
     *
     * <p>20 是「真实充能 + 额外层」的**合计**上限（{@link GameplayConstants#CHARGE_MAX_STACKS}）。
     * 额外层是独立加成层，不写进 {@code ChargeEffect}；其它系统（筹码消耗等）不感知它。
     */
    public static int effectiveCharge(Player player) {
        if (player == null) return 0;
        int stacks = ChargeManager.getStacks(player);
        int bonus = bonusCharge(player);
        return Math.min(GameplayConstants.CHARGE_MAX_STACKS, stacks + bonus);
    }

    /**
     * 佩戴态的 10t 被动刷新：首次装备发放 + 额外充能层重算 + 手持路障转换 + buff 数值刷新。
     * 不佩戴时一律空操作（清理由 {@code LinglingSignEvents} / {@code clearAll} 负责）。
     */
    public static void tickPassive(Player player) {
        if (player == null || player.level().isClientSide()) return;
        if (!isEquipped(player)) return;
        grantFirstEquip(player);
        recomputeBonusCharge(player);
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

    /** 卸下 / 失去佩戴时的完整清理：额外层归零 + 摘修饰符 + 摘 buff。 */
    public static void clearAll(Player player) {
        if (player == null) return;
        ModAttachments.setLinglingBonusCharge(player, 0);
        clearModifiers(player);
        ModEffectRemoval.remove(player, ModEffects.LINGLING_SURGE);
    }

}
