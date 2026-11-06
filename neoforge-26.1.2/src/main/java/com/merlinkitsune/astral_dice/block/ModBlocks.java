package com.merlinkitsune.astral_dice.block;

import com.merlinkitsune.astral_dice.AstralDiceMod;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

/** 方块注册（卡牌商店 / 筹码商店 / 抽奖盒） */
public class ModBlocks {

    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(AstralDiceMod.MODID);

    public static final DeferredBlock<Block> CARD_SHOP = BLOCKS.<Block>registerBlock("card_shop",
            CardShopBlock::new,
            // noOcclusion：模型只有 14.5/16 格高，不是满方块 —— 不声明会让贴着本方块的相邻面被错误剔除（出现透视/黑面）
            props -> props
                    .mapColor(MapColor.WOOD)
                    .strength(2.5F)
                    .sound(SoundType.WOOD)
                    .noOcclusion());

    /** 筹码商店方块(计划表 R19):右键打开「筹码商店」界面(候选抽取/购买/刷新全在服务端结算)。 */
    public static final DeferredBlock<Block> CHIP_SHOP = BLOCKS.<Block>registerBlock("chip_shop",
            ChipShopBlock::new,
            // noOcclusion：模型底座只有 13/16 格高，不是满方块 —— 不声明会让贴着本方块的相邻面被错误剔除（出现透视/黑面）
            props -> props
                    .mapColor(MapColor.WOOD)
                    .strength(2.5F)
                    .sound(SoundType.WOOD)
                    .noOcclusion());

    /**
     * 路障的隐形碰撞方块(2026-10-17「路障」效果牌):**没有 BlockItem**,只能由
     * `entity/BarricadeEntity` 放置/删除。碰撞箱 1.5 格高(栅栏高度,跳不过去)、
     * 不渲染、不参与寻路、不可破坏 —— 详见 {@link BarricadeBlock} 类头。
     */
    public static final DeferredBlock<Block> BARRICADE_COLLISION = BLOCKS.<Block>registerBlock("barricade_collision",
            BarricadeBlock::new,
            props -> props
                    .mapColor(MapColor.WOOD)
                    .strength(-1.0F, 3600000.0F)   // 不可破坏:拆路障走"打实体扣耐久",不是拆方块
                    .sound(SoundType.WOOD)
                    .noOcclusion()
                    .noLootTable()
                    .isRedstoneConductor((state, level, pos) -> false)
                    .isSuffocating((state, level, pos) -> false)
                    .isViewBlocking((state, level, pos) -> false));

    /**
     * 魔渊触须占格方块（隐形、不可破坏）。
     *
     * <p>和路障同一套范式：魔渊触须实体存活期间占住自己那一格
     * （不能再放其它方块 / 实体），实体消失时对应移除。
     */
    public static final DeferredBlock<Block> ABYSS_TENTACLE_COLLISION = BLOCKS.<Block>registerBlock("abyss_tentacle_collision",
            AbyssTentacleBlock::new,
            props -> props
                    .mapColor(MapColor.COLOR_PURPLE)
                    .strength(-1.0F, 3600000.0F)   // 不可破坏：只能由魔渊触须自己收回
                    .sound(SoundType.SCULK)
                    .noOcclusion()
                    .noLootTable()
                    .isRedstoneConductor((state, level, pos) -> false)
                    .isSuffocating((state, level, pos) -> false)
                    .isViewBlocking((state, level, pos) -> false));

    /** 抽奖盒(2026-10-10 事件体系重写):右键从公共事件池抽一条事件(独立注册,触发器 BLOCK_USE)。 */
    public static final DeferredBlock<Block> EVENT_DRAWER = BLOCKS.<Block>registerBlock("event_drawer",
            EventDrawBlock::new,
            props -> props
                    .mapColor(MapColor.METAL)
                    .strength(2.5F)
                    .sound(SoundType.METAL));
}
