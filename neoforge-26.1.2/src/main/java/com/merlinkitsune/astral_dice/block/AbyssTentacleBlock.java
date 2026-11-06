package com.merlinkitsune.astral_dice.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.level.pathfinder.PathType;
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
 *   <li>{@link RenderShape#INVISIBLE}：不渲染（视觉由实体模型 + 魔渊污泥承担）；</li>
 *   <li>{@code strength(-1, 3600000)} + {@code noLootTable}：生存玩家拆不掉、破坏不掉东西；</li>
 *   <li>寻路按「可走」处理：怪物能规划到贴脸位置攻击触须，但碰撞箱照旧挡住它们（与路障同口径）。</li>
 * </ul>
 */
public class AbyssTentacleBlock extends Block {

    private static final VoxelShape SHAPE = Shapes.box(0.0D, 0.0D, 0.0D, 1.0D, 1.0D, 1.0D);

    public AbyssTentacleBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos,
                                           CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.INVISIBLE;
    }

    @Override
    protected boolean isPathfindable(BlockState state, PathComputationType pathComputationType) {
        return true;
    }

    @Override
    public PathType getBlockPathType(BlockState state, BlockGetter level, BlockPos pos, Mob mob) {
        return PathType.WALKABLE;
    }
}
