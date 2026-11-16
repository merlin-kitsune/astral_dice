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
 * 星愿铁砧：三输入升星工作台（骰子 + 两格材料 -> 升星骰子）。
 *
 * <p>外观与物理行为完全沿用原版 {@link AnvilBlock}（重力、落地音效、砸落伤害），
 * 但星愿铁砧是永久方块：没有耐久、不调用 {@code AnvilBlock#damage}，
 * 也不加入 {@code minecraft:anvil} 标签，因此不会触发原版铁砧的损坏逻辑。
 *
 * <p>1.20.1 没有 Codec，方块状态直接继承原版铁砧的 {@code FACING}。
 */
public class AstralAnvilBlock extends AnvilBlock {

    public AstralAnvilBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    /**
     * 打开星愿铁砧菜单。标题键 {@code container.astral_dice.astral_anvil} 由客户端语言文件提供。
     */
    @Override
    public MenuProvider getMenuProvider(BlockState state, Level level, BlockPos pos) {
        return new SimpleMenuProvider(
                (containerId, inventory, player) -> new AstralAnvilMenu(containerId, inventory,
                        ContainerLevelAccess.create(level, pos)),
                Component.translatable("container.astral_dice.astral_anvil"));
    }
}
