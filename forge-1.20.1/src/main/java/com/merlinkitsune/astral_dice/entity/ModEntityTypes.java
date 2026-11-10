package com.merlinkitsune.astral_dice.entity;

import com.merlinkitsune.astral_dice.AstralDiceMod;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * 实体类型注册（2026-10-17 新牌「路障」引入的首个自有实体；2026-10-17 移植到 forge-1.20.1；
 * 2026-10-18「强化拒止」为第二个自有实体）。
 *
 * <p>本仓 1.20.1 线此前**没有任何自有实体**，因此这里同时确立两条范式：
 * <ul>
 *   <li>注册器本体在这里，由 {@code AstralDiceMod} 构造器 {@code .register(modEventBus)}；</li>
 *   <li>**属性**（{@code MAX_HEALTH = 30} 等）不在注册器里写，而是由
 *       {@link com.merlinkitsune.astral_dice.event.ModEntityEvents} 在
 *       {@code EntityAttributeCreationEvent} 里 {@code put} —— 这是 1.20.1 Forge 的唯一入口
 *       （{@code LivingEntity} 已没有 {@code createAttributes()} 抽象方法，属性表来自
 *       {@code DefaultAttributes}，该事件负责填充它）。</li>
 * </ul>
 *
 * <p><b>1.20.1 平台差异</b>（相对 neoforge-1.21.1 蓝本）：注册器用通用的
 * {@code DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, MODID)} + {@code RegistryObject}，
 * 与 {@code block/ModBlocks} / {@code item/ModItems} 同款（1.21.1 用的是
 * {@code DeferredRegister.create(Registries.ENTITY_TYPE, …)} + {@code DeferredHolder}）。
 *
 * <p>{@code MobCategory.MISC}：路障不参与自然生成，也不需要刷怪管控；
 * {@code sized(1.2F, 1.0F)} 对应 bbmodel 的 20×14 像素板（宽度 1.25 格）；
 * {@code clientTrackingRange(8)} 与仇恨半径同量级，保证 6 格内的怪物与玩家都能收到实体。
 */
public final class ModEntityTypes {

    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
            DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, AstralDiceMod.MODID);

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
     * （模型略高于实体盒，属可见取舍）；{@code clientTrackingRange(10)} 覆盖 8 格吸附半径，
     * 保证半径内的玩家与怪物都能收到实体。
     *
     * <p><b>1.20.1 平台差异</b>：本线注册器类型是 {@code RegistryObject<EntityType<RepulsionEntity>>}
     * （1.21.1 蓝本是 {@code DeferredHolder<EntityType<?>, EntityType<RepulsionEntity>>}）；
     * {@code EntityType.Builder} 的构建链（{@code of / sized / clientTrackingRange / build}）两线同形。
     */
    public static final RegistryObject<EntityType<RepulsionEntity>> REPULSION =
            ENTITY_TYPES.register("repulsion", () -> EntityType.Builder
                    .of(RepulsionEntity::new, MobCategory.MISC)
                    .sized(1.6F, 2.4F)
                    .clientTrackingRange(10)
                    .build("repulsion"));

    /**
     * Syke 触手（2026-10-29；2026-10-29 由 neoforge-1.21.1 蓝本移植到本线）：本仓第一个 GeckoLib 动画实体。
     *
     * <p>尺寸 {@code sized(2.6F, 3.0F)} 取自模型实测（底盘约 2.45 格宽、通高约 2.96 格）；
     * {@code clientTrackingRange(10)} 与强化拒止同量级，保证 10 格内的客户端能追踪到该实体
     * （受伤触发的攻击动画要广播给追踪者，追踪关系由该值决定）。
     *
     * <p>{@link MobCategory#MISC} 与路障 / 强化拒止一致：不是怪物、不参与自然生成与刷怪上限。
     *
     * <p><b>1.20.1 平台差异</b>：注册器类型是 {@code RegistryObject<EntityType<SykeTentacleEntity>>}
     * （1.21.1 蓝本是 {@code DeferredHolder<EntityType<?>, EntityType<SykeTentacleEntity>>}）；
     * {@code EntityType.Builder} 的构建链（{@code of / sized / clientTrackingRange / build}）两线同形。
     */
    public static final RegistryObject<EntityType<SykeTentacleEntity>> SYKE_TENTACLE =
            ENTITY_TYPES.register("sykestentacle", () -> EntityType.Builder
                    .of(SykeTentacleEntity::new, MobCategory.MISC)
                    .sized(2.6F, 3.0F)
                    .clientTrackingRange(10)
                    .build("sykestentacle"));

    /**
     * 金鲤立牌的漂浮饮品（【葫芦饮】/【仙酿】，2026-11-09；由 neoforge-1.21.1 蓝本移植）。
     *
     * <p>尺寸 {@code sized(0.35F, 0.45F)}：瓶子模型约 0.3 格宽、0.43 格高，碰撞箱贴身即可
     * （该实体本就不参与碰撞，{@code isPickable/canBeCollidedWith} 均为 false，尺寸只用于
     * 拾取扫描的 {@code inflate} 起点与渲染包围盒）；{@code clientTrackingRange(10)} 保证
     * 10 格内的客户端能收到该实体（放置后需要立刻可见）。
     *
     * <p><b>1.20.1 平台差异</b>：注册器类型是 {@code RegistryObject<EntityType<SouriDrinkEntity>>}
     * （1.21.1 蓝本是 {@code DeferredHolder<EntityType<?>, EntityType<SouriDrinkEntity>>}）；
     * 构建链两线同形。
     */
    public static final RegistryObject<EntityType<SouriDrinkEntity>> SOURI_DRINK =
            ENTITY_TYPES.register("souri_drink", () -> EntityType.Builder
                    // 显式类型实参:该实体有 (EntityType,Level) 与 (Level,UUID,Variant) 两个构造器,
                    // `::new` 在多构造器下的目标类型推断会失败(单构造器的 Barricade/Syke 不需要)。
                    .<SouriDrinkEntity>of(SouriDrinkEntity::new, MobCategory.MISC)
                    .sized(0.35F, 0.45F)
                    .clientTrackingRange(10)
                    .build("souri_drink"));

    // 小猎手立牌(inkshadow):【传送门】。同样多构造器,必须显式类型实参;
    // 尺寸 1.2×1.2 覆盖模型可见范围,clientTrackingRange(10) 保证放置后立刻可见。
    public static final RegistryObject<EntityType<InkShadowPortalEntity>> INKSHADOW_PORTAL =
            ENTITY_TYPES.register("inkshadow_portal", () -> EntityType.Builder
                    .<InkShadowPortalEntity>of(InkShadowPortalEntity::new, MobCategory.MISC)
                    .sized(1.2F, 1.2F)
                    .clientTrackingRange(10)
                    .build("inkshadow_portal"));

    private ModEntityTypes() {
    }
}
