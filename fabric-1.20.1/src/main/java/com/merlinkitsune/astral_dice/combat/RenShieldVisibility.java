package com.merlinkitsune.astral_dice.combat;

import java.util.Collection;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 鼠鼠护盾「他人可见」的客户端镜像（entityId 集合）。
 *
 * <h2>为什么需要它</h2>
 * 原版 Minecraft <b>从不同步</b> {@code MobEffectInstance} 给「本人 + 自己乘客」以外的玩家：
 * 全 jar 构造 {@code ClientboundUpdateMobEffectPacket} 只有 4 个点，全部只发本人或乘客
 * （{@code ServerPlayer.onEffectAdded / onEffectUpdated}、{@code PlayerList.sendActiveEffects}、
 * {@code LivingEntity.sendEffectToPassengers}），{@code ServerEntity} 内不含任何效果同步代码。
 * 原版为「他人可见」单开的两条通道都走 {@code SynchedEntityData}：
 * ① {@code LivingEntity.updateGlowingStatus()} → {@code setSharedFlag(6)}（发光轮廓）；
 * ② {@code LivingEntity.updateSynchronizedMobEffectParticles()} → {@code DATA_EFFECT_PARTICLES}
 * （只同步粒子外观 {@code ParticleOptions}，<b>不同步实例</b>）。
 * ⇒ 因此他人客户端上 {@code player.hasEffect(ModEffects.REN_SHIELD)} <b>恒为 false</b>，
 * <b>不能</b>作为渲染条件（这正是鼠鼠护盾曾经「只有持有者自己看得见」的根因）。
 *
 * <h2>维护方式</h2>
 * 唯一写入方 = {@code network.RenShieldStatePayload} 的 handler（**全量替换**语义：
 * 收到就把本地集合整体换成服务端给的列表）。服务端在护盾状态变化时广播全量给所有玩家，
 * 并在玩家登录时单独补发一份 ⇒ 跨服务器 entityId 撞号不会残留（登录即整体刷新）。
 *
 * <p>全量列表天然覆盖「清除」：某玩家不再持有护盾时，他就不在服务端列表里，
 * 下一份全量包到达时本地集合自然不再包含他，无需单独的移除消息。
 *
 * <p><b>双端可加载</b>：本类不含任何客户端专有类型（{@code Minecraft} / {@code PoseStack} 等），
 * payload 的 handler 可直接引用它，不会在专用服务端触发 {@code BootstrapMethods} 问题。
 */
public final class RenShieldVisibility {

    /** 解码长度上限：全服护盾持有者不可能超过该值，超出的包按畸形丢弃（防恶意分配） */
    public static final int MAX_ENTRIES = 4096;

    private static final Set<Integer> SHIELDED = ConcurrentHashMap.newKeySet();

    private RenShieldVisibility() {
    }

    /** 全量替换：先清空再写入（服务端给的就是完整列表，因此本方法同时承担「清除」语义） */
    public static void replaceAll(Collection<Integer> entityIds) {
        Set<Integer> incoming = ConcurrentHashMap.newKeySet();
        if (entityIds != null) {
            incoming.addAll(entityIds);
        }
        SHIELDED.retainAll(incoming);
        SHIELDED.addAll(incoming);
    }

    /** 该实体当前是否应渲染护盾球 */
    public static boolean isShielded(int entityId) {
        return SHIELDED.contains(entityId);
    }

    /** 断开连接 / 切换服务器时清空（entityId 只在单个服务端会话内唯一） */
    public static void clear() {
        SHIELDED.clear();
    }
}
