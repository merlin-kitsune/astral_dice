package com.merlinkitsune.astral_dice.compat.curios;

import java.util.Map;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;

/**
 * 单个槽位组的句柄(Curios {@code ICurioStacksHandler} 的子集)。
 *
 * <p>实现由 {@link CuriosApi} 提供,底层是前置库 {@code TrinketsCompat.SlotHandler}
 * 包装的 Trinkets {@code TrinketInventory}。
 *
 * <p>⚠️ 相比 Curios,本适配层把修饰符 key 从 String 统一为 {@link ResourceLocation}
 * (Trinkets 3.10.0 / 1.21.1 的原生签名;1.21.1 的 {@code AttributeModifier} 本身就以
 * {@code ResourceLocation} 为 id,1.20.1 线是 {@link java.util.UUID}) —— 见
 * {@code TrinketsCompat.SlotHandler#getModifiers()} 的完整说明。
 */
public interface ICurioStacksHandler {

    /** 该组的内容容器。 */
    IItemHandler getStacks();

    /** 该组的槽位数(已计入修饰符增量)。 */
    int getSlots();

    /** 该组当前的属性修饰符(key = modifier 的 ResourceLocation id)。 */
    Map<ResourceLocation, AttributeModifier> getModifiers();

    /** 按 ResourceLocation id 移除修饰符(不存在时为 no-op)。 */
    void removeModifier(ResourceLocation id);

    /** 登记一个持久修饰符(随存档保留);用于动态调整本槽位组的槽位数。 */
    void addPermanentModifier(AttributeModifier modifier);

    /** 提交变更并触发同步。 */
    void update();
}
