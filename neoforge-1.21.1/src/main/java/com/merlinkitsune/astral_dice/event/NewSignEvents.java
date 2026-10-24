package com.merlinkitsune.astral_dice.event;

import com.merlinkitsune.astral_dice.effect.DorothyWarmthEffect;
import com.merlinkitsune.astral_dice.effect.ModEffects;
import com.merlinkitsune.astral_dice.item.sign.AlanaSignItem;
import com.merlinkitsune.astral_dice.item.sign.DorothySignItem;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent;
import net.neoforged.neoforge.event.entity.living.LivingHealEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.living.MobEffectEvent;

/**
 * 本批 4 个新立牌的通用事件接线。
 *
 * <ul>
 *   <li>铁处女 / 摸鱼：免疫一切伤害与负面效果；摸鱼还要「不被怪物索敌」。</li>
 *   <li>多萝西：每次有效治疗叠 1 层【温暖】；瞬间治疗 / 生命恢复按效果等级计算。</li>
 *   <li>修女：受击 &gt;10 点移除被动加成；骰神赐福结束始终移除。</li>
 * </ul>
 */
@EventBusSubscriber(modid = "astral_dice")
public final class NewSignEvents {

    private static boolean immune(Player player) {
        return player.hasEffect(ModEffects.ALANA_IRON_MAIDEN) || player.hasEffect(ModEffects.DEEPSEEK_FISHING);
    }

    @SubscribeEvent
    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
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
        // 2026-10-21 用户裁决：路障 / 强化拒止不吃任何药水（1.21.1 的 addEffect 只走本事件，
        // 覆盖 canBeAffected 已无效 ⇒ 必须在这里拦）
        if (event.getEntity() instanceof com.merlinkitsune.astral_dice.entity.BarricadeEntity
                || event.getEntity() instanceof com.merlinkitsune.astral_dice.entity.RepulsionEntity) {
            event.setResult(net.neoforged.neoforge.event.entity.living.MobEffectEvent.Applicable.Result.DO_NOT_APPLY);
            return;
        }
        if (!(event.getEntity() instanceof Player player)) return;
        if (player.level().isClientSide()) return;
        if (!immune(player)) return;
        var holder = event.getEffectInstance().getEffect();
        // 2026-10-21: 青之诅咒由玩家**自己装备**的筹码施加,不属于「外来负面效果」
        // ⇒ 铁处女 / 摸鱼的 HARMFUL 免疫不得拦它(否则 addEffect 返回 false、效果直接消失)。
        if (holder.value() == ModEffects.BLUE_CURSE.value()) return;
        if (holder.value().getCategory() == net.minecraft.world.effect.MobEffectCategory.HARMFUL) {
            event.setResult(net.neoforged.neoforge.event.entity.living.MobEffectEvent.Applicable.Result.DO_NOT_APPLY);
        }
    }

    @SubscribeEvent
    public static void onChangeTarget(LivingChangeTargetEvent event) {
        if (event.getNewAboutToBeSetTarget() instanceof Player player
                && player.hasEffect(ModEffects.DEEPSEEK_FISHING)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onLivingHeal(LivingHealEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (player.level().isClientSide()) return;
        if (!player.isAlive()) return;
        if (event.isCanceled()) return;
        // 过疗 / 空治疗不算有效治疗：满血或治疗量 <= 0 时 heal() 不会改变生命值
        if (event.getAmount() <= 0.0F) return;
        if (player.getHealth() >= player.getMaxHealth()) return;
        if (!DorothySignItem.isEquippedForWarmth(player)) return;

        // 有标注来源（瞬间治疗 / 生命恢复）取其效果等级，其余治疗（自然恢复 / 其他 heal 调用）每次 1 层
        DorothyWarmthHealContext.Source source = DorothyWarmthHealContext.current();
        grantWarmth(player, source == null ? 1 : source.level());
    }

    private static void grantWarmth(Player player, int stacks) {
        int current = DorothySignItem.warmthStacks(player);
        int next = Math.min(DorothyWarmthEffect.MAX_STACKS, current + Math.max(1, stacks));
        if (next <= current) return;
        player.addEffect(new MobEffectInstance(ModEffects.DOROTHY_WARMTH, MobEffectInstance.INFINITE_DURATION,
                next - 1, false, false, true));
        DorothySignItem.refreshWarmthModifier(player);
    }

    @SubscribeEvent
    public static void onEffectRemoved(MobEffectEvent.Remove event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (player.level().isClientSide()) return;
        if (event.getEffectInstance() != null
                && event.getEffectInstance().getEffect().equals(ModEffects.DICE_BLESSING)) {
            AlanaSignItem.clearBonus(player);
        }
    }

    @SubscribeEvent
    public static void onEffectExpired(MobEffectEvent.Expired event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (event.getEffectInstance() != null
                && event.getEffectInstance().getEffect().equals(ModEffects.DICE_BLESSING)) {
            AlanaSignItem.clearBonus(player);
        }
    }
}
