package com.merlinkitsune.astral_dice.item.chip;

import com.merlinkitsune.astral_dice.item.CuriosCompat;
import com.merlinkitsune.astral_dice.item.ModItems;
import net.minecraft.world.entity.player.Player;

/**
 * 磨刀石筹码:
 * <ul>
 *   <li>生命值为 50% 或更低时:攻击力 +4;</li>
 *   <li>生命值为 50% 或更低时:受到的伤害 -2;</li>
 *   <li>生命值大于 1 点时:使受到的伤害不超过剩余生命值(不会因一次伤害被击倒)。</li>
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
        // 「汲取」期间视为满血/无条件通过:血量 > 1 的不可击杀保护同样无条件生效
        boolean guardActive = health > 1.0F
                || player.hasEffect(com.merlinkitsune.astral_dice.effect.ModEffects.PAPARA_BITE.get());
        if (guardActive) {
            // 「使受到的伤害不超过剩余生命值」:按不可致死语义处理(至多扣到剩 1 点生命值)
            float cap = health - 1.0F;
            if (reduced > cap) reduced = cap;
        }
        return reduced;
    }
}
