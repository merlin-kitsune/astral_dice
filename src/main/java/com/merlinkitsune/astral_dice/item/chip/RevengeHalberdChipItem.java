package com.merlinkitsune.astral_dice.item.chip;

import com.merlinkitsune.astral_dice.effect.ModEffects;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
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
}
