package com.merlinkitsune.astral_dice.block;

import com.merlinkitsune.astral_dice.AstralDiceMod;
import com.merlinkitsune.astral_dice.platform.registry.DeferredRegister;
import com.merlinkitsune.astral_dice.platform.registry.RegistryObject;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;

/**
 * 方块注册表（fabric 线）。
 *
 * <p>2026-11-01 移植自 forge-1.20.1 {@code block/ModBlocks.java}，**注册项、注册顺序、属性逐条一致**：
 * <ol>
 *   <li>{@code card_shop} → {@link CardShopBlock}</li>
 *   <li>{@code chip_shop} → {@link ChipShopBlock}</li>
 *   <li>{@code event_drawer} → {@link EventDrawBlock}</li>
 *   <li>{@code barricade_collision} → {@link BarricadeBlock}（**无 BlockItem**，仅路障实体使用的隐形碰撞方块）</li>
 * </ol>
 *
 * <h2>平台差异（fabric 等价实现）</h2>
 * <ul>
 *   <li>注册容器：forge 用 {@code DeferredRegister.create(ForgeRegistries.BLOCKS, MODID)}；
 *       fabric 用 {@code DeferredRegister.create(Registries.BLOCK, MODID)}（原版 {@code ResourceKey} 入口，
 *       见 {@code platform/registry/DeferredRegister#create(ResourceKey, String)}）。</li>
 *   <li>属性：1.20.1 两侧都是原版 {@link BlockBehaviour.Properties#of()}，**无需** FabricBlockSettings。</li>
 *   <li>BlockItem：1.20.1 无 {@code registerSimpleBlockItem}，两侧都在物品侧手工注册 ——
 *       fabric 侧见 {@code item/ModItems.java} 的 {@code CARD_SHOP} / {@code CHIP_SHOP} / {@code EVENT_DRAWER}。</li>
 * </ul>
 *
 * <h2>接线（由 Lead 在 {@code AstralDiceMod#commitRegistrations()} 完成）</h2>
 * <p><b>必须插在该方法的第一行（{@code ModItems.ITEMS.register(BUS)} 之前）</b>：
 * BlockItem 的供应商在 commit 时才求值，但它引用的 {@code ModBlocks.X.get()} 要求方块已绑定。
 * 调用一行即可：{@code com.merlinkitsune.astral_dice.block.ModBlocks.BLOCKS.register(BUS);}
 * （等价静态入口 {@link #bootstrap()} 也已提供）。
 */
public final class ModBlocks {

    public static final DeferredRegister<Block> BLOCKS =
            DeferredRegister.create(Registries.BLOCK, AstralDiceMod.MODID);

    /** 卡牌商店：木色、2.5 硬度、木质音效、不剔除相邻面（模型比满格矮）。 */
    public static final RegistryObject<Block> CARD_SHOP = BLOCKS.register("card_shop",
            () -> new CardShopBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.WOOD)
                    .strength(2.5F)
                    .sound(SoundType.WOOD)
                    .noOcclusion()));

    /** 筹码商店：属性同卡牌商店。 */
    public static final RegistryObject<Block> CHIP_SHOP = BLOCKS.register("chip_shop",
            () -> new ChipShopBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.WOOD)
                    .strength(2.5F)
                    .sound(SoundType.WOOD)
                    .noOcclusion()));

    /** 事件抽屉：金属色/金属音效；**故意不加 noOcclusion**（满格方块，与 forge 一致）。 */
    public static final RegistryObject<Block> EVENT_DRAWER = BLOCKS.register("event_drawer",
            () -> new EventDrawBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(2.5F)
                    .sound(SoundType.METAL)));

    /**
     * 路障的隐形碰撞方块：不可破坏（拆路障走打实体那条路）、不掉落、不参与光照/窒息/红石。
     * **没有 BlockItem**（玩家拿不到，也不进创造栏）。
     */
    public static final RegistryObject<Block> BARRICADE_COLLISION = BLOCKS.register("barricade_collision",
            () -> new BarricadeBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.WOOD)
                    .strength(-1.0F, 3600000.0F)
                    .sound(SoundType.WOOD)
                    .noOcclusion()
                    .noLootTable()
                    .isRedstoneConductor((state, level, pos) -> false)
                    .isSuffocating((state, level, pos) -> false)
                    .isViewBlocking((state, level, pos) -> false)));

    private ModBlocks() {
    }

    /**
     * 静态入口：提交方块注册表。与 {@code ModBlocks.BLOCKS.register(AstralDiceMod.BUS)} 等价，
     * 供 {@code AstralDiceMod#commitRegistrations()} 首行调用（**必须在物品注册之前**）。
     */
    public static void bootstrap() {
        BLOCKS.register(AstralDiceMod.BUS);
    }
}
