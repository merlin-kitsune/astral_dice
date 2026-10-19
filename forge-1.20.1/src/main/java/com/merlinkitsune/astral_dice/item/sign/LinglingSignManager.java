package com.merlinkitsune.astral_dice.item.sign;

import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;
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
import com.merlinkitsune.starengine.item.CuriosCompat;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
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
 *   <li>① <b>无 DataAttachmentType</b> ⇒ 跨死亡标记 / 额外充能层落到 {@code component/ModAttachments}
 *       的玩家 NBT 键({@code lingling_granted} / {@code lingling_bonus_charge},
 *       照既有 {@code sign_active_cooldown_end} 的写法),并在 {@code component/AstralData#onPlayerClone}
 *       的死亡白名单里登记 —— 等价于 1.21.1 蓝本的 {@code .copyOnDeath()}。</li>
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

    /** 「自己放置的强化拒止」扫描半径(格) */
    public static final double BONUS_SCAN_RADIUS = 32.0D;

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

    // ── §4.6 吸附目标数 → 额外充能层 ───────────────────────────────────────────

    /**
     * 统计「<b>自己放置的</b>强化拒止当前吸附的目标数」(去重;目标须仍在服务端可解析且存活)。
     */
    public static int countTrackedByOwnedFields(Player player) {
        if (player == null || !(player.level() instanceof ServerLevel serverLevel)) return 0;
        AABB area = player.getBoundingBox().inflate(BONUS_SCAN_RADIUS);
        int count = 0;
        Set<UUID> counted = new HashSet<>();
        for (RepulsionEntity field : serverLevel.getEntitiesOfClass(RepulsionEntity.class, area)) {
            UUID owner = field.getOwnerUUID();
            if (owner == null || !owner.equals(player.getUUID())) continue;
            for (UUID id : new HashSet<>(field.getTrackedTargets())) {
                if (!counted.add(id)) continue;
                if (!field.isTracking(id)) continue;
                if (!(serverLevel.getEntity(id) instanceof LivingEntity living) || !living.isAlive()) continue;
                count++;
            }
        }
        return count;
    }

    /** 目标是否正被任意强化拒止吸附(§4.7 判定入口) */
    public static boolean isTrackedByAny(LivingEntity target) {
        if (target == null || target.level().isClientSide) return false;
        AABB area = target.getBoundingBox().inflate(TRACK_QUERY_RADIUS);
        for (RepulsionEntity field : target.level().getEntitiesOfClass(RepulsionEntity.class, area)) {
            if (field.isTracking(target.getUUID())) return true;
        }
        return false;
    }

    /** 按当前吸附目标数重算额外充能层(值不变则不写 NBT) */
    public static void recomputeBonusCharge(Player player) {
        if (player == null || player.level().isClientSide) return;
        int bonus = countTrackedByOwnedFields(player);
        if (bonus != ModAttachments.getLinglingBonusCharge(player)) {
            ModAttachments.setLinglingBonusCharge(player, bonus);
        }
    }

    public static int bonusCharge(Player player) {
        return player == null ? 0 : ModAttachments.getLinglingBonusCharge(player);
    }

    /** 有效充能层 = 基础层 + 额外层,合计封顶 {@link GameplayConstants#CHARGE_MAX_STACKS} */
    public static int effectiveCharge(Player player) {
        if (player == null) return 0;
        return Math.min(GameplayConstants.CHARGE_MAX_STACKS, ChargeManager.getStacks(player) + bonusCharge(player));
    }

    // ── 每 10t 被动刷新(§4.1 / §4.4 / §4.6 / §5 实时修饰器) ────────────────────

    /**
     * 佩戴时的被动刷新(由 {@code event/PlayerTickEvents} 每 10 tick 调用一次)。
     *
     * <p>Forge 的 {@code TickEvent.PlayerTickEvent} 每 tick 发两次(START/END) ⇒ 本方法必须**幂等**:
     * 赠牌有一次性标记、转换是幂等替换、层数只在变化时写、修饰器按「值/操作不同才重挂」。
     */
    public static void tickPassive(Player player) {
        if (player == null || player.level().isClientSide) return;
        if (!isEquipped(player)) return;
        grantFirstEquip(player);
        recomputeBonusCharge(player);
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

    /** 卸下 / 死亡:层数归零 + 摘净修饰器 + 移除效果 */
    public static void clearAll(Player player) {
        if (player == null) return;
        ModAttachments.setLinglingBonusCharge(player, 0);
        clearModifiers(player);
        ModEffectRemoval.remove(player, ModEffects.LINGLING_SURGE.get());
    }

}
