package com.merlinkitsune.astral_dice.compat.curios;

import net.minecraft.world.entity.LivingEntity;

/**
 * 饰品槽位上下文(Fabric 侧适配) —— 形状对齐 Curios 5.x 的 {@code top.theillusivec4.curios.api.SlotContext}。
 *
 * <p>本模组只用到 {@link #entity()} 与 {@link #identifier()}(实测:40 处 {@code slotContext.entity()}、
 * 3 处 {@code slotContext.identifier()}),故这里保留这两个访问器 + {@link #index()}。
 *
 * <p>Fabric 侧的底层是 Trinkets 的 {@code SlotReference}(只带 {@code inventory()} 与 {@code index()}),
 * 槽位名从 {@code inventory().getSlotType().getName()} 取。
 */
public record SlotContext(LivingEntity entity, String identifier, int index) {

    /** 槽位标识(对应 Curios 的 {@code dice} / {@code stand} / {@code chip})。 */
    @Override
    public String identifier() {
        return identifier;
    }

    public String slotId() {
        return identifier;
    }
}
