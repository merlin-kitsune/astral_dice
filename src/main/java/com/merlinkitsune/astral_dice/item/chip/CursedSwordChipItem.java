package com.merlinkitsune.astral_dice.item.chip;

import com.merlinkitsune.astral_dice.component.GameplayConstants;
import com.merlinkitsune.astral_dice.component.ModAttachments;
import com.merlinkitsune.astral_dice.effect.ModEffects;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.SlotContext;
import com.merlinkitsune.astral_dice.item.ModItems;

/**
 * 诅咒之剑筹码:装备时始终受到"青之诅咒"影响。
 * 每击杀 1 个 20 血以上的敌对目标,攻击力 +1,上限由配置
 * {@link GameplayConstants#CURSED_SWORD_BONUS_MAX} 决定(默认 32,最大 64)。
 * 移除筹码时清除全部攻击力加成与青之诅咒效果。
 */
public class CursedSwordChipItem extends BaseChipItem {
    // 内部移除青之诅咒标记:仅用于卸下筹码时主动清理,避免被外部效果移除保护拦截
    private static boolean removingBlueCurse = false;

    public CursedSwordChipItem(Properties properties) {
        super(properties);
    }

    public static boolean isRemovingBlueCurse() {
        return removingBlueCurse;
    }

    // 玩家是否佩戴诅咒之剑筹码
    public static boolean isEquipped(Player player) {
        if (player == null) return false;
        var curios = CuriosApi.getCuriosInventory(player);
        return curios.isPresent() && curios.get().findFirstCurio(s -> s.is(ModItems.CURSED_SWORD.get())).isPresent();
    }

    @Override
    public void onEquip(SlotContext slotContext, ItemStack curio, ItemStack prevStack) {
        if (!(slotContext.entity() instanceof Player player)) return;
        if (player.level().isClientSide()) return;
        applyBlueCurse(player);
    }

    @Override
    public void curioTick(SlotContext slotContext, ItemStack stack) {
        if (!(slotContext.entity() instanceof Player player)) return;
        if (player.level().isClientSide()) return;
        // 持续保持青之诅咒,防止效果因任何原因消失
        applyBlueCurse(player);
    }

    @Override
    protected void onChipUnequip(Player player, ItemStack stack) {
        // 清除所有加成与青之诅咒效果
        ModAttachments.setCursedSwordBonus(player, 0);
        removeBlueCurse(player);
    }

    // 主动移除青之诅咒(临时放行内部移除)
    public static void removeBlueCurse(Player player) {
        removingBlueCurse = true;
        try {
            player.removeEffect(ModEffects.BLUE_CURSE);
        } finally {
            removingBlueCurse = false;
        }
    }

    // 击杀敌对目标(20 血以上)时增加 1 点攻击力,受配置上限约束
    public static void onKill(Player player) {
        if (player == null || player.level().isClientSide()) return;
        if (!isEquipped(player)) return;
        int current = ModAttachments.getCursedSwordBonus(player);
        int max = GameplayConstants.CURSED_SWORD_BONUS_MAX;
        if (current < max) {
            ModAttachments.setCursedSwordBonus(player, current + 1);
        }
    }

    private static void applyBlueCurse(Player player) {
        if (!player.hasEffect(ModEffects.BLUE_CURSE)) {
            player.addEffect(new MobEffectInstance(ModEffects.BLUE_CURSE, Integer.MAX_VALUE, 0, false, true, true));
        }
    }
}
