package com.merlinkitsune.astral_dice.block;

import com.merlinkitsune.astral_dice.screen.AstralAnvilMenu;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.AnvilBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 星愿铁砧：属性与行为完全沿用原版铁砧（重力、音效、摔落伤害、朝向），
 * 但**没有耐久**，也不接 {@link AnvilBlock} 的损坏/降级逻辑。
 * <p>右键打开星愿铁砧界面（{@link AstralAnvilMenu}）——三输入槽：骰子 + 两格星币。
 * 方块本身不注册进 {@code minecraft:anvil} 标签。
 */
public class AstralAnvilBlock extends AnvilBlock {

    public AstralAnvilBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    public MapCodec<AnvilBlock> codec() {
        return simpleCodec(AstralAnvilBlock::new);
    }

    @Override
    protected MenuProvider getMenuProvider(BlockState state, Level level, BlockPos pos) {
        return new SimpleMenuProvider(
                (containerId, playerInventory, player) -> new AstralAnvilMenu(containerId, playerInventory,
                        ContainerLevelAccess.create(level, pos)),
                Component.translatable("container.astral_dice.astral_anvil"));
    }
}
