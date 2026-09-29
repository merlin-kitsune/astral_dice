package com.merlinkitsune.astral_dice.compat.curios;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;

import com.merlinkitsune.astral_dice.compat.accessories.AccessoriesCompat;
import com.merlinkitsune.astral_dice.init.ModCompatibilityCheck;
import com.merlinkitsune.starenginelib.item.TrinketsCompat;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.item.ItemStack;

/**
 * 饰品 API 门面(Fabric 侧适配) —— 类名与 Curios 的 {@code top.theillusivec4.curios.api.CuriosApi}
 * 一致,使消费方 94 处 {@code CuriosApi.*} 调用只需换 import。
 *
 * <h2>多源聚合(2026-09-29 起)</h2>
 * <p>Fabric 侧存在**两条互不相通的饰品通道**,本类把它们的查询面聚合成同一个视图,
 * 从而让全部消费方代码**一行不改**就同时支持两者:
 * <table border="1">
 *   <caption>数据源与优先级</caption>
 *   <tr><th>优先级</th><th>来源</th><th>前置</th><th>何时生效</th></tr>
 *   <tr><td>1(主源)</td><td>{@link AccessoriesCompat}(Accessories)</td><td>二选一之一</td>
 *       <td>装了 Accessories <b>且</b>该实体有 Accessories 能力</td></tr>
 *   <tr><td>2(兜底)</td><td>{@link TrinketsCompat}(Trinkets)</td><td>二选一之一</td>
 *       <td>装了 Trinkets(2026-09-29 起由硬依赖改为软依赖)</td></tr>
 * </table>
 * ⚠️ <b>两个模组「至少装一个」由 {@code ModCompatibilityCheck#verifyAccessoryProviderOrThrow()}
 * 在 mod 初始化最早期保证</b>(Fabric 的 {@code depends} 是 AND 语义、表达不了 OR,
 * 故两个都只进 {@code recommends});本类只负责「装了哪个就走哪条通道」,
 * 两者皆缺时本类的两个分支都为空集(那种情况游戏已在该检查处停下)。
 * <ul>
 *   <li><b>读</b>{@code findFirstCurio / findCurios}:按优先级依次查,取并集 ⇒ 玩家把饰品装在
 *       哪一边都认得出<b>功能都生效</b>;</li>
 *   <li><b>写</b>{@code getStacksHandler(id)}:同一槽位名两边都存在时返回一个
 *       {@link MergedHandler} —— 读/写**优先作用于当前持有内容的那一边**,
 *       槽位修饰符(筹码栏位数)则**两侧同写**,保证两边的槽位数始终一致;</li>
 *   <li>只有一边存在该槽位时直接透传,零包装开销。</li>
 * </ul>
 *
 * <p>⚠️ <b>主源方向</b>取 Accessories:装了 Accessories 的玩家是冲着它更完整的系统去的,
 * 无内容可读时(例如下蹲右键自动装备)应当落进它的界面里。反之只装 Trinkets 的玩家
 * (绝大多数)走的仍是单源快路径,行为与本次改动前**完全一致**。
 *
 * <p>⚠️ <b>类加载隔离</b>:{@link AccessoriesCompat} 直接引用 {@code io.wispforest.accessories.*},
 * 而 Accessories 是软依赖 ⇒ 该模组缺席时绝不能让那个类被 JVM 加载。故所有调用都被
 * {@link #ACCESSORIES_LOADED}(静态布尔,来自 {@code FabricLoader})守住,且本类**签名里
 * 不出现任何 Accessories 类型** —— Java 的类解析发生在「首次执行到该指令时」,
 * 未执行的 {@code invokestatic} 不会触发加载。
 */
public final class CuriosApi {

    /** 兼容用常量:Curios 侧为 "curios";Fabric 侧槽位由数据包定义,不再使用该值。 */
    public static final String MODID = ModCompatibilityCheck.TRINKETS_MOD_ID;

    /**
     * 两条饰品通道是否在场。
     *
     * <p><b>单一权威</b>在 {@link ModCompatibilityCheck}(它同时负责「至少装一个」的装载期校验);
     * 这里的静态布尔只是**为热路径缓存**一次 {@code FabricLoader} 查询 ——
     * 二者取值来源同一常量,不会漂移。
     *
     * <p>⚠️ 两者都是<b>类加载期求值、只读字符串</b> —— 不触碰对方任何类型,
     * 因此可以在本门面里安全地判空(见类头「类加载隔离」)。
     */
    private static final boolean ACCESSORIES_LOADED = ModCompatibilityCheck.isAccessoriesPresent();
    private static final boolean TRINKETS_LOADED = ModCompatibilityCheck.isTrinketsPresent();

    /** Accessories 是否在场(供诊断/日志使用)。 */
    public static boolean isAccessoriesPresent() {
        return ACCESSORIES_LOADED;
    }

    /** Trinkets 是否在场(供诊断/日志使用;调用 {@code TrinketBridge} 前必须先过这一关)。 */
    public static boolean isTrinketsPresent() {
        return TRINKETS_LOADED;
    }

    /** 饰品库存(对应 Curios 的 {@code getCuriosInventory},返回 Optional)。 */
    public static Optional<ICursiosItemHandler> getCuriosInventory(LivingEntity entity) {
        if (entity == null) {
            return Optional.empty();
        }
        List<ICursiosItemHandler> sources = new ArrayList<>(2);
        if (ACCESSORIES_LOADED) {
            AccessoriesCompat.getInventory(entity).ifPresent(sources::add);
        }
        // ⚠️ 必须判空:Fabric 侧 Trinkets 与 Accessories 二选一,Trinkets 缺席时
        //    裸调 TrinketsCompat 会在类解析阶段抛 NoClassDefFoundError。
        if (TRINKETS_LOADED) {
            TrinketsCompat.getCuriosInventory(entity).map(View::new).ifPresent(sources::add);
        }

        if (sources.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(sources.size() == 1 ? sources.get(0) : new Aggregate(sources));
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
        public Map<UUID, AttributeModifier> getModifiers() {
            return delegate.getModifiers();
        }

        @Override
        public void removeModifier(UUID id) {
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
        public Map<UUID, AttributeModifier> getModifiers() {
            Map<UUID, AttributeModifier> out = new LinkedHashMap<>();
            for (ICurioStacksHandler handler : handlers) {
                out.putAll(handler.getModifiers());
            }
            return out;
        }

        @Override
        public void removeModifier(UUID id) {
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
