package com.merlinkitsune.astral_dice.event;

import com.merlinkitsune.astral_dice.AstralDiceMod;
import com.merlinkitsune.astral_dice.entity.RepulsionEntity;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.entity.EntityTeleportEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 强化拒止 · 末影人 / 潜影贝「闪现」拦截（2026-10-21 用户裁决；1.20.1 移植）。
 *
 * <p><b>为什么需要事件层</b>：{@code RepulsionEntity#controlTarget} 的「单 tick 位移 &gt; 2 格 ⇒ 拉回」
 * 只能**事后纠正** —— 实体若在力场 tick 之后才闪现，客户端会先看到一帧闪动。本类在传送**发生前**
 * 取消它，观感上不出闪动。
 *
 * <p><b>判据（只吃被吸附的目标）</b>：传送实体 16 格内存在一座 {@link RepulsionEntity}，且它的
 * {@link RepulsionEntity#isTracking(java.util.UUID)} 对该实体 UUID 返回 true ⇒ 取消。
 * 非受控目标、其它传送（传送命令 / 末影珍珠 / 紫颂果）**一律放行**。
 *
 * <p><b>平台差异</b>：1.20.1 的击杀点在 {@code EnderMan#teleport}（{@code m_32544_}），
 * {@code ForgeEventFactory#onEnderTeleport} 发射 {@code EntityTeleportEvent.EnderEntity}
 * （类级 {@code @Cancelable}，子类同样可取消）；取消后原版方法直接 {@code return false}，不发生位移。
 * NeoForge 1.21.1 是同一事件名（{@code net.neoforged.neoforge.event.entity} 包下，
 * 实现 {@code ICancellableEvent}）⇒ 两线判据逐字对等。
 */
@Mod.EventBusSubscriber(modid = AstralDiceMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class RepulsionTeleportGuard {

    /** 「被吸附目标 ↔ 强化拒止」关联查询的包围盒余量（格）；与 {@code RepulsionCreeperGuard} 同口径。 */
    private static final double LINK_RADIUS = 16.0D;

    private RepulsionTeleportGuard() {
    }

    @SubscribeEvent
    public static void onEnderTeleport(EntityTeleportEvent.EnderEntity event) {
        LivingEntity living = event.getEntityLiving();
        if (living == null || living.level().isClientSide) return;
        if (!isTrackedByRepulsion(living.level(), living)) return;
        // 取消 ⇒ EnderMan#teleport 里的 event.isCanceled() 分支直接 return false
        event.setCanceled(true);
    }

    /** 传送实体附近是否有强化拒止正在吸附它（跨维度不会同 level，天然隔离）。 */
    private static boolean isTrackedByRepulsion(Level level, LivingEntity target) {
        AABB area = target.getBoundingBox().inflate(LINK_RADIUS);
        for (RepulsionEntity repulsion : level.getEntitiesOfClass(RepulsionEntity.class, area)) {
            if (repulsion.isTracking(target.getUUID())) return true;
        }
        return false;
    }
}
