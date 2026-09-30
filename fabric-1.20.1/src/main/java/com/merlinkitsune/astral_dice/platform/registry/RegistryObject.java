package com.merlinkitsune.astral_dice.platform.registry;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;

import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;

/**
 * 注册表条目句柄(对齐 Forge 的 {@code registries.RegistryObject})。
 *
 * <p>语义:句柄在 {@link DeferredRegister#register(String, Supplier)} 时创建,
 * 其值在 {@link DeferredRegister#commit()} 时填充(即真正调用 {@code Registry.register})。
 * {@link #get()} 在填充前调用会抛异常 —— 与 Forge 一致,便于把「注册前取用」这类时序错误
 * 在早期暴露,而不是静默返回 null。
 */
public final class RegistryObject<T> {

    private final ResourceLocation id;
    private final ResourceKey<T> key;
    private final List<Consumer<T>> afterBind = new ArrayList<>();
    private T value;
    private boolean bound;

    RegistryObject(ResourceLocation id, ResourceKey<T> key) {
        this.id = id;
        this.key = key;
    }

    void bind(T value) {
        this.value = value;
        this.bound = true;
        if (!afterBind.isEmpty()) {
            List<Consumer<T>> flush = new ArrayList<>(afterBind);
            afterBind.clear();
            flush.forEach(c -> c.accept(value));
        }
    }

    /** 取注册后的实例。 */
    public T get() {
        if (!bound) {
            throw new IllegalStateException(
                    "Registry object " + id + " is not bound yet (its DeferredRegister has not been committed)");
        }
        return value;
    }

    public boolean isPresent() {
        return bound;
    }

    public boolean isEmpty() {
        return !bound;
    }

    public ResourceLocation getId() {
        return id;
    }

    public ResourceKey<T> getKey() {
        return key;
    }

    /** 派生句柄:源绑定后自动绑定映射值。 */
    public <U> RegistryObject<U> map(Function<? super T, ? extends U> mapper) {
        RegistryObject<U> mapped = new RegistryObject<>(id, null);
        Consumer<T> apply = v -> mapped.bind(mapper.apply(v));
        if (bound) {
            apply.accept(value);
        } else {
            afterBind.add(apply);
        }
        return mapped;
    }

    public void ifPresent(Consumer<? super T> action) {
        if (bound) {
            action.accept(value);
        }
    }

    public T orElse(T fallback) {
        return bound ? value : fallback;
    }

    /** 以 id 做相等/哈希 —— 对齐「句柄身份」而非「值身份」,避免注册前后行为漂移。 */
    @Override
    public boolean equals(Object o) {
        return this == o || (o instanceof RegistryObject<?> other && id.equals(other.id));
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }

    @Override
    public String toString() {
        return "RegistryObject[" + id + (bound ? "" : ", unbound") + "]";
    }
}
