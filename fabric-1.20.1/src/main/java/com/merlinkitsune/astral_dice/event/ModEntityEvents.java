package com.merlinkitsune.astral_dice.event;

import com.merlinkitsune.astral_dice.entity.ModEntityTypes;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;

/**
 * 自有实体的**属性**注册（2026-10-17「路障」引入；2026-10-17 移植到 forge-1.20.1；
 * 2026-11-01 移植到 fabric-1.20.1）。
 *
 * <p>1.20.1 的实体属性表由 {@code DefaultAttributes} 保存，Fabric 侧唯一填充入口是
 * {@code FabricDefaultAttributeRegistry#register(EntityType, AttributeSupplier.Builder)}
 * （Forge 侧是 {@code EntityAttributeCreationEvent}；Fabric 没有事件，只有命令式注册表）。
 * **漏注册的后果**：实体一创建就在
 * {@code new AttributeMap(DefaultAttributes.getSupplier(type))} 处抛异常 ⇒ 生成即崩，
 * 因此本类是「路障能放下」的必要条件，不是可选优化。
 *
 * <p><b>调用时机（Lead 接线）</b>：必须在 {@code ModEntityTypes.ENTITY_TYPES} 已提交之后，
 * 也就是 {@code AstralDiceMod#commitRegistrations()} 之后调用一次
 * {@link #bootstrap()}（本类只提供静态入口，不动 {@code AstralDiceMod}）。
 * {@code register} 内部会读 {@code ModEntityTypes.BARRICADE.get()}，注册器未提交时
 * {@code RegistryObject#get()} 抛 {@code IllegalStateException}；重复调用同一 EntityType 也会崩。
 *
 * <p>路障：{@code MAX_HEALTH = 30}（用户需求「路障本身30点耐久」）。
 * {@code LivingEntity.createLivingAttributes()} 已带上全套基础属性
 * （移动速度、护甲、击退抗性…），这里只把最大生命从默认 20 覆写为 30。
 */
public final class ModEntityEvents {

    private ModEntityEvents() {
    }

    /**
     * 一次性登记全部自有实体的属性表。由 {@code AstralDiceMod#onInitialize()} 在
     * {@code commitRegistrations()} 之后调用一次（common 与 client entrypoint 共用同一个
     * 静态入口，重复调用会抛异常）。
     */
    public static void bootstrap() {
        FabricDefaultAttributeRegistry.register(ModEntityTypes.BARRICADE.get(),
                LivingEntity.createLivingAttributes()
                        .add(Attributes.MAX_HEALTH, 30.0D)
                        .build());

        // 强化拒止(2026-10-18;2026-10-18 移植到本线;2026-10-21 总量由 100 调整为 50):50 点耐久。
        // 耐久直接由 getHealth() 承载,扣到 <= 0 时自毁(见 entity/RepulsionEntity#consumeDurability);
        // 扣减节奏不变(每 200 tick 扣 min(吸附目标数, 5)),故 50 点 = 最多 10 次满额结算。
        FabricDefaultAttributeRegistry.register(ModEntityTypes.REPULSION.get(),
                LivingEntity.createLivingAttributes()
                        .add(Attributes.MAX_HEALTH, 50.0D)
                        .build());
    }
}
