package com.merlinkitsune.astral_dice.item;

import net.minecraft.world.entity.LivingEntity;
import com.merlinkitsune.astral_dice.item.CuriosCompat;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.type.capability.ICuriosItemHandler;
import top.theillusivec4.curios.api.type.inventory.ICurioStacksHandler;

import java.util.Map;
import java.util.Optional;

/**
 * Curios 1.20.1 API 适配:1.21 的 {@code CuriosApi.getCuriosInventory} 返回 Optional,
 * 1.20.1 返回 LazyOptional;本助手统一为 Optional,保持 1.21 分支的调用面
 * ({@code isEmpty()/isPresent()/get()})不变。
 */
public final class CuriosCompat {

    public static Optional<ICuriosItemHandler> getCuriosInventory(LivingEntity entity) {
        return CuriosApi.getCuriosInventory(entity).resolve();
    }

    /** 按名称取槽位组(1.20.1 同名 API,集中包装便于统一处理空值)。 */
    public static Optional<ICurioStacksHandler> getStacksHandler(LivingEntity entity, String identifier) {
        return getCuriosInventory(entity)
                .flatMap(handler -> Optional.ofNullable(handler.getCurios().get(identifier)));
    }

    /** 全部槽位组(1.20.1: Map&lt;String, ICurioStacksHandler&gt;)。 */
    public static Map<String, ICurioStacksHandler> getCuriosMap(LivingEntity entity) {
        return getCuriosInventory(entity)
                .map(ICuriosItemHandler::getCurios)
                .orElseGet(java.util.Collections::emptyMap);
    }

    private CuriosCompat() {
    }
}
