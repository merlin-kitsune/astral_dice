package com.merlinkitsune.astral_dice.item.card;

import com.merlinkitsune.astral_dice.event.EffectTimerGuard;

import com.merlinkitsune.astral_dice.component.ModAttachments;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * 以毒攻毒(效果牌):使用后获得 中毒 8 秒;随后移除最多 3 种原版负面效果,
 * 并获得 生命恢复 II 15 秒(隐藏效果图标)。
 */
public class FightPoisonWithPoisonCardItem extends BaseEffectCardItem {
    /** 中毒持续 tick(8 秒) */
    private static final int POISON_DURATION_TICKS = 160;
    /** 生命恢复延迟 tick(8 秒后触发) */
    private static final int REGEN_DELAY_TICKS = 160;
    /** 生命恢复持续 tick(15 秒) */
    private static final int REGEN_DURATION_TICKS = 300;

    public FightPoisonWithPoisonCardItem(Properties properties) {
        super(properties);
    }


    @Override
    protected String cardTypeId() {
        return "fight_poison_with_poison";
    }

    @Override
    protected void applyEffect(Level level, Player user, LivingEntity applyTo, ItemStack stack) {
        EffectTimerGuard.apply(applyTo, new MobEffectInstance(MobEffects.POISON, POISON_DURATION_TICKS, 0, false, true));
        if (applyTo instanceof Player targetPlayer) {
            ModAttachments.setFightPoisonWithPoisonRegenAt(targetPlayer,
                    targetPlayer.level().getGameTime() + REGEN_DELAY_TICKS);
        }
    }

    /** 每 tick 驱动:中毒结束后移除最多 3 种原版负面效果,并给予隐藏图标的生命恢复 II */
    public static void tick(Player player) {
        if (player.level().isClientSide()) return;
        long regenAt = ModAttachments.getFightPoisonWithPoisonRegenAt(player);
        if (regenAt <= 0) return;
        if (player.level().getGameTime() < regenAt) return;
        ModAttachments.setFightPoisonWithPoisonRegenAt(player, 0);
        removeUpToThreeVanillaNegativeEffects(player);
        // ambient=false, visible=false, showIcon=false → 隐藏生命恢复效果图标
        EffectTimerGuard.apply(player, new MobEffectInstance(MobEffects.REGENERATION, REGEN_DURATION_TICKS, 1, false, false, false));
    }

    /** 移除最多 3 种原版负面效果(仅 minecraft 命名空间且为 HARMFUL 分类) */
    private static void removeUpToThreeVanillaNegativeEffects(Player player) {
        int removed = 0;
        // 迭代快照:getActiveEffects() 是活跃效果 map 的实时视图,边遍历边 removeEffect 会触发
        // ConcurrentModificationException(服务端 tick 中崩溃)
        for (MobEffectInstance instance : new java.util.ArrayList<>(player.getActiveEffects())) {
            if (removed >= 3) break;
            MobEffect effect = instance.getEffect();
            String id = net.minecraftforge.registries.ForgeRegistries.MOB_EFFECTS.getKey(instance.getEffect()).toString();
            if (id != null && id.startsWith("minecraft:")
                    && effect.getCategory() == MobEffectCategory.HARMFUL) {
                player.removeEffect(instance.getEffect());
                removed++;
            }
        }
    }
}
