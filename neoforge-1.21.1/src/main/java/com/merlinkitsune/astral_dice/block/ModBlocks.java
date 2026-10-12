package com.merlinkitsune.astral_dice.block;

import com.merlinkitsune.astral_dice.AstralDiceMod;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

/** 方块注册（卡牌商店 / 筹码商店 / 事件抽取机） */
public class ModBlocks {

    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(AstralDiceMod.MODID);

    public static final DeferredBlock<Block> CARD_SHOP = BLOCKS.register("card_shop",
            // noOcclusion：模型只有 14.5/16 格高，不是满方块 —— 不声明会让贴着本方块的相邻面被错误剔除（出现透视/黑面）
            () -> new CardShopBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.WOOD)
                    .strength(2.5F)
                    .sound(SoundType.WOOD)
                    .noOcclusion()));

    /** 筹码商店方块(计划表 R19):右键打开「筹码商店」界面(候选抽取/购买/刷新全在服务端结算)。 */
    public static final DeferredBlock<Block> CHIP_SHOP = BLOCKS.register("chip_shop",
            // noOcclusion：模型底座只有 13/16 格高，不是满方块 —— 不声明会让贴着本方块的相邻面被错误剔除（出现透视/黑面）
            () -> new ChipShopBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.WOOD)
                    .strength(2.5F)
                    .sound(SoundType.WOOD)
                    .noOcclusion()));

    /** 事件抽取机(2026-10-10 事件体系重写):右键从公共事件池抽一条事件(独立注册,触发器 BLOCK_USE)。 */
    public static final DeferredBlock<Block> EVENT_DRAWER = BLOCKS.register("event_drawer",
            () -> new EventDrawBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(2.5F)
                    .sound(SoundType.METAL)));
}
