package com.merlinkitsune.astral_dice.event;

import com.merlinkitsune.astral_dice.AstralDiceMod;
import com.merlinkitsune.astral_dice.effect.ModEffects;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.player.Player;

import com.merlinkitsune.astral_dice.platform.event.EventPriority;
import com.merlinkitsune.astral_dice.platform.event.SubscribeEvent;
import com.merlinkitsune.astral_dice.platform.event.entity.living.MobEffectEvent;

import com.merlinkitsune.starenginelib.event.ModEffectRemoval;
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
        // ① 生物类目标(非玩家)允许清除自身效果(2026-09-30 用户裁决):
        //    诡厄巫法的术士等 AI 靠"喝奶自清效果"处理身上的异常状态,而本模组的命名空间守卫会拦下该移除,
        //    于是它们**反复尝试喝奶**却永远清不掉 ⇒ 服务端卡顿(用户实测)。原版女巫及其它模组的同类单位同理。
        //    本模组施加给生物的多为减益(标记 / 虚弱印记 / 破绽 / 厄运 / 隐匿调查),允许其自清无害;
        //    ⚠️ 玩家侧口径不变(下方两条与命名空间守卫仍生效)。
        if (!(event.getEntity() instanceof Player)) return;
        // ★ 2026-10-04 修复（Q3）：目标身上**已无该效果** ⇒ 本次是「无效移除」，直接放行、不拦截。
        //    fabric 侧本事件由 `PuzzlesBridges` 从 Puzzles 的 `MobEffectEvents.REMOVE` 桥接而来，
        //    而「阻止移除」的落地方式是 `EventResult.INTERRUPT`；当注入点不可取消时会抛
        //    `CancellationException: The call removeEffect is not cancellable`。
        //    ⚠️ 语义上：**不存在的效果本来就不需要「保留」** ⇒ 这里早退既不改变任何可观察行为，
        //    也从根上避开那条不可取消的路径（属正面逻辑修复，不是 try/catch 绕过）。
        if (!event.getEntity().hasEffect(effect.getEffect())) return;
        // ②「标记」不再防清理(2026-09-30 用户裁决):允许玩家用牛奶 / `/effect clear` 清除标记。
        //    标记的强度由"层数"承载,且它是施加给**目标**的减益,玩家有正当理由想清掉它。
        //    ⚠️ 伴随的发光仍受下方"同寿命"规则约束:标记还在时发光不可被单独清除;
        //       若一次批量清除中发光先于标记被处理,发光会留到自然到期(≤60 秒)—— 已登记的轻微观感残留。
        if (effect.getEffect() == ModEffects.MARKED.get()) return;
        // 标记携带的发光效果:标记仍存在时同步保留发光(牛奶/effect clear 不得单独清除),
        // 保证发光与标记同寿命——标记自然到期/死亡时两者一起移除(此时标记已不存在,此处自动放行)
        if (effect.getEffect() == net.minecraft.world.effect.MobEffects.GLOWING
                && event.getEntity().hasEffect(ModEffects.MARKED.get())) {
            event.setCanceled(true);
            return;
        }
        String effectId = net.minecraft.core.registries.BuiltInRegistries.MOB_EFFECT.getKey(effect.getEffect()).toString();
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
        String id = net.minecraft.core.registries.BuiltInRegistries.MOB_EFFECT.getKey(instance.getEffect()).toString();
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
        EffectTimerGuard.forget(player, net.minecraft.core.registries.BuiltInRegistries.MOB_EFFECT.getKey(instance.getEffect()).toString());
    }

}
