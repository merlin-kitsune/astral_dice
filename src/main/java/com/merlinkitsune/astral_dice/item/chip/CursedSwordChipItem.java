package com.merlinkitsune.astral_dice.item.chip;

import com.merlinkitsune.astral_dice.AstralDiceMod;
import com.merlinkitsune.astral_dice.component.GameplayConstants;
import com.merlinkitsune.astral_dice.component.ModAttachments;
import com.merlinkitsune.astral_dice.effect.ModEffects;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.SlotContext;
import com.merlinkitsune.astral_dice.item.ModItems;

/**
 * 诅咒之剑筹码:装备时始终受到"青之诅咒"影响。
 * 骰神赐福期间,每击杀 1 个 20 血以上的敌对目标,攻击力 +1;
 * 每个骰神赐福效果期间最多触发一次,上限由配置
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
        ensureCurseMarker(player, curio);
    }

    @Override
    public void curioTick(SlotContext slotContext, ItemStack stack) {
        if (!(slotContext.entity() instanceof Player player)) return;
        if (player.level().isClientSide()) return;
        // 持续保持青之诅咒,防止效果因任何原因消失
        applyBlueCurse(player);
        // 确保装备中的诅咒之剑带有千咒刻印,使千咒卷轴将其计入诅咒数量
        ensureCurseMarker(player, stack);
    }

    @Override
    protected void onChipUnequip(Player player, ItemStack stack) {
        // 清除所有加成与青之诅咒效果
        ModAttachments.setCursedSwordBonus(player, 0);
        ModAttachments.setCursedSwordBlessingTriggered(player, false);
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

    // 骰神赐福期间击杀敌对目标(20 血以上)时增加 1 点攻击力;每个赐福周期最多触发一次
    public static void onKill(Player player) {
        if (player == null || player.level().isClientSide()) return;
        if (!isEquipped(player)) return;
        // 仅在骰神赐福期间生效
        if (!player.hasEffect(ModEffects.DICE_BLESSING)) return;
        // 每个骰神赐福效果期间只能触发一次
        if (ModAttachments.getCursedSwordBlessingTriggered(player)) return;
        ModAttachments.setCursedSwordBlessingTriggered(player, true);
        int current = ModAttachments.getCursedSwordBonus(player);
        int max = GameplayConstants.CURSED_SWORD_BONUS_MAX;
        if (current < max) {
            ModAttachments.setCursedSwordBonus(player, current + 1);
        }
    }

    // 为诅咒之剑附加"千咒刻印"诅咒附魔(仅用于被千咒卷轴识别为 1 点诅咒,无其他效果)
    private static void ensureCurseMarker(Player player, ItemStack stack) {
        if (stack == null || stack.isEmpty()) return;
        Holder<Enchantment> marker = player.level().registryAccess()
                .lookupOrThrow(Registries.ENCHANTMENT)
                .get(ResourceKey.create(Registries.ENCHANTMENT,
                        ResourceLocation.fromNamespaceAndPath(AstralDiceMod.MODID, "curse_marker")))
                .orElse(null);
        if (marker == null) return;
        if (stack.getEnchantments().getLevel(marker) <= 0) {
            stack.enchant(marker, 1);
        }
    }

    private static void applyBlueCurse(Player player) {
        if (!player.hasEffect(ModEffects.BLUE_CURSE)) {
            player.addEffect(new MobEffectInstance(ModEffects.BLUE_CURSE, Integer.MAX_VALUE, 0, false, true, true));
        }
    }
}
