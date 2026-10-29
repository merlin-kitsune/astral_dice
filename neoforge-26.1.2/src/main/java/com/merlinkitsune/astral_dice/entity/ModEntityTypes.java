package com.merlinkitsune.astral_dice.entity;

import com.merlinkitsune.astral_dice.AstralDiceMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * 本线自有实体的注册表（26.1.2）：目前只有 GeckoLib 动画实体 Syke 触手。
 *
 * <p>注册在 {@code AstralDiceMod} 构造函数里完成（{@code ENTITY_TYPES.register(modEventBus)}），
 * 属性表由 {@code event/ModEntityEvents#registerAttributes} 在 {@code EntityAttributeCreationEvent} 上补齐，
 * 客户端渲染器由 {@code client/ModClientEvents#registerEntityRenderers} 注册。
 *
 * <p>⚠️ 26.1.2 的原版 {@code EntityType.Builder} 只剩 {@code build(ResourceKey)} 一个重载
 * （javap 实证：{@code public EntityType<T> build(net.minecraft.resources.ResourceKey<EntityType<?>>)}，
 * 1.21.1 的 {@code build(String)} 已移除）⇒ 注册工厂必须用 {@code register(String, Function<Identifier, T>)}
 * 拿到 id 再自行构造 {@link ResourceKey}。
 */
public final class ModEntityTypes {

    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
            DeferredRegister.create(Registries.ENTITY_TYPE, AstralDiceMod.MODID);

    /**
     * Syke 触手（{@code astral_dice:sykestentacle}）。
     *
     * <p>尺寸与追踪距离与 1.21.1 参考实现逐字一致：{@code sized(2.6F, 3.0F)}、{@code clientTrackingRange(10)}；
     * 类别 {@link MobCategory#MISC}（无 AI、无生成规则，仅按需召唤）。
     */
    public static final DeferredHolder<EntityType<?>, EntityType<SykeTentacleEntity>> SYKE_TENTACLE =
            ENTITY_TYPES.register("sykestentacle", id -> EntityType.Builder
                    .of(SykeTentacleEntity::new, MobCategory.MISC)
                    .sized(2.6F, 3.0F)
                    .clientTrackingRange(10)
                    .build(ResourceKey.create(Registries.ENTITY_TYPE, id)));

    private ModEntityTypes() {
    }
}
