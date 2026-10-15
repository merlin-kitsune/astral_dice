package com.merlinkitsune.astral_dice.item.chip;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import com.merlinkitsune.astral_dice.item.CurioSlotUtil;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import com.merlinkitsune.astral_dice.item.ChargeManager;
import com.merlinkitsune.astral_dice.item.ModItems;
import net.minecraft.world.entity.player.Player;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.SlotContext;
import net.minecraft.world.item.ItemStack;

/**
 * 永动机筹码:
 * <ul>
 *   <li>触发骰神赐福时,若当前充能**不足 8 点**则补充至 8 点(已达标不再增加,不溢出;
 *       触发点位于 {@code DiceCombatEvents} 骰神赐福新周期开始处);</li>
 *   <li><b>原版近战冷却 -50%</b>(2026-10-15 用户裁决追加能力):装备期间攻击冷却时长减半。</li>
 * </ul>
 *
 * <p><b>「冷却 -50% ⇔ 攻击速度 ×2」的换算</b>:原版攻击冷却时长 = 1 / 攻击速度属性 × 20 tick
 * ⇒ 冷却减半等价于攻击速度属性**翻倍**。故本类用 {@link Attributes#ATTACK_SPEED} 的
 * {@code ADD_MULTIPLIED_TOTAL = }{@value #ATTACK_SPEED_MULTIPLIER} 实现(属性终值 = 原值 × 2),
 * 照同线「速度轮滑 / 夹心饼干-美味 / 摩托车头盔」的 {@code getAttributeModifiers} 先例
 * (装备期间生效、Curios 在装备变更时求值)。
 */
public class PerpetualMotionChipItem extends BaseChipItem {
    /** 触发骰神赐福时补足的充能目标层数(仅补足,不叠加;2026-10-15 用户裁决:6 → 8) */
    public static final int CHARGE_TARGET = 8;

    /**
     * 攻击速度倍率(属性修饰器 {@code ADD_MULTIPLIED_TOTAL} 的取值)。
     * <p>1.0 = 属性终值 ×2 ⇒ 攻击冷却时长 -50%(见类注释的换算)。
     */
    public static final double ATTACK_SPEED_MULTIPLIER = 1.0D;

    public PerpetualMotionChipItem(Properties properties) {
        super(properties);
    }

    /**
     * 本筹码攻击速度修饰器的 id(经 BaseChipItem 的静态派生入口取得,与实例版逐字节同源)。
     * <p>用途:骰战计算攻击冷却系数时"临时扣除本筹码的攻击速度贡献"(见
     * {@code combat.DiceCombatEvents#diceBattleAttackStrengthScale})—— 先按本 id 从 {@code ATTACK_SPEED}
     * 属性实例里取出这条修饰器,再叠加一个抵消项求原版蓄力系数。
     * <p>⚠️ 必须**延迟求值**:本类在 {@code ModItems} 注册那一刻被实例化,那时物品还没进注册表
     * ⇒ 写成静态常量会拿到空 key。
     */
    public static net.minecraft.resources.ResourceLocation attackSpeedModifierId() {
        return BaseChipItem.attributeModifierId(ModItems.PERPETUAL_MOTION.get(), "attack_speed");
    }

    /**
     * 装备期间:攻击速度 +100%(= ×2)⇒ 原版近战攻击冷却时长 -50%。
     * <p>静态加成(不随充能层数变化)⇒ 用 Curios 的 {@code getAttributeModifiers} 即可,
     * 无需照「能量回收器」那样每 tick 维护瞬态修饰器。修饰器 id 由物品注册名派生
     * ({@link BaseChipItem#attributeModifierId}),与其它筹码不冲突。
     */
    @Override
    public Multimap<Holder<Attribute>, AttributeModifier> getAttributeModifiers(
            SlotContext slotContext, ResourceLocation id, ItemStack stack) {
        Multimap<Holder<Attribute>, AttributeModifier> map = HashMultimap.create();
        map.put(Attributes.ATTACK_SPEED,
                new AttributeModifier(attackSpeedModifierId(), ATTACK_SPEED_MULTIPLIER,
                        AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        return map;
    }

    public static boolean isEquipped(Player player) {
        // 总闸门(骰子装备和卸除调整):未佩戴骰子 ⇒ 本件功能一律不生效(数值保留)
        if (!CurioSlotUtil.hasDiceEquipped(player)) return false;
        if (player == null) return false;
        var curios = CuriosApi.getCuriosInventory(player);
        return curios.isPresent() && curios.get().findFirstCurio(s -> s.is(ModItems.PERPETUAL_MOTION.get())).isPresent();
    }

    /** 触发骰神赐福时调用:佩戴永动机且当前充能不足 {@link #CHARGE_TARGET} 点时,补充至该值 */
    public static void onBlessingStart(Player player) {
        if (player == null || player.level().isClientSide()) return;
        if (!isEquipped(player)) return;
        int current = ChargeManager.getStacks(player);
        if (current >= CHARGE_TARGET) return;
        ChargeManager.addStacks(player, CHARGE_TARGET - current);
    }
}
