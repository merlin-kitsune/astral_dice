package com.merlinkitsune.astral_dice.component;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.function.Function;

/**
 * 1.20.1 Forge 数据组件 shim:1.21 NeoForge 的 {@code DataComponentType} 在 1.20.1 不存在,
 * 本类以 ItemStack NBT 键实现等价语义(persistent 编解码),供 {@link ModDataComponents} 常量使用。
 *
 * <p>调用面与 1.21 的 ItemStack 组件 API 对应:
 * <ul>
 *   <li>{@code stack.get(KEY)} → {@code KEY.get(stack)}</li>
 *   <li>{@code stack.set(KEY, v)} → {@code KEY.set(stack, v)}</li>
 *   <li>{@code stack.has(KEY)} → {@code KEY.has(stack)}</li>
 *   <li>{@code stack.remove(KEY)} → {@code KEY.remove(stack)}</li>
 * </ul>
 * 1.21 的 {@code Item.Properties.component(KEY, v)} 注册默认值由 {@code withItemDefault} 的
 * 按物品默认值承担(未写入 NBT 前读取默认值,与组件默认值行为一致)。
 */
public final class ItemDataKey<T> {
    private final String name;
    private final Codec<T> codec;
    private final Function<Item, T> itemDefault;

    private ItemDataKey(String name, Codec<T> codec, Function<Item, T> itemDefault) {
        this.name = name;
        this.codec = codec;
        this.itemDefault = itemDefault;
    }

    /** 无默认值键:未写入 NBT 前 {@link #get} 返回 null。 */
    public static <T> ItemDataKey<T> create(String name, Codec<T> codec) {
        return new ItemDataKey<>(name, codec, null);
    }

    /** 带固定默认值的键。 */
    public static <T> ItemDataKey<T> withDefault(String name, Codec<T> codec, T defaultValue) {
        return new ItemDataKey<>(name, codec, item -> defaultValue);
    }

    /** 带按物品默认值的键(对应 1.21 在 Item.Properties 上注册组件默认值)。 */
    public static <T> ItemDataKey<T> withItemDefault(String name, Codec<T> codec, Function<Item, T> itemDefault) {
        return new ItemDataKey<>(name, codec, itemDefault);
    }

    public String name() {
        return name;
    }

    /** NBT 中是否存在该键(不含默认值;对应 1.21 {@code stack.has} 对非默认组件的判定)。 */
    public boolean has(ItemStack stack) {
        return stack.hasTag() && stack.getTag().contains(name);
    }

    /** 读取:NBT 值优先,其次按物品默认值,均无则 null。 */
    public T get(ItemStack stack) {
        if (stack.hasTag() && stack.getTag().contains(name)) {
            DataResult<T> result = codec.parse(net.minecraft.nbt.NbtOps.INSTANCE, stack.getTag().get(name));
            T value = result.result().orElse(null);
            if (value != null) {
                return value;
            }
        }
        return itemDefault != null ? itemDefault.apply(stack.getItem()) : null;
    }

    /** 读取:NBT 值优先,其次 fallback(不走按物品默认值)。 */
    public T getOrDefault(ItemStack stack, T fallback) {
        if (stack.hasTag() && stack.getTag().contains(name)) {
            DataResult<T> result = codec.parse(net.minecraft.nbt.NbtOps.INSTANCE, stack.getTag().get(name));
            T value = result.result().orElse(null);
            if (value != null) {
                return value;
            }
        }
        return fallback;
    }

    /** 写入(编解码失败时静默跳过,与组件系统"不可能失败"的宽松语义对齐)。 */
    public void set(ItemStack stack, T value) {
        if (value == null) {
            remove(stack);
            return;
        }
        DataResult<Tag> result = codec.encodeStart(net.minecraft.nbt.NbtOps.INSTANCE, value);
        result.result().ifPresent(tag -> stack.getOrCreateTag().put(name, tag));
    }

    /** 移除。 */
    public void remove(ItemStack stack) {
        if (stack.hasTag()) {
            stack.getTag().remove(name);
        }
    }
}
