package com.merlinkitsune.astral_dice.item.chip;

import com.merlinkitsune.astral_dice.combat.DiceCombatModifiers;
import com.merlinkitsune.astral_dice.effect.ModEffects;
import com.merlinkitsune.astral_dice.item.CurioSlotUtil;
import com.merlinkitsune.astral_dice.item.ModItems;
import com.merlinkitsune.astral_dice.platform.util.Effects;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import com.merlinkitsune.astral_dice.compat.curios.SlotContext;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 完美树枝：触发骰神赐福时获得 1 层「超强精力」（最多 10 层），死亡时扣除 1 层。
 * 每层攻击力 +2、防御力 +1；卸下筹码清空层数。
 */
public class PerfectBranchChipItem extends BaseChipItem {
    public static final int MAX_LAYERS = 10;
    public static final String DEFENSE_MODIFIER_ID = "perfect_branch_defense";

    private static final Map<UUID, Integer> PRESERVED = new ConcurrentHashMap<>();

    public PerfectBranchChipItem(Properties properties) {
        super(properties);
    }

    public static boolean isEquipped(LivingEntity entity) {
        if (entity == null) return false;
        if (!CurioSlotUtil.hasDiceEquipped(entity)) return false;
        return CurioSlotUtil.hasAnyCurioMatching(entity, s -> s.is(ModItems.PERFECT_BRANCH_CHIP.get()));
    }

    public static int getLayers(Player player) {
        if (player == null) return 0;
        MobEffectInstance instance = player.getEffect(Effects.holder(ModEffects.SUPER_ENERGY.get()));
        return instance == null ? 0 : Math.min(MAX_LAYERS, instance.getAmplifier() + 1);
    }

    private static void applyLayers(Player player, int layers) {
        if (layers <= 0) {
            player.removeEffect(Effects.holder(ModEffects.SUPER_ENERGY.get()));
            return;
        }
        player.addEffect(new MobEffectInstance(Effects.holder(ModEffects.SUPER_ENERGY.get()),
                MobEffectInstance.INFINITE_DURATION, Math.min(MAX_LAYERS, layers) - 1, false, false, true));
    }

    public static void onBlessingStart(Player player) {
        if (player.level().isClientSide()) return;
        if (!isEquipped(player)) return;
        int current = getLayers(player);
        if (current >= MAX_LAYERS) return;
        applyLayers(player, current + 1);
    }

    /** 死亡事件（效果移除之前）暂存「当前层数 - 1」；重生时仅当筹码仍装备才恢复。 */
    public static void preserveOnDeath(Player player) {
        if (player == null || player.level().isClientSide()) return;
        if (!isEquipped(player)) {
            PRESERVED.remove(player.getUUID());
            return;
        }
        int layers = getLayers(player);
        if (layers <= 0) {
            PRESERVED.remove(player.getUUID());
            return;
        }
        PRESERVED.put(player.getUUID(), layers - 1);
    }

    public static void restoreAfterDeath(Player player) {
        if (player == null || player.level().isClientSide()) return;
        Integer saved = PRESERVED.get(player.getUUID());
        if (saved == null) return;
        if (saved <= 0) {
            PRESERVED.remove(player.getUUID());
            return;
        }
        if (!isEquipped(player)) return;
        PRESERVED.remove(player.getUUID());
        applyLayers(player, saved);
    }

    @Override
    public void curioTick(SlotContext slotContext, ItemStack stack) {
        if (!(slotContext.entity() instanceof Player player)) return;
        if (player.level().isClientSide()) return;
        int layers = CurioSlotUtil.hasDiceEquipped(player) ? getLayers(player) : 0;
        DiceCombatModifiers.setDefenseArmorBonus(player, DEFENSE_MODIFIER_ID, layers);
    }

    @Override
    protected void onChipUnequip(Player player, ItemStack stack) {
        player.removeEffect(Effects.holder(ModEffects.SUPER_ENERGY.get()));
        DiceCombatModifiers.setDefenseArmorBonus(player, DEFENSE_MODIFIER_ID, 0);
        PRESERVED.remove(player.getUUID());
    }
}
