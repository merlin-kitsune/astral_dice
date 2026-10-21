package com.merlinkitsune.astral_dice.event;

import com.merlinkitsune.astral_dice.effect.DorothyWarmthEffect;
import com.merlinkitsune.astral_dice.effect.ModEffects;
import com.merlinkitsune.astral_dice.item.sign.AlanaSignItem;
import com.merlinkitsune.astral_dice.item.sign.DorothySignItem;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.event.entity.living.LivingChangeTargetEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.living.MobEffectEvent;

/**
 * 本批 4 个新立牌的通用事件接线。
 *
 * <ul>
 *   <li>铁处女 / 摸鱼：免疫一切伤害与负面效果；摸鱼还要「不被怪物索敌」。</li>
 *   <li>多萝西：获得治疗（生命恢复效果）按等级叠【温暖】。</li>
 *   <li>修女：受击 &gt;10 点移除被动加成；骰神赐福结束始终移除。</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = "astral_dice")
public final class NewSignEvents {

    private static boolean immune(Player player) {
        return player.hasEffect(ModEffects.ALANA_IRON_MAIDEN.get()) || player.hasEffect(ModEffects.DEEPSEEK_FISHING.get());
    }

    @SubscribeEvent
    public static void onIncomingDamage(LivingHurtEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (player.level().isClientSide()) return;
        if (immune(player)) {
            event.setCanceled(true);
            return;
        }
        // 修女被动：赐福期间单次受击 >10 点 ⇒ 立即移除全部加成
        if (event.getAmount() > 10.0F && com.merlinkitsune.astral_dice.component.ModAttachments
                .getAlanaPassiveBonus(player) > 0) {
            AlanaSignItem.clearBonus(player);
        }
    }

    @SubscribeEvent
    public static void onEffectApplicable(MobEffectEvent.Applicable event) {
        // 2026-10-21 用户裁决：路障 / 强化拒止不吃任何药水
        if (event.getEntity() instanceof com.merlinkitsune.astral_dice.entity.BarricadeEntity
                || event.getEntity() instanceof com.merlinkitsune.astral_dice.entity.RepulsionEntity) {
            event.setResult(net.minecraftforge.eventbus.api.Event.Result.DENY);
            return;
        }
        if (!(event.getEntity() instanceof Player player)) return;
        if (player.level().isClientSide()) return;
        if (!immune(player)) return;
        if (event.getEffectInstance().getEffect().getCategory()
                == net.minecraft.world.effect.MobEffectCategory.HARMFUL) {
            event.setResult(net.minecraftforge.eventbus.api.Event.Result.DENY);
        }
    }

    @SubscribeEvent
    public static void onChangeTarget(LivingChangeTargetEvent event) {
        if (event.getNewTarget() instanceof Player player
                && player.hasEffect(ModEffects.DEEPSEEK_FISHING.get())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onEffectAdded(MobEffectEvent.Added event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (player.level().isClientSide()) return;
        MobEffectInstance instance = event.getEffectInstance();
        if (instance == null) return;
        if (!instance.getEffect().equals(ModEffects.DOROTHY_WARMTH.get())) {
            if (!DorothySignItem.isEquippedForWarmth(player)) return;
            boolean healing = instance.getEffect().equals(MobEffects.REGENERATION)
                    || instance.getEffect().equals(MobEffects.HEAL);
            if (!healing) return;
            grantWarmth(player, instance.getAmplifier() + 1);
        }
    }

    private static void grantWarmth(Player player, int stacks) {
        int current = DorothySignItem.warmthStacks(player);
        int next = Math.min(DorothyWarmthEffect.MAX_STACKS, current + Math.max(1, stacks));
        if (next <= current) return;
        player.addEffect(new MobEffectInstance(ModEffects.DOROTHY_WARMTH.get(), -1,
                next - 1, false, false, true));
        DorothySignItem.refreshWarmthModifier(player);
    }

    @SubscribeEvent
    public static void onEffectRemoved(MobEffectEvent.Remove event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (player.level().isClientSide()) return;
        if (event.getEffectInstance() != null
                && event.getEffectInstance().getEffect().equals(ModEffects.DICE_BLESSING.get())) {
            AlanaSignItem.clearBonus(player);
        }
    }

    @SubscribeEvent
    public static void onEffectExpired(MobEffectEvent.Expired event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (event.getEffectInstance() != null
                && event.getEffectInstance().getEffect().equals(ModEffects.DICE_BLESSING.get())) {
            AlanaSignItem.clearBonus(player);
        }
    }
}
