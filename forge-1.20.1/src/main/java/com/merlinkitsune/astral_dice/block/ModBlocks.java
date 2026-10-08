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
            () -> new CardShopBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.WOOD)
                    .strength(2.5F)
                    .sound(SoundType.WOOD)));
}
