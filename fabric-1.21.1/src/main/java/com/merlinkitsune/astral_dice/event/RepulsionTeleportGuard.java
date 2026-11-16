package com.merlinkitsune.astral_dice.event;

import com.merlinkitsune.astral_dice.entity.RepulsionEntity;
import com.merlinkitsune.astral_dice.platform.event.SubscribeEvent;
import com.merlinkitsune.astral_dice.platform.event.entity.EntityTeleportEvent;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

/**
 * 强化拒止 · 末影人 / 潜影贝「闪现」拦截（2026-10-21 用户裁决；forge-1.20.1 → fabric-1.20.1 1:1 移植）。
 *
 * <p><b>为什么需要事件层</b>：{@code RepulsionEntity#controlTarget} 的「单 tick 位移 &gt; 2 格 ⇒ 拉回」
 * 只能**事后纠正** —— 实体若在力场 tick 之后才闪现，客户端会先看到一帧闪动。本类在传送**发生前**
 * 取消它，观感上不出闪动。
 *
 * <p><b>判据（只吃被吸附的目标）</b>：传送实体 16 格内存在一座 {@link RepulsionEntity}，且它的
 * {@link RepulsionEntity#isTracking(java.util.UUID)} 对该实体 UUID 返回 true ⇒ 取消。
 * 非受控目标、其它传送（传送命令 / 末影珍珠 / 紫颂果）**一律放行**。
 *
 * <p><b>fabric-1.20.1 平台差异</b>（相对 forge-1.20.1 基线）：
 * <ul>
 *   <li>无 {@code @Mod.EventBusSubscriber}：改用本线垫片 {@link SubscribeEvent}；
 *       事件类 {@link EntityTeleportEvent.EnderEntity} 已在
 *       {@code platform/event/entity/EntityTeleportEvent.java} 提供（{@code @Cancelable}，
 *       {@code getEntityLiving()} 同签名），判据与基线逐字对等；</li>
 *   <li>⚠️ <b>必须在 {@code AstralDiceMod#registerListeners()} 里显式登记本类</b>，
 *       否则静默失效；</li>
 *   <li>⚠️ <b>本线当前没有派发 {@code EnderEntity} 的桥</b>（forge 是
 *       {@code ForgeEventFactory#onEnderTeleport} 打在 {@code EnderMan#teleport} 上）⇒
 *       本监听可编译、可登记，但<b>目前行为休眠</b>；补齐方式见
 *       {@code porting/fabric-1.20.1/B3-EFFECTCARDS.md}「需主 Agent 接线处」。</li>
 * </ul>
 */
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
        // 取消 ⇒ 派发点（未来的 EnderMan#teleport 桥）看到 isCanceled() 直接放弃位移
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
