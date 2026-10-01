package com.merlinkitsune.astral_dice.component;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

/**
 * 客户端附件同步缓存:接收服务端 S2C 同步包(仅 synced 键),
 * 供 {@link AttachedDataKey#get} 在客户端(本地玩家)读取。键不存在时回退默认值。
 */
public final class ClientAstralData {
    private static CompoundTag cache = new CompoundTag();

    private ClientAstralData() {
    }

    public static <T> T get(AttachedDataKey<T> key) {
        if (!cache.contains(key.name)) {
            return key.defaultValue.get();
        }
        T value = key.codec.parse(net.minecraft.nbt.NbtOps.INSTANCE, cache.get(key.name)).result().orElse(null);
        return value != null ? value : key.defaultValue.get();
    }

    public static void put(String name, Tag tag) {
        if (tag == null) {
            cache.remove(name);
        } else {
            cache.put(name, tag);
        }
    }

    public static void clear() {
        cache = new CompoundTag();
    }
}
