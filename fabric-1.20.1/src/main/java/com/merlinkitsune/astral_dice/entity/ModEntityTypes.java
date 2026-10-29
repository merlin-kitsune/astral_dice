package com.merlinkitsune.astral_dice.entity;

import com.merlinkitsune.astral_dice.AstralDiceMod;
import com.merlinkitsune.astral_dice.platform.registry.DeferredRegister;
import com.merlinkitsune.astral_dice.platform.registry.RegistryObject;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.ai.attributes.Attributes;

/**
 * 本模组自有的实体类型（2026-10-29 起；此前本线**没有任何实体基建**）。
 *
 * <h2>为什么走 platform/registry 垫片</h2>
 * <p>本线的注册声明面统一是 {@code DeferredRegister} + {@code RegistryObject}
 * （{@code item/ModItems}、{@code effect/ModEffects}、{@code init/ModParticles} … 共 8 个），
 * 由 {@code AstralDiceMod#commitRegistrations()} 逐个 {@code register(BUS)} 提交。
 * 实体注册沿用同一形状，好处是：① 声明点与其余内容一致，不引入第二套写法；
 * ② {@code Registry.register} 的细节留在垫片里（{@code DeferredRegister.java:126}）；
 * ③ 将来新增第二个实体只是加一行 {@code ENTITY_TYPES.register(...)}。
 *
 * <h2>属性注册为什么必须单独一步</h2>
 * <p>Fabric 的属性表不是 {@code EntityAttributeCreationEvent}（那是 Forge 侧），
 * 而是 {@code FabricDefaultAttributeRegistry.register(EntityType, AttributeSupplier.Builder)}。
 * 它要求**实体类型已注册**，因此调用点放在 {@code AstralDiceMod#onInitialize()} 的
 * {@code commitRegistrations()} 之后；缺这一步实体进世界时会因「没有属性表」抛异常。
 */
public final class ModEntityTypes {

    /** 本线的实体类型注册器（提交点：{@code AstralDiceMod#commitRegistrations()}）。 */
    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
            DeferredRegister.create(Registries.ENTITY_TYPE, AstralDiceMod.MODID);

    /**
     * {@code astral_dice:sykestentacle} —— GeckoLib 动画实体（纯展示体，无 AI）。
     *
     * <p>尺寸 {@code sized(2.6F, 3.0F)} 依据模型实测（底盘约 2.45 格宽、通高约 2.96 格）；
     * {@code clientTrackingRange(10)} 与 1.21.1 蓝本一致。
     */
    public static final RegistryObject<EntityType<SykeTentacleEntity>> SYKE_TENTACLE =
            ENTITY_TYPES.register("sykestentacle", () -> EntityType.Builder
                    .of(SykeTentacleEntity::new, MobCategory.MISC)
                    .sized(2.6F, 3.0F)
                    .clientTrackingRange(10)
                    .build("sykestentacle"));

    private ModEntityTypes() {
    }

    /**
     * 注册实体属性表（Fabric 侧 {@code EntityAttributeCreationEvent} 的等价物）。
     *
     * <p>必须在 {@link #SYKE_TENTACLE} 已提交之后调用。数值与 1.21.1 蓝本的
     * {@code ModEntityEvents} 逐项一致：{@code MAX_HEALTH = 20.0}、{@code KNOCKBACK_RESISTANCE = 1.0}。
     */
    public static void registerAttributes() {
        FabricDefaultAttributeRegistry.register(SYKE_TENTACLE.get(), LivingEntity.createLivingAttributes()
                .add(Attributes.MAX_HEALTH, 20.0D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0D));
    }
}
