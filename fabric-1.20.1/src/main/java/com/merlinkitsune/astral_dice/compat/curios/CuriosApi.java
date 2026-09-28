package com.merlinkitsune.astral_dice.compat.curios;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;

import com.merlinkitsune.starenginelib.item.TrinketsCompat;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

/**
 * 饰品 API 门面(Fabric 侧适配) —— 类名与 Curios 的 {@code top.theillusivec4.curios.api.CuriosApi}
 * 一致,使消费方 27 处 {@code CuriosApi.*} 调用只需换 import。
 *
 * <h2>底层</h2>
 * 全部委托给前置库的 {@link TrinketsCompat}(Trinkets 3.7.2)。本类只做
 * 「库的视图类型 → 本模组的 Curios 形状接口」这一层类型适配。
 *
 * <p>⚠️ {@link #MODID} 保留只为兼容旧调用点:Curios 侧它用于 IMC 槽位注册,
 * Fabric 侧槽位改由数据包({@code data/trinkets/slots/**})定义,该常量已无实际作用。
 */
public final class CuriosApi {

    /** 兼容用常量:Curios 侧为 "curios";Fabric 侧槽位由数据包定义,不再使用该值。 */
    public static final String MODID = "trinkets";

    /** 饰品库存(对应 Curios 的 {@code getCuriosInventory},返回 Optional)。 */
    public static Optional<ICuriosItemHandler> getCuriosInventory(LivingEntity entity) {
        return TrinketsCompat.getCuriosInventory(entity).map(View::new);
    }

    /** 按标识取槽位组(对应 Forge 侧 {@code CuriosCompat.getStacksHandler})。 */
    public static Optional<ICurioStacksHandler> getStacksHandler(LivingEntity entity, String identifier) {
        return getCuriosInventory(entity).flatMap(view -> view.getStacksHandler(identifier));
    }

    /** 全部槽位组(对应 Forge 侧 {@code CuriosCompat.getCuriosMap})。 */
    public static Map<String, ICurioStacksHandler> getCuriosMap(LivingEntity entity) {
        return getCuriosInventory(entity).map(ICuriosItemHandler::getCurios).orElseGet(Collections::emptyMap);
    }

    private static final class View implements ICuriosItemHandler {
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

    private CuriosApi() {
    }
}
