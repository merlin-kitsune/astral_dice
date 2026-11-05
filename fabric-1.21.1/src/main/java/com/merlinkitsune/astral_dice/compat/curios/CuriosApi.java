package com.merlinkitsune.astral_dice.compat.curios;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

import com.merlinkitsune.astral_dice.init.ModCompatibilityCheck;
import com.merlinkitsune.starengine.item.TrinketsCompat;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.item.ItemStack;

/**
 * 饰品 API 门面(Fabric 侧适配) —— 类名与 Curios 的 {@code top.theillusivec4.curios.api.CuriosApi}
 * 一致,使消费方 94 处 {@code CuriosApi.*} 调用只需换 import。
 *
 * <h2>单源:Trinkets(2026-11-05 用户裁决)</h2>
 * <p>本线曾经把 Accessories 与 Trinkets 聚合成同一视图(2026-09-29),但 Accessories 在
 * 1.21.1 上硬依赖 owo-lib(兼容性灾难),且 Accessories Compatibility Layer 会绕过按
 * Trinkets 设计的槽位限制 ⇒ 裁决为<b>只做 Trinkets</b>:Accessories / ACL 已进
 * {@code ModCompatibilityCheck.BLACKLIST},装了即拒绝启动。
 * 本门面因此只剩一条通道,读/写都直通 {@code TrinketsCompat}。
 *
 * <p>⚠️ <b>类加载隔离</b>:{@link TrinketBridge} 直接引用 {@code dev.emi.trinkets.api.*},
 * 而 Trinkets 是软依赖 ⇒ 该模组缺席时绝不能让那个类被 JVM 加载;所有调用都被
 * {@link #TRINKETS_LOADED}(静态布尔,来自 {@code FabricLoader})守住。
 */
public final class CuriosApi {

    /** 兼容用常量:Curios 侧为 "curios";Fabric 侧槽位由数据包定义,不再使用该值。 */
    public static final String MODID = ModCompatibilityCheck.TRINKETS_MOD_ID;

    /**
     * 饰品通道是否在场(2026-11-05:只剩 Trinkets 一条)。
     *
     * <p><b>单一权威</b>在 {@link ModCompatibilityCheck}(它同时负责「装了哪个」的装载期校验);
     * 这里的静态布尔只是**为热路径缓存**一次 {@code FabricLoader} 查询 ——
     * 二者取值来源同一常量,不会漂移。
     */
    private static final boolean TRINKETS_LOADED = ModCompatibilityCheck.isTrinketsPresent();

    /** Trinkets 是否在场(供诊断/日志使用;调用 {@code TrinketBridge} 前必须先过这一关)。 */
    public static boolean isTrinketsPresent() {
        return TRINKETS_LOADED;
    }

    /** 饰品库存(对应 Curios 的 {@code getCuriosInventory},返回 Optional)。 */
    public static Optional<ICursiosItemHandler> getCuriosInventory(LivingEntity entity) {
        if (entity == null) {
            return Optional.empty();
        }
        // 2026-11-05:饰品后端只做 Trinkets(Accessories / ACL 已进不兼容列表)。
        // ⚠️ 必须判空:Trinkets 缺席时裸调 TrinketsCompat 会在类解析阶段抛 NoClassDefFoundError。
        if (!TRINKETS_LOADED) {
            return Optional.empty();
        }
        return TrinketsCompat.getCuriosInventory(entity).map(View::new);
    }

    /** 按标识取槽位组(对应 Forge 侧 {@code CuriosCompat.getStacksHandler})。 */
    public static Optional<ICurioStacksHandler> getStacksHandler(LivingEntity entity, String identifier) {
        return getCuriosInventory(entity).flatMap(view -> view.getStacksHandler(identifier));
    }

    /** 全部槽位组(对应 Forge 侧 {@code CuriosCompat.getCuriosMap})。 */
    public static Map<String, ICurioStacksHandler> getCuriosMap(LivingEntity entity) {
        return getCuriosInventory(entity).map(ICursiosItemHandler::getCurios).orElseGet(Collections::emptyMap);
    }

    // ------------------------------------------------------------------
    // Trinkets 单源视图
    // ------------------------------------------------------------------

    private static final class View implements ICursiosItemHandler {
        private final TrinketsCompat.InventoryView delegate;
        private final Map<String, ICurioStacksHandler> handlers;

        View(TrinketsCompat.InventoryView delegate) {
            this.delegate = delegate;
            Map<String, ICurioStacksHandler> mapped = new LinkedHashMap<>();
            delegate.getCurios().forEach((id, handler) -> mapped.put(id, new Handler(handler)));
            this.handlers = Collections.unmodifiableMap(mapped);
        }

        @Override
        public Map<String, ICurioStacksHandler> getCurios() {
            return handlers;
        }

        @Override
        public Optional<ICurioStacksHandler> getStacksHandler(String identifier) {
            return Optional.ofNullable(handlers.get(identifier));
        }

        @Override
        public Optional<SlotResult> findFirstCurio(Predicate<ItemStack> predicate) {
            return delegate.findFirstCurio(predicate)
                    .map(r -> new SlotResult(r.slotId(), r.index(), r.stack()));
        }

        @Override
        public List<SlotResult> findCurios(Predicate<ItemStack> predicate) {
            return delegate.findCurios(predicate).stream()
                    .map(r -> new SlotResult(r.slotId(), r.index(), r.stack()))
                    .toList();
        }
    }

    private static final class Handler implements ICurioStacksHandler {
        private final TrinketsCompat.SlotHandler delegate;

        Handler(TrinketsCompat.SlotHandler delegate) {
            this.delegate = delegate;
        }

        @Override
        public IItemHandler getStacks() {
            return new Slots(delegate.getStacks());
        }

        @Override
        public int getSlots() {
            return delegate.getSlots();
        }

        @Override
        public Map<ResourceLocation, AttributeModifier> getModifiers() {
            return delegate.getModifiers();
        }

        @Override
        public void removeModifier(ResourceLocation id) {
            delegate.removeModifier(id);
        }

        @Override
        public void addPermanentModifier(AttributeModifier modifier) {
            delegate.addPermanentModifier(modifier);
        }

        @Override
        public void update() {
            delegate.update();
        }
    }

    private static final class Slots implements IItemHandler {
        private final TrinketsCompat.SlotInventory delegate;

        Slots(TrinketsCompat.SlotInventory delegate) {
            this.delegate = delegate;
        }

        @Override
        public int getSlots() {
            return delegate.getSlots();
        }

        @Override
        public ItemStack getStackInSlot(int slot) {
            return delegate.getStackInSlot(slot);
        }

        @Override
        public void setStackInSlot(int slot, ItemStack stack) {
            delegate.setStackInSlot(slot, stack);
        }
    }

    // ------------------------------------------------------------------
    // 多源聚合
    // ------------------------------------------------------------------

    /** 多源聚合视图({@code sources} 按优先级排列:下标 0 = 主源)。 */
    private static final class Aggregate implements ICursiosItemHandler {
        private final List<ICursiosItemHandler> sources;
        private final Map<String, ICurioStacksHandler> mergedHandlers;

        Aggregate(List<ICursiosItemHandler> sources) {
            this.sources = List.copyOf(sources);

            Map<String, List<ICurioStacksHandler>> byIdentifier = new LinkedHashMap<>();
            for (ICursiosItemHandler source : this.sources) {
                source.getCurios().forEach((identifier, handler) ->
                        byIdentifier.computeIfAbsent(identifier, k -> new ArrayList<>()).add(handler));
            }
            Map<String, ICurioStacksHandler> merged = new LinkedHashMap<>();
            byIdentifier.forEach((identifier, handlers) ->
                    merged.put(identifier, handlers.size() == 1 ? handlers.get(0) : new MergedHandler(handlers)));
            this.mergedHandlers = Collections.unmodifiableMap(merged);
        }

        @Override
        public Map<String, ICurioStacksHandler> getCurios() {
            return mergedHandlers;
        }

        @Override
        public Optional<ICurioStacksHandler> getStacksHandler(String identifier) {
            return Optional.ofNullable(mergedHandlers.get(identifier));
        }

        @Override
        public Optional<SlotResult> findFirstCurio(Predicate<ItemStack> predicate) {
            for (ICursiosItemHandler source : sources) {
                Optional<SlotResult> hit = source.findFirstCurio(predicate);
                if (hit.isPresent()) {
                    return hit;
                }
            }
            return Optional.empty();
        }

        /**
         * 两源的并集。
         *
         * <p>⚠️ 按 {@link ItemStack} **实例同一性**去重:若玩家额外装了官方
         * {@code Trinkets Compat Layer}(它用 mixin 把 {@code TrinketsApi} 重定向到 Accessories 数据),
         * 两个源其实是同一份库存的两个视图,会出现同一个槽位栈被枚举两次 ⇒ 不去重会让
         * 「找齐所有立牌」这类调用拿到重复项。用实例而非 {@code equals} 去重,
         * 才不会把两个内容恰好相同的**不同**饰品误并成一个。
         */
        @Override
        public List<SlotResult> findCurios(Predicate<ItemStack> predicate) {
            List<SlotResult> out = new ArrayList<>();
            Set<ItemStack> seen = Collections.newSetFromMap(new IdentityHashMap<>());
            for (ICursiosItemHandler source : sources) {
                for (SlotResult result : source.findCurios(predicate)) {
                    if (seen.add(result.stack())) {
                        out.add(result);
                    }
                }
            }
            return out;
        }
    }

    /**
     * 同一槽位名同时存在于两个源时的合并处理器。
     *
     * <p>两条规则:
     * <ol>
     *   <li><b>内容</b>:读优先取「非空的那一边」——
     *       这样 {@code DiceCurioItem} 读 dice 槽、{@code BaseSignItem} 读 stand 槽时,
     *       不论玩家把饰品装在哪一侧都读得到;写则落到「当前持有内容的那一边」,
     *       都空时落主源(即下蹲右键自动装备的落点)。</li>
     *   <li><b>槽位修饰符</b>(筹码栏位数):<b>两侧同写</b>。保证不管玩家从哪个界面看,
     *       筹码栏位数都是当前骰子星级对应的值;也让「槽位数 = max(两侧)」的读数保持稳定。</li>
     * </ol>
     */
    private static final class MergedHandler implements ICurioStacksHandler {
        private final List<ICurioStacksHandler> handlers;

        MergedHandler(List<ICurioStacksHandler> handlers) {
            this.handlers = List.copyOf(handlers);
        }

        private int maxSlots() {
            int max = 0;
            for (ICurioStacksHandler handler : handlers) {
                max = Math.max(max, handler.getSlots());
            }
            return max;
        }

        @Override
        public IItemHandler getStacks() {
            return new MergedSlots(handlers);
        }

        @Override
        public int getSlots() {
            return maxSlots();
        }

        @Override
        public Map<ResourceLocation, AttributeModifier> getModifiers() {
            Map<ResourceLocation, AttributeModifier> out = new LinkedHashMap<>();
            for (ICurioStacksHandler handler : handlers) {
                out.putAll(handler.getModifiers());
            }
            return out;
        }

        @Override
        public void removeModifier(ResourceLocation id) {
            for (ICurioStacksHandler handler : handlers) {
                handler.removeModifier(id);
            }
        }

        @Override
        public void addPermanentModifier(AttributeModifier modifier) {
            for (ICurioStacksHandler handler : handlers) {
                handler.addPermanentModifier(modifier);
            }
        }

        @Override
        public void update() {
            for (ICurioStacksHandler handler : handlers) {
                handler.update();
            }
        }
    }

    private static final class MergedSlots implements IItemHandler {
        private final List<ICurioStacksHandler> handlers;

        MergedSlots(List<ICurioStacksHandler> handlers) {
            this.handlers = handlers;
        }

        @Override
        public int getSlots() {
            int max = 0;
            for (ICurioStacksHandler handler : handlers) {
                max = Math.max(max, handler.getStacks().getSlots());
            }
            return max;
        }

        @Override
        public ItemStack getStackInSlot(int slot) {
            for (ICurioStacksHandler handler : handlers) {
                IItemHandler stacks = handler.getStacks();
                if (slot < 0 || slot >= stacks.getSlots()) {
                    continue;
                }
                ItemStack stack = stacks.getStackInSlot(slot);
                if (!stack.isEmpty()) {
                    return stack;
                }
            }
            return ItemStack.EMPTY;
        }

        @Override
        public void setStackInSlot(int slot, ItemStack stack) {
            // 写入当前持有内容的源(移除物品时这里找到的就是真正装着它的那一侧)
            for (ICurioStacksHandler handler : handlers) {
                IItemHandler stacks = handler.getStacks();
                if (slot >= 0 && slot < stacks.getSlots() && !stacks.getStackInSlot(slot).isEmpty()) {
                    stacks.setStackInSlot(slot, stack);
                    return;
                }
            }
            // 都空(新装备):落主源
            for (ICurioStacksHandler handler : handlers) {
                IItemHandler stacks = handler.getStacks();
                if (slot >= 0 && slot < stacks.getSlots()) {
                    stacks.setStackInSlot(slot, stack);
                    return;
                }
            }
        }
    }

    private CuriosApi() {
    }
}
