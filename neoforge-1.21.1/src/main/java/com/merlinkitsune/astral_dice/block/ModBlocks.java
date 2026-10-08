package com.merlinkitsune.astral_dice.block;

import com.merlinkitsune.astral_dice.AstralDiceMod;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

/** 方块注册（本批首个方块：卡牌商店） */
public class ModBlocks {

    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(AstralDiceMod.MODID);

    public static final DeferredBlock<Block> CARD_SHOP = BLOCKS.register("card_shop",
            () -> new CardShopBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.WOOD)
                    .strength(2.5F)
                    .sound(SoundType.WOOD)));
}
