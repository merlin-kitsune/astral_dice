package com.merlinkitsune.astral_dice.component;

import com.mojang.serialization.Codec;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.entity.LivingEntity;

import java.util.function.Supplier;

/**
 * 1.20.1 Forge 玩家附件 shim:1.21 NeoForge 的 {@code AttachmentType} 在 1.20.1 不存在。
 * 玩家数据存于 {@link AstralData} Capability(随玩家 NBT 持久化,维度切换保留、死亡不复制——
 * 与 NeoForge 附件默认行为一致);非玩家实体(虚弱印记/隐匿调查来源等)存于 ForgeData
 * (实体自带持久化,不随死亡复制由实体生命周期决定)。
 *
 * <p>{@code synced} 键在服务端写入后立即向该玩家推送 S2C 同步包,客户端读取路由到
 * {@link ClientAstralData} 缓存(对应 1.21 附件的 {@code .sync()} 语义)。
 */
public final class AttachedDataKey<T> {
    final String name;
    final Codec<T> codec;
    final Supplier<T> defaultValue;
    final boolean synced;
    final boolean transientData;

    private AttachedDataKey(String name, Codec<T> codec, Supplier<T> defaultValue, boolean synced, boolean transientData) {
        this.name = name;
        this.codec = codec;
        this.defaultValue = defaultValue;
        this.synced = synced;
        this.transientData = transientData;
    }

    public static <T> Builder<T> builder(String name, Codec<T> codec, Supplier<T> defaultValue) {
        return new Builder<>(name, codec, defaultValue);
    }

    public String name() {
        return name;
    }

    boolean synced() {
        return synced;
    }

    /** 读取:服务端走 Capability/ForgeData,客户端(本地玩家)走同步缓存。 */
    public T get(LivingEntity holder) {
        if (holder.level().isClientSide()) {
            return ClientAstralData.get(this);
        }
        CompoundTag store = store(holder);
        if (store == null || !store.contains(name)) {
            return defaultValue.get();
        }
        T value = codec.parse(net.minecraft.nbt.NbtOps.INSTANCE, store.get(name)).result().orElse(null);
        return value != null ? value : defaultValue.get();
    }

    /** 写入:服务端持久化并按需同步;客户端为无操作(值以服务端下发为准)。 */
    public void set(LivingEntity holder, T value) {
        if (holder.level().isClientSide()) {
            return;
        }
        CompoundTag store = store(holder);
        if (store == null) {
            return;
        }
        Tag tag = codec.encodeStart(net.minecraft.nbt.NbtOps.INSTANCE, value).result().orElse(null);
        if (tag == null) {
            store.remove(name);
        } else {
            store.put(name, tag);
        }
        if (holder instanceof net.minecraft.server.level.ServerPlayer serverPlayer && synced) {
            com.merlinkitsune.astral_dice.network.ModNetwork.syncAttachment(serverPlayer, this, tag);
        }
    }

    /** 服务端存储位置:玩家=AstralData Capability;其他实体=ForgeData(persistentData)。 */
    private CompoundTag store(LivingEntity holder) {
        if (holder instanceof net.minecraft.world.entity.player.Player player) {
            AstralData data = player.getCapability(ModCapabilities.ASTRAL_DATA).orElse(null);
            return data != null ? (transientData ? data.transientStore() : data.persistentStore()) : null;
        }
        return holder.getPersistentData();
    }

    /** 服务端原始 tag 读取(同步快照用);键不存在返回 null。 */
    public Tag readRawTag(LivingEntity holder) {
        CompoundTag store = store(holder);
        return store != null && store.contains(name) ? store.get(name) : null;
    }

    public static final class Builder<T> {
        private final String name;
        private final Codec<T> codec;
        private final Supplier<T> defaultValue;
        private boolean synced;
        private boolean transientData;

        private Builder(String name, Codec<T> codec, Supplier<T> defaultValue) {
            this.name = name;
            this.codec = codec;
            this.defaultValue = defaultValue;
        }

        /** 客户端同步(对应 1.21 {@code AttachmentType.builder().sync()})。 */
        public Builder<T> sync() {
            this.synced = true;
            return this;
        }

        /** 仅内存态、不随玩家 NBT 持久化(对应 1.21 不带 serialize 的附件)。 */
        public Builder<T> inMemory() {
            this.transientData = true;
            return this;
        }

        public AttachedDataKey<T> build() {
            return new AttachedDataKey<>(name, codec, defaultValue, synced, transientData);
        }
    }
}
