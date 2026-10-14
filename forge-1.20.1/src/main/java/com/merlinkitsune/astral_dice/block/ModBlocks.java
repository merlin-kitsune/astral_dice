package com.merlinkitsune.astral_dice.block;

import com.merlinkitsune.astral_dice.AstralDiceMod;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * 方块注册（1.20.1 Forge 线首个方块：卡牌商店）。
 *
 * <p>平台差异：1.21.1 用 {@code DeferredRegister.Blocks} + {@code DeferredBlock<Block>}
 * （NeoForge 专有）；1.20.1 Forge 只有通用的
 * {@code DeferredRegister.create(ForgeRegistries.BLOCKS, MODID)} + {@code RegistryObject<Block>}，
 * 注册行与 {@code item/ModItems.java} 同款（由 {@code AstralDiceMod} 的 mod 事件总线注册）。
 */
public class ModBlocks {

    public static final DeferredRegister<Block> BLOCKS =
            DeferredRegister.create(ForgeRegistries.BLOCKS, AstralDiceMod.MODID);

    public static final RegistryObject<Block> CARD_SHOP = BLOCKS.register("card_shop",
            // noOcclusion：模型只有 14.5/16 格高，不是满方块 —— 不声明会让贴着本方块的相邻面被错误剔除（出现透视/黑面）
            () -> new CardShopBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.WOOD)
                    .strength(2.5F)
                    .sound(SoundType.WOOD)
                    .noOcclusion()));

    /** 筹码商店方块(2026-10-12 移植):放置后右键打开「筹码商店」界面。 */
    public static final RegistryObject<Block> CHIP_SHOP = BLOCKS.register("chip_shop",
            // 与卡牌商店同款:模型高过一格,声明 noOcclusion 免得贴着本方块的相邻面被错误剔除
            () -> new ChipShopBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.WOOD)
                    .strength(2.5F)
                    .sound(SoundType.WOOD)
                    .noOcclusion()));

    /** 抽奖盒(2026-10-10 事件体系重写):右键从公共事件池抽一条事件(BLOCK_USE)。 */
    public static final RegistryObject<Block> EVENT_DRAWER = BLOCKS.register("event_drawer",
            () -> new EventDrawBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(2.5F)
                    .sound(SoundType.METAL)));
}
