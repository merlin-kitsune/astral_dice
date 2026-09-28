package com.merlinkitsune.astral_dice.compat.curios;

import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;

import net.minecraft.world.item.ItemStack;

/**
 * 饰品库存视图(Curios {@code ICuriosItemHandler} 的子集)。
 *
 * <p>实现由 {@link CuriosApi#getCuriosInventory} 提供,底层委托给前置库
 * {@code com.merlinkitsune.starenginelib.item.TrinketsCompat}(Trinkets 3.7.2)。
 */
public interface ICuriosItemHandler {

    /** 全部槽位组(按槽位标识索引:{@code dice} / {@code stand} / {@code chip})。 */
    Map<String, ICurioStacksHandler> getCurios();

    /** 按标识取槽位组。 */
    Optional<ICurioStacksHandler> getStacksHandler(String identifier);

    /** 首个命中谓词的已装备物品。 */
    Optional<SlotResult> findFirstCurio(Predicate<ItemStack> predicate);
}
