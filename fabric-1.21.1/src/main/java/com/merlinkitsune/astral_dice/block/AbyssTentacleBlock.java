package com.merlinkitsune.astral_dice.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * 「魔渊区域」的隐形占格方块（注册 id {@code astral_dice:abyss_tentacle_collision}，**没有 BlockItem**）。
 *
 * <p>用户裁决（2026-11-06）：「触须所在的格子具有碰撞箱，且不能在该格放置任何其它方块和实体」。
 * 做法照 {@link BarricadeBlock} 的既有范式：一个满格碰撞箱的隐形方块，生命周期完全由
 * {@code entity/SykeTentacleEntity} 管理（放置 / 移除都在实体里），方块自身无状态、无 BlockEntity。
 *
 * <ul>
 *   <li>碰撞箱 = 整格 {@code (0,0,0)-(1,1,1)} ⇒ 实体站不进去、方块也无法被替换（格子已被占）；</li>
 *   <li>{@link RenderShape#INVISIBLE}：不渲染；</li>
 *   <li>{@code strength(-1, 3600000)} + {@code noLootTable}（在 {@code ModBlocks} 里给）：拆不掉、不掉落；</li>
 *   <li>寻路按「可走」处理（{@link PathComputationType#LAND} 返回 true）。</li>
 * </ul>
 *
 * <p><b>平台差异（唯一一处）</b>：P0（neoforge-1.21.1）另覆写了
 * {@code getBlockPathType(BlockState, BlockGetter, BlockPos, Mob)} 返回 {@code PathType.WALKABLE} ——
 * 该重载是 Forge/NeoForge 的补丁方法，<b>fabric 的 {@code Block} 上没有它</b>（写了会编译失败：
 * 「方法不会覆盖或实现超类型的方法」）。本线只保留 fabric 侧存在的
 * {@link #isPathfindable(BlockState, PathComputationType)}，寻路语义（可规划到该格）不变。
 */
public class AbyssTentacleBlock extends Block {

    private static final VoxelShape SHAPE = Shapes.box(0.0D, 0.0D, 0.0D, 1.0D, 1.0D, 1.0D);

    public AbyssTentacleBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.INVISIBLE;
    }

    @Override
    protected boolean isPathfindable(BlockState state, PathComputationType type) {
        return true;
    }
}
