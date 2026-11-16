package com.merlinkitsune.astral_dice.block;

import com.merlinkitsune.astral_dice.screen.AstralAnvilMenu;
import com.mojang.serialization.MapCodec;
import javax.annotation.Nullable;
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
 * 星愿铁砧（2026-11-15）。
 *
 * <p>继承 {@link AnvilBlock}：重力下落、铁砧音效、落地砸伤与四朝向全部照原版；
 * 但**没有耐久** —— 本方块永不调用 {@link AnvilBlock#damage(BlockState)}，也不进
 * {@code minecraft:anvil} 标签（那个标签决定要不要裂/掉级，也会把本方块算进原版铁砧的破碎逻辑）。
 *
 * <p>右键打开三输入菜单 {@link AstralAnvilMenu}（槽位几何与 176×166 GUI 见菜单类头）。
 */
public class AstralAnvilBlock extends AnvilBlock {

    /**
     * 1.21.1 必须提供 Codec，否则方块在服务器启动时序列化失败。
     *
     * <p>返回类型**必须是 {@code MapCodec<AnvilBlock>}**：原版 {@link AnvilBlock#codec()} 把父类的
     * {@code MapCodec<? extends FallingBlock>} 具体化成了 {@code MapCodec<AnvilBlock>}，泛型不协变，
     * 子类写 {@code MapCodec<AstralAnvilBlock>} 会编译不过。{@code simpleCodec} 在目标类型处推断
     * {@code B = AnvilBlock}，工厂引用仍返回本类实例。
     */
    public static final MapCodec<AnvilBlock> CODEC = simpleCodec(AstralAnvilBlock::new);

    private static final Component CONTAINER_TITLE = Component.translatable("container.astral_dice.astral_anvil");

    public AstralAnvilBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    public MapCodec<AnvilBlock> codec() {
        return CODEC;
    }

    /**
     * 容器提供者：走 {@link SimpleMenuProvider} + {@link ContainerLevelAccess#create(Level, BlockPos)}，
     * 语义与 {@link AnvilBlock} 完全一致，只把菜单换成星愿铁砧菜单。
     */
    @Override
    protected @Nullable MenuProvider getMenuProvider(BlockState state, Level level, BlockPos pos) {
        return new SimpleMenuProvider(
                (containerId, inventory, player) -> new AstralAnvilMenu(containerId, inventory, ContainerLevelAccess.create(level, pos)),
                CONTAINER_TITLE);
    }
}
