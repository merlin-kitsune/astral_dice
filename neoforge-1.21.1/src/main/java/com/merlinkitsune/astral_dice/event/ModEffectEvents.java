package com.merlinkitsune.astral_dice.event;

import com.merlinkitsune.astral_dice.AstralDiceMod;
import com.merlinkitsune.astral_dice.effect.ModEffects;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.MobEffectEvent;

import com.merlinkitsune.starengine.event.EffectTimerGuard;
import com.merlinkitsune.starengine.event.ModEffectRemoval;
@EventBusSubscriber(modid = com.merlinkitsune.astral_dice.AstralDiceMod.MODID)
public class ModEffectEvents {
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onModEffectRemovalPrevented(MobEffectEvent.Remove event) {
        if (event.getEntity().level().isClientSide()) return;
        // 本模组内部移除(ModEffectRemoval)/计时器守卫的强制移除(时长校正)放行
        if (ModEffectRemoval.isInternal()) return;
        if (EffectTimerGuard.isForcedRemoval()) return;
        // 死亡时允许清除,保证死亡后效果状态能正常重置(同时丢弃保护记录,不跨生命周期补回)
        if (event.getEntity().isDeadOrDying()) {
            if (event.getEntity() instanceof Player deadPlayer) {
                PlayerEffectProtection.clear(deadPlayer);
            }
            return;
        }
        MobEffectInstance effect = event.getEffectInstance();
        if (effect == null || effect.getEffect() == null) return;
        // ① 生物类目标(非玩家)允许清除自身效果(2026-09-30 用户裁决):
        //    诡厄巫法的术士等 AI 靠"喝奶自清效果"处理身上的异常状态,而本模组的命名空间守卫会拦下该移除,
        //    于是它们**反复尝试喝奶**却永远清不掉 ⇒ 服务端卡顿(用户实测)。原版女巫及其它模组的同类单位同理。
        //    本模组施加给生物的多为减益(标记 / 虚弱印记 / 破绽 / 厄运 / 隐匿调查),允许其自清无害;
        //    ⚠️ 玩家侧口径不变(下方两条与命名空间守卫仍生效)。
        if (!(event.getEntity() instanceof Player)) return;
        // ②「可清除声明」(2026-10-23 用户裁决统一口径):只有显式写进
        //    PlayerEffectProtection.DECLARED_CLEARABLE 的效果才允许被外部清除,其余玩家侧效果一律不受影响。
        //    当前白名单仅 astral_dice:marked(2026-09-30 裁决:标记是施加给**目标**的减益,
        //    强度由层数承载,玩家有正当理由想清掉它)。
        //    ⚠️ 伴随的发光仍受下方"同寿命"规则约束:标记还在时发光不可被单独清除;
        //       若一次批量清除中发光先于标记被处理,发光会留到自然到期(≤60 秒)—— 已登记的轻微观感残留。
        String effectId = effect.getEffect().getRegisteredName();
        if (PlayerEffectProtection.isDeclaredClearable(effectId)) return;
        // 标记携带的发光效果:标记仍存在时同步保留发光(牛奶/effect clear 不得单独清除),
        // 保证发光与标记同寿命——标记自然到期/死亡时两者一起移除(此时标记已不存在,此处自动放行)
        if (effect.getEffect().value() == net.minecraft.world.effect.MobEffects.GLOWING.value()
                && event.getEntity().hasEffect(ModEffects.MARKED)) {
            event.setCanceled(true);
            return;
        }
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

    // 玩家离线:丢弃保护记录(重登后效果由存档恢复,无需跨会话补回),避免长时间运行下按 UUID 累积
    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        PlayerEffectProtection.clear(event.getEntity());
    }
    // 死亡重生 / 跨维度克隆(2026-10-23 复核 P3):原版死亡**不派发** MobEffectEvent.Remove
    // (ServerPlayer#restoreFrom 不复制效果)、PlayerLifecycleHandler 只清本模组那 11 个效果,
    // 因此若玩家只带「经 apply 施加的原版效果」死亡,快照会残留到 endTick+40 并与补回通道同源造成「陈旧时长复活」。
    // Clone 在重生/换维度时对新实体触发,按 UUID 清场即可(两者 UUID 相同)。
    @SubscribeEvent
    public static void onPlayerClone(PlayerEvent.Clone event) {
        PlayerEffectProtection.clear(event.getEntity());
    }
    // 外部清除的补回入口(2026-10-23 用户裁决):玩家侧效果「除非被声明可清除,否则不受任何外部影响」。
    // 原版命名空间的效果无法按实例取消(MobEffectEvent.Remove 只带效果类型;按类型取消会连玩家自己喝的
    // 同类药水一起锁住 = 牛奶失效),因此这里在取消判定之后,把「本模组施加、且未声明可清除」的效果
    // 排进下一 tick 的补回队列(见 PlayerEffectProtection)。自定义命名空间的效果仍由上面的处理器直接取消。
    // ⚠️ 必须是 LOWEST:前置处理器(HIGH)一旦取消,就说明效果没被移除,不该补回。
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onExternalRemovalRestore(MobEffectEvent.Remove event) {
        if (event.isCanceled()) return;
        if (event.getEntity().level().isClientSide()) return;
        if (ModEffectRemoval.isInternal()) return;
        if (EffectTimerGuard.isForcedRemoval()) return;
        if (!(event.getEntity() instanceof Player player)) return;
        if (player.isDeadOrDying()) return;
        MobEffectInstance effect = event.getEffectInstance();
        if (effect == null || effect.getEffect() == null) return;
        PlayerEffectProtection.onExternalRemoval(player, effect.getEffect().getRegisteredName());
    }

}
