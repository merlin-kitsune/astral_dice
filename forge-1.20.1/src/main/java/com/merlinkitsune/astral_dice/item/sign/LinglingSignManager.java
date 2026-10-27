package com.merlinkitsune.astral_dice.item.sign;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.merlinkitsune.astral_dice.AstralDiceMod;
import com.merlinkitsune.astral_dice.component.ModAttachments;
import com.merlinkitsune.astral_dice.component.ModDataComponents;
import com.merlinkitsune.astral_dice.effect.ModEffects;
import com.merlinkitsune.astral_dice.entity.RepulsionEntity;
import com.merlinkitsune.astral_dice.item.ChargeManager;
import com.merlinkitsune.astral_dice.item.CurioSlotUtil;
import com.merlinkitsune.astral_dice.item.ModItems;
import com.merlinkitsune.astral_dice.item.card.BaseEffectCardItem;
import com.merlinkitsune.astral_dice.item.card.ExclusiveCardUtil;
import com.merlinkitsune.astral_dice.item.card.RepulsionCardItem;
import com.merlinkitsune.starengine.component.GameplayConstants;
import com.merlinkitsune.starengine.event.ModEffectRemoval;
import com.merlinkitsune.starengine.item.CuriosCompat;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;

/**
 * 「怪力乱神立牌」(lingling)的全部服务端逻辑(1.20.1 Forge 移植版)。
 *
 * <p>蓝本 = {@code neoforge-1.21.1/src/main/java/com/merlinkitsune/astral_dice/item/sign/LinglingSignManager.java}
 * (只读,禁止修改)。规格唯一依据 = {@code docs/features/lingling-sign-spec.md} §4 / §5。
 *
 * <p><b>平台差异(1.20.1 Forge)</b>:
 * <ul>
 *   <li>① <b>无 DataAttachmentType</b> ⇒ 跨死亡标记落到 {@code component/ModAttachments}
 *       的玩家 NBT 键({@code lingling_granted},照既有 {@code sign_active_cooldown_end} 的写法),
 *       并在 {@code component/AstralData#onPlayerClone} 的死亡白名单里登记 ——
 *       等价于 1.21.1 蓝本的 {@code .copyOnDeath()}。</li>
 *   <li>② 属性修饰器 id 由 {@code ResourceLocation} 换为 <b>UUIDv5</b>
 *       ({@code UUID.nameUUIDFromBytes("astral_dice:" + key)},照 {@code AmeSignItem} 先例),
 *       操作枚举 {@code ADD_MULTIPLIED_TOTAL} → {@code MULTIPLY_TOTAL}。</li>
 *   <li>③ {@code ModEffects.LINGLING_SURGE} 是 {@code RegistryObject} ⇒ 全部引用处 {@code .get()}。</li>
 *   <li>④ Curios 走 {@code CuriosCompat.getCuriosInventory} 返回 {@code Optional}。</li>
 *   <li>⑤ 强化拒止(RepulsionEntity)在 1.20.1 侧<b>本来没有 owner</b> ⇒ 本次补 {@code ownerUUID}
 *       (持久化 long[2])+ {@code getTrackedTargets()},否则「只算自己放置的」无从判定(§4.6)。</li>
 * </ul>
 */
public final class LinglingSignManager {

    private LinglingSignManager() {
    }

    /** 立牌注册名(与 ModItems 注册 id 一致) */
    public static final String SIGN_ID = AstralDiceMod.MODID + ":lingling_sign";

    /** 首次装备赠送的强化拒止张数(一生一次) */
    public static final int FIRST_EQUIP_GRANT = 2;

    /** 主动技能立即赠送的强化拒止张数 */
    public static final int ACTIVE_GRANT = 1;

    /** 「目标是否被任意强化拒止吸附」查询半径(格) */
    public static final double TRACK_QUERY_RADIUS = 16.0D;

    /** 攻击被吸附目标的额外伤害(点) */
    public static final float TRACKED_TARGET_BONUS_DAMAGE = 5.0F;

    /** 被动刷新间隔(tick) */
    public static final int REFRESH_INTERVAL_TICKS = 10;

    /** 攻击力修饰器键(→ UUIDv5) */
    private static final String MODIFIER_ATTACK = "lingling_surge_attack";

    /** 移速修饰器键(→ UUIDv5) */
    private static final String MODIFIER_SPEED = "lingling_surge_speed";

    // ── 佩戴判定 ───────────────────────────────────────────────────────────────

    /**
     * 是否佩戴本立牌:Curios「stand」槽内存在 + 骰子闸门开启(与 AmeSignItem / RenSignItem 同款口径)。
     */
    public static boolean isEquipped(Player player) {
        return SignEquipCheck.isEquipped(player, ModItems.LINGLING_SIGN.get());
    }

    // ── 强化拒止 / 路障互转 ────────────────────────────────────────────────────

    /** 发放 n 张强化拒止(背包满则掉落在脚下;仅服务端) */
    public static void giveRepulsion(Player player, int count) {
        if (player == null || count <= 0 || player.level().isClientSide) return;
        ItemStack cards = BaseEffectCardItem.createCopyFor(RepulsionCardItem.CARD_TYPE_ID, player);
        if (cards.isEmpty()) return;
        cards.setCount(count);
        if (!player.getInventory().add(cards)) player.drop(cards, false);
    }

    /** 路障 → 强化拒止(数量守恒) */
    public static ItemStack toRepulsion(ItemStack barricade, Player player) {
        ItemStack cards = BaseEffectCardItem.createCopyFor(RepulsionCardItem.CARD_TYPE_ID, player);
        if (cards.isEmpty()) return barricade.copy();
        cards.setCount(barricade.getCount());
        return cards;
    }

    /** 强化拒止 → 路障(数量守恒;§4.5 丢出时的入世前替换) */
    public static ItemStack toBarricade(ItemStack repulsion) {
        return new ItemStack(ModItems.EFFECT_CARD_BARRICADE.get(), repulsion.getCount());
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

    public static boolean isBarricade(ItemStack stack) {
        return !stack.isEmpty() && stack.is(ModItems.EFFECT_CARD_BARRICADE.get());
    }

    public static boolean isRepulsion(ItemStack stack) {
        return !stack.isEmpty() && stack.is(ModItems.EFFECT_CARD_REPULSION.get());
    }

    // ── §4.1 首次装备赠牌(一生一次) ───────────────────────────────────────────

    /**
     * 首次装备发放 2 张强化拒止,<b>一生一次</b>。
     *
     * <p>⚠️ 刻意<b>不</b>用基类的 {@code claimEquipGrant} 通道 —— 那条通道在卸下时会释放标记,
     * 卸下再装可反复刷牌;本判定只读玩家级 NBT 键(跨死亡保留),卸下清零路径不会碰它。
     */
    public static boolean grantFirstEquip(Player player) {
        if (player == null || player.level().isClientSide) return false;
        if (ModAttachments.isLinglingGranted(player)) return false;
        giveRepulsion(player, FIRST_EQUIP_GRANT);
        ModAttachments.setLinglingGranted(player, true);
        return true;
    }

    // ── §4.2 / §4.4 背包 / 手持路障自动转强化拒止 ─────────────────────────────

    /** 拾取后把背包(含副手)里的路障全部转为强化拒止 */
    public static void convertBarricadesInInventory(Player player) {
        if (player == null || player.level().isClientSide) return;
        var inventory = player.getInventory();
        for (int i = 0; i < inventory.items.size(); i++) {
            ItemStack stack = inventory.items.get(i);
            if (!isBarricade(stack)) continue;
            inventory.setItem(i, toRepulsion(stack, player));
        }
        ItemStack offhand = player.getItemInHand(InteractionHand.OFF_HAND);
        if (isBarricade(offhand)) {
            player.setItemInHand(InteractionHand.OFF_HAND, toRepulsion(offhand, player));
        }
    }

    /** 主手 / 副手手持的路障转为强化拒止(10t 节流由调用方保证) */
    public static void convertHeldBarricades(Player player) {
        if (player == null || player.level().isClientSide) return;
        ItemStack main = player.getMainHandItem();
        if (isBarricade(main)) player.setItemInHand(InteractionHand.MAIN_HAND, toRepulsion(main, player));
        ItemStack offhand = player.getItemInHand(InteractionHand.OFF_HAND);
        if (isBarricade(offhand)) {
            player.setItemInHand(InteractionHand.OFF_HAND, toRepulsion(offhand, player));
        }
    }

    // ── §4.7 目标是否被吸附 ────────────────────────────────────────────────────

    /** 目标是否正被任意强化拒止吸附(§4.7 判定入口) */
    public static boolean isTrackedByAny(LivingEntity target) {
        if (target == null || target.level().isClientSide) return false;
        AABB area = target.getBoundingBox().inflate(TRACK_QUERY_RADIUS);
        for (RepulsionEntity field : target.level().getEntitiesOfClass(RepulsionEntity.class, area)) {
            if (field.isTracking(target.getUUID())) return true;
        }
        return false;
    }

    /** 有效充能层 = {@code min(20, ChargeManager.getStacks)};2026-10-21 起不再有「额外加成层」账本 */
    public static int effectiveCharge(Player player) {
        if (player == null) return 0;
        return Math.min(GameplayConstants.CHARGE_MAX_STACKS, ChargeManager.getStacks(player));
    }

    // ── 每 10t 被动刷新(§4.1 / §4.4 / §4.6 / §5 实时修饰器) ────────────────────

    /**
     * 佩戴时的被动刷新(由 {@code event/PlayerTickEvents} 每 10 tick 调用一次)。
     *
     * <p>Forge 的 {@code TickEvent.PlayerTickEvent} 每 tick 发两次(START/END) ⇒ 本方法必须**幂等**:
     * 赠牌有一次性标记、转换是幂等替换、修饰器按「值/操作不同才重挂」。
     */
    public static void tickPassive(Player player) {
        if (player == null || player.level().isClientSide) return;
        if (!isEquipped(player)) return;
        grantFirstEquip(player);
        convertHeldBarricades(player);
        // 即使效果已过期也要刷新一次,以便把残留修饰器摘净(§5「结束/卸下摘净」)
        refreshActiveModifiers(player);
    }

    public static boolean hasSurge(Player player) {
        return player != null && player.hasEffect(ModEffects.LINGLING_SURGE.get());
    }

    // ── §5 主动:实时把充能层转成攻击力 / 移速 ─────────────────────────────────

    /** 按实时有效充能层重挂属性修饰器;无「能量突击」时摘净 */
    public static void refreshActiveModifiers(Player player) {
        if (player == null) return;
        if (!hasSurge(player)) {
            clearModifiers(player);
            return;
        }
        int charge = effectiveCharge(player);
        SignAttributeUtil.apply(player, Attributes.ATTACK_DAMAGE, MODIFIER_ATTACK, charge,
                AttributeModifier.Operation.ADDITION);
        SignAttributeUtil.apply(player, Attributes.MOVEMENT_SPEED, MODIFIER_SPEED, charge / 100.0D,
                AttributeModifier.Operation.MULTIPLY_TOTAL);
    }

    /** 摘掉本立牌挂上的全部属性修饰器 */
    public static void clearModifiers(Player player) {
        if (player == null) return;
        SignAttributeUtil.remove(player, Attributes.ATTACK_DAMAGE, MODIFIER_ATTACK);
        SignAttributeUtil.remove(player, Attributes.MOVEMENT_SPEED, MODIFIER_SPEED);
    }

    /** 卸下 / 死亡:摘净修饰器 + 移除效果(**不回收**强化拒止已到账的真实充能,2026-10-21 用户裁决) */
    public static void clearAll(Player player) {
        if (player == null) return;
        clearModifiers(player);
        ModEffectRemoval.remove(player, ModEffects.LINGLING_SURGE.get());
    }

    // ══════════════════════════════════════════════════════════════════════
    //  立牌依附闸门:强化拒止只在「怪力乱神」立牌**在位**时合法
    //  (2026-10-27 用户裁决,五条规则的代码侧落地)
    // ══════════════════════════════════════════════════════════════════════

    /**
     * 立牌是否**在位**(只看 Curios {@code stand} 槽,**不含骰子闸门**)。
     *
     * <p>强化拒止的合法性判据用它,而不是 {@link #isEquipped}:换骰子只是「功能暂时不生效」,
     * 不该把强化拒止销毁;只有立牌离身才算失去依附。
     */
    public static boolean hasSign(Player player) {
        return SignEquipCheck.hasSign(player, ModItems.LINGLING_SIGN.get());
    }

    /** 该 stack 是否是**物品形态**的强化拒止({@code astral_dice:repulsion},与效果牌区分)。 */
    public static boolean isRepulsionItem(ItemStack stack) {
        return stack != null && !stack.isEmpty() && stack.is(ModItems.REPULSION.get());
    }

    /**
     * 10t 对账(幂等,服务端):让玩家持有集合与「立牌是否在位」保持一致。
     *
     * <ul>
     *   <li>立牌在位 ⇒ 未绑定 owner 的**卡牌**绑给当前持有者(并写显示名,供 tooltip);
     *       已绑给别人的卡牌原样保留({@code ExclusiveCardUtil.canUse} 会拦住使用);</li>
     *   <li>立牌不在位 ⇒ 玩家手里**全部卡牌**就地换回等张数路障(用户裁决 ①);
     *       物品形态**一律不动** —— 用户裁决 ④「物品可保留,但只有本人能取回和使用」,
     *       保留由 {@code RepulsionItem#use} 的立牌闸门 + owner 闸门拦。</li>
     * </ul>
     *
     * <p><b>为什么不在这里销毁物品</b>(2026-10-27 复核 P3 的答复):用户裁决 ② 的销毁挂钩是
     * 「**主动卸下**立牌」这一个动作({@link #onSignLost}),而裁决 ④ 明确「物品存进背包 / 箱子 /
     * 其它 mod 存储可保留」。因此非主动清槽({@code /curios set} 换空、槽位缩水挤出、第三方强拆)
     * 走本方法时**不销毁**物品:它留在背包里,但已经被使用侧的立牌闸门锁死,已放置的力场
     * 由实体侧安全网收走。这样「有立牌才能用」与「非故意清槽不吞玩家物品」两条硬规则同时成立。
     */
    public static void reconcileInventory(Player player) {
        if (player == null || player.level().isClientSide) return;
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
                // 只有本人能补名字;别人的物品原样不动(tooltip 会显示「未知」)
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

    /** 卡牌:无 owner ⇒ 绑定当前持有者;已有 owner 且是本人但缺显示名 ⇒ 补名字(老存档)。 */
    private static void bindOwnerSmartly(ItemStack stack, Player player) {
        Optional<UUID> owner = ExclusiveCardUtil.ownerOf(stack);
        if (owner.isEmpty()) {
            ExclusiveCardUtil.setOwner(stack, player);
        } else if (owner.get().equals(player.getUUID()) && ExclusiveCardUtil.ownerName(stack).isEmpty()) {
            ModDataComponents.OWNER_NAME.set(stack, player.getGameProfile().getName());
        }
    }

    /** 物品:owner 是本人且显示名缺失 ⇒ 补写(老存档物品 / 命令生成的物品)。 */
    private static void backfillItemOwnerName(ItemStack stack, Player player) {
        Optional<UUID> owner = ExclusiveCardUtil.ownerOf(stack);
        if (owner.isPresent() && owner.get().equals(player.getUUID())
                && ExclusiveCardUtil.ownerName(stack).isEmpty()) {
            ModDataComponents.OWNER_NAME.set(stack, player.getGameProfile().getName());
        }
    }

    /**
     * **立牌离身**的即时清理(用户裁决 ①②③ 一并落地;只在服务端生效、幂等)。
     *
     * <ol>
     *   <li>手里的强化拒止**卡牌**全部换回等张数路障;</li>
     *   <li>身上(主物品栏 + 副手)的强化拒止**物品**立即销毁;</li>
     *   <li>本人名下**已放置**的力场实体立即销毁。</li>
     * </ol>
     *
     * <p>死亡时跳过:掉立牌不是「主动卸下」,此时不做销毁;重生后若仍无立牌,
     * 卡牌会在下一个 10t 对账里变回路障,已放置力场由实体侧安全网回收。
     */
    public static void onSignLost(Player player) {
        if (player == null || player.level().isClientSide) return;
        if (player.isDeadOrDying()) return;
        convertRepulsionCardsToBarricade(player);
        destroyRepulsionItems(player);
        destroyOwnedFields(player);
    }

    /** 把玩家持有集合里的强化拒止**卡牌**就地换回等张数路障(物品形态不动)。返回换算张数。 */
    public static int convertRepulsionCardsToBarricade(Player player) {
        if (player == null || player.level().isClientSide) return 0;
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

    /** 销毁玩家持有集合里的强化拒止**物品**(立牌离身时调用)。返回销毁个数。 */
    public static int destroyRepulsionItems(Player player) {
        if (player == null || player.level().isClientSide) return 0;
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
     * 销毁本人名下**已放置**的全部强化拒止实体(跨维度)。
     *
     * <p>先收集再删:{@code getAllEntities()} 返回的集合在 {@code discard()} 期间可能被改写
     * (同 {@code AstralPartyCommand} 的口径)。
     */
    public static int destroyOwnedFields(Player player) {
        if (player == null || player.level().isClientSide) return 0;
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
