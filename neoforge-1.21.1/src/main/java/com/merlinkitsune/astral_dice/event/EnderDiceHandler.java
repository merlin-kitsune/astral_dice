package com.merlinkitsune.astral_dice.event;

import com.merlinkitsune.astral_dice.AstralDiceMod;
import com.merlinkitsune.astral_dice.component.ModAttachments;
import com.merlinkitsune.astral_dice.item.ModItems;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import top.theillusivec4.curios.api.CuriosApi;

/**
 * 末影骰子:与下界合金骰子同阶。
 *
 * 功能:
 * - 受到致命伤害时,触发一次"不死图腾"效果(恢复 1 生命 + 移除全部效果 + 生命恢复 II(0:45)
 *   + 伤害吸收 II(0:05) + 火焰抗性(0:40) + 图腾动画),随后进入 5:00 冷却;
 * - 装备期间处于雨中/水下时,受到的伤害 +40%(经 {@link LivingDamageEvent.Pre} 于最终减免后放大)。
 */
@EventBusSubscriber(modid = AstralDiceMod.MODID)
public final class EnderDiceHandler {

    /** 不死图腾效果冷却:5:00 = 6000 tick */
    public static final int TOTEM_COOLDOWN_TICKS = 20 * 60 * 5;
    /** 雨中/水下受到伤害倍率(+40%) */
    public static final float RAIN_WATER_DAMAGE_MULTIPLIER = 1.4F;

    // === 不死图腾效果参数(与原版一致) ===
    private static final int REGEN_DURATION_TICKS = 900;   // 生命恢复 II,0:45
    private static final int REGEN_AMPLIFIER = 1;
    private static final int ABSORPTION_DURATION_TICKS = 100; // 伤害吸收 II,0:05
    private static final int ABSORPTION_AMPLIFIER = 1;
    private static final int FIRE_RESIST_DURATION_TICKS = 800; // 火焰抗性,0:40
    private static final int FIRE_RESIST_AMPLIFIER = 0;

    private EnderDiceHandler() {
    }

    public static boolean hasEnderDie(Player player) {
        var curios = CuriosApi.getCuriosInventory(player);
        return curios.isPresent()
                && curios.get().findFirstCurio(s -> s.is(ModItems.ENDER_DICE.get())).isPresent();
    }

    /** 不死图腾冷却是否仍在进行(剩余 > 0 时为 true) */
    public static boolean isTotemOnCooldown(Player player) {
        return player.level().getGameTime() < ModAttachments.getEnderDieTotemCooldownEnd(player);
    }

    // 装备期间处于雨中或水下:受到的伤害 +40%
    @SubscribeEvent
    public static void onLivingDamagePre(LivingDamageEvent.Pre event) {
        LivingEntity entity = event.getEntity();
        if (entity.level().isClientSide()) return;
        if (!(entity instanceof Player player)) return;
        if (!hasEnderDie(player)) return;
        // 雨(含被雨淋到)/水下/气泡柱都满足"雨中或水下"
        if (!player.isInWaterRainOrBubble()) return;
        event.setNewDamage(event.getNewDamage() * RAIN_WATER_DAMAGE_MULTIPLIER);
    }

    // 受到致命伤害:未处于冷却时触发一次不死图腾效果并进入 5:00 冷却
    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        LivingEntity entity = event.getEntity();
        if (entity.level().isClientSide()) return;
        if (!(entity instanceof Player player)) return;
        if (event.isCanceled()) return;
        // 与原版不死图腾一致:无视无敌(bypasses_invulnerability)的致死伤害不触发
        if (event.getSource().is(net.minecraft.tags.DamageTypeTags.BYPASSES_INVULNERABILITY)) return;
        if (!hasEnderDie(player)) return;
        if (isTotemOnCooldown(player)) return;

        event.setCanceled(true);
        // 与原版图腾一致:清除「可被图腾治愈」的效果(死亡后不掉落/不触发死亡逻辑,由取消死亡保证)
        player.setHealth(1.0F);
        player.removeEffectsCuredBy(net.neoforged.neoforge.common.EffectCures.PROTECTED_BY_TOTEM);
        player.addEffect(new MobEffectInstance(MobEffects.REGENERATION,
                REGEN_DURATION_TICKS, REGEN_AMPLIFIER, false, true));
        player.addEffect(new MobEffectInstance(MobEffects.ABSORPTION,
                ABSORPTION_DURATION_TICKS, ABSORPTION_AMPLIFIER, false, true));
        player.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE,
                FIRE_RESIST_DURATION_TICKS, FIRE_RESIST_AMPLIFIER, false, true));
        // 客户端播放不死图腾动画/音效(原版实体事件 35)
        player.level().broadcastEntityEvent(player, (byte) 35);
        // 开始 5:00 冷却(以世界时间为准)
        ModAttachments.setEnderDieTotemCooldownEnd(player,
                player.level().getGameTime() + TOTEM_COOLDOWN_TICKS);
    }
}
