package com.merlinkitsune.astral_dice.block;

import com.merlinkitsune.astral_dice.screen.AstralAnvilMenu;
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
 * 星愿铁砧:属性/重力/落地音效/摔落伤害全部继承原版铁砧,但**永不破损**
 * (无耐久、不调用 {@link AnvilBlock#damage})。
 *
 * <p>1.20.1 没有 BlockCodec(1.21+ 才有);开界面沿用 {@link AnvilBlock} 自带的 use() 路径,
 * 只需覆写 {@link #getMenuProvider};方块属性(金属色/金属音/5,1200/需正确工具)在
 * {@code ModBlocks#ASTRAL_ANVIL} 里给;本方块**不加入** minecraft:anvil 标签。
 */
public class AstralAnvilBlock extends AnvilBlock {
    /** 界面标题 container.astral_dice.astral_anvil(lang 由资源批负责)。 */
    public static final Component CONTAINER_TITLE =
            Component.translatable("container.astral_dice.astral_anvil");

    public AstralAnvilBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    public MenuProvider getMenuProvider(BlockState state, Level level, BlockPos pos) {
        return new SimpleMenuProvider(
                (containerId, inventory, player) -> new AstralAnvilMenu(
                        containerId, inventory, ContainerLevelAccess.create(level, pos)),
                CONTAINER_TITLE);
    }
}
