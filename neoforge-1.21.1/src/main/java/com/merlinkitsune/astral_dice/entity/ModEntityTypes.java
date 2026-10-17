package com.merlinkitsune.astral_dice.entity;

import com.merlinkitsune.astral_dice.AstralDiceMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * 实体类型注册（2026-10-17 新牌「路障」引入的首个自有实体；2026-10-18「强化拒止」为第二个）。
 *
 * <p>本仓此前**没有任何自有实体**，因此这里同时确立两条范式：
 * <ul>
 *   <li>注册器本体在这里，由 {@code AstralDiceMod} 构造器 {@code .register(modEventBus)}；</li>
 *   <li>**属性**（{@code MAX_HEALTH = 30} 等）不在注册器里写，而是由
 *       {@link com.merlinkitsune.astral_dice.event.ModEntityEvents} 在
 *       {@code EntityAttributeCreationEvent} 里 {@code put} —— 这是 1.21.1 的唯一入口
 *       （{@code LivingEntity} 已没有 {@code createAttributes()} 抽象方法，
 *       属性表来自 {@code DefaultAttributes}，该事件负责填充它）。</li>
 * </ul>
 *
 * <p>{@code MobCategory.MISC}：路障不参与自然生成，也不需要刷怪管控；
 * {@code sized(1.2F, 1.0F)} 对应 bbmodel 的 20×14 像素板（宽度 1.25 格）；
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
                    .build("barricade"));

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
                    .build("repulsion"));

    private ModEntityTypes() {
    }
}
