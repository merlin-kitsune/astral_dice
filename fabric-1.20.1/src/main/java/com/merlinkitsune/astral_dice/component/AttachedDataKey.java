package com.merlinkitsune.astral_dice.component;

import com.merlinkitsune.astral_dice.AstralDiceMod;
import com.mojang.serialization.Codec;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentTarget;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;

import java.util.function.Supplier;

/**
 * 玩家/实体附件键(Fabric 1.20.1)。
 *
 * <h2>实现选型:改用 Fabric API 附件</h2>
 * Forge 侧本类是「AstralData Capability(玩家)+ {@code getPersistentData()}(其他实体)」的双通道 shim ——
 * 它本来就是为**模拟 NeoForge 的 {@code AttachmentType}** 而写的。本轮实测确认:
 * Fabric API 0.92.12 **内置** {@code fabric-data-attachment-api-v1}
 * ({@code AttachmentRegistry} / {@code AttachmentType} / {@code AttachmentTarget}),
 * 与 NeoForge 的附件结构 **1:1**:
 * <table border="1">
 *   <caption>语义对照</caption>
 *   <tr><th>本类</th><th>Fabric API 附件</th><th>NeoForge 附件</th></tr>
 *   <tr><td>{@code codec} + 持久化</td><td>{@code Builder#persistent(Codec)}</td><td>{@code .serialize(Codec)}</td></tr>
 *   <tr><td>{@code defaultValue}</td><td>{@code Builder#initializer(Supplier)}</td><td>{@code .initializer(Supplier)}</td></tr>
 *   <tr><td>{@code inMemory()}(不落盘)</td><td>不加 {@code persistent}</td><td>不加 {@code serialize}</td></tr>
 *   <tr><td>随死亡保留的键</td><td>{@code Builder#copyOnDeath()}</td><td>{@code .copyOnDeath()}</td></tr>
 * </table>
 * ⇒ 单一机制覆盖「玩家 + 非玩家实体」两种宿主(Forge 侧需要两条通道),
 * 死亡复制由 FAPI 承担 —— 原来手写在 {@code AstralData#onPlayerClone} 里的复制表随之删除。
 *
 * <p>⚠️ {@code sync()} 语义**仍由本模组自己的 S2C 包承担**({@code ModNetwork#syncAttachment} +
 * {@link ClientAstralData} 缓存),没有改用 FAPI 的 {@code syncWith}:原实现已把自己的一套
 * 同步/快照/去重口径(含「缺失键下发显式默认值」这条防残留逻辑)写死,换机制会改变可见行为。
 *
 * <p>⚠️ 与 Forge 侧的**存档不兼容**(已登记):Forge 把玩家数据写在
 * {@code ForgeData → astral_data} 复合里;本实现写在实体 NBT 的附件段。两条线的存档不能互读属预期。
 */
public final class AttachedDataKey<T> {

    final String name;
    final Codec<T> codec;
    final Supplier<T> defaultValue;
    final boolean synced;
    final boolean transientData;
    private final AttachmentType<T> attachment;

    private AttachedDataKey(String name, Codec<T> codec, Supplier<T> defaultValue,
                           boolean synced, boolean transientData, boolean copyOnDeath) {
        this.name = name;
        this.codec = codec;
        this.defaultValue = defaultValue;
        this.synced = synced;
        this.transientData = transientData;

        AttachmentRegistry.Builder<T> builder = AttachmentRegistry.builder();
        if (!transientData) {
            builder.persistent(codec);
        }
        if (copyOnDeath) {
            builder.copyOnDeath();
        }
        builder.initializer(defaultValue);
        this.attachment = builder.buildAndRegister(new ResourceLocation(AstralDiceMod.MODID, name));
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

    /** 底层 Fabric 附件类型(供需要直接操作的场景)。 */
    public AttachmentType<T> attachment() {
        return attachment;
    }

    /** 读取:服务端走附件本体,客户端(本地玩家)走同步缓存。 */
    public T get(LivingEntity holder) {
        if (holder.level().isClientSide()) {
            return ClientAstralData.get(this);
        }
        return target(holder).getAttachedOrCreate(attachment, defaultValue);
    }

    /** 写入:服务端持久化并按需同步;客户端为无操作(值以服务端下发为准)。 */
    public void set(LivingEntity holder, T value) {
        if (holder.level().isClientSide()) {
            return;
        }
        AttachmentTarget target = target(holder);
        if (value == null) {
            target.removeAttached(attachment);
        } else {
            target.setAttached(attachment, value);
        }
        if (holder instanceof net.minecraft.server.level.ServerPlayer serverPlayer && synced) {
            com.merlinkitsune.astral_dice.network.ModNetwork.syncAttachment(serverPlayer, this, rawTag(value));
        }
    }

    private static AttachmentTarget target(LivingEntity holder) {
        // LivingEntity 经 FAPI 的 injected interface 在编译期就已实现 AttachmentTarget ⇒ 直接强转
        return (AttachmentTarget) holder;
    }

    private Tag rawTag(T value) {
        if (value == null) {
            return null;
        }
        return codec.encodeStart(NbtOps.INSTANCE, value).result().orElse(null);
    }

    /** 服务端原始 tag 读取(同步快照用);键不存在返回 null。 */
    public Tag readRawTag(LivingEntity holder) {
        if (holder.level().isClientSide()) {
            return null;
        }
        AttachmentTarget target = target(holder);
        if (!target.hasAttached(attachment)) {
            return null;
        }
        return rawTag(target.getAttached(attachment));
    }

    /**
     * 默认值的原始 tag(全量快照对**缺失键下发显式默认值**用)。
     * 键在服务端不存在时必须下发,否则客户端会保留上一会话/上一个世界的残留缓存值。
     */
    public Tag defaultRawTag() {
        return codec.encodeStart(NbtOps.INSTANCE, defaultValue.get()).result().orElse(null);
    }

    /** 便捷:把 NBT tag 按本键解码(供网络层使用)。 */
    public T decode(Tag tag) {
        if (tag == null) {
            return defaultValue.get();
        }
        T value = codec.parse(NbtOps.INSTANCE, tag).result().orElse(null);
        return value != null ? value : defaultValue.get();
    }

    /** 调试/快照用:把本键当前值打成单键 compound。 */
    public CompoundTag snapshot(LivingEntity holder) {
        CompoundTag out = new CompoundTag();
        Tag tag = readRawTag(holder);
        if (tag != null) {
            out.put(name, tag);
        }
        return out;
    }

    public static final class Builder<T> {
        private final String name;
        private final Codec<T> codec;
        private final Supplier<T> defaultValue;
        private boolean synced;
        private boolean transientData;
        private boolean copyOnDeath;

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

        /** 仅内存态、不随实体 NBT 持久化(对应 1.21 不带 serialize 的附件)。 */
        public Builder<T> inMemory() {
            this.transientData = true;
            return this;
        }

        /**
         * 死亡重生后保留(对应 NeoForge 附件 / FAPI 附件的 {@code copyOnDeath()})。
         *
         * <p>Forge 侧这张「随死亡保留的键」清单写在 {@code AstralData#onPlayerClone} 里;
         * 本实现改由各键自己声明 —— 两者是同一份集合,改动时必须同步核对
         * {@code ModAttachments} 中带 {@code .copyOnDeath()} 的键。
         */
        public Builder<T> copyOnDeath() {
            this.copyOnDeath = true;
            return this;
        }

        public AttachedDataKey<T> build() {
            return new AttachedDataKey<>(name, codec, defaultValue, synced, transientData, copyOnDeath);
        }
    }
}
