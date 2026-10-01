package com.merlinkitsune.astral_dice.compat.accessories;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;

import com.merlinkitsune.astral_dice.AstralDiceMod;
import com.merlinkitsune.astral_dice.compat.curios.ICurioItem;
import com.merlinkitsune.astral_dice.compat.curios.ICurioStacksHandler;
import com.merlinkitsune.astral_dice.compat.curios.ICursiosItemHandler;
import com.merlinkitsune.astral_dice.compat.curios.IItemHandler;
import com.merlinkitsune.astral_dice.compat.curios.SlotContext;
import com.merlinkitsune.astral_dice.compat.curios.SlotResult;

import com.google.common.collect.Multimap;

import io.wispforest.accessories.api.AccessoriesAPI;
import io.wispforest.accessories.api.AccessoriesCapability;
import io.wispforest.accessories.api.AccessoriesContainer;
import io.wispforest.accessories.api.Accessory;
import io.wispforest.accessories.api.attributes.AccessoryAttributeBuilder;
import io.wispforest.accessories.api.slot.SlotReference;
import io.wispforest.accessories.api.slot.SlotType;
import net.fabricmc.fabric.api.util.TriState;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.Container;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * <b>Accessories(Wisp Forest 数据驱动饰品库)适配层</b> —— 让本模组的饰品在 Trinkets 之外
 * 多一条可用的装备通道,两者**同时安装**时也能各自正常工作(双源聚合见
 * {@link com.merlinkitsune.astral_dice.compat.curios.CuriosApi})。
 *
 * <h2>为什么是「原生适配」而不是装官方兼容层</h2>
 * <ul>
 *   <li>Wisp Forest 为 1.20.1 提供的是已**停更**的 {@code Trinkets Compat Layer for Accessories}
 *       (其 issue #271 记录了 1.20.1 版「log-less crash」,且官方明确声明 1.20.1 停止开发);
 *       1.21.1+ 才有的 {@code Accessories Compatibility Layer} 也不覆盖 1.20.1。</li>
 *   <li>兼容层的做法是 mixin 到 Trinkets 上把 {@code TrinketComponent} 包成 Accessories 视图,
 *       **前提是 Accessories 侧已有同名槽位数据** —— 也就是说槽位数据这一层无论如何都要我们自己写。</li>
 * </ul>
 *
 * <h2>与 Trinkets / Curios 的机制对照(实测自 1.0.0-beta.48 的 sources)</h2>
 * <table border="1">
 *   <caption>三个平台的概念映射</caption>
 *   <tr><th>本模组门面({@code compat.curios})</th><th>Trinkets 3.7.2</th><th>Accessories 1.0.0-beta.48</th></tr>
 *   <tr><td>{@code ICuriosItemHandler}</td><td>{@code TrinketComponent}</td>
 *       <td>{@code AccessoriesCapability}({@code getContainers()})</td></tr>
 *   <tr><td>{@code ICurioStacksHandler}</td><td>{@code TrinketInventory}</td>
 *       <td>{@code AccessoriesContainer}</td></tr>
 *   <tr><td>{@code getStacks().getSlots()/getStackInSlot}</td><td>{@code getContainerSize()/getItem()}</td>
 *       <td>{@code getSize()} / {@code getAccessories().getItem()}</td></tr>
 *   <tr><td>{@code getModifiers()} → {@code Map<UUID, AttributeModifier>}</td>
 *       <td>同类型({@code TrinketInventory#getModifiers})</td>
 *       <td><b>同类型</b>({@code AccessoriesContainer#getModifiers})—— 零转换</td></tr>
 *   <tr><td>{@code addPermanentModifier / removeModifier / update}</td>
 *       <td>{@code addPersistentModifier / removeModifier / update}</td>
 *       <td>{@code addPersistentModifier / removeModifier / update}</td></tr>
 * </table>
 * ⇒ 「筹码栏位数随骰子星级增长」这条链路三个平台**同构**(都是往槽位组挂一个
 * {@code AttributeModifier.Operation.ADDITION} 的绝对修饰符再 update),故无需改玩法代码。
 *
 * <h2>⚠️ 类加载隔离(硬约束)</h2>
 * <p>本类及其全部内部类**直接引用 {@code io.wispforest.accessories.*}**。Accessories 是
 * **软依赖** ⇒ 该模组缺席时,<b>绝不允许</b>本类被 JVM 加载(否则 {@code NoClassDefFoundError})。
 * 因此:
 * <ul>
 *   <li>调用点必须先过 {@code FabricLoader.getInstance().isModLoaded("accessories")}
 *       ——见 {@link CuriosApi} 的 {@code accessoriesOrNull()};</li>
 *   <li>本类的<b>公开签名里不出现任何 Accessories 类型</b>(方法参数/返回值只用本模组与
 *       Minecraft 类型),这样 {@code CuriosApi} 的常量池里不会出现 Accessories 类名。</li>
 * </ul>
 *
 * <p>⚠️ 另一处已知耦合:{@code AccessoriesContainer#getAccessories()} 的返回类型是
 * {@code io.wispforest.accessories.impl.ExpandedSimpleContainer}(impl 包,非公开 API)。
 * 这是 Accessories 自己的 API 签名造成的,无法回避;1.20.1 线已冻结在 beta.48,风险可控。
 * 本类把它当原版 {@link Container} 使用,不触碰 impl 独有成员。
 */
public final class AccessoriesCompat {

    /** Accessories 的 mod id(软依赖判定用)。 */
    public static final String MOD_ID = "accessories";

    /**
     * 槽位验证器 id:{@code astral_dice:curio_slot}。
     *
     * <p>由 {@link #register()} 注册为「本模组三个饰品槽的准入判据」,并在
     * {@code data/astral_dice/accessories/slot/*.json} 的 {@code validators} 里引用。
     * 用**自有命名空间**注册自定义 predicate,而不是往 {@code accessories:dice}
     * 这类他人命名空间的标签里塞物品 —— 后者会把本模组的物品清单写进别人的数据空间,
     * 与 Accessories 将来可能新增的同名槽位产生冲突。
     */
    public static final ResourceLocation SLOT_VALIDATOR_ID =
            new ResourceLocation(AstralDiceMod.MODID, "curio_slot");

    /** 槽位名 → 物品清单标签名(与 Trinkets / Curios 侧同一份清单:见 data/curios/tags/items)。 */
    private static final Map<String, String> SLOT_TAGS = Map.of(
            "dice", "dice",
            "stand", "stand",
            "chip", "chip");

    /** 启动自检的探针物品:每个槽位取一件代表物。 */
    private static final Map<String, Item> SLOT_PROBES = Map.of(
            "dice", com.merlinkitsune.astral_dice.item.ModItems.DICE.get(),
            "stand", com.merlinkitsune.astral_dice.item.ModItems.MISAKI_SIGN.get(),
            "chip", com.merlinkitsune.astral_dice.item.ModItems.TARGET_CHIP.get());

    private static boolean registered = false;

    /**
     * 注册槽位验证器 + 为本模组全部 {@link ICurioItem} 挂 Accessories 适配器(幂等)。
     *
     * <p>调用时机 = mod 初始化(双端),必须早于任何装备校验与数据包重载:
     * 槽位 JSON 里的 {@code validators} 引用本 predicate,而 predicate 是**静态注册表**
     * (不随数据包重载清空),故 init 期注册一次即可覆盖后续全部重载。
     */
    public static void register() {
        if (registered) {
            return;
        }
        registered = true;

        AccessoriesAPI.registerPredicate(SLOT_VALIDATOR_ID, AccessoriesCompat::validateSlot);

        int count = 0;
        for (Item item : BuiltInRegistries.ITEM) {
            ResourceLocation id = BuiltInRegistries.ITEM.getKey(item);
            if (!AstralDiceMod.MODID.equals(id.getNamespace())) {
                continue;
            }
            if (item instanceof ICurioItem curio) {
                AccessoriesAPI.registerAccessory(item, new Adapter(curio));
                count++;
            }
        }
        AstralDiceMod.LOGGER.info("[Astral Dice] 已为 {} 件饰品物品注册 Accessories 适配器,槽位验证器 = {}",
                count, SLOT_VALIDATOR_ID);
    }

    /**
     * 槽位准入判据:物品在对应标签里才可装入。
     *
     * <p>判据源 = {@code data/curios/tags/items/<slot>.json}(与 Curios 侧 IMC 槽位、
     * Trinkets 侧 tag 验证器**同一份清单**,避免三处各写一份而漏项)。
     *
     * <p>语义与 Accessories 内建 {@code accessories:tag} 验证器对齐:命中给 {@link TriState#TRUE}、
     * 未命中给 {@link TriState#DEFAULT}(不是 FALSE —— DEFAULT 表示「本验证器不表态」,
     * 让同一槽位上可能存在的其它验证器继续判定)。
     */
    private static TriState validateSlot(Level level, SlotType slotType, int index, ItemStack stack) {
        String tagName = SLOT_TAGS.get(slotType.name());
        if (tagName == null) {
            return TriState.DEFAULT;
        }
        TagKey<Item> tag = TagKey.create(Registries.ITEM, new ResourceLocation("curios", tagName));
        return stack.is(tag) ? TriState.TRUE : TriState.DEFAULT;
    }

    /** 取该实体的 Accessories 库存视图;实体没有 Accessories 能力时返回空。 */
    public static Optional<ICursiosItemHandler> getInventory(LivingEntity entity) {
        if (entity == null) {
            return Optional.empty();
        }
        AccessoriesCapability capability = AccessoriesCapability.get(entity);
        return capability == null ? Optional.empty() : Optional.of(new View(capability));
    }

    /** 槽位引用 → 本模组的 {@link SlotContext}(第 2 参 = 槽位标识,与 Curios/Trinkets 侧同名)。 */
    private static SlotContext context(SlotReference reference) {
        return new SlotContext(reference.entity(), reference.slotName(), reference.slot());
    }

    /**
     * 一次性启动自检：把「骰子 / 立牌 / 筹码」各自**实际可入的槽位**打进日志。
     *
     * <p>为什么需要它：槽位数据({@code data/astral_dice/accessories/slot/*.json} +
     * {@code entity/*.json})由数据包加载，出问题时多数情况**只打 WARN 不抛异常**
     * —— 例如 {@code EntitySlotLoader} 找不到槽位、或 {@code strictMode} 把它挡掉。
     * 「启动没有报错」并不等于「槽位真的生效」。这里用
     * {@link AccessoriesAPI#getStackSlotTypes} 反查一次，把**判据**落到日志上：
     * 三条探针里任何一条列出空列表，就说明「物品 → 槽位」的准入链断了
     * (槽位定义 / entity 绑定 / {@code curio_slot} 验证器，三者之一)。
     *
     * <p>⚠️ 必须在**数据包加载之后**执行 ⇒ 挂 {@code SERVER_STARTED}。
     */
    public static void installSlotDiagnostics() {
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            Level level = server.overworld();
            StringBuilder report = new StringBuilder();
            for (Map.Entry<String, Item> probe : SLOT_PROBES.entrySet()) {
                Collection<SlotType> slots =
                        AccessoriesAPI.getStackSlotTypes(level, new ItemStack(probe.getValue()));
                report.append('\n').append("  ").append(probe.getKey()).append(" -> ")
                        .append(slots.stream().map(SlotType::name).sorted().toList());
            }
            AstralDiceMod.LOGGER.info("[Astral Dice][Accessories] 槽位准入自检{}", report);
        });
    }

    /**
     * 给「与该槽位一一对应」的属性修饰符求值时用的 UUID。
     *
     * <p>对齐 Trinkets 侧 {@code SlotAttributes.getUuid(ref)} 的语义(槽位派生)。
     * 本模组现有的 {@link ICurioItem#getAttributeModifiers} 实现一律**忽略**该参数、
     * 自行按物品注册名派生 UUID,故这里只需给一个稳定值。
     */
    private static UUID slotUuid(SlotReference reference) {
        return UUID.nameUUIDFromBytes(reference.createSlotPath().getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    // ------------------------------------------------------------------
    // 物品侧:ICurioItem → Accessory
    // ------------------------------------------------------------------

    /**
     * {@link ICurioItem} → Accessories {@link Accessory} 的桥(逐件物品一个实例)。
     *
     * <p>回调语义与 {@code TrinketBridge.Adapter} 保持一致(两处必须同改):
     * {@code onEquip} 的 prevStack、{@code onUnequip} 的 newStack 一律补
     * {@link ItemStack#EMPTY} —— Accessories 与 Trinkets 一样只交出「涉及的那一件」,
     * 而本模组的方法体是按 Curios 的「槽位原内容 / 将要占位的栈」语义写的。
     */
    private record Adapter(ICurioItem delegate) implements Accessory {

        @Override
        public void tick(ItemStack stack, SlotReference reference) {
            delegate.curioTick(context(reference), stack);
        }

        @Override
        public void onEquip(ItemStack stack, SlotReference reference) {
            delegate.onEquip(context(reference), ItemStack.EMPTY, stack);
        }

        @Override
        public void onUnequip(ItemStack stack, SlotReference reference) {
            delegate.onUnequip(context(reference), ItemStack.EMPTY, stack);
        }

        @Override
        public boolean canEquip(ItemStack stack, SlotReference reference) {
            return delegate.canEquip(context(reference), stack);
        }

        @Override
        public boolean canUnequip(ItemStack stack, SlotReference reference) {
            return delegate.canUnequip(context(reference), stack);
        }

        /**
         * ⚠️ 显式**禁用** Accessories 自带的「右键即装备」。
         *
         * <p>本模组的装备入口是 {@code CurioSlotUtil.tryAutoEquip}(下蹲右键),
         * 其中带有两条业务校验:① 立牌/筹码必须先佩戴骰子;② 同种饰品不可重复装备。
         * Accessories 的 equip-from-use 走的是另一条旁路,不经过上述校验 ⇒ 会让玩家
         * 绕过规则把筹码装进没有骰子的角色。保持与 Curios / Trinkets 侧**同一入口**。
         */
        @Override
        public boolean canEquipFromUse(ItemStack stack) {
            return false;
        }

        @Override
        @SuppressWarnings({"deprecation", "removal"})
        public boolean canEquipFromUse(ItemStack stack, SlotReference reference) {
            return false;
        }

        @Override
        public void getDynamicModifiers(ItemStack stack, SlotReference reference, AccessoryAttributeBuilder builder) {
            Multimap<Attribute, AttributeModifier> modifiers =
                    delegate.getAttributeModifiers(context(reference), slotUuid(reference), stack);
            // addExclusive:同一 (属性, 来源) 只保留一条。本模组的修饰符 UUID 由物品注册名派生
            // ⇒ 同种饰品的第二个副本不会把加成翻倍，与 Curios 侧「同 UUID 覆盖」的语义一致。
            modifiers.forEach(builder::addExclusive);
        }
    }

    // ------------------------------------------------------------------
    // 库存侧:AccessoriesCapability → ICursiosItemHandler
    // ------------------------------------------------------------------

    private static final class View implements ICursiosItemHandler {
        private final Map<String, ICurioStacksHandler> handlers;

        View(AccessoriesCapability capability) {
            Map<String, ICurioStacksHandler> mapped = new LinkedHashMap<>();
            capability.getContainers().forEach((slotName, container) -> mapped.put(slotName, new Handler(container)));
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
            for (Map.Entry<String, ICurioStacksHandler> entry : handlers.entrySet()) {
                IItemHandler stacks = entry.getValue().getStacks();
                for (int i = 0; i < stacks.getSlots(); i++) {
                    ItemStack stack = stacks.getStackInSlot(i);
                    if (!stack.isEmpty() && predicate.test(stack)) {
                        return Optional.of(new SlotResult(entry.getKey(), i, stack));
                    }
                }
            }
            return Optional.empty();
        }

        @Override
        public List<SlotResult> findCurios(Predicate<ItemStack> predicate) {
            List<SlotResult> out = new ArrayList<>();
            for (Map.Entry<String, ICurioStacksHandler> entry : handlers.entrySet()) {
                IItemHandler stacks = entry.getValue().getStacks();
                for (int i = 0; i < stacks.getSlots(); i++) {
                    ItemStack stack = stacks.getStackInSlot(i);
                    if (!stack.isEmpty() && predicate.test(stack)) {
                        out.add(new SlotResult(entry.getKey(), i, stack));
                    }
                }
            }
            return out;
        }
    }

    private static final class Handler implements ICurioStacksHandler {
        private final AccessoriesContainer container;

        Handler(AccessoriesContainer container) {
            this.container = container;
        }

        @Override
        public IItemHandler getStacks() {
            return new Slots(container);
        }

        @Override
        public int getSlots() {
            return container.getSize();
        }

        @Override
        public Map<UUID, AttributeModifier> getModifiers() {
            return container.getModifiers();
        }

        @Override
        public void removeModifier(UUID id) {
            container.removeModifier(id);
        }

        @Override
        public void addPermanentModifier(AttributeModifier modifier) {
            container.addPersistentModifier(modifier);
        }

        @Override
        public void update() {
            container.update();
        }
    }

    private static final class Slots implements IItemHandler {
        private final AccessoriesContainer container;

        Slots(AccessoriesContainer container) {
            this.container = container;
        }

        @Override
        public int getSlots() {
            return container.getSize();
        }

        @Override
        public ItemStack getStackInSlot(int slot) {
            Container inventory = container.getAccessories();
            if (slot < 0 || slot >= inventory.getContainerSize()) {
                return ItemStack.EMPTY;
            }
            return inventory.getItem(slot);
        }

        @Override
        public void setStackInSlot(int slot, ItemStack stack) {
            Container inventory = container.getAccessories();
            if (slot < 0 || slot >= inventory.getContainerSize()) {
                return;
            }
            inventory.setItem(slot, stack);
        }
    }

    private AccessoriesCompat() {
    }
}
