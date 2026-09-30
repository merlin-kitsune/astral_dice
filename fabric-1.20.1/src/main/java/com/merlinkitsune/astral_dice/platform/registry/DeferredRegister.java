package com.merlinkitsune.astral_dice.platform.registry;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import com.merlinkitsune.astral_dice.platform.event.IEventBus;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;

/**
 * 延迟注册器(对齐 Forge 的 {@code registries.DeferredRegister})。
 *
 * <h2>为什么做这层薄包装</h2>
 * 消费方有 9 个注册类({@code ModItems} 137 项 / {@code ModEffects} 45 / {@code ModSounds} 12 /
 * {@code ModParticles} / {@code ModMenuTypes} / {@code ModRecipeSerializers} / {@code ModCreativeTabs} /
 * {@code ModEnchantments} / {@code AstralLootModifiers}),共 93 处
 * {@code REG.register("name", () -> new X(...))} 与 1800+ 处 {@code .get()}。
 * 有这层包装后,**9 个注册类的声明面与调用面零改动**,平台差异收敛到本文件。
 *
 * <h2>语义</h2>
 * <ul>
 *   <li>{@code create(registry|key, modid)}:记录目标注册表,不立即注册。</li>
 *   <li>{@code register(name, supplier)}:登记条目,返回尚未绑定的 {@link RegistryObject}。</li>
 *   <li>{@link #commit()}(经 {@code register(IEventBus)} 触发):按登记顺序真正
 *       {@code Registry.register(...)} 并绑定句柄。**必须在 mod 初始化阶段调用**
 *       (Fabric 的注册表在此阶段仍可写)。</li>
 * </ul>
 *
 * <p>⚠️ 与 Forge 的差异(已登记):Forge 的条目创建时机由 {@code RegisterEvent} 决定,
 * 且 {@code RegistryObject#get()} 在「注册表冻结前」可解析;本实现的 {@code get()} 只在本
 * {@code DeferredRegister} 提交后可用。消费方的既有约定本就是「不在静态初始化阶段
 * 调用 .get()」,故不受影响。
 */
public final class DeferredRegister<T> {

    private record Entry<I>(String name, Supplier<? extends I> supplier, RegistryObject<I> holder) {
    }

    private final Registry<T> registry;
    private final ResourceKey<? extends Registry<T>> registryKey;
    private final String modid;
    private final List<Entry<?>> entries = new ArrayList<>();
    private final List<RegistryObject<?>> pending = new ArrayList<>();
    private boolean committed;

    private DeferredRegister(Registry<T> registry, ResourceKey<? extends Registry<T>> registryKey, String modid) {
        this.registry = registry;
        this.registryKey = registryKey;
        this.modid = modid;
    }

    /** 由已解析的 {@link Registry} 创建(对应 {@code DeferredRegister.create(ForgeRegistries.ITEMS, MODID)})。 */
    public static <T> DeferredRegister<T> create(Registry<T> registry, String modid) {
        return new DeferredRegister<>(registry, null, modid);
    }

    /**
     * 由 {@link ResourceKey} 创建(对应 {@code DeferredRegister.create(Registries.MOB_EFFECT, MODID)})。
     * 注册表在提交时经 {@link BuiltInRegistries#REGISTRY} 解析 —— 避免在类初始化阶段就触碰注册表。
     */
    public static <T> DeferredRegister<T> create(ResourceKey<? extends Registry<T>> key, String modid) {
        return new DeferredRegister<>(null, key, modid);
    }

    /** 登记一个条目,得到尚未绑定的句柄。 */
    public <I extends T> RegistryObject<I> register(String name, Supplier<? extends I> supplier) {
        if (committed) {
            throw new IllegalStateException("DeferredRegister for " + modid + " already committed; cannot add " + name);
        }
        ResourceLocation id = new ResourceLocation(modid, name);
        entries.add(new Entry<>(name, supplier, newHolder(id)));
        return lastHolder();
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private <I extends T> RegistryObject<I> newHolder(ResourceLocation id) {
        ResourceKey<I> key = null;
        if (registryKey != null) {
            key = ResourceKey.create((ResourceKey) registryKey, id);
        }
        RegistryObject<I> holder = new RegistryObject<>(id, key);
        pending.add(holder);
        return holder;
    }

    @SuppressWarnings("unchecked")
    private <I extends T> RegistryObject<I> lastHolder() {
        return (RegistryObject<I>) pending.get(pending.size() - 1);
    }

    /** 全部已登记句柄(对齐 Forge 的 {@code DeferredRegister#getEntries})。 */
    @SuppressWarnings("unchecked")
    public java.util.Collection<RegistryObject<T>> getEntries() {
        java.util.List<RegistryObject<T>> out = new ArrayList<>(entries.size());
        for (Entry<?> e : entries) {
            out.add((RegistryObject<T>) e.holder());
        }
        return out;
    }

    /** Forge 形状的入口:{@code bus} 只用于触发提交(本实现不往总线注册任何东西)。 */
    public void register(IEventBus bus) {
        commit();
    }

    /** 直接提交(不经过总线)。 */
    public void commit() {
        if (committed) {
            return;
        }
        committed = true;
        Registry<T> target = resolveRegistry();
        for (Entry<?> raw : entries) {
            bindUnchecked(target, raw);
        }
    }

    @SuppressWarnings("unchecked")
    private <I extends T> void bindUnchecked(Registry<T> target, Entry<?> raw) {
        Entry<I> entry = (Entry<I>) raw;
        I value = entry.supplier().get();
        Registry.register(target, entry.holder().getId(), value);
        entry.holder().bind(value);
    }

    @SuppressWarnings("unchecked")
    private Registry<T> resolveRegistry() {
        if (registry != null) {
            return registry;
        }
        Registry<?> resolved = BuiltInRegistries.REGISTRY.get(registryKey.location());
        if (resolved == null) {
            throw new IllegalStateException("No registry found for key " + registryKey.location());
        }
        return (Registry<T>) resolved;
    }
}
