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
 * 星愿铁砧方块：重力、铁砧音效、摔落伤害全部继承原版，但**没有耐久** —— 不参与
 * {@code AnvilBlock.damage} 的 ANVIL/CHIPPED_ANVIL 链条，也不加入 {@code minecraft:anvil} 标签。
 *
 * <p>交互打开 {@link AstralAnvilMenu}；标题 key 为 {@code container.astral_dice.astral_anvil}。
 */
public class AstralAnvilBlock extends AnvilBlock {

    /**
     * 方块编解码器。
     *
     * <p>原版 {@code AnvilBlock#codec()} 返回 {@code MapCodec<AnvilBlock>}，泛型不变 ⇒ 本类的字段与覆写
     * 都必须是 {@code MapCodec<AnvilBlock>}（目标类型推断把 {@code simpleCodec} 的 B 推到 AnvilBlock），
     * 而构造器引用仍指向本类。
     */
    public static final MapCodec<AnvilBlock> CODEC = simpleCodec(AstralAnvilBlock::new);

    public AstralAnvilBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    public MapCodec<AnvilBlock> codec() {
        return CODEC;
    }

    @Override
    protected MenuProvider getMenuProvider(BlockState state, Level level, BlockPos pos) {
        return new SimpleMenuProvider(
                (containerId, inventory, player) -> new AstralAnvilMenu(containerId, inventory,
                        ContainerLevelAccess.create(level, pos)),
                Component.translatable("container.astral_dice.astral_anvil"));
    }
}
