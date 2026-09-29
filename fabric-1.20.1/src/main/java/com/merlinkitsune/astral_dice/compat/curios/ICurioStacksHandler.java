package com.merlinkitsune.astral_dice.compat.curios;

import java.util.Map;
import java.util.UUID;

import net.minecraft.world.entity.ai.attributes.AttributeModifier;

/**
 * 单个槽位组的句柄(Curios {@code ICurioStacksHandler} 的子集)。
 *
 * <p>实现由 {@link CuriosApi} 提供,底层是前置库 {@code TrinketsCompat.SlotHandler}
 * 包装的 Trinkets {@code TrinketInventory}。
 *
 * <p>⚠️ 相比 Curios,本适配层把修饰符 key 从 String 统一为 {@link UUID}
 * (Trinkets 原生签名;本模组的槽位修饰符常量本就是 UUID) —— 见
 * {@code TrinketsCompat.SlotHandler#getModifiers()} 的完整说明。
 */
public interface ICurioStacksHandler {

    /** 该组的内容容器。 */
    IItemHandler getStacks();

    /** 该组的槽位数(已计入修饰符增量)。 */
    int getSlots();

    /** 该组当前的属性修饰符(key = modifier 的 UUID)。 */
    Map<UUID, AttributeModifier> getModifiers();

    /** 按 UUID 移除修饰符(不存在时为 no-op)。 */
    void removeModifier(UUID id);

    /** 登记一个持久修饰符(随存档保留);用于动态调整本槽位组的槽位数。 */
    void addPermanentModifier(AttributeModifier modifier);

    /** 提交变更并触发同步。 */
    void update();
}
