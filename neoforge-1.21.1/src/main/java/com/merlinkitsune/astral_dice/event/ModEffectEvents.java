package com.merlinkitsune.astral_dice.event;

import com.merlinkitsune.astral_dice.AstralDiceMod;
import com.merlinkitsune.astral_dice.effect.ModEffects;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.player.Player;

import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.MobEffectEvent;

import com.merlinkitsune.starenginelib.event.ModEffectRemoval;
@EventBusSubscriber(modid = com.merlinkitsune.astral_dice.AstralDiceMod.MODID)
public class ModEffectEvents {
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onModEffectRemovalPrevented(MobEffectEvent.Remove event) {
        if (event.getEntity().level().isClientSide()) return;
        // 本模组内部移除(ModEffectRemoval)/计时器守卫的强制移除(时长校正)放行
        if (ModEffectRemoval.isInternal()) return;
        if (EffectTimerGuard.isForcedRemoval()) return;
        // 死亡时允许清除,保证死亡后效果状态能正常重置
        if (event.getEntity().isDeadOrDying()) return;
        MobEffectInstance effect = event.getEffectInstance();
        if (effect == null || effect.getEffect() == null) return;
        // 标记携带的发光效果:标记仍存在时同步保留发光(牛奶/effect clear 不得单独清除),
        // 保证发光与标记同寿命——标记自然到期/死亡时两者一起移除(此时标记已不存在,此处自动放行)
        if (effect.getEffect().value() == net.minecraft.world.effect.MobEffects.GLOWING.value()
                && event.getEntity().hasEffect(ModEffects.MARKED)) {
            event.setCanceled(true);
            return;
        }
        String effectId = effect.getEffect().getRegisteredName();
        if (effectId != null && effectId.startsWith(AstralDiceMod.MODID + ":")) {
            event.setCanceled(true);
        }
    }

    // 隐匿调查效果移除(目标死亡/被清除):清除来源

    @SubscribeEvent
    public static void onEffectTimerRecord(MobEffectEvent.Added event) {
        if (event.getEntity().level().isClientSide()) return;
        if (!(event.getEntity() instanceof Player player)) return;
        MobEffectInstance instance = event.getEffectInstance();
        if (instance == null || instance.getEffect() == null) return;
        String id = instance.getEffect().getRegisteredName();
        if (id == null || !id.startsWith(AstralDiceMod.MODID + ":")) return;
        EffectTimerGuard.record(player, instance);
    }

    // 计时器守卫:本模组效果被成功移除(未被拦截/非守卫自身/非死亡)时遗忘计时记录,
    // 避免守卫把本模组主动结束的效果(如卸下骇客立牌结束隐身)重新施加回来

    @SubscribeEvent
    public static void onEffectTimerForget(MobEffectEvent.Remove event) {
        if (event.getEntity().level().isClientSide()) return;
        if (event.isCanceled()) return;
        if (EffectTimerGuard.isForcedRemoval()) return;
        if (event.getEntity().isDeadOrDying()) return;
        if (!(event.getEntity() instanceof Player player)) return;
        MobEffectInstance instance = event.getEffectInstance();
        if (instance == null || instance.getEffect() == null) return;
        EffectTimerGuard.forget(player, instance.getEffect().getRegisteredName());
    }

}
