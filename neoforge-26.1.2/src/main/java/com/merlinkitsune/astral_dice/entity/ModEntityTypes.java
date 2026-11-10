package com.merlinkitsune.astral_dice.entity;

import com.merlinkitsune.astral_dice.AstralDiceMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * 实体类型注册（2026-10-17 新牌「路障」引入的首个自有实体；2026-10-18「强化拒止」为第二个；
 * 2026-10-29 GeckoLib 动画实体「Syke 触手」为第三个）。
 *
 * <p>本仓此前**没有任何自有实体**，因此这里同时确立两条范式：
 * <ul>
 *   <li>注册器本体在这里，由 {@code AstralDiceMod} 构造器 {@code .register(modEventBus)}；</li>
 *   <li>**属性**（{@code MAX_HEALTH = 30} 等）不在注册器里写，而是由
 *       {@link com.merlinkitsune.astral_dice.event.ModEntityEvents} 在
 *       {@code EntityAttributeCreationEvent} 里 {@code put} —— 这是 26.1.2 的唯一入口
 *       （{@code LivingEntity} 已没有 {@code createAttributes()} 抽象方法，
 *       属性表来自 {@code DefaultAttributes}，该事件负责填充它）。</li>
 * </ul>
 *
 * <p>⚠️ 26.1.2 的原版 {@code EntityType.Builder} 只剩 {@code build(ResourceKey)} 一个重载
 * （javap 实证：{@code public EntityType<T> build(net.minecraft.resources.ResourceKey<EntityType<?>>)}，
 * 1.21.1 的 {@code build(String)} 已移除）⇒ 注册工厂必须自行构造 {@link ResourceKey}。
 * 两条注册重载在 26.1.2 都存在（javap 实证）：路障 / 强化拒止走
 * {@code register(String, Supplier)} + {@code Identifier.fromNamespaceAndPath(...)}，
 * Syke 触手走 {@code register(String, Function<Identifier, T>)} 直接拿注册 id 现拼。
 *
 * <p>{@code MobCategory.MISC}：路障不参与自然生成，也不需要刷怪管控；
 * {@code sized(1.2F, 2.0F)} 对应 bbmodel 的 20×14 像素板（宽度约 1.25 格，实体盒高 2 格）；
 * {@code clientTrackingRange(8)} 与仇恨半径同量级，保证 6 格内的怪物与玩家都能收到实体。
 */
public final class ModEntityTypes {

    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
            DeferredRegister.create(Registries.ENTITY_TYPE, AstralDiceMod.MODID);

    /** 路障（可见实体部分；隐形碰撞方块见 {@code block/BarricadeBlock}）。 */
    public static final DeferredHolder<EntityType<?>, EntityType<BarricadeEntity>> BARRICADE =
            ENTITY_TYPES.register("barricade", () -> EntityType.Builder
                    .of(BarricadeEntity::new, MobCategory.MISC)
                    .sized(1.2F, 2.0F)
                    .clientTrackingRange(8)
                    .build(ResourceKey.create(Registries.ENTITY_TYPE,
                            Identifier.fromNamespaceAndPath(AstralDiceMod.MODID, "barricade"))));

    /**
     * 强化拒止（2026-10-18 新效果牌「强化拒止」，属「怪力乱神」立牌；该立牌会在后续添加）。
     *
     * <p>尺寸 {@code sized(1.6F, 2.4F)}：bbmodel 的菱形头对角线约 1.06 格宽、整体高约 2.47 格
     * （模型略高于实体盒，属可见取舍）；{@code clientTrackingRange(10)} 覆盖 8 格吸附半径，
     * 保证半径内的玩家与怪物都能收到实体。
     */
    public static final DeferredHolder<EntityType<?>, EntityType<RepulsionEntity>> REPULSION =
            ENTITY_TYPES.register("repulsion", () -> EntityType.Builder
                    .of(RepulsionEntity::new, MobCategory.MISC)
                    .sized(1.6F, 2.4F)
                    .clientTrackingRange(10)
                    .build(ResourceKey.create(Registries.ENTITY_TYPE,
                            Identifier.fromNamespaceAndPath(AstralDiceMod.MODID, "repulsion"))));

    /**
     * Syke 触手（{@code astral_dice:sykestentacle}，2026-10-29）：本仓第一个 GeckoLib 动画实体，
     * 同时作为另外三条加载器线的移植参考。
     *
     * <p>尺寸 {@code sized(2.6F, 3.0F)} 来自模型实测：底盘（触手最外圈）约 2.45 格宽、通高约 2.96 格，
     * 向上取整并留少量余量，避免命中框比可见模型窄；{@code clientTrackingRange(10)} 与命中框同量级，
     * 保证渲染距离内的客户端都收到该实体 —— 动画在客户端播放，追踪范围偏小会直接看不到。
     *
     * <p>{@code MobCategory.MISC} 与上面两个实体一致：不参与自然生成，也不进刷怪管控。
     * 注册工厂走 {@code register(String, Function<Identifier, T>)} 直接拿到注册 id，
     * 故不需要像上面两个实体那样手写 {@code Identifier}。
     */
    public static final DeferredHolder<EntityType<?>, EntityType<SykeTentacleEntity>> SYKE_TENTACLE =
            ENTITY_TYPES.register("sykestentacle", id -> EntityType.Builder
                    .of(SykeTentacleEntity::new, MobCategory.MISC)
                    .sized(2.6F, 3.0F)
                    .clientTrackingRange(10)
                    .build(ResourceKey.create(Registries.ENTITY_TYPE, id)));

    /**
     * 金鲤漂浮饮品（{@code astral_dice:souri_drink}，2026-11-09）：无碰撞、无重力、不可推动的纯展示实体；
     * 尺寸按 bbmodel 实测（约 0.9 格高、0.6 格宽），拾取扫描在实体自己的 tick 内完成。
     *
     * <p>26.1.2 只有 {@code build(ResourceKey)} 重载 ⇒ 走 {@code register(String, Function<Identifier, T>)}，
     * 注册 id 由加载器直接注入（与 {@link #SYKE_TENTACLE} 同法）。
     */
    public static final DeferredHolder<EntityType<?>, EntityType<SouriDrinkEntity>> SOURI_DRINK =
            ENTITY_TYPES.register("souri_drink", id -> EntityType.Builder
                    .<SouriDrinkEntity>of(SouriDrinkEntity::new, MobCategory.MISC)
                    .sized(0.35F, 0.45F)
                    .clientTrackingRange(10)
                    .build(ResourceKey.create(Registries.ENTITY_TYPE, id)));

    private ModEntityTypes() {
    }
}
