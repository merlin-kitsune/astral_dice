/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package com.merlinkitsune.astral_dice.platform.event.entity.living;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.level.BaseSpawner;
import net.minecraft.world.level.ServerLevelAccessor;
// DROPPED-FORGE-IMPORT common.MinecraftForge
import com.merlinkitsune.astral_dice.platform.event.Cancelable;
import com.merlinkitsune.astral_dice.platform.event.entity.EntityEvent;
import org.jetbrains.annotations.Nullable;

/**
 * MobSpawnEvent is fired when a Mob is spawned.<br>
 * 本文件自 Forge 1.20.1（47.4.10）的 {@code net.minecraftforge.event.entity.living.MobSpawnEvent}
 * 逐字段转译:类名、构造器、访问器与 Forge 完全一致,注释里的 {@code MinecraftForge#EVENT_BUS}
 * 在本线对应 {@code com.merlinkitsune.astral_dice.platform.event.LoaderBus#INSTANCE}。<br>
 * <br>
 * 事件类本身是纯 POJO —— 只换 import,消费方逻辑不动。<br>
 **/
public class MobSpawnEvent extends EntityEvent
{
    private final ServerLevelAccessor level;
    private final double x;
    private final double y;
    private final double z;

    public MobSpawnEvent(Mob mob, ServerLevelAccessor level, double x, double y, double z)
    {
        super(mob);
        this.level = level;
        this.x = x;
        this.y = y;
        this.z = z;
    }

    /** 与 Forge 一致的协变覆写:本事件族只对 {@link Mob} 派发。 */
    @Override
    public Mob getEntity()
    {
        return (Mob) super.getEntity();
    }

    public ServerLevelAccessor getLevel() { return level; }

    public double getX() { return x; }

    public double getY() { return y; }

    public double getZ() { return z; }

    /**
     * This event is fired before {@link Mob#finalizeSpawn} is called.<br>
     * 语义与触发时机(Forge 47.4.10 逐字对齐):
     * <ul>
     *   <li>在 {@code Mob#finalizeSpawn} <b>之前</b>派发 —— Forge 侧由 transformer 在调用点
     *       注入,源码/字节码里都看不到该调用;本线由
     *       {@code mixin/bridge/MobFinalizeSpawnBridgeMixin} 注入 {@code Mob#finalizeSpawn} 的 HEAD 等价复刻</li>
     *   <li>取消(本类的 {@code @Cancelable}) ⇒ {@code finalizeSpawn} 不被调用,返回值恒为 {@code null};
     *       <b>但实体仍会入世</b>(Forge 原注释口径)</li>
     *   <li>{@link #setSpawnCancelled(boolean)} ⇒ 由平台阻止该次生成(Forge 原注释口径)</li>
     * </ul>
     * 本事件只在逻辑服务端派发。<br>
     **/
    @Cancelable
    public static class FinalizeSpawn extends MobSpawnEvent
    {
        private DifficultyInstance difficulty;
        private final MobSpawnType spawnType;
        @Nullable
        private SpawnGroupData spawnData;
        @Nullable
        private CompoundTag spawnTag;
        @Nullable
        private final BaseSpawner spawner;
        private boolean spawnCancelled;

        public FinalizeSpawn(Mob mob, ServerLevelAccessor level, double x, double y, double z,
                             DifficultyInstance difficulty, MobSpawnType spawnType,
                             @Nullable SpawnGroupData spawnData, @Nullable CompoundTag spawnTag,
                             @Nullable BaseSpawner spawner)
        {
            super(mob, level, x, y, z);
            this.difficulty = difficulty;
            this.spawnType = spawnType;
            this.spawnData = spawnData;
            this.spawnTag = spawnTag;
            this.spawner = spawner;
        }

        public DifficultyInstance getDifficulty() { return difficulty; }

        public void setDifficulty(DifficultyInstance difficulty) { this.difficulty = difficulty; }

        public MobSpawnType getSpawnType() { return spawnType; }

        @Nullable
        public SpawnGroupData getSpawnData() { return spawnData; }

        public void setSpawnData(@Nullable SpawnGroupData spawnData) { this.spawnData = spawnData; }

        @Nullable
        public CompoundTag getSpawnTag() { return spawnTag; }

        public void setSpawnTag(@Nullable CompoundTag spawnTag) { this.spawnTag = spawnTag; }

        /** Only non-null when the spawn came from a {@link BaseSpawner}(即刷怪笼),本线桥固定传 null。 */
        @Nullable
        public BaseSpawner getSpawner() { return spawner; }

        public void setSpawnCancelled(boolean cancel) { this.spawnCancelled = cancel; }

        public boolean isSpawnCancelled() { return spawnCancelled; }
    }
}
