package com.merlinkitsune.astral_dice.event;

import com.merlinkitsune.astral_dice.AstralDiceMod;
import com.merlinkitsune.astral_dice.entity.ModEntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;

/**
 * 自有实体的属性表注册（26.1.2）。
 *
 * <p>{@link EntityAttributeCreationEvent} 是**模组总线**事件（javap：{@code implements IModBusEvent}），
 * 而 26.1.2 的 {@code @EventBusSubscriber} 只剩 {@code value()/modid()}（FML 11.0.15 已删 {@code bus()}），
 * 静态 {@code @SubscribeEvent} 方法一律视为模组总线订阅 ⇒ 本类只写 {@code modid} 即可，
 * 不得照抄 1.21.1 的 {@code bus = EventBusSubscriber.Bus.MOD}。
 *
 * <p>属性值与 1.21.1 参考实现（{@code neoforge-1.21.1/.../event/ModEntityEvents.java}）逐字对齐：
 * 20 点生命 + 满额击退抗性（触手是布景级实体，不应被打得位移）。
 */
@EventBusSubscriber(modid = AstralDiceMod.MODID)
public final class ModEntityEvents {

    private ModEntityEvents() {
    }

    @SubscribeEvent
    public static void registerAttributes(EntityAttributeCreationEvent event) {
        event.put(ModEntityTypes.SYKE_TENTACLE.get(), LivingEntity.createLivingAttributes()
                .add(Attributes.MAX_HEALTH, 20.0D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0D)
                .build());
    }
}
