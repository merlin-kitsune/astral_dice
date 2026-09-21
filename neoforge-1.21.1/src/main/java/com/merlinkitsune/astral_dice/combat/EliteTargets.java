package com.merlinkitsune.astral_dice.combat;

import com.merlinkitsune.starenginelib.item.BossEntityUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.LivingEntity;
import com.merlinkitsune.starenginelib.combat.HostileTargets;

/**
 * 「精英怪物或 Boss」判定的**唯一入口**（2026-09-21 用户裁决，必须遵守）。
 *
 * <p>口径（用户原话）：
 * <ul>
 *   <li><b>精英</b>：原版或模组怪物，<b>血量 &gt; 40 或 护甲 &gt; 20</b>；</li>
 *   <li><b>Boss</b>：{@code c:bosses} 通用标签 / 屏幕上方 boss 血条（{@link BossEntityUtil}）；</li>
 *   <li><b>神化（Apotheosis）联动</b>：玩家安装该模组时，被其标记为「神化 Boss / 神化精英」的生物。
 *       判定走实体持久化 NBT —— {@code apoth.boss}（Apothic Invader 即「神化 Boss」）与
 *       {@code apoth.miniboss}（Apothic Elite 即「神化精英」），二者均为 {@code boolean}。</li>
 * </ul>
 *
 * <p><b>为什么读 NBT 而不是引用 Apotheosis 的类</b>：本模组**不硬依赖** Apotheosis
 * （玩家可以不装它）。{@code apoth.boss} / {@code apoth.miniboss} 这两个键是 Apotheosis
 * 的公开约定（源码常量 {@code Invader.BOSS_KEY} / {@code Elite.MINIBOSS_KEY}），
 * 写在实体的持久化数据里 ——
 * 1.20.1 Forge 落盘为实体 NBT 的 {@code ForgeData}，
 * 1.21.1 NeoForge 落盘为 {@code NeoForgeData}，
 * 两线均通过 {@code Entity#getPersistentData()} 读取，**调用面完全一致**，故无需分平台实现。
 * 未安装 Apotheosis 时这两个键恒不存在，判定自然为 {@code false}。
 *
 * <p>与 {@link HostileTargets} 的分工：「敌对」判的是**立场**，「精英」判的是**强度档位**，
 * 二者正交 —— 精英判定必须先通过敌对判定才有意义（调用方自行保证）。
 */
public final class EliteTargets {

    /** 神化 Boss（Apothic Invader）标记键 —— 与 Apotheosis {@code Invader.BOSS_KEY} 一致。 */
    private static final String APOTH_BOSS_KEY = "apoth.boss";

    /** 神化精英（Apothic Elite / miniboss）标记键 —— 与 Apotheosis {@code Elite.MINIBOSS_KEY} 一致。 */
    private static final String APOTH_MINIBOSS_KEY = "apoth.miniboss";

    /** 血量阈值（用户裁决）：最大生命值**严格大于**该值即视为精英。 */
    public static final float ELITE_HEALTH_THRESHOLD = 40.0F;

    /** 护甲阈值（用户裁决）：护甲值**严格大于**该值即视为精英。 */
    public static final int ELITE_ARMOR_THRESHOLD = 20;

    private EliteTargets() {
    }

    /**
     * 该实体是否属于「精英怪物或 Boss」。
     *
     * @param entity 待判定实体（通常已通过 {@link HostileTargets#isHostile} 的敌对判定）
     * @return {@code true} = 精英或 Boss
     */
    public static boolean isEliteOrBoss(LivingEntity entity) {
        if (entity == null) return false;
        // ① 强度阈值：血量 > 40 或 护甲 > 20（覆盖原版与模组怪物）
        if (entity.getMaxHealth() > ELITE_HEALTH_THRESHOLD) return true;
        if (entity.getArmorValue() > ELITE_ARMOR_THRESHOLD) return true;
        // ② Boss：c:bosses 通用标签 / 屏幕上方 boss 血条
        if (BossEntityUtil.isBossEntity(entity)) return true;
        // ③ 神化（Apotheosis）标记：apoth.boss（Invader）/ apoth.miniboss（Elite）
        CompoundTag persistent = entity.getPersistentData();
        return persistent.getBoolean(APOTH_BOSS_KEY) || persistent.getBoolean(APOTH_MINIBOSS_KEY);
    }
}
