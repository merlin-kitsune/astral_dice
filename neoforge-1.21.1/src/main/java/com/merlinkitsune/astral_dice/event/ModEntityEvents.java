package com.merlinkitsune.astral_dice.event;

import com.merlinkitsune.astral_dice.AstralDiceMod;
import com.merlinkitsune.astral_dice.entity.ModEntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;

/**
 * 自有实体的**属性**注册（2026-10-17「路障」引入）。
 *
 * <p>1.21.1 的实体属性表由 {@code DefaultAttributes} 保存，唯一填充入口是
 * {@link EntityAttributeCreationEvent}（Mod 总线）。**漏注册的后果**：实体一创建就在
 * {@code new AttributeMap(DefaultAttributes.getSupplier(type))} 处抛异常 ⇒ 生成即崩，
 * 因此本类是「路障能放下」的必要条件，不是可选优化。
 *
 * <p>路障：{@code MAX_HEALTH = 30}（用户需求「路障本身30点耐久」）。
 * {@code LivingEntity.createLivingAttributes()} 已带上全套基础属性
 * （移动速度、护甲、击退抗性…），这里只把最大生命从默认 20 覆写为 30。
 */
@EventBusSubscriber(modid = AstralDiceMod.MODID, bus = EventBusSubscriber.Bus.MOD)
public final class ModEntityEvents {

    private ModEntityEvents() {
    }

    @SubscribeEvent
    public static void onEntityAttributeCreation(EntityAttributeCreationEvent event) {
        event.put(ModEntityTypes.BARRICADE.get(),
                LivingEntity.createLivingAttributes()
                        .add(Attributes.MAX_HEALTH, 30.0D)
                        .build());

        // 强化拒止(2026-10-18):100 点耐久(用户需求「强化拒止拥有100点耐久」)。
        // 耐久直接由 getHealth() 承载,扣到 <= 0 时自毁(见 entity/RepulsionEntity#consumeDurability)
        event.put(ModEntityTypes.REPULSION.get(),
                LivingEntity.createLivingAttributes()
                        .add(Attributes.MAX_HEALTH, 100.0D)
                        .build());
    }
}
