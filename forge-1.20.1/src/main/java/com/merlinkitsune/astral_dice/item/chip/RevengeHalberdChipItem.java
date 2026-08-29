package com.merlinkitsune.astral_dice.item.chip;

import com.merlinkitsune.astral_dice.effect.ModEffects;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import com.merlinkitsune.astral_dice.item.CuriosCompat;
import top.theillusivec4.curios.api.CuriosApi;
import com.merlinkitsune.astral_dice.item.ModItems;

/**
 * 复仇之戟筹码:装备时,若身上出现指定负面/诅咒效果,则获得攻击力/防御力 +6。
 * - 攻击触发效果:虚弱、缓慢、挖掘疲劳、失明、黑暗、蓄风、盘丝、渗浆、寄生、青之诅咒
 * - 防御触发效果:饥饿、反胃、中毒、凋零、袭击之兆、试炼之兆、标记
 * 同一类别中无论存在多少个效果都只 +6,不叠加;对应效果全部消失后加成立即消失。
 */
public class RevengeHalberdChipItem extends BaseChipItem {
    /** 攻击/防御触发时提供的固定加成 */
    public static final int BONUS = 6;

    public RevengeHalberdChipItem(Properties properties) {
        super(properties);
    }

    // 玩家是否佩戴复仇之戟筹码
    public static boolean isEquipped(Player player) {
        if (player == null) return false;
        var curios = CuriosCompat.getCuriosInventory(player);
        return curios.isPresent() && curios.get().findFirstCurio(s -> s.is(ModItems.REVENGE_HALBERD.get())).isPresent();
    }

    // 是否拥有任意攻击触发效果
    public static boolean hasAttackTriggerEffect(Player player) {
        return player.hasEffect(MobEffects.WEAKNESS)
                || player.hasEffect(MobEffects.MOVEMENT_SLOWDOWN)
                || player.hasEffect(MobEffects.DIG_SLOWDOWN)
                || player.hasEffect(MobEffects.BLINDNESS)
                || player.hasEffect(MobEffects.DARKNESS)
                || player.hasEffect(ModEffects.BLUE_CURSE.get());
    }

    // 是否拥有任意防御触发效果
    public static boolean hasDefenseTriggerEffect(Player player) {
        return player.hasEffect(MobEffects.HUNGER)
                || player.hasEffect(MobEffects.CONFUSION)
                || player.hasEffect(MobEffects.POISON)
                || player.hasEffect(MobEffects.WITHER)
                || player.hasEffect(ModEffects.MARKED.get());
    }

    // 移除"复仇之戟"显示效果时的放行标志(效果移除拦截器据此放行)
    private static boolean removingEffect = false;

    public static boolean isRemovingEffect() {
        return removingEffect;
    }

    /** 当前攻击力加成(0 或 BONUS) */
    public static int currentAttackBonus(Player player) {
        return isEquipped(player) && hasAttackTriggerEffect(player) ? BONUS : 0;
    }

    /** 当前防御力加成(0 或 BONUS) */
    public static int currentDefenseBonus(Player player) {
        return isEquipped(player) && hasDefenseTriggerEffect(player) ? BONUS : 0;
    }

    /** 每 tick 驱动:任意加成触发时显示"复仇之戟"效果图标,全部消失时移除 */
    public static void updateDisplayEffect(Player player) {
        if (player.level().isClientSide()) return;
        if (isEquipped(player)
                && (hasAttackTriggerEffect(player) || hasDefenseTriggerEffect(player))) {
            player.addEffect(new MobEffectInstance(ModEffects.REVENGE_HALBERD.get(), 100, 0, false, false, true));
        } else if (player.hasEffect(ModEffects.REVENGE_HALBERD.get())) {
            removingEffect = true;
            try {
                player.removeEffect(ModEffects.REVENGE_HALBERD.get());
            } finally {
                removingEffect = false;
            }
        }
    }
}
