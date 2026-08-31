package com.merlinkitsune.astral_dice.item.chip;

import com.merlinkitsune.astral_dice.effect.ModEffects;
import com.merlinkitsune.astral_dice.event.ModEffectRemoval;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
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
        var curios = CuriosApi.getCuriosInventory(player);
        return curios.isPresent() && curios.get().findFirstCurio(s -> s.is(ModItems.REVENGE_HALBERD.get())).isPresent();
    }

    // 是否拥有任意攻击触发效果
    public static boolean hasAttackTriggerEffect(Player player) {
        return player.hasEffect(MobEffects.WEAKNESS)
                || player.hasEffect(MobEffects.MOVEMENT_SLOWDOWN)
                || player.hasEffect(MobEffects.DIG_SLOWDOWN)
                || player.hasEffect(MobEffects.BLINDNESS)
                || player.hasEffect(MobEffects.DARKNESS)
                || player.hasEffect(MobEffects.WIND_CHARGED)
                || player.hasEffect(MobEffects.WEAVING)
                || player.hasEffect(MobEffects.OOZING)
                || player.hasEffect(MobEffects.INFESTED)
                || player.hasEffect(ModEffects.BLUE_CURSE);
    }

    // 是否拥有任意防御触发效果
    public static boolean hasDefenseTriggerEffect(Player player) {
        return player.hasEffect(MobEffects.HUNGER)
                || player.hasEffect(MobEffects.CONFUSION)
                || player.hasEffect(MobEffects.POISON)
                || player.hasEffect(MobEffects.WITHER)
                || player.hasEffect(MobEffects.RAID_OMEN)
                || player.hasEffect(MobEffects.TRIAL_OMEN)
                || player.hasEffect(ModEffects.MARKED);
    }

    // 移除"复仇之戟"显示效果:经 ModEffectRemoval 内部通道放行移除拦截
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
            // 效果已存在时不重复施加,避免每 tick 触发效果更新/同步包
            if (!player.hasEffect(ModEffects.REVENGE_HALBERD)) {
                player.addEffect(new MobEffectInstance(ModEffects.REVENGE_HALBERD, 100, 0, false, false, true));
            }
        } else if (player.hasEffect(ModEffects.REVENGE_HALBERD)) {
            ModEffectRemoval.remove(player, ModEffects.REVENGE_HALBERD);
        }
    }

    /** 每 tick 驱动:防御力折算为真实护甲(1 防御力 = 2 护甲值;攻击加成仍走骰战攻击修饰器) */
    public static void updateArmorBonus(Player player) {
        com.merlinkitsune.astral_dice.combat.DiceCombatModifiers.setDefenseArmorBonus(
                player, "revenge_halberd_def_armor", currentDefenseBonus(player));
    }

    @Override
    protected void onChipUnequip(Player player, ItemStack stack) {
        com.merlinkitsune.astral_dice.combat.DiceCombatModifiers.setDefenseArmorBonus(
                player, "revenge_halberd_def_armor", 0);
    }
}
