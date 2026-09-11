package com.merlinkitsune.astral_dice.item.chip;

import com.merlinkitsune.astral_dice.item.ModItems;
import net.minecraft.world.entity.player.Player;
import top.theillusivec4.curios.api.CuriosApi;

/**
 * 磨刀石筹码:
 * <ul>
 *   <li>血量不多于 50% 时:攻击力 +4;</li>
 *   <li>血量不多于 50% 时:受到的伤害 -2;</li>
 *   <li>血量大于 1 点时:使受到的伤害不超过剩余血量(不会因一次伤害被击倒)。</li>
 * </ul>
 *
 * 减伤与非致死上限由事件类在最终伤害阶段执行(见 ChipDamageHandler)。
 */
public class WhetstoneChipItem extends BaseChipItem {
    /** 攻击力加成触发的生命比例阈值(不多于 50%) */
    public static final float HEALTH_THRESHOLD = 0.5F;
    /** 低血量时提供的攻击力 */
    public static final int ATTACK_BONUS = 4;
    /** 低血量时减少的所受伤害 */
    public static final float DAMAGE_REDUCTION = 2.0F;

    public WhetstoneChipItem(Properties properties) {
        super(properties);
    }

    /** 玩家是否佩戴磨刀石 */
    public static boolean isEquipped(Player player) {
        if (player == null) return false;
        var curios = CuriosApi.getCuriosInventory(player);
        return curios.isPresent()
                && curios.get().findFirstCurio(s -> s.is(ModItems.WHETSTONE_CHIP.get())).isPresent();
    }

    /** 血量是否不多于最大生命值的 50% */
    public static boolean isLowHealth(Player player) {
        if (player == null) return false;
        return player.getHealth() <= player.getMaxHealth() * HEALTH_THRESHOLD;
    }

    /** 当前攻击力加成(0 或 {@link #ATTACK_BONUS}) */
    public static int getAttackBonus(Player player) {
        if (!isEquipped(player)) return 0;
        return isLowHealth(player) ? ATTACK_BONUS : 0;
    }

    /**
     * 受到伤害时计算修正后的伤害:
     * 佩戴且血量不多于 50% → -2;血量 > 1 时伤害被限制为不超过(剩余血量 - 1),
     * 保证不会被一次伤害击倒(至少保留 1 点血量)。
     */
    public static float modifyIncomingDamage(Player player, float damage) {
        if (damage <= 0) return damage;
        if (!isEquipped(player)) return damage;

        float reduced = damage;
        if (isLowHealth(player)) {
            reduced = Math.max(0.0F, reduced - DAMAGE_REDUCTION);
        }
        float health = player.getHealth();
        if (health > 1.0F) {
            // 「使受到的伤害不超过剩余血量」:按不可致死语义处理(至多扣到剩 1 点血量)
            float cap = health - 1.0F;
            if (reduced > cap) reduced = cap;
        }
        return reduced;
    }
}
