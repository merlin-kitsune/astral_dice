package com.merlinkitsune.astral_dice.item.chip;

import com.merlinkitsune.starenginelib.item.CuriosCompat;
import com.merlinkitsune.astral_dice.component.ModAttachments;
import com.merlinkitsune.astral_dice.item.ModItems;
import net.minecraft.world.entity.player.Player;

/**
 * 磨刀石筹码:
 * <ul>
 *   <li>生命值为 50% 或更低时:攻击力 +4;</li>
 *   <li>生命值为 50% 或更低时:受到的伤害 -2;</li>
 *   <li>生命值大于 1 点时:使受到的伤害不超过剩余生命值(不会因一次伤害被击倒;该保命能力带 1:00 冷却)。</li>
 * </ul>
 *
 * 减伤与非致死上限由事件类在最终伤害阶段执行(见 ChipDamageHandler)。
 */
public class WhetstoneChipItem extends BaseChipItem {
    /** 攻击力加成触发的生命比例阈值(生命值为 50% 或更低) */
    public static final float HEALTH_THRESHOLD = 0.5F;
    /** 低生命值时提供的攻击力 */
    public static final int ATTACK_BONUS = 4;
    /** 低生命值时减少的所受伤害 */
    public static final float DAMAGE_REDUCTION = 2.0F;

    /**
     * 「保留 1 血」（不可被一次伤害击倒）的**触发冷却**：1:00。
     *
     * <p>2026-09-24 用户裁决：该能力此前**无冷却**、且生效条件与安全气囊的「致命伤害」完全重叠
     * ⇒ 气囊（消耗充能 + 1:00 冷却）的资源优势被抹平。现改为**一次保命耗一次冷却**；
     * 保命优先级为 **安全气囊 &gt; 磨刀石**（见 {@code event/ChipDamageHandler}）。
     * 冷却**只约束「保留 1 血」**：低血量减伤（-2）与攻击力 +4 不受影响。
     */
    public static final int GUARD_COOLDOWN_TICKS = 20 * 60;

    public WhetstoneChipItem(Properties properties) {
        super(properties);
    }

    /** 玩家是否佩戴磨刀石 */
    public static boolean isEquipped(Player player) {
        if (player == null) return false;
        var curios = CuriosCompat.getCuriosInventory(player);
        return curios.isPresent()
                && curios.get().findFirstCurio(s -> s.is(ModItems.WHETSTONE_CHIP.get())).isPresent();
    }

    /**
     * 生命值是否为最大生命值的 50% 或更低。
     *
     * <p>吸血鬼立牌(papara)主动「汲取」生效期间:任何以血量条件决定是否生效的效果都**视为条件通过**
     * (无条件触发),故此处直接返回 true。
     */
    public static boolean isLowHealth(Player player) {
        if (player == null) return false;
        return player.hasEffect(com.merlinkitsune.astral_dice.effect.ModEffects.PAPARA_BITE.get())
                || player.getHealth() <= player.getMaxHealth() * HEALTH_THRESHOLD;
    }

    /** 当前攻击力加成(0 或 {@link #ATTACK_BONUS}) */
    public static int getAttackBonus(Player player) {
        if (!isEquipped(player)) return 0;
        return isLowHealth(player) ? ATTACK_BONUS : 0;
    }

    /** 当前是否处于「保留 1 血」的触发冷却中(冷却中该保命能力不生效,减伤与攻击力加成不受影响) */
    public static boolean isGuardOnCooldown(Player player) {
        if (player == null) return false;
        return player.level().getGameTime() < ModAttachments.getWhetstoneGuardCooldownEnd(player);
    }

    /**
     * 受到伤害时计算修正后的伤害:
     * 佩戴且生命值为 50% 或更低 → -2;生命值 > 1 时伤害被限制为不超过(剩余生命值 - 1),
     * 保证不会被一次伤害击倒(至少保留 1 点生命值)。
     */
    public static float modifyIncomingDamage(Player player, float damage) {
        if (damage <= 0) return damage;
        if (!isEquipped(player)) return damage;

        float reduced = damage;
        if (isLowHealth(player)) {
            reduced = Math.max(0.0F, reduced - DAMAGE_REDUCTION);
        }
        float health = player.getHealth();
        // ⚠️ 但该保命能力自 2026-09-24 起**带 1:00 冷却**(用户裁决):冷却中不再提供保护 ——
        //    此时只有安全气囊能接住致命一击(保命优先级 **安全气囊 > 磨刀石**,
        //    见 event/ChipDamageHandler)。冷却**只约束这一条**:上面的 -2 减伤不受影响。
        boolean guardActive = !isGuardOnCooldown(player)
                && (health > 1.0F
                || player.hasEffect(com.merlinkitsune.astral_dice.effect.ModEffects.PAPARA_BITE.get()));
        if (guardActive) {
            // 「使受到的伤害不超过剩余生命值」:按不可致死语义处理(至多扣到剩 1 点生命值)
            float cap = Math.max(0.0F, health - 1.0F);
            if (reduced > cap) {
                reduced = cap;
                // 保命**实际生效**才进冷却(不致命的攻击不消耗冷却);仅服务端写入
                if (!player.level().isClientSide()) {
                    ModAttachments.setWhetstoneGuardCooldownEnd(player,
                            player.level().getGameTime() + GUARD_COOLDOWN_TICKS);
                }
            }
        }
        return reduced;
    }
}
