package com.merlinkitsune.astral_dice.entity;

import com.merlinkitsune.astral_dice.AstralDiceMod;
import com.merlinkitsune.astral_dice.platform.registry.DeferredRegister;
import com.merlinkitsune.astral_dice.platform.registry.RegistryObject;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;

/**
 * 实体类型注册（2026-10-17 新牌「路障」引入的首个自有实体；2026-10-17 移植到 forge-1.20.1；
 * 2026-10-18「强化拒止」为第二个自有实体；2026-11-01 移植到 fabric-1.20.1）。
 *
 * <p>本仓 1.20.1 线此前**没有任何自有实体**，因此这里同时确立两条范式：
 * <ul>
 *   <li>注册器本体在这里，由 {@code AstralDiceMod#commitRegistrations()} 逐条
 *       {@code ENTITY_TYPES.register(BUS)}（fabric 线无 Mod 事件总线，注册器在提交阶段干跑）；</li>
 *   <li>**属性**（{@code MAX_HEALTH = 30} 等）不在注册器里写，而是由
 *       {@link com.merlinkitsune.astral_dice.event.ModEntityEvents} 在
 *       {@code FabricDefaultAttributeRegistry.register(type, supplier)} 里登记 —— 这是 1.20.1
 *       Fabric 的唯一入口（{@code LivingEntity} 已没有 {@code createAttributes()} 抽象方法，
 *       属性表来自 {@code DefaultAttributes}，该注册表负责填充它）。</li>
 * </ul>
 *
 * <p>2026-10-29 新增 GeckoLib 动画实体 {@code sykestentacle}（属性同样由
 * {@link com.merlinkitsune.astral_dice.event.ModEntityEvents} 登记，本类不写属性代码）。
 *
 * <p><b>1.20.1 Fabric 平台差异</b>（相对 forge-1.20.1 / neoforge-1.21.1 蓝本）：
 * 注册器用本仓自建 shim {@code com.merlinkitsune.astral_dice.platform.registry.DeferredRegister}
 * 加 {@code net.minecraft.core.registries.Registries.ENTITY_TYPE} 资源键，与 {@code block/ModBlocks} /
 * {@code item/ModItems} 同款（Forge 侧是 {@code DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, …)}）；
 * {@code EntityType.Builder} 的构建链（{@code of / sized / clientTrackingRange / build}）三线同形，
 * 因为 1.20.1 的原版 API 完全一致。
 *
 * <p>{@code MobCategory.MISC}：路障不参与自然生成，也不需要刷怪管控；
 * {@code sized(1.2F, 2.0F)} 对应 bbmodel 的 20×14 像素板（宽度 1.25 格）；
 * {@code clientTrackingRange(8)} 与仇恨半径同量级，保证 6 格内的怪物与玩家都能收到实体。
 */
public final class ModEntityTypes {

    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
            DeferredRegister.create(Registries.ENTITY_TYPE, AstralDiceMod.MODID);

    /** 路障（可见实体部分；隐形碰撞方块见 {@code block/BarricadeBlock}）。 */
    public static final RegistryObject<EntityType<BarricadeEntity>> BARRICADE =
            ENTITY_TYPES.register("barricade", () -> EntityType.Builder
                    .of(BarricadeEntity::new, MobCategory.MISC)
                    .sized(1.2F, 2.0F)
                    .clientTrackingRange(8)
                    .build("barricade"));

    /**
     * 强化拒止（2026-10-18 新效果牌「强化拒止」，属「怪力乱神」立牌；该立牌会在后续添加；
     * 2026-10-18 移植到本线）。
     *
     * <p>尺寸 {@code sized(1.6F, 2.4F)}：bbmodel 的菱形头对角线约 1.06 格宽、整体高约 2.47 格
     * （模型略高于实体盒，属可见取舍）；{@code clientTrackingRange(10)} 覆盖吸附半径，
     * 保证半径内的玩家与怪物都能收到实体。
     */
    public static final RegistryObject<EntityType<RepulsionEntity>> REPULSION =
            ENTITY_TYPES.register("repulsion", () -> EntityType.Builder
                    .of(RepulsionEntity::new, MobCategory.MISC)
                    .sized(1.6F, 2.4F)
                    .clientTrackingRange(10)
                    .build("repulsion"));

    /**
     * {@code astral_dice:sykestentacle} —— GeckoLib 动画实体（纯展示体，无 AI；
     * 2026-10-29 于本线新增，2026-11-01 随主线合并保留）。
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
}
